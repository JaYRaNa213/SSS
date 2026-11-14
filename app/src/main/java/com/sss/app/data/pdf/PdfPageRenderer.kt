package com.sss.app.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

/** Thin wrapper around PdfRenderer. PdfRenderer allows ONE open page at a time, so every call is serialized. */
class PdfPageRenderer(private val context: Context, private val uri: Uri) {

    private var fd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private val mutex = Mutex()

    /** width / height of every page. Filled by open(). */
    var aspects: List<Float> = emptyList()
        private set

    /** Returns null on success, otherwise an error message for the user. */
    suspend fun open(): String? = withContext(Dispatchers.IO) {
        try {
            val p = context.contentResolver.openFileDescriptor(uri, "r")
                ?: return@withContext "Could not open this PDF"
            fd = p
            val r = PdfRenderer(p)
            renderer = r
            if (r.pageCount == 0) return@withContext "This PDF has no pages"
            val list = ArrayList<Float>(r.pageCount)
            for (i in 0 until r.pageCount) {
                val page = r.openPage(i)
                try {
                    list.add(page.width.toFloat() / page.height.toFloat().coerceAtLeast(1f))
                } finally {
                    page.close()
                }
            }
            aspects = list
            null
        } catch (e: SecurityException) {
            "This PDF is password-protected"
        } catch (e: IOException) {
            Log.e(TAG, "open failed", e)
            "This PDF is damaged or unsupported"
        } catch (e: Exception) {
            Log.e(TAG, "open failed", e)
            "Could not open this PDF"
        }
    }

    suspend fun render(index: Int, widthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val r = renderer ?: return@withLock null
                if (index !in 0 until r.pageCount || index >= aspects.size) return@withLock null
                val h = (widthPx / aspects[index]).toInt().coerceIn(1, 6000)
                val bmp = Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)               // PdfRenderer draws on transparent
                val page = r.openPage(index)
                try {
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                } finally {
                    page.close()
                }
                bmp
            } catch (e: OutOfMemoryError) {
                Log.w(TAG, "OOM rendering page $index")
                null
            } catch (e: Exception) {
                Log.e(TAG, "render failed for page $index", e)
                null
            }
        }
    }

    fun close() {
        try { renderer?.close() } catch (_: Exception) {}
        try { fd?.close() } catch (_: Exception) {}
        renderer = null
        fd = null
    }

    private companion object { const val TAG = "SSS_PDF_RENDER" }
}