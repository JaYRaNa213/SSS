package com.sss.app.capture

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.sss.app.MainActivity
import com.sss.app.R
import com.sss.app.data.repository.FolderRepository
import com.sss.app.data.repository.ScreenshotMetaRepository
import com.sss.app.data.repository.ScreenshotRepository
import com.sss.app.data.repository.SessionRepository
import com.sss.app.data.repository.UndoDeleteManager
import com.sss.app.data.settings.AppSettings
import com.sss.app.data.settings.SettingsKeys
import com.sss.app.data.settings.SettingsRepository
import com.sss.app.ui.theme.SSSTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@AndroidEntryPoint
class OverlayBubbleService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null

    private var bubbleView: AssistiveBubbleView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var closeView: CloseDotView? = null
    private var panelView: View? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var labelView: TextView? = null
    private var longPressRunnable: Runnable? = null

    @Inject lateinit var folderRepository: FolderRepository
    @Inject lateinit var sessionRepository: SessionRepository
    @Inject lateinit var screenshotRepository: ScreenshotRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var metaRepository: ScreenshotMetaRepository
    @Inject lateinit var undoManager: UndoDeleteManager

    private var settings = AppSettings()
    private val screenshotObserver by lazy { ScreenshotObserver(applicationContext) { uri -> onScreenshotDetected(uri) } }
    private var sessionJob: Job? = null
    private var settingsJob: Job? = null
    private var badgeJob: Job? = null
    private var lastFolderId: Long? = null
    private var lastBadge: Pair<Long?, Int>? = null
    private var activeName: String? = null

    private val idleRunnable = Runnable {
        bubbleView?.animate()?.alpha(settings.bubbleOpacity.coerceIn(0.2f, 1f))?.setDuration(400)?.start()
    }
    private val removeLabelRunnable = Runnable { removeLabel() }

    companion object {
        const val TAG = "SSS_OVERLAY"
        const val ACTION_SHOW = "com.sss.app.action.SHOW_BUBBLE"
        const val ACTION_HIDE = "com.sss.app.action.HIDE_BUBBLE"
        const val ACTION_STOP_CAPTURE = "com.sss.app.action.STOP_CAPTURE"
        const val ACTION_TOGGLE_PAUSE = "com.sss.app.action.TOGGLE_PAUSE"
        private const val CHANNEL_ID = "sss_capture"
        private const val NOTIF_ID = 1001

        /** Starts the service (screenshot detection + bubble). Works even without the overlay permission (no bubble then). */
        fun showBubble(context: Context) {
            val intent = Intent(context, OverlayBubbleService::class.java).apply { action = ACTION_SHOW }
            ContextCompat.startForegroundService(context, intent)
        }

        fun hideBubble(context: Context) {
            context.startService(Intent(context, OverlayBubbleService::class.java).apply { action = ACTION_HIDE })
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> stopServiceAndHideOverlay()
            ACTION_STOP_CAPTURE -> serviceScope.launch {
                sessionRepository.stopSession()
                stopServiceAndHideOverlay()
            }
            ACTION_TOGGLE_PAUSE -> serviceScope.launch {
                val cur = settingsRepository.settings.first().capturePaused
                settingsRepository.set(SettingsKeys.CAPTURE_PAUSED, !cur)
            }
            else -> {   // ACTION_SHOW, or null when Android restarts the sticky service
                goForeground()   // must be first: startForegroundService() allows ~5 seconds
                serviceScope.launch {
                    settings = settingsRepository.settings.first()
                    showOverlay()
                    startWatchers()
                }
            }
        }
        return START_STICKY
    }

    // ------------------------------------------------------------------ notification
    private fun buildNotification(folderName: String?, paused: Boolean): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Screenshot capture", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        fun svc(code: Int, action: String) = PendingIntent.getService(
            this, code, Intent(this, OverlayBubbleService::class.java).setAction(action), flags
        )
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), flags)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_4s)
            .setColor(0xFFFEC84A.toInt())
            .setContentTitle(if (paused) "4S is paused" else "4S is capturing")
            .setContentText(folderName?.let { "Saving to $it" } ?: "New screenshots go to your selected folder")
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Stop", svc(1, ACTION_STOP_CAPTURE))
            .addAction(0, if (paused) "Resume" else "Pause", svc(2, ACTION_TOGGLE_PAUSE))
            .addAction(0, "Show bubble", svc(3, ACTION_SHOW))
            .build()
    }

    private fun goForeground() {
        val n = buildNotification(null, settings.capturePaused)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun updateNotification(folderName: String?, paused: Boolean) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(folderName, paused))
    }

    // ------------------------------------------------------------------ watchers
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun startWatchers() {
        if (settingsJob?.isActive != true) {
            settingsJob = serviceScope.launch {
                settingsRepository.settings.collect { s ->
                    settings = s
                    applyBubbleSettings()
                }
            }
        }

        if (sessionJob?.isActive != true) {
            sessionJob = serviceScope.launch {
                combine(
                    sessionRepository.observeActiveSession(),
                    settingsRepository.settings.map { it.capturePaused }.distinctUntilChanged()
                ) { s, p -> s to p }.collect { (session, paused) ->
                    if (session != null && session.isActive) {
                        bubbleView?.paused = paused
                        val name = folderRepository.getFoldersOnce().firstOrNull { it.id == session.folderId }?.name
                        activeName = name
                        updateNotification(name, paused)
                        if (paused) screenshotObserver.stop() else screenshotObserver.start()   // start() is idempotent
                        val prev = lastFolderId
                        if (prev != null && prev != session.folderId && name != null) showFolderLabel("Now saving to $name")
                        lastFolderId = session.folderId
                    } else {
                        screenshotObserver.stop()
                        // the app writes the session row a moment AFTER starting this service: wait and re-check
                        delay(2_000)
                        val again = sessionRepository.getActiveSession()
                        if (again == null || !again.isActive) stopServiceAndHideOverlay()
                    }
                }
            }
        }

        if (badgeJob?.isActive != true) {
            badgeJob = serviceScope.launch {
                sessionRepository.observeActiveSession()
                    .map { it?.takeIf { s -> s.isActive }?.folderId }
                    .distinctUntilChanged()
                    .flatMapLatest { id ->
                        if (id == null) flowOf<Pair<Long?, Int>>(null to 0)
                        else screenshotRepository.getScreenshotsByFolder(id).map { id to it.size }
                    }
                    .collect { cur ->
                        val prev = lastBadge
                        lastBadge = cur
                        bubbleView?.count = cur.second
                        // pulse only when the SAME folder got a new screenshot (not when switching folders)
                        if (prev != null && prev.first == cur.first && cur.second > prev.second) {
                            bubbleView?.pulse()
                            resetIdleTimer()
                            activeName?.let { showFolderLabel("Saved to $it") }
                            if (settings.haptic) bubbleView?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                    }
            }
        }
    }

    private fun onScreenshotDetected(uri: Uri) {   // called on a background thread
        serviceScope.launch(Dispatchers.IO) {
            try {
                val session = sessionRepository.getActiveSession()
                if (session == null || !session.isActive) return@launch
                val inserted = screenshotRepository.addScreenshotIfAbsent(session.folderId, uri.toString())
                if (inserted && settings.autoCrop) metaRepository.analyzeUri(uri.toString())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save screenshot $uri", e)   // e.g. folder deleted meanwhile: never crash
            }
        }
    }

    // ------------------------------------------------------------------ bubble
    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()
    private fun bubbleSizePx() = dp(when (settings.bubbleSize) { 0 -> 40; 2 -> 56; else -> 48 })
    private fun overlayType() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun showOverlay() {
        if (!Settings.canDrawOverlays(this) || bubbleView != null) return
        try {
            val size = bubbleSizePx()
            val sw = resources.displayMetrics.widthPixels
            val sh = resources.displayMetrics.heightPixels
            val view = AssistiveBubbleView(this).apply {
                contentDescription = "4S capture controls"
                count = max(0, lastBadge?.second ?: 0)
                paused = settings.capturePaused
            }
            val p = WindowManager.LayoutParams(
                size, size, overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = if (settings.bubbleX >= 0) settings.bubbleX.coerceIn(0, max(0, sw - size)) else dp(8)
                y = if (settings.bubbleY >= 0) settings.bubbleY.coerceIn(0, max(0, sh - size)) else dp(200)
            }
            bubbleView = view
            bubbleParams = p
            attachBubbleTouch(view)
            windowManager?.addView(view, p)
            resetIdleTimer()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show bubble", e)
            bubbleView = null
        }
    }

    private fun attachBubbleTouch(view: View) {
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var moving = false
        var longPressed = false
        view.setOnTouchListener { _, event ->
            resetIdleTimer()
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = bubbleParams?.x ?: 0
                    initialY = bubbleParams?.y ?: 0
                    touchX = event.rawX
                    touchY = event.rawY
                    moving = false
                    longPressed = false
                    longPressRunnable = Runnable { longPressed = true; showCloseControl() }
                    mainHandler.postDelayed(longPressRunnable!!, 500)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (abs(dx) > 8 || abs(dy) > 8) {
                        if (!moving) {
                            moving = true
                            longPressRunnable?.let { mainHandler.removeCallbacks(it) }
                            removeCloseControl()
                            closeFolderPanel()
                        }
                        bubbleParams?.let { p ->
                            val size = bubbleSizePx()
                            p.x = (initialX + dx).coerceIn(0, max(0, resources.displayMetrics.widthPixels - size))
                            p.y = (initialY + dy).coerceIn(0, max(0, resources.displayMetrics.heightPixels - size))
                            updateBubbleLayout()
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    longPressRunnable?.let { mainHandler.removeCallbacks(it) }
                    if (moving) {
                        if (settings.snapEdge) snapToEdge() else savePosition()
                    } else if (!longPressed) {
                        if (closeView != null) removeCloseControl() else toggleFolderPanel()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    longPressRunnable?.let { mainHandler.removeCallbacks(it) }
                    true
                }
                else -> false
            }
        }
    }

    private fun updateBubbleLayout() {
        try { windowManager?.updateViewLayout(bubbleView, bubbleParams) } catch (e: Exception) { Log.e(TAG, "updateBubbleLayout", e) }
    }

    private fun snapToEdge() {
        val p = bubbleParams ?: return
        val size = bubbleSizePx()
        val sw = resources.displayMetrics.widthPixels
        val target = if (p.x + size / 2 < sw / 2) 0 else sw - size
        ValueAnimator.ofInt(p.x, target).apply {
            duration = 180
            addUpdateListener { p.x = it.animatedValue as Int; updateBubbleLayout() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { savePosition() }
            })
        }.start()
    }

    private fun savePosition() {
        val p = bubbleParams ?: return
        serviceScope.launch {
            settingsRepository.set(SettingsKeys.BUBBLE_X, p.x)
            settingsRepository.set(SettingsKeys.BUBBLE_Y, p.y)
        }
    }

    private fun applyBubbleSettings() {
        val p = bubbleParams ?: return
        val size = bubbleSizePx()
        if (p.width != size) { p.width = size; p.height = size; updateBubbleLayout() }
        bubbleView?.paused = settings.capturePaused
        resetIdleTimer()
    }

    private fun resetIdleTimer() {
        bubbleView?.alpha = 1f
        mainHandler.removeCallbacks(idleRunnable)
        mainHandler.postDelayed(idleRunnable, 2500)
    }

    // ------------------------------------------------------------------ small red close control (16dp visual, 40dp touch)
    private fun showCloseControl() {
        if (closeView != null || bubbleView == null) return
        try {
            val bp = bubbleParams ?: return
            val size = bubbleSizePx()
            val touch = dp(40)
            val sw = resources.displayMetrics.widthPixels
            val view = CloseDotView(this).apply {
                contentDescription = "Hide 4S button"
                setOnClickListener { hideBubbleOnly() }   // capture keeps running
            }
            val p = WindowManager.LayoutParams(
                touch, touch, overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = (bp.x + size - dp(28)).coerceIn(0, max(0, sw - touch))   // bottom-right corner (badge is top-right)
                y = bp.y + size - dp(28)
            }
            closeView = view
            windowManager?.addView(view, p)
        } catch (e: Exception) {
            Log.e(TAG, "showCloseControl", e)
            closeView = null
        }
    }

    private fun removeCloseControl() {
        closeView?.let { try { windowManager?.removeView(it) } catch (e: Exception) { } }
        closeView = null
    }

    private fun hideBubbleOnly() {
        removeCloseControl()
        closeFolderPanel()
        removeLabel()
        mainHandler.removeCallbacks(idleRunnable)
        bubbleView?.let { try { windowManager?.removeView(it) } catch (e: Exception) { } }
        bubbleView = null
    }

    // ------------------------------------------------------------------ folder-name label (1.5 s after switching folders)
    private fun removeLabel() {
        mainHandler.removeCallbacks(removeLabelRunnable)
        labelView?.let { try { windowManager?.removeView(it) } catch (e: Exception) { } }
        labelView = null
    }

    private fun showFolderLabel(message: String) {
        if (bubbleView == null) return
        removeLabel()
        val tv = TextView(this).apply {
            text = "\u2713  $message"
            maxLines = 1
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFF5F5F6.toInt())
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = GradientDrawable().apply {
                setColor(0xF2131316.toInt()); cornerRadius = dp(22).toFloat(); setStroke(dp(1), 0xFF2C2C32.toInt())
            }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(96)
        }
        try {
            labelView = tv
            windowManager?.addView(tv, lp)
            mainHandler.postDelayed(removeLabelRunnable, 1800)
        } catch (e: Exception) {
            labelView = null
        }
    }

    // ------------------------------------------------------------------ folder panel
    private fun toggleFolderPanel() { if (panelView != null) closeFolderPanel() else openFolderPanel() }

    private fun setPanelFocusable(focusable: Boolean) {
        val p = panelParams ?: return
        val v = panelView ?: return
        p.flags = if (focusable) p.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        else p.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        try { windowManager?.updateViewLayout(v, p) } catch (e: Exception) { }
    }

    private fun openFolderPanel() {
        if (panelView != null) return
        try {
            val bp = bubbleParams
            val size = bubbleSizePx()
            val sw = resources.displayMetrics.widthPixels
            val left = (bp?.x ?: 0) + size / 2 < sw / 2
            val params = WindowManager.LayoutParams(
                min(dp(300), sw - dp(24)), WindowManager.LayoutParams.WRAP_CONTENT, overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = (if (left) Gravity.START else Gravity.END) or Gravity.CENTER_VERTICAL
                x = size + dp(12)   // beside the bubble
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
            }
            val cv = ComposeView(this).apply {
                setViewTreeLifecycleOwner(this@OverlayBubbleService)
                setViewTreeSavedStateRegistryOwner(this@OverlayBubbleService)
                setOnTouchListener { _, e ->
                    if (e.action == MotionEvent.ACTION_OUTSIDE) { closeFolderPanel(); true } else false
                }
                setContent {
                    SSSTheme {
                        FloatingFolderPanel(
                            folderRepository = folderRepository,
                            sessionRepository = sessionRepository,
                            screenshotRepository = screenshotRepository,
                            settingsRepository = settingsRepository,
                            scope = serviceScope,
                            onClose = { closeFolderPanel() },
                            onOpenApp = {
                                closeFolderPanel()
                                startActivity(Intent(this@OverlayBubbleService, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                            },
                            onStop = {
                                serviceScope.launch { sessionRepository.stopSession(); stopServiceAndHideOverlay() }
                            },
                            onFocusable = { setPanelFocusable(it) },
                            onRemoveLast = { shot -> serviceScope.launch { undoManager.delete(listOf(shot)) } }
                        )
                    }
                }
            }
            panelView = cv
            panelParams = params
            windowManager?.addView(cv, params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open panel", e)
            panelView = null
            panelParams = null
        }
    }

    private fun closeFolderPanel() {
        panelView?.let { try { windowManager?.removeView(it) } catch (e: Exception) { } }
        panelView = null
        panelParams = null
    }

    // ------------------------------------------------------------------ stop
    private fun stopServiceAndHideOverlay() {
        screenshotObserver.stop()
        hideBubbleOnly()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        screenshotObserver.stop()
        serviceScope.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        hideBubbleOnly()
    }
}