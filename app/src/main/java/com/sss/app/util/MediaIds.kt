package com.sss.app.util

import android.content.ContentUris
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.ComponentActivity

object MediaIds {
    /** content://media/external/images/media/123 -> 123. Fallback: stable hash for non-media paths. */
    fun fromPath(path: String): Long = try {
        ContentUris.parseId(Uri.parse(path))
    } catch (e: Exception) {
        (path.hashCode().toLong() and 0x7fffffffL) or (1L shl 40)
    }
}

/** Coil model for a stored path. */
fun String.toImageModel(): Any =
    if (startsWith("content://") || startsWith("file://")) Uri.parse(this) else this

fun Context.findActivity(): ComponentActivity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is ComponentActivity) return c
        c = c.baseContext
    }
    return null
}