package com.sss.app.util

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.sss.app.data.pdf.PdfItem
import java.text.DateFormat
import java.util.Date
import java.util.Locale

object PdfActions {

    fun share(context: Context, item: PdfItem) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, item.uri)
            putExtra(Intent.EXTRA_SUBJECT, item.title)
            clipData = ClipData.newRawUri(item.name, item.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(Intent.createChooser(send, "Share PDF"))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No app can share this file", Toast.LENGTH_SHORT).show()
        }
    }

    fun openExternal(context: Context, item: PdfItem) {
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(item.uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(Intent.createChooser(view, "Open with"))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No PDF app found on this phone", Toast.LENGTH_SHORT).show()
        }
    }
}

object PdfFormat {
    fun size(bytes: Long): String = when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> "${bytes / 1024} KB"
        else -> String.format(Locale.US, "%.1f MB", bytes / 1048576.0)
    }

    fun date(ms: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ms))
}