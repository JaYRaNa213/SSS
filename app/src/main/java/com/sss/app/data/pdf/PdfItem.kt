package com.sss.app.data.pdf

import android.net.Uri

data class PdfItem(
    val id: Long,
    val uri: Uri,
    val name: String,          // includes ".pdf"
    val sizeBytes: Long,
    val modifiedMs: Long
) {
    val title: String get() = name.removeSuffix(".pdf").removeSuffix(".PDF")
}