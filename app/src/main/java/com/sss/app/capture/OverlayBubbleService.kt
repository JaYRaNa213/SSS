package com.sss.app.capture

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.sss.app.data.DatabaseProvider
import com.sss.app.data.repository.FolderRepository
import com.sss.app.data.repository.SessionRepository
import com.sss.app.ui.theme.SSSTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs
import android.graphics.Color as AndroidColor

class OverlayBubbleService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var windowManager: WindowManager? = null

    // 4S Bubble
    private var bubbleView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    // Red Close Control
    private var redDotView: View? = null
    private var redDotParams: WindowManager.LayoutParams? = null
    private var isRedDotShowing = false

    // Folder Panel
    private var panelView: View? = null
    private var isPanelOpen = false

    private lateinit var folderRepository: FolderRepository
    private lateinit var sessionRepository: SessionRepository

    // Inactivity / Foggy state
    private val idleRunnable = Runnable {
        bubbleView?.animate()?.alpha(0.55f)?.setDuration(400)?.start()
    }

    // Long press
    private var longPressRunnable: Runnable? = null

    companion object {
        const val TAG = "SSS_OVERLAY"
        const val ACTION_SHOW = "com.sss.app.action.SHOW_BUBBLE"
        const val ACTION_HIDE = "com.sss.app.action.HIDE_BUBBLE"

        fun showBubble(context: Context) {
            if (!Settings.canDrawOverlays(context)) {
                Log.w(TAG, "Overlay permission missing")
                return
            }
            val intent = Intent(context, OverlayBubbleService::class.java).apply {
                action = ACTION_SHOW
            }
            context.startService(intent)
        }

        fun hideBubble(context: Context) {
            val intent = Intent(context, OverlayBubbleService::class.java).apply {
                action = ACTION_HIDE
            }
            context.startService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Overlay service created")
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val db = DatabaseProvider.getDatabase(this)
        folderRepository = FolderRepository(db.folderDao())
        sessionRepository = SessionRepository(db.sessionDao())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_SHOW -> showOverlay()
            ACTION_HIDE -> stopServiceAndHideOverlay()
        }
        return START_NOT_STICKY
    }

    private fun showOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Overlay permission missing")
            return
        }

        if (bubbleView != null) {
            Log.d(TAG, "4S bubble already visible")
            return
        }

        Log.d(TAG, "Showing AssistiveTouch 4S bubble")
        try {
            val density = resources.displayMetrics.density
            val bubbleSizeDp = 46
            val sizePx = (bubbleSizeDp * density + 0.5f).toInt()

            val assistiveTouchView = AssistiveTouchBubbleView(this).apply {
                contentDescription = "SSS capture controls"
            }

            bubbleView = assistiveTouchView

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            bubbleParams = WindowManager.LayoutParams(
                sizePx,
                sizePx,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 50
                y = 350
            }

            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f
            var isMoving = false
            var isLongPressed = false

            resetIdleTimer()

            bubbleView?.setOnTouchListener(object : View.OnTouchListener {
                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    resetIdleTimer()

                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = bubbleParams?.x ?: 0
                            initialY = bubbleParams?.y ?: 0
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            isMoving = false
                            isLongPressed = false

                            longPressRunnable = Runnable {
                                isLongPressed = true
                                Log.d(TAG, "Long press detected -> showing red close control")
                                showRedCloseControl()
                            }
                            mainHandler.postDelayed(longPressRunnable!!, 500)
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = (event.rawX - initialTouchX).toInt()
                            val dy = (event.rawY - initialTouchY).toInt()

                            if (abs(dx) > 8 || abs(dy) > 8) {
                                if (!isMoving) {
                                    isMoving = true
                                    longPressRunnable?.let { mainHandler.removeCallbacks(it) }
                                    if (isRedDotShowing) {
                                        removeRedCloseControl()
                                    }
                                }

                                bubbleParams?.let { p ->
                                    p.x = initialX + dx
                                    p.y = initialY + dy

                                    val screenWidth = resources.displayMetrics.widthPixels
                                    val screenHeight = resources.displayMetrics.heightPixels
                                    p.x = p.x.coerceIn(0, screenWidth - sizePx)
                                    p.y = p.y.coerceIn(0, screenHeight - sizePx)

                                    try {
                                        windowManager?.updateViewLayout(bubbleView, p)
                                        if (isRedDotShowing) {
                                            updateRedDotPosition()
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error updating bubble layout", e)
                                    }
                                }
                            }
                            return true
                        }
                        MotionEvent.ACTION_UP -> {
                            longPressRunnable?.let { mainHandler.removeCallbacks(it) }

                            if (!isMoving && !isLongPressed) {
                                if (isRedDotShowing) {
                                    removeRedCloseControl()
                                } else {
                                    Log.d(TAG, "4S bubble short tap -> toggling panel")
                                    toggleFolderPanel()
                                }
                            }
                            return true
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            longPressRunnable?.let { mainHandler.removeCallbacks(it) }
                            return true
                        }
                    }
                    return false
                }
            })

            windowManager?.addView(bubbleView, bubbleParams)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show 4S bubble overlay", e)
            bubbleView = null
        }
    }

    private fun resetIdleTimer() {
        bubbleView?.alpha = 1.0f
        mainHandler.removeCallbacks(idleRunnable)
        mainHandler.postDelayed(idleRunnable, 2500)
    }

    private fun showRedCloseControl() {
        if (isRedDotShowing || bubbleView == null) return

        try {
            val density = resources.displayMetrics.density
            val dotSizePx = (24 * density + 0.5f).toInt()

            val dotView = RedCloseDotView(this).apply {
                contentDescription = "Hide 4S button"
                setOnClickListener {
                    Log.d(TAG, "Red close control tapped -> hiding 4S bubble ONLY (capture session remains ACTIVE)")
                    hideBubbleOnly()
                }
            }

            redDotView = dotView

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            redDotParams = WindowManager.LayoutParams(
                dotSizePx,
                dotSizePx,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = (bubbleParams?.x ?: 0) + (46 * density).toInt() - (12 * density).toInt()
                y = (bubbleParams?.y ?: 0) - (8 * density).toInt()
            }

            windowManager?.addView(redDotView, redDotParams)
            isRedDotShowing = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show red close control", e)
            redDotView = null
            isRedDotShowing = false
        }
    }

    private fun updateRedDotPosition() {
        if (redDotView != null && bubbleParams != null && redDotParams != null) {
            val density = resources.displayMetrics.density
            redDotParams?.x = (bubbleParams?.x ?: 0) + (46 * density).toInt() - (12 * density).toInt()
            redDotParams?.y = (bubbleParams?.y ?: 0) - (8 * density).toInt()
            try {
                windowManager?.updateViewLayout(redDotView, redDotParams)
            } catch (e: Exception) {
                Log.e(TAG, "Error updating red dot position", e)
            }
        }
    }

    private fun removeRedCloseControl() {
        if (redDotView != null) {
            try {
                windowManager?.removeView(redDotView)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing red dot control", e)
            } finally {
                redDotView = null
                isRedDotShowing = false
            }
        }
    }

    private fun hideBubbleOnly() {
        removeRedCloseControl()
        closeFolderPanel()
        mainHandler.removeCallbacks(idleRunnable)
        if (bubbleView != null) {
            try {
                windowManager?.removeView(bubbleView)
                Log.d(TAG, "4S bubble hidden from screen (Capture remains ACTIVE)")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing bubble view", e)
            } finally {
                bubbleView = null
            }
        }
    }

    private fun toggleFolderPanel() {
        if (isPanelOpen) {
            closeFolderPanel()
        } else {
            openFolderPanel()
        }
    }

    private fun openFolderPanel() {
        if (panelView != null) return

        try {
            val metrics = resources.displayMetrics
            val screenWidth = metrics.widthPixels
            val density = metrics.density

            val targetWidthPx = (screenWidth * 0.28f).toInt().coerceIn(
                (220 * density).toInt(),
                (360 * density).toInt()
            )

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val panelParams = WindowManager.LayoutParams(
                targetWidthPx,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                x = 0
            }

            val composeView = ComposeView(this).apply {
                setViewTreeLifecycleOwner(this@OverlayBubbleService)
                setViewTreeSavedStateRegistryOwner(this@OverlayBubbleService)
                setContent {
                    SSSTheme {
                        FloatingFolderPanel(
                            folderRepository = folderRepository,
                            sessionRepository = sessionRepository,
                            onClose = { closeFolderPanel() },
                            scope = serviceScope
                        )
                    }
                }
            }

            panelView = composeView
            windowManager?.addView(panelView, panelParams)
            isPanelOpen = true
            Log.d(TAG, "Floating folder panel opened from LEFT")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open floating folder panel", e)
            panelView = null
            isPanelOpen = false
        }
    }

    private fun closeFolderPanel() {
        if (panelView != null) {
            try {
                windowManager?.removeView(panelView)
                Log.d(TAG, "Floating folder panel closed")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing panel view", e)
            } finally {
                panelView = null
                isPanelOpen = false
            }
        }
    }

    private fun stopServiceAndHideOverlay() {
        hideBubbleOnly()
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        serviceScope.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        removeRedCloseControl()
        closeFolderPanel()
        if (bubbleView != null) {
            try {
                windowManager?.removeView(bubbleView)
            } catch (e: Exception) {
                // ignore
            }
            bubbleView = null
        }
        Log.d(TAG, "Overlay service destroyed")
    }
}

