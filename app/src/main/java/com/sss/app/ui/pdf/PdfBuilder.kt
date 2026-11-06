package com.sss.app.ui.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.sss.app.data.local.ScreenshotMetaEntity
import com.sss.app.ui.common.MetaMath
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class PdfQuality(val label: String, val maxDim: Int, val jpeg: Int?) {
    HIGH("High", 2400, null), MEDIUM("Medium", 1600, 85), LOW("Low", 1080, 60)
}

enum class PdfOrder(val label: String) { SELECTED("As selected"), OLDEST("Oldest first"), NEWEST("Newest first") }

data class PdfOptions(
    val perPage: Int = 1,
    val a4: Boolean = true,
    val quality: PdfQuality = PdfQuality.MEDIUM,
    val order: PdfOrder = PdfOrder.SELECTED,
    val useCrop: Boolean = true,
    val margin: Boolean = true
)

class PdfPageSource(val path: String, val meta: ScreenshotMetaEntity?, val rotation: Int)

object PdfBuilder {

    /** Blocking. Call from a background thread. [checkCancel] should throw when the job was cancelled. */
    fun build(
        context: Context,
        pages: List<PdfPageSource>,
        o: PdfOptions,
        out: OutputStream,
        onProgress: (done: Int, total: Int) -> Unit,
        checkCancel: () -> Unit
    ) {
        val doc = PdfDocument()
        try {
            var done = 0
            pages.chunked(o.perPage.coerceIn(1, 2)).forEachIndexed { gi, group ->
                checkCancel()
                val bitmaps = group.map { load(context, it, o) }
                val (pw, ph) = pageSize(o, bitmaps)
                val page = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, gi + 1).create())
                draw(page.canvas, bitmaps, pw, ph, o)
                doc.finishPage(page)
                bitmaps.forEach { it?.recycle() }
                done += group.size
                onProgress(done, pages.size)
            }
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    private fun load(context: Context, src: PdfPageSource, o: PdfOptions): Bitmap? {
        val uri = if (src.path.startsWith("content://") || src.path.startsWith("file://")) Uri.parse(src.path)
        else Uri.fromFile(File(src.path))
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        val longSide = max(bounds.outWidth, bounds.outHeight)
        if (longSide <= 0) return null
        var sample = 1
        while (longSide / (sample * 2) >= o.quality.maxDim) sample *= 2
        var bmp = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null

        val m = src.meta
        if (o.useCrop && m != null && MetaMath.hasCrop(m)) {
            val x = (m.cropLeft * bmp.width).roundToInt().coerceIn(0, bmp.width - 1)
            val y = (m.cropTop * bmp.height).roundToInt().coerceIn(0, bmp.height - 1)
            val w = (bmp.width * (1f - m.cropLeft - m.cropRight)).roundToInt().coerceIn(1, bmp.width - x)
            val h = (bmp.height * (1f - m.cropTop - m.cropBottom)).roundToInt().coerceIn(1, bmp.height - y)
            val c = Bitmap.createBitmap(bmp, x, y, w, h)
            if (c !== bmp) bmp.recycle()
            bmp = c
        }
        if (src.rotation % 360 != 0) {
            val r = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(src.rotation.toFloat()) }, true)
            if (r !== bmp) bmp.recycle()
            bmp = r
        }
        o.quality.jpeg?.let { q ->
            val bos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, q, bos)
            bmp.recycle()
            val bytes = bos.toByteArray()
            bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        }
        return bmp
    }

    private fun pageSize(o: PdfOptions, bitmaps: List<Bitmap?>): Pair<Int, Int> {
        if (o.a4 || o.perPage == 2) return 595 to 842
        val b = bitmaps.firstOrNull() ?: return 595 to 842
        val s = 842f / max(b.width, b.height)
        return (b.width * s).roundToInt().coerceAtLeast(100) to (b.height * s).roundToInt().coerceAtLeast(100)
    }

    private fun draw(canvas: Canvas, bitmaps: List<Bitmap?>, pw: Int, ph: Int, o: PdfOptions) {
        val m = if (o.margin) 16f else 0f
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        if (o.perPage == 1) {
            drawFit(canvas, bitmaps.firstOrNull(), RectF(m, m, pw - m, ph - m), paint)
        } else {
            val gap = 8f
            val cellH = (ph - 2 * m - gap) / 2f
            drawFit(canvas, bitmaps.getOrNull(0), RectF(m, m, pw - m, m + cellH), paint)
            drawFit(canvas, bitmaps.getOrNull(1), RectF(m, m + cellH + gap, pw - m, m + 2 * cellH + gap), paint)
        }
    }

    private fun drawFit(canvas: Canvas, b: Bitmap?, r: RectF, paint: Paint) {
        if (b == null) return
        val s = min(r.width() / b.width, r.height() / b.height)
        val w = b.width * s
        val h = b.height * s
        val left = r.left + (r.width() - w) / 2f
        val top = r.top + (r.height() - h) / 2f
        canvas.drawBitmap(b, null, RectF(left, top, left + w, top + h), paint)
    }
}