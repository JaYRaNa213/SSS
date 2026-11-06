package com.sss.app.ui.common

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

object AutoCropper {

    private const val TARGET_W = 300        // analysis bitmap width
    private const val BLACK_MEAN = 8.0      // 0..255 : "pure black bar" brightness limit
    private const val BLACK_STD = 4.0       // 0..255 : "no content" variation limit
    private const val NEED_ROWS = 3         // consecutive content rows to stop trimming
    private const val MARGIN = 0.005f       // safety margin (fraction of size)
    private const val MIN_AREA = 0.15f      // never keep less than 15% of the image

    data class Result(
        val top: Float, val bottom: Float, val left: Float, val right: Float,
        val w: Int, val h: Int
    )

    fun analyze(context: Context, uri: Uri): Result? = try {
        analyzeInternal(context, uri)
    } catch (e: Exception) {
        null
    }

    private fun analyzeInternal(context: Context, uri: Uri): Result? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        val ow = bounds.outWidth
        val oh = bounds.outHeight
        if (ow <= 0 || oh <= 0) return null

        var sample = 1
        while (ow / (sample * 2) >= TARGET_W) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        try {
            val w = bmp.width
            val h = bmp.height
            val px = IntArray(w * h)
            bmp.getPixels(px, 0, w, 0, 0, w, h)
            val lum = IntArray(w * h) {
                val c = px[it]
                (((c shr 16) and 255) * 299 + ((c shr 8) and 255) * 587 + (c and 255) * 114) / 1000
            }

            fun rowEmpty(y: Int, x0: Int, x1: Int): Boolean {
                var s = 0.0; var s2 = 0.0; val n = (x1 - x0).toDouble()
                for (x in x0 until x1) { val v = lum[y * w + x].toDouble(); s += v; s2 += v * v }
                val m = s / n
                return m < BLACK_MEAN && sqrt(max(0.0, s2 / n - m * m)) < BLACK_STD
            }
            fun colEmpty(x: Int, y0: Int, y1: Int): Boolean {
                var s = 0.0; var s2 = 0.0; val n = (y1 - y0).toDouble()
                for (y in y0 until y1) { val v = lum[y * w + x].toDouble(); s += v; s2 += v * v }
                val m = s / n
                return m < BLACK_MEAN && sqrt(max(0.0, s2 / n - m * m)) < BLACK_STD
            }

            // 1) system bars (only for full-screen captures of this device)
            val (sysTop, sysBottom) = systemBars(context, ow, oh)
            val top0 = (sysTop * h).roundToInt()
            val bottom0 = h - (sysBottom * h).roundToInt()
            if (bottom0 - top0 < 10) return Result(0f, 0f, 0f, 0f, ow, oh)

            // 2) black bars: first / last run of NEED_ROWS content rows
            var first = -1; var run = 0; var topIdx = top0
            for (y in top0 until bottom0) {
                if (!rowEmpty(y, 0, w)) { if (first < 0) first = y; run++; if (run >= NEED_ROWS) { topIdx = first; break } }
                else { run = 0; first = -1 }
            }
            var last = -1; run = 0; var bottomIdx = bottom0
            for (y in bottom0 - 1 downTo top0) {
                if (!rowEmpty(y, 0, w)) { if (last < 0) last = y; run++; if (run >= NEED_ROWS) { bottomIdx = last + 1; break } }
                else { run = 0; last = -1 }
            }
            if (bottomIdx - topIdx < h * MIN_AREA) { topIdx = top0; bottomIdx = bottom0 }

            // 3) pillar boxes inside the remaining rows
            var left = 0; var right = w
            run = 0; first = -1
            for (x in 0 until w) {
                if (!colEmpty(x, topIdx, bottomIdx)) { if (first < 0) first = x; run++; if (run >= NEED_ROWS) { left = first; break } }
                else { run = 0; first = -1 }
            }
            run = 0; last = -1
            for (x in w - 1 downTo 0) {
                if (!colEmpty(x, topIdx, bottomIdx)) { if (last < 0) last = x; run++; if (run >= NEED_ROWS) { right = last + 1; break } }
                else { run = 0; last = -1 }
            }
            if (right - left < w * MIN_AREA) { left = 0; right = w }

            val mH = MARGIN * h
            val mW = MARGIN * w
            val t = max(top0.toFloat(), topIdx - mH) / h
            val b = (h - minOf(bottom0.toFloat(), bottomIdx + mH)) / h
            val l = max(0f, left - mW) / w
            val r = (w - minOf(w.toFloat(), right + mW)) / w

            val area = (1f - l - r) * (1f - t - b)
            return if (area < MIN_AREA) Result(0f, 0f, 0f, 0f, ow, oh)
            else Result(t.coerceAtLeast(0f), b.coerceAtLeast(0f), l.coerceAtLeast(0f), r.coerceAtLeast(0f), ow, oh)
        } finally {
            bmp.recycle()
        }
    }

    /** (status bar fraction, navigation bar fraction) of the image height; (0,0) if it is not a full portrait capture. */
    private fun systemBars(context: Context, w: Int, h: Int): Pair<Float, Float> {
        val wm = context.getSystemService(WindowManager::class.java) ?: return 0f to 0f
        val bw: Int
        val bh: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.maximumWindowMetrics.bounds
            bw = b.width(); bh = b.height()
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(dm)
            bw = dm.widthPixels; bh = dm.heightPixels
        }
        val matches = kotlin.math.abs(w - bw) <= 2 && kotlin.math.abs(h - bh) <= 2
        if (!matches) return 0f to 0f

        val res = context.resources
        fun dim(name: String): Int {
            val id = res.getIdentifier(name, "dimen", "android")
            return if (id > 0) res.getDimensionPixelSize(id) else 0
        }
        val modeId = res.getIdentifier("config_navBarInteractionMode", "integer", "android")
        val gesture = modeId > 0 && res.getInteger(modeId) == 2
        val status = dim("status_bar_height")
        val nav = if (gesture) dim("navigation_bar_gesture_height") else dim("navigation_bar_height")
        return status.toFloat() / h to nav.toFloat() / h
    }
}