class AssistiveTouchBubbleView(context: Context) : View(context) {

    private val density = context.resources.displayMetrics.density
    private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#DC1E1E22")
        style = Paint.Style.FILL
    }
    private val middlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#993C3C42")
        style = Paint.Style.FILL
    }
    private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#66636366")
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        textSize = 13f * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = Math.min(width, height) / 2f

        canvas.drawCircle(cx, cy, radius, outerPaint)
        canvas.drawCircle(cx, cy, radius * 0.76f, middlePaint)
        canvas.drawCircle(cx, cy, radius * 0.54f, innerPaint)

        val fontMetrics = textPaint.fontMetrics
        val textY = cy - (fontMetrics.descent + fontMetrics.ascent) / 2f
        canvas.drawText("4S", cx, textY, textPaint)
    }
}

class RedCloseDotView(context: Context) : View(context) {

    private val density = context.resources.displayMetrics.density
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#FF3B30")
        style = Paint.Style.FILL
    }
    private val xPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        strokeWidth = 2.5f * density
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = Math.min(width, height) / 2f

        canvas.drawCircle(cx, cy, radius, bgPaint)

        val offset = radius * 0.38f
        canvas.drawLine(cx - offset, cy - offset, cx + offset, cy + offset, xPaint)
        canvas.drawLine(cx + offset, cy - offset, cx - offset, cy + offset, xPaint)
    }
}

