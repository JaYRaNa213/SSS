package com.sss.app.ui.pdf

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object PdfSaver {

    data class Saved(val uri: Uri, val name: String, val sizeBytes: Long)

    /** Removes illegal file characters, limits to 80 chars, strips ".pdf". */
    fun sanitize(raw: String): String {
        var s = raw.replace(Regex("[\\\\/:*?\"<>|]"), "").trim().replace(Regex("\\s+"), " ")
        if (s.length > 80) s = s.take(80).trim()
        return s.removeSuffix(".pdf").trim()
    }

    /** "Name", or "Name (1)", "Name (2)" ... if a file with that name already exists. */
    fun uniqueName(context: Context, base: String): String {
        if (base.isBlank()) return ""
        var n = base
        var i = 1
        while (exists(context, "$n.pdf")) { n = "$base ($i)"; i++ }
        return n
    }

    private fun exists(context: Context, fileName: String): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
                arrayOf(fileName, "Download/4S%"), null
            )?.use { it.count > 0 } ?: false
        } else {
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName).exists()
        }

    /** Blocking. [writer] receives the output stream. On any failure the half-written file is removed. */
    fun save(context: Context, nameNoExt: String, writer: (OutputStream) -> Unit): Saved {
        val fileName = "$nameNoExt.pdf"
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cv = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/4S")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) ?: error("Could not create the PDF file")
            try {
                (resolver.openOutputStream(uri) ?: error("Could not open the PDF file")).use(writer)
                cv.clear()
                cv.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, cv, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
            val size = resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
                ?.use { if (it.moveToFirst()) it.getLong(0) else 0L } ?: 0L
            return Saved(uri, fileName, size)
        }
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.cacheDir
        val file = File(dir, fileName)
        try {
            FileOutputStream(file).use(writer)
        } catch (e: Exception) {
            file.delete()
            throw e
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Saved(uri, fileName, file.length())
    }
}