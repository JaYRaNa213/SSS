package com.sss.app.capture

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import kotlin.math.min

private const val ACCENT = 0xFFFEC84A.toInt()
private const val ACCENT_ON = 0xFF1A1405.toInt()
private const val LINE = 0xFF2C2C32.toInt()
private const val SURFACE = 0xFF232328.toInt()
private const val TEXT = 0xFFF5F5F6.toInt()
private const val BG = 0xFF0A0A0B.toInt()

/** Solid yellow "4S" circle with a soft outer ring. Grey with pause bars when paused. */
class AssistiveBubbleView(context: Context) : View(context) {

    private val d = context.resources.displayMetrics.density
    private var badgeScale = 1f

    var count: Int = 0
        set(v) { field = v; invalidate() }
    var paused: Boolean = false
        set(v) { field = v; invalidate() }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f * d }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 14f * d; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textAlign = Paint.Align.CENTER
    }
    private val badge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BG; style = Paint.Style.FILL }
    private val badgeText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ACCENT; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textAlign = Paint.Align.CENTER
    }
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TEXT; style = Paint.Style.FILL }

    fun pulse() {
        ValueAnimator.ofFloat(1f, 1.3f, 1f).apply {
            duration = 250
            addUpdateListener { badgeScale = it.animatedValue as Float; invalidate() }
        }.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f - 4f * d
        fill.color = if (paused) SURFACE else ACCENT
        canvas.drawCircle(cx, cy, r, fill)
        ring.color = if (paused) LINE else (ACCENT and 0x00FFFFFF) or 0x66000000
        canvas.drawCircle(cx, cy, r + 2f * d, ring)

        if (paused) {
            val bw = 3.5f * d
            val bh = r * 0.8f
            canvas.drawRoundRect(cx - bw * 1.7f, cy - bh / 2, cx - bw * 0.7f, cy + bh / 2, d, d, bar)
            canvas.drawRoundRect(cx + bw * 0.7f, cy - bh / 2, cx + bw * 1.7f, cy + bh / 2, d, d, bar)
        } else {
            text.color = ACCENT_ON
            val fm = text.fontMetrics
            canvas.drawText("4S", cx, cy - (fm.descent + fm.ascent) / 2f, text)
        }

        if (count > 0 && !paused) {
            val label = if (count > 99) "99+" else count.toString()
            val br = 9f * d * badgeScale
            val bx = width - 9f * d
            val by = 9f * d
            canvas.drawCircle(bx, by, br, badge)
            badgeText.textSize = (if (label.length > 2) 7.5f else 10f) * d
            val fm = badgeText.fontMetrics
            canvas.drawText(label, bx, by - (fm.descent + fm.ascent) / 2f, badgeText)
        }
    }
}

/** Small red close control. Visual circle is 16dp; the view itself (touch area) is made 40dp by the service. */
class CloseDotView(context: Context) : View(context) {
    private val d = context.resources.displayMetrics.density
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF5A5F.toInt(); style = Paint.Style.FILL }
    private val x = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt(); strokeWidth = 1.5f * d; strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val r = 8f * d
        canvas.drawCircle(cx, cy, r, bg)
        val o = r * 0.38f
        canvas.drawLine(cx - o, cy - o, cx + o, cy + o, x)
        canvas.drawLine(cx + o, cy - o, cx - o, cy + o, x)
    }
}