package com.sss.app.data.pdf

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed interface PdfDeleteResult {
    data object Done : PdfDeleteResult
    data class NeedsConfirm(val sender: IntentSender) : PdfDeleteResult
    data class Failed(val message: String) : PdfDeleteResult
}

/**
 * Lists and manages the PDFs that 4S saved in Downloads/4S.
 * On Android 10+ an app can see and edit its OWN files in MediaStore without any permission.
 * (PDFs created by a previous installation of the app are not visible on Android 10+. That is a system rule.)
 */
@Singleton
class PdfLibraryRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "SSS_PDF_LIB"
        private const val MIME = "application/pdf"
        const val FOLDER_PATH = "Download/4S/"      // RELATIVE_PATH used when the PDF is saved

        fun sanitizeName(raw: String): String? {
            val s = raw.trim().removeSuffix(".pdf").removeSuffix(".PDF")
                .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(80)
            return s.ifEmpty { null }
        }
    }

    private fun collection(): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Downloads.EXTERNAL_CONTENT_URI
        else MediaStore.Files.getContentUri("external")

    fun uriFor(id: Long): Uri = ContentUris.withAppendedId(collection(), id)

    private val projection = arrayOf(
        MediaStore.MediaColumns._ID,
        MediaStore.MediaColumns.DISPLAY_NAME,
        MediaStore.MediaColumns.SIZE,
        MediaStore.MediaColumns.DATE_MODIFIED,
        MediaStore.MediaColumns.DATE_ADDED
    )

    @Suppress("DEPRECATION")
    private fun folderSelection(): Pair<String, Array<String>> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ("${MediaStore.MediaColumns.MIME_TYPE} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? " +
                "AND ${MediaStore.MediaColumns.IS_PENDING} = 0") to arrayOf(MIME, "$FOLDER_PATH%")
        } else {
            "${MediaStore.MediaColumns.MIME_TYPE} = ? AND ${MediaStore.MediaColumns.DATA} LIKE ?" to
                arrayOf(MIME, "%/$FOLDER_PATH%")
        }

    private fun readItems(selection: String, args: Array<String>, sort: String?): List<PdfItem> {
        val out = ArrayList<PdfItem>()
        context.contentResolver.query(collection(), projection, selection, args, sort)?.use { c ->
            val idC = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameC = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeC = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val modC = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val addC = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            while (c.moveToNext()) {
                val id = c.getLong(idC)
                val seconds = c.getLong(modC).takeIf { it > 0 } ?: c.getLong(addC)
                out.add(PdfItem(id, uriFor(id), c.getString(nameC).orEmpty(), c.getLong(sizeC), seconds * 1000))
            }
        }
        return out
    }

    suspend fun list(): List<PdfItem> = withContext(Dispatchers.IO) {
        try {
            val (sel, args) = folderSelection()
            readItems(sel, args, "${MediaStore.MediaColumns.DATE_MODIFIED} DESC")
        } catch (e: Exception) {
            Log.e(TAG, "list failed", e)
            emptyList()
        }
    }

    suspend fun get(id: Long): PdfItem? = withContext(Dispatchers.IO) {
        try {
            readItems("${MediaStore.MediaColumns._ID} = ?", arrayOf(id.toString()), null).firstOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "get failed", e)
            null
        }
    }

    /** Fallback for the export screen when it only knows the file name. */
    suspend fun findByName(name: String): PdfItem? = list().firstOrNull { it.name == name }

    /** Returns null on success, otherwise a message for the user. */
    suspend fun rename(item: PdfItem, raw: String): String? = withContext(Dispatchers.IO) {
        val base = sanitizeName(raw) ?: return@withContext "Enter a file name"
        val newName = "$base.pdf"
        if (newName == item.name) return@withContext null
        try {
            val values = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, newName) }
            val n = context.contentResolver.update(item.uri, values, null, null)
            if (n > 0) null else "Could not rename this file"
        } catch (e: SecurityException) {
            "Can't rename: this PDF was created by another installation of 4S"
        } catch (e: Exception) {
            Log.e(TAG, "rename failed", e)
            "Could not rename this file"
        }
    }

    suspend fun delete(item: PdfItem): PdfDeleteResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        try {
            if (resolver.delete(item.uri, null, null) > 0) PdfDeleteResult.Done
            else PdfDeleteResult.Failed("This PDF no longer exists")
        } catch (e: SecurityException) {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                    PdfDeleteResult.NeedsConfirm(MediaStore.createDeleteRequest(resolver, listOf(item.uri)).intentSender)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException ->
                    PdfDeleteResult.NeedsConfirm(e.userAction.actionIntent.intentSender)
                else -> PdfDeleteResult.Failed("Could not delete this PDF")
            }
        } catch (e: Exception) {
            Log.e(TAG, "delete failed", e)
            PdfDeleteResult.Failed("Could not delete this PDF")
        }
    }
}