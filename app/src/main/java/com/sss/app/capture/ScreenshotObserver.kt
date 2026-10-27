package com.sss.app.capture

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Watches MediaStore for NEW screenshots while a capture session is active.
 *
 * Fixes compared to the old version:
 *  - no 2-minute look-back (old screenshots were being pulled into the new folder)
 *  - processes EVERY new row (old code stopped after the first one -> bursts were lost)
 *  - oldest-first, so sequence numbers follow the order you took them
 *  - uses the shared ScreenshotMatcher (no more "screen"/"capture"/"shot" false positives)
 *  - MediaStore queries run on a background thread, not the main thread
 *  - API < 29 safe (RELATIVE_PATH / IS_PENDING only requested on Q+)
 *
 * NOTE: onScreenshotDetected is now called on a BACKGROUND thread.
 * Launch a coroutine in it (viewModelScope / serviceScope) - do not touch UI there.
 */
class ScreenshotObserver(
    private val context: Context,
    private val onScreenshotDetected: (Uri) -> Unit
) {

    companion object {
        private const val TAG = "SSS_SCREENSHOT"
        private const val CLOCK_SKEW_SEC = 2L      // tiny tolerance only
        private const val DEBOUNCE_MS = 300L
    }

    private val contentResolver = context.contentResolver
    private val processedIds = HashSet<Long>()

    @Volatile private var isStarted = false
    private var sessionStartTimeSec = 0L

    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var observer: ContentObserver? = null

    private val scanRunnable = Runnable { scanNewImages() }

    @Synchronized
    fun start() {
        if (isStarted) return
        isStarted = true

        sessionStartTimeSec = System.currentTimeMillis() / 1000 - CLOCK_SKEW_SEC
        processedIds.clear()

        val t = HandlerThread("sss-screenshot-observer").also { it.start() }
        val h = Handler(t.looper)
        thread = t
        handler = h

        val obs = object : ContentObserver(h) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                scheduleScan(DEBOUNCE_MS)
            }
        }
        observer = obs

        try {
            contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                obs
            )
            Log.d(TAG, "Screenshot observer STARTED")
        } catch (e: Exception) {
            Log.e(TAG, "Error registering ContentObserver", e)
        }
    }

    @Synchronized
    fun stop() {
        if (!isStarted) return
        isStarted = false

        handler?.removeCallbacksAndMessages(null)
        observer?.let {
            try {
                contentResolver.unregisterContentObserver(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering observer", e)
            }
        }
        observer = null
        handler = null
        thread?.quitSafely()
        thread = null
        Log.d(TAG, "Screenshot observer STOPPED")
    }

    private fun scheduleScan(delayMs: Long) {
        val h = handler ?: return
        h.removeCallbacks(scanRunnable)
        h.postDelayed(scanRunnable, delayMs)
    }

    private fun hasPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun scanNewImages() {
        if (!isStarted || !hasPermission()) return

        val projection = buildList {
            add(MediaStore.Images.Media._ID)
            add(MediaStore.Images.Media.DISPLAY_NAME)
            add(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            add(MediaStore.Images.Media.DATE_ADDED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Images.Media.RELATIVE_PATH)
                add(MediaStore.Images.Media.IS_PENDING)
            }
        }.toTypedArray()

        val selection = "${MediaStore.Images.Media.DATE_ADDED} >= ?"
        val args = arrayOf(sessionStartTimeSec.toString())
        // OLDEST first -> sequence numbers follow the order you took the screenshots
        val sort = "${MediaStore.Images.Media.DATE_ADDED} ASC, ${MediaStore.Images.Media._ID} ASC"

        var sawPending = false

        try {
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, selection, args, sort
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val bucketCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val pathCol = c.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                val pendingCol = c.getColumnIndex(MediaStore.Images.Media.IS_PENDING)

                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    if (id in processedIds) continue               // <- continue, NOT break/return

                    if (pendingCol >= 0 && c.getInt(pendingCol) == 1) {
                        sawPending = true
                        continue
                    }

                    val name = c.getString(nameCol)
                    val bucket = c.getString(bucketCol)
                    val path = if (pathCol >= 0) c.getString(pathCol) else null

                    if (!ScreenshotMatcher.isScreenshot(name, path, bucket)) continue

                    processedIds.add(id)
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                    )
                    Log.d(TAG, "SCREENSHOT DETECTED: $uri ($name)")
                    onScreenshotDetected(uri)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning MediaStore", e)
        }

        if (sawPending && isStarted) scheduleScan(1000)
    }
}