@Composable
fun FloatingFolderPanel(
    folderRepository: FolderRepository,
    sessionRepository: SessionRepository,
    onClose: () -> Unit,
    scope: CoroutineScope
) {
    val folders by folderRepository.folders.collectAsState(initial = emptyList())
    val activeSession by sessionRepository.observeActiveSession().collectAsState(initial = null)
    val activeFolderId = activeSession?.folderId

    var showNewFolderInput by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    Surface(
        shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
        color = Color(0xFF1C1C1E),
        shadowElevation = 10.dp,
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "4S",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "🟢 Active",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF4CAF50)
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close panel",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = Color(0xFF33333F))
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Select Folder",
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray,
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
            ) {
                items(folders, key = { it.id }) { folder ->
                    val isActive = folder.id == activeFolderId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .background(
                                color = if (isActive) Color(0xFF2C2C2E) else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                scope.launch {
                                    sessionRepository.switchActiveFolder(folder.id)
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = "Folder",
                                tint = if (isActive) Color(0xFF64B5F6) else Color.LightGray,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = folder.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                color = Color.White,
                                maxLines = 1
                            )
                        }

                        if (isActive) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Active folder",
                                tint = Color(0xFF64B5F6),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = Color(0xFF33333F))
            Spacer(modifier = Modifier.height(8.dp))

            if (showNewFolderInput) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        label = { Text("Folder name", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                showNewFolderInput = false
                                newFolderName = ""
                            }
                        ) {
                            Text("Cancel", fontSize = 12.sp)
                        }
                        Button(
                            onClick = {
                                val trimmed = newFolderName.trim()
                                if (trimmed.isNotEmpty()) {
                                    scope.launch {
                                        val newId = folderRepository.addFolder(trimmed)
                                        sessionRepository.switchActiveFolder(newId)
                                        newFolderName = ""
                                        showNewFolderInput = false
                                    }
                                }
                            }
                        ) {
                            Text("Create", fontSize = 12.sp)
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showNewFolderInput = true }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add folder",
                        tint = Color(0xFF64B5F6),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "New Folder",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF64B5F6)
                    )
                }
            }
        }
    }
}
