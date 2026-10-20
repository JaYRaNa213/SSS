package com.sss.app.ui.ocr

import com.sss.app.data.ocr.OcrImageResult

enum class OcrStatus {
    IDLE,
    PROCESSING,
    SUCCESS,
    PARTIAL_SUCCESS,
    ERROR
}

data class OcrUiState(
    val status: OcrStatus = OcrStatus.IDLE,
    val completedImages: Int = 0,
    val totalImages: Int = 0,
    val results: List<OcrImageResult> = emptyList(),
    val errorMessage: String? = null,
    val createdDocumentId: Long? = null
) {
    val progress: Float
        get() = if (totalImages == 0) 0f else completedImages.toFloat() / totalImages
}
