package com.sss.app.capture

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.sss.app.MainActivity
import kotlin.math.abs

class OverlayBubbleService : Service() {

    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null
    private var params: WindowManager.LayoutParams? = null

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
        Log.d(TAG, "Overlay service started")
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_SHOW -> showOverlay()
            ACTION_HIDE -> hideOverlay()
        }
        return START_NOT_STICKY
    }

    private fun showOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Overlay permission missing")
            return
        }

        if (bubbleView != null) {
            Log.d(TAG, "Bubble already visible")
            return
        }

        Log.d(TAG, "Showing bubble")
        try {
            val sizeDp = 56
            val scale = resources.displayMetrics.density
            val sizePx = (sizeDp * scale + 0.5f).toInt()

            val textView = TextView(this).apply {
                text = "SSS"
                setTextColor(Color.WHITE)
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
            }

            val shape = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#1976D2")) // dark/blue-ish
            }
            textView.background = shape

            bubbleView = textView

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            params = WindowManager.LayoutParams(
                sizePx,
                sizePx,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 100
                y = 200
            }

            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f
            var isMoving = false

            bubbleView?.setOnTouchListener(object : View.OnTouchListener {
                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = params?.x ?: 0
                            initialY = params?.y ?: 0
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            isMoving = false
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = (event.rawX - initialTouchX).toInt()
                            val dy = (event.rawY - initialTouchY).toInt()
                            if (abs(dx) > 5 || abs(dy) > 5) {
                                isMoving = true
                            }
                            params?.let { p ->
                                p.x = initialX + dx
                                p.y = initialY + dy

                                val screenWidth = resources.displayMetrics.widthPixels
                                val screenHeight = resources.displayMetrics.heightPixels
                                p.x = p.x.coerceIn(0, screenWidth - sizePx)
                                p.y = p.y.coerceIn(0, screenHeight - sizePx)

                                try {
                                    windowManager?.updateViewLayout(bubbleView, p)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error updating view layout", e)
                                }
                            }
                            Log.d(TAG, "Bubble dragged")
                            return true
                        }
                        MotionEvent.ACTION_UP -> {
                            if (!isMoving) {
                                Log.d(TAG, "Bubble clicked")
                                val launchIntent = Intent(this@OverlayBubbleService, MainActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                }
                                try {
                                    startActivity(launchIntent)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error launching MainActivity from bubble", e)
                                }
                            }
                            return true
                        }
                    }
                    return false
                }
            })

            windowManager?.addView(bubbleView, params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show bubble overlay", e)
            bubbleView = null
        }
    }

    private fun hideOverlay() {
        if (bubbleView != null) {
            try {
                windowManager?.removeView(bubbleView)
                Log.d(TAG, "Bubble removed")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing bubble view", e)
            } finally {
                bubbleView = null
            }
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
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
