package com.sss.app.util

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.io.File
import java.io.InputStream

/** Reads a whole image into memory, trying several ways to open it (some phones refuse the first one). */
object ImageBytes {

    fun read(context: Context, uri: Uri): ByteArray? =
        runCatching { open(context, uri)?.use { it.readBytes() } }.getOrNull()

    private fun open(context: Context, uri: Uri): InputStream? {
        val resolver = context.contentResolver
        val text = uri.toString()

        if (text.startsWith("content://")) {
            runCatching { resolver.openInputStream(uri) }.getOrNull()?.let { return it }
            runCatching { resolver.openFileDescriptor(uri, "r")?.let { ParcelFileDescriptor.AutoCloseInputStream(it) } }
                .getOrNull()?.let { return it }
            runCatching { resolver.openAssetFileDescriptor(uri, "r")?.createInputStream() }.getOrNull()?.let { return it }
            runCatching {
                var filePath: String? = null
                resolver.query(uri, arrayOf(MediaStore.Images.Media.DATA), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val idx = c.getColumnIndex(MediaStore.Images.Media.DATA)
                        if (idx >= 0) filePath = c.getString(idx)
                    }
                }
                filePath?.let { p -> File(p).takeIf { it.exists() }?.inputStream() }
            }.getOrNull()?.let { return it }
        }

        val clean = if (text.startsWith("file://")) (uri.path ?: text.removePrefix("file://")) else text
        val file = File(clean)
        if (file.exists()) runCatching { file.inputStream() }.getOrNull()?.let { return it }

        return runCatching { resolver.openInputStream(uri) }.getOrNull()
    }
}