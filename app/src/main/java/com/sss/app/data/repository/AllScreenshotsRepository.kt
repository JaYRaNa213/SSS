package com.sss.app.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.sss.app.capture.ScreenshotMatcher
import com.sss.app.data.local.ScreenshotEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * "Other" folder = screenshots that
 *   1. were taken AFTER this app was installed,
 *   2. are real screenshots (strict match),
 *   3. are NOT already inside one of the user's folders,
 *   4. were not "removed" by the user (hidden list).
 *
 * The constructor is unchanged, so your Hilt module does not need to change.
 */
class AllScreenshotsRepository(
    private val context: Context,
    private val screenshotRepository: ScreenshotRepository
) {

    companion object {
        private const val TAG = "SSS_ALL_SCREENSHOTS"
        private const val PREFS = "sss_other_folder"
        private const val KEY_HIDDEN_IDS = "hidden_media_ids"
        private const val KEY_FIRST_SEEN = "first_seen_sec"
        private val prefsLock = Any()
    }

    private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    // ------------------------------------------------------------------
    // Install time
    // ------------------------------------------------------------------

    @Suppress("DEPRECATION")
    private fun installTimeSec(): Long {
        val fromPackageManager = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime / 1000
        }.getOrNull()
        if (fromPackageManager != null) return fromPackageManager

        val saved = prefs.getLong(KEY_FIRST_SEEN, 0L)
        if (saved != 0L) return saved
        val now = System.currentTimeMillis() / 1000
        prefs.edit().putLong(KEY_FIRST_SEEN, now).apply()
        return now
    }

    // ------------------------------------------------------------------
    // Hidden list ("removed from SSS" but file still on device)
    // ------------------------------------------------------------------

    fun hide(uris: Collection<String>) {
        val ids = uris.mapNotNull { idFromUri(it)?.toString() }
        if (ids.isEmpty()) return
        synchronized(prefsLock) {
            val current = prefs.getStringSet(KEY_HIDDEN_IDS, emptySet()).orEmpty().toMutableSet()
            current.addAll(ids)
            prefs.edit().putStringSet(KEY_HIDDEN_IDS, current).apply()
        }
    }

    private fun hiddenIds(): Set<Long> =
        prefs.getStringSet(KEY_HIDDEN_IDS, emptySet()).orEmpty()
            .mapNotNull { it.toLongOrNull() }
            .toSet()

    // content://media/external/images/media/123  ->  123
    private fun idFromUri(value: String): Long? =
        if (value.startsWith("content://media/")) {
            runCatching { ContentUris.parseId(Uri.parse(value)) }.getOrNull()
        } else null

    // ------------------------------------------------------------------
    // Query
    // ------------------------------------------------------------------

    suspend fun getAllScreenshots(): List<ScreenshotEntity> = withContext(Dispatchers.IO) {

        // Screenshots that already live in a folder -> must NOT appear in Other.
        val assignedPaths = try {
            screenshotRepository.getAllScreenshotsList().map { it.filePath }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading folder screenshots", e)
            emptyList()
        }
        val assignedStrings = assignedPaths.toSet()
        val assignedIds = assignedPaths.mapNotNull { idFromUri(it) }.toSet()
        val hidden = hiddenIds()

        val projection = buildList {
            add(MediaStore.Images.Media._ID)
            add(MediaStore.Images.Media.DISPLAY_NAME)
            add(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            add(MediaStore.Images.Media.DATE_ADDED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Images.Media.RELATIVE_PATH)
            }
        }.toTypedArray()

        // Only images added after install. Old gallery screenshots are never read.
        val selection = "${MediaStore.Images.Media.DATE_ADDED} >= ?"
        val args = arrayOf(installTimeSec().toString())
        val sort = "${MediaStore.Images.Media.DATE_ADDED} DESC, ${MediaStore.Images.Media._ID} DESC"

        val result = mutableListOf<ScreenshotEntity>()

        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, selection, args, sort
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val bucketCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val pathCol = c.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)

                var seq = 1
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    if (id in assignedIds || id in hidden) continue

                    val name = c.getString(nameCol)
                    val bucket = c.getString(bucketCol)
                    val path = if (pathCol >= 0) c.getString(pathCol) else null
                    if (!ScreenshotMatcher.isScreenshot(name, path, bucket)) continue

                    val uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                    ).toString()
                    if (uri in assignedStrings) continue

                    result.add(
                        ScreenshotEntity(
                            id = id,                 // MediaStore id: unique inside this list
                            folderId = -1L,
                            filePath = uri,
                            sequenceNumber = seq++,
                            capturedAt = c.getLong(dateCol) * 1000
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying MediaStore for Other screenshots", e)
        }

        // NOTE: the old "merge all Room screenshots" block is intentionally gone.
        // It put folder screenshots into Other and caused duplicate LazyGrid keys
        // (Room ids 1,2,3... colliding with MediaStore ids).
        result
    }
}