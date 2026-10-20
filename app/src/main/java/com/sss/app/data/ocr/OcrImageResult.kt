package com.sss.app.data.ocr

data class OcrImageResult(
    val sourceUri: String,
    val sourceIndex: Int,
    val extractedText: String,
    val errorMessage: String? = null
) {
    val succeeded: Boolean
        get() = errorMessage == null
}
