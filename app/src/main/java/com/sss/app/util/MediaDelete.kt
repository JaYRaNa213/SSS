package com.sss.app.util

import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/**
 * Deleting images that belong to the system (e.g. Pictures/Screenshots).
 *
 * Android 11+ (API 30): the system shows its own "Allow SSS to delete N items?" dialog.
 *   -> returns an IntentSender; launch it with StartIntentSenderForResult.
 * Below API 30: deletes directly and returns null.
 */
object MediaDelete {

    fun delete(context: Context, uris: List<Uri>): IntentSender? {
        if (uris.isEmpty()) return null

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender
        } else {
            uris.forEach { uri ->
                runCatching { context.contentResolver.delete(uri, null, null) }
            }
            null
        }
    }
}