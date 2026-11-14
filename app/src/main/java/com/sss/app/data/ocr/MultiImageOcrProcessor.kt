package com.sss.app.data.ocr

import android.net.Uri
import com.sss.app.data.local.ScreenshotMetaEntity
import kotlinx.coroutines.CancellationException

class OcrInput(val uri: Uri, val meta: ScreenshotMetaEntity?)

class MultiImageOcrProcessor(
    private val ocrRepository: OcrRepository
) {
    suspend fun processImages(
        inputs: List<OcrInput>,
        useCrop: Boolean,
        onProgress: (completed: Int, total: Int) -> Unit
    ): List<OcrPage> {
        val pages = ArrayList<OcrPage>()
        inputs.forEachIndexed { i, input ->
            val page = try {
                ocrRepository.recognize(input.uri, input.meta, useCrop, i)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("SSS_OCR", "OCR failed for ${input.uri}", e)
                OcrPage(i, 0, 0, emptyList(), error = e.message ?: e.javaClass.simpleName)
            }
            pages.add(page)
            onProgress(i + 1, inputs.size)
        }
        return pages
    }
}