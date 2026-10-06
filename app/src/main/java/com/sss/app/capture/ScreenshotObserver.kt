package com.sss.app.capture

import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log

class ScreenshotObserver(
    private val contentResolver: ContentResolver,
    private val onScreenshotDetected: (Uri) -> Unit
) {

    companion object {
        private const val TAG = "SSS_SCREENSHOT"
    }

    private var lastProcessedUri: Uri? = null

    private val handler = Handler(Looper.getMainLooper())

    private val observer = object : ContentObserver(handler) {

        override fun onChange(
            selfChange: Boolean,
            uri: Uri?
        ) {
            super.onChange(selfChange, uri)

            Log.d(
                TAG,
                "MediaStore changed: $uri"
            )

            if (uri != null) {
                checkIfScreenshot(uri)
            }
        }
    }

    fun start() {
        Log.d(
            TAG,
            "Screenshot observer STARTED"
        )

        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            observer
        )
    }

    fun stop() {
        Log.d(
            TAG,
            "Screenshot observer STOPPED"
        )

        try {
            contentResolver.unregisterContentObserver(observer)
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Error stopping observer",
                e
            )
        }
    }

    private fun checkIfScreenshot(uri: Uri) {

        if (uri == lastProcessedUri) {
            Log.d(
                TAG,
                "Duplicate URI ignored: $uri"
            )
            return
        }

        /*
         * Android may notify MediaStore before the screenshot
         * metadata is completely written.
         *
         * Wait 1 second before querying the URI.
         */
        handler.postDelayed(
            {
                queryScreenshot(uri)
            },
            1000
        )
    }

    private fun queryScreenshot(uri: Uri) {

        if (uri == lastProcessedUri) {
            Log.d(
                TAG,
                "Duplicate URI ignored before query: $uri"
            )
            return
        }

        val projection = arrayOf(
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.MIME_TYPE,
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

                Log.d(
                    TAG,
                    "Query successful. rows=${cursor.count}"
                )

                if (!cursor.moveToFirst()) {

                    Log.d(
                        TAG,
                        "No row found for URI: $uri"
                    )

                    return
                }

                val nameIndex =
                    cursor.getColumnIndex(
                        MediaStore.Images.Media.DISPLAY_NAME
                    )

                val pathIndex =
                    cursor.getColumnIndex(
                        MediaStore.Images.Media.RELATIVE_PATH
                    )

                val mimeIndex =
                    cursor.getColumnIndex(
                        MediaStore.Images.Media.MIME_TYPE
                    )

                val pendingIndex =
                    cursor.getColumnIndex(
                        MediaStore.Images.Media.IS_PENDING
                    )

                val fileName =
                    if (nameIndex >= 0) {
                        cursor.getString(nameIndex)
                    } else {
                        ""
                    }

                val relativePath =
                    if (pathIndex >= 0) {
                        cursor.getString(pathIndex)
                    } else {
                        ""
                    }

                val mimeType =
                    if (mimeIndex >= 0) {
                        cursor.getString(mimeIndex)
                    } else {
                        ""
                    }

                val isPending =
                    if (pendingIndex >= 0) {
                        cursor.getInt(pendingIndex)
                    } else {
                        0
                    }

                Log.d(
                    TAG,
                    "Image metadata: " +
                            "name=$fileName, " +
                            "path=$relativePath, " +
                            "mime=$mimeType, " +
                            "pending=$isPending"
                )

                /*
                 * Screenshot is still being created.
                 */
                if (isPending == 1) {

                    Log.d(
                        TAG,
                        "Image is still pending: $uri"
                    )

                    return
                }

                val isScreenshot =
                    fileName.contains(
                        "screenshot",
                        ignoreCase = true
                    ) ||
                            relativePath.contains(
                                "screenshot",
                                ignoreCase = true
                            )

                if (isScreenshot) {

                    lastProcessedUri = uri

                    Log.d(
                        TAG,
                        "SCREENSHOT DETECTED: $uri"
                    )

                    onScreenshotDetected(uri)

                } else {

                    Log.d(
                        TAG,
                        "Not a screenshot: $fileName"
                    )
                }
            }

        } catch (e: SecurityException) {

            Log.e(
                TAG,
                "Permission denied while reading MediaStore",
                e
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Error reading MediaStore URI: $uri",
                e
            )
        }
    }
}