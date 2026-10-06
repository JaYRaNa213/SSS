package com.sss.app.capture

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat

class ScreenshotObserver(
    private val context: Context,
    private val onScreenshotDetected: (Uri) -> Unit
) {

    companion object {
        private const val TAG = "SSS_SCREENSHOT"
        private val SCREENSHOT_KEYWORDS = listOf(
            "screenshot",
            "screen_shot",
            "screenshot_",
            "screen-shot",
            "screencap",
            "screen_cap"
        )
    }

    private val contentResolver: ContentResolver = context.contentResolver
    private val handler = Handler(Looper.getMainLooper())
    private val processedIds = mutableSetOf<Long>()

    private var sessionStartTimeSec: Long = 0
    private var isStarted = false

    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)

            Log.d(TAG, "MediaStore changed: $uri")

            if (!isStarted) return

            handler.post {
                processMediaStoreChange(uri)
            }
        }
    }

    fun start() {
        Log.d(TAG, "Screenshot observer STARTED")

        isStarted = true
        sessionStartTimeSec = (System.currentTimeMillis() / 1000) - 2
        processedIds.clear()
        handler.removeCallbacksAndMessages(null)

        try {
            contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error registering ContentObserver", e)
        }
    }

    fun stop() {
        Log.d(TAG, "Screenshot observer STOPPED")

        isStarted = false
        handler.removeCallbacksAndMessages(null)

        try {
            contentResolver.unregisterContentObserver(observer)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping observer", e)
        }
    }

    private fun checkStoragePermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        val granted = ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            Log.w(
                TAG,
                "Storage permission ($permission) NOT granted! MediaStore queries for external screenshots will fail or return 0 rows."
            )
        }

        return granted
    }

    private fun processMediaStoreChange(uri: Uri?) {
        if (!isStarted) return

        if (!checkStoragePermission()) {
            return
        }

        var foundDirect = false
        if (uri != null && uri != MediaStore.Images.Media.EXTERNAL_CONTENT_URI) {
            foundDirect = querySpecificUri(uri)
        }

        if (!foundDirect) {
            queryNewestImages()
        }
    }

    private fun querySpecificUri(uri: Uri): Boolean {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.IS_PENDING
        )

        try {
            contentResolver.query(
                uri,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                Log.d(TAG, "Direct query successful for $uri. rows=${cursor.count}")

                if (cursor.moveToFirst()) {
                    return inspectAndProcessRow(cursor, uri)
                } else {
                    Log.d(TAG, "No row found for direct URI: $uri (fallback to collection query)")
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException querying direct URI: $uri", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error querying direct URI: $uri", e)
        }

        return false
    }

    private fun queryNewestImages() {
        if (!isStarted) return

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.IS_PENDING,
            MediaStore.Images.Media.DATE_ADDED
        )

        val selection = "${MediaStore.Images.Media.DATE_ADDED} >= ?"
        val selectionArgs = arrayOf(sessionStartTimeSec.toString())
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC, ${MediaStore.Images.Media._ID} DESC"

        try {
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                Log.d(TAG, "Collection query successful. rows=${cursor.count}")

                var count = 0
                while (cursor.moveToNext() && count < 10) {
                    count++
                    val idIndex = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                    val id = if (idIndex >= 0) cursor.getLong(idIndex) else -1L
                    val contentUri = if (id != -1L) {
                        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    } else {
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    }

                    val processed = inspectAndProcessRow(cursor, contentUri)
                    if (processed) {
                        break
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException querying MediaStore collection", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error querying MediaStore collection", e)
        }
    }

    private fun inspectAndProcessRow(
        cursor: android.database.Cursor,
        contentUri: Uri
    ): Boolean {
        val idIndex = cursor.getColumnIndex(MediaStore.Images.Media._ID)
        val nameIndex = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
        val pathIndex = cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
        val bucketIndex = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
        val dataIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
        val pendingIndex = cursor.getColumnIndex(MediaStore.Images.Media.IS_PENDING)

        val id = if (idIndex >= 0) cursor.getLong(idIndex) else -1L
        val fileName = if (nameIndex >= 0) cursor.getString(nameIndex) else ""
        val relativePath = if (pathIndex >= 0) cursor.getString(pathIndex) else ""
        val bucketName = if (bucketIndex >= 0) cursor.getString(bucketIndex) else ""
        val filePath = if (dataIndex >= 0) cursor.getString(dataIndex) else ""
        val isPending = if (pendingIndex >= 0) cursor.getInt(pendingIndex) else 0

        Log.d(
            TAG,
            "Image metadata: id=$id, name=$fileName, path=$relativePath, bucket=$bucketName, pending=$isPending"
        )

        if (id != -1L && processedIds.contains(id)) {
            Log.d(TAG, "Image id $id already processed")
            return true
        }

        if (isPending == 1) {
            Log.d(TAG, "Image $id is still pending. Scheduling retry in 1s...")
            handler.postDelayed({
                if (isStarted) {
                    queryNewestImages()
                }
            }, 1000)
            return false
        }

        val isScreenshot = isScreenshotMatch(fileName, relativePath, bucketName, filePath)

        if (isScreenshot) {
            if (id != -1L) {
                processedIds.add(id)
            }

            Log.d(TAG, "SCREENSHOT DETECTED: $contentUri (name=$fileName, path=$relativePath)")
            onScreenshotDetected(contentUri)
            return true
        } else {
            Log.d(TAG, "Not a screenshot: $fileName (path=$relativePath, bucket=$bucketName)")
        }

        return false
    }

    private fun isScreenshotMatch(
        fileName: String?,
        relativePath: String?,
        bucketName: String?,
        filePath: String?
    ): Boolean {
        fun String?.hasKeyword(): Boolean {
            if (this.isNullOrEmpty()) return false
            return SCREENSHOT_KEYWORDS.any { kw ->
                this.contains(kw, ignoreCase = true)
            }
        }

        return fileName.hasKeyword() ||
                relativePath.hasKeyword() ||
                bucketName.hasKeyword() ||
                filePath.hasKeyword()
    }
}