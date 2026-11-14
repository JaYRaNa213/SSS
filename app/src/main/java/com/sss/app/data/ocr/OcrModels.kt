package com.sss.app.data.ocr

/** One physical line of text with its position in the (cropped) image. Pure data, no Android types. */
data class OcrLine(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val block: Int          // ML Kit text block the line belongs to
) {
    val height: Int get() = (bottom - top).coerceAtLeast(1)
    val width: Int get() = (right - left).coerceAtLeast(1)
}

data class OcrPage(
    val index: Int,
    val width: Int,
    val height: Int,
    val lines: List<OcrLine>,
    val rawText: String = "",
    val error: String? = null
)