package com.sss.app.data.ocr

import android.net.Uri

class MultiImageOcrProcessor(
    private val ocrRepository: OcrRepository
) {

    suspend fun processImages(
        selectedUris: List<Uri>,
        onProgress: (completed: Int, total: Int) -> Unit
    ): List<OcrImageResult> {

        val results = mutableListOf<OcrImageResult>()
        val total = selectedUris.size

        selectedUris.forEachIndexed { index, uri ->
            val result = try {
                val extracted = ocrRepository.extractText(uri)
                OcrImageResult(
                    sourceUri = uri.toString(),
                    sourceIndex = index,
                    extractedText = extracted
                )
            } catch (exception: Exception) {
                OcrImageResult(
                    sourceUri = uri.toString(),
                    sourceIndex = index,
                    extractedText = "",
                    errorMessage = exception.message
                        ?: "Unable to extract text from this image."
                )
            }

            results.add(result)
            onProgress(index + 1, total)
        }

        return results
    }
}
