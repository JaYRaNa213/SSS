package com.sss.app.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.sss.app.data.local.ScreenshotMetaEntity
import com.sss.app.ui.common.MetaMath
import com.sss.app.util.ImageBytes
import kotlinx.coroutines.tasks.await
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

class OcrRepository(private val context: Context) {

    private var rec: TextRecognizer? = null
    private fun recognizer(): TextRecognizer =
        rec ?: TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).also { rec = it }

    fun close() { rec?.close(); rec = null }

    /**
     * Reads one screenshot. Normal path: decode the bitmap, apply crop and manual rotation, then read it.
     * Fallback: let ML Kit open the file itself (no crop, no rotation) so a result is still produced.
     */
    suspend fun recognize(uri: Uri, meta: ScreenshotMetaEntity?, useCrop: Boolean, index: Int): OcrPage {
        val bmp = try {
            loadBitmap(uri, meta, useCrop)
        } catch (e: Exception) {
            Log.w(TAG, "loadBitmap failed for $uri", e); null
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "Out of memory decoding $uri"); null
        }
        if (bmp != null) {
            try {
                val result = recognizer().process(InputImage.fromBitmap(bmp, 0)).await()
                return toPage(index, result, bmp.width, bmp.height)
            } finally {
                bmp.recycle()
            }
        }
        Log.w(TAG, "Using ML Kit file loader (no crop or rotation) for $uri")
        val image = openInputImage(uri)
        val result = recognizer().process(image).await()
        return toPage(index, result, image.width, image.height)
    }

    private fun openInputImage(uri: Uri): InputImage =
        try {
            InputImage.fromFilePath(context, uri)
        } catch (e: Exception) {
            val path = uri.path
            if (path != null && File(path).exists()) InputImage.fromFilePath(context, Uri.fromFile(File(path))) else throw e
        }

    private fun toPage(index: Int, result: Text, w: Int, h: Int): OcrPage {
        val lines = ArrayList<OcrLine>()
        result.textBlocks.forEachIndexed { b, block ->
            for (line in block.lines) {
                val r = line.boundingBox ?: continue
                val t = line.text.trim()
                if (t.isNotEmpty()) lines.add(OcrLine(t, r.left, r.top, r.right, r.bottom, b))
            }
        }
        // safety net: text but no positions (should not happen) -> keep the text, one line per row
        if (lines.isEmpty() && result.text.isNotBlank()) {
            result.text.lines().filter { it.isNotBlank() }.forEachIndexed { i, t ->
                lines.add(OcrLine(t.trim(), 0, i * 30, 1000, i * 30 + 28, 0))
            }
        }
        return OcrPage(index = index, width = w.coerceAtLeast(1), height = h.coerceAtLeast(1), lines = lines, rawText = result.text)
    }

    private fun loadBitmap(uri: Uri, meta: ScreenshotMetaEntity?, useCrop: Boolean): Bitmap? {
        val bytes = ImageBytes.read(context, uri)
        if (bytes == null || bytes.isEmpty()) { Log.w(TAG, "Could not read bytes of $uri"); return null }

        // Size only. The return value of this call is null BY DESIGN (inJustDecodeBounds), so it is ignored.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val longSide = max(bounds.outWidth, bounds.outHeight)
        if (longSide <= 0) { Log.w(TAG, "Unreadable image data for $uri"); return null }

        var sample = 1
        while (longSide / (sample * 2) >= MAX_SIDE) sample *= 2
        var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: run { Log.w(TAG, "Decode returned null for $uri"); return null }

        if (useCrop && meta != null && MetaMath.hasCrop(meta)) {
            val x = (meta.cropLeft * bmp.width).roundToInt().coerceIn(0, bmp.width - 1)
            val y = (meta.cropTop * bmp.height).roundToInt().coerceIn(0, bmp.height - 1)
            val w = (bmp.width * (1f - meta.cropLeft - meta.cropRight)).roundToInt().coerceIn(1, bmp.width - x)
            val h = (bmp.height * (1f - meta.cropTop - meta.cropBottom)).roundToInt().coerceIn(1, bmp.height - y)
            val c = Bitmap.createBitmap(bmp, x, y, w, h)
            if (c !== bmp) bmp.recycle()
            bmp = c
        }
        val rot = meta?.rotation?.takeIf { it >= 0 } ?: 0
        if (rot % 360 != 0) {
            val r = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rot.toFloat()) }, true)
            if (r !== bmp) bmp.recycle()
            bmp = r
        }
        return bmp
    }

    private companion object {
        const val TAG = "SSS_OCR"
        const val MAX_SIDE = 3200
    }
}