package com.sss.app.ui.ocr

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sss.app.data.local.StudyDocumentEntity
import com.sss.app.data.ocr.MultiImageOcrProcessor
import com.sss.app.data.ocr.OcrRepository
import com.sss.app.data.repository.StudyDocumentRepository
import com.sss.app.domain.ocr.TextCleanupProcessor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class OcrViewModel @Inject constructor(
    application: Application,
    private val studyDocumentRepository: StudyDocumentRepository
) : AndroidViewModel(application) {

    private val ocrRepository = OcrRepository(application)
    private val multiProcessor = MultiImageOcrProcessor(ocrRepository)

    private val _ocrUiState = MutableStateFlow(OcrUiState())
    val ocrUiState: StateFlow<OcrUiState> = _ocrUiState.asStateFlow()

    private val _currentDocument = MutableStateFlow<StudyDocumentEntity?>(null)
    val currentDocument: StateFlow<StudyDocumentEntity?> = _currentDocument.asStateFlow()

    private val _saveStatus = MutableStateFlow("Saved")
    val saveStatus: StateFlow<String> = _saveStatus.asStateFlow()

    fun extractTextFromImages(folderId: Long?, folderName: String, selectedUris: List<Uri>) {
        if (_ocrUiState.value.status == OcrStatus.PROCESSING) return
        if (selectedUris.isEmpty()) {
            _ocrUiState.value = OcrUiState(
                status = OcrStatus.ERROR,
                errorMessage = "Select at least one screenshot."
            )
            return
        }

        val orderedUris = selectedUris.toList()

        _ocrUiState.value = OcrUiState(
            status = OcrStatus.PROCESSING,
            totalImages = orderedUris.size
        )

        viewModelScope.launch {
            try {
                val results = withContext(Dispatchers.IO) {
                    multiProcessor.processImages(orderedUris) { completed, total ->
                        _ocrUiState.value = _ocrUiState.value.copy(
                            completedImages = completed,
                            totalImages = total
                        )
                    }
                }

                val succeededCount = results.count { it.succeeded }
                val combinedTextBuilder = StringBuilder()

                results.forEachIndexed { index, result ->
                    combinedTextBuilder.append("--- Screenshot ${index + 1} ---\n\n")
                    if (result.succeeded) {
                        val cleaned = TextCleanupProcessor.clean(result.extractedText)
                        if (cleaned.isNotBlank()) {
                            combinedTextBuilder.append(cleaned)
                        } else {
                            combinedTextBuilder.append("[No text recognized in this image]")
                        }
                    } else {
                        combinedTextBuilder.append("[OCR Failed: ${result.errorMessage}]")
                    }
                    combinedTextBuilder.append("\n\n")
                }

                val finalContent = combinedTextBuilder.toString().trim()
                val docTitle = "$folderName Notes"

                val document = StudyDocumentEntity(
                    title = docTitle,
                    content = finalContent,
                    sourceFolderId = folderId,
                    sourceImageCount = orderedUris.size
                )

                val docId = studyDocumentRepository.saveDocument(document)
                val savedDoc = document.copy(id = docId)

                _currentDocument.value = savedDoc

                _ocrUiState.value = OcrUiState(
                    status = if (succeededCount == orderedUris.size) OcrStatus.SUCCESS else OcrStatus.PARTIAL_SUCCESS,
                    completedImages = orderedUris.size,
                    totalImages = orderedUris.size,
                    results = results,
                    createdDocumentId = docId
                )

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ocrUiState.value = OcrUiState(
                    status = OcrStatus.ERROR,
                    errorMessage = e.message ?: "Text extraction failed."
                )
            }
        }
    }

    fun loadDocument(documentId: Long) {
        viewModelScope.launch {
            val doc = studyDocumentRepository.getDocumentById(documentId)
            _currentDocument.value = doc
        }
    }

    fun updateDocumentTitle(newTitle: String) {
        val current = _currentDocument.value ?: return
        val updated = current.copy(title = newTitle, updatedAt = System.currentTimeMillis())
        _currentDocument.value = updated
        saveDocumentInternal(updated)
    }

    fun updateDocumentContent(newContent: String) {
        val current = _currentDocument.value ?: return
        val updated = current.copy(content = newContent, updatedAt = System.currentTimeMillis())
        _currentDocument.value = updated
        saveDocumentInternal(updated)
    }

    private fun saveDocumentInternal(doc: StudyDocumentEntity) {
        _saveStatus.value = "Saving..."
        viewModelScope.launch {
            studyDocumentRepository.updateDocument(doc)
            _saveStatus.value = "Saved"
        }
    }

    fun deleteDocument(document: StudyDocumentEntity, onDeleted: () -> Unit) {
        viewModelScope.launch {
            studyDocumentRepository.deleteDocument(document)
            onDeleted()
        }
    }

    fun resetOcrState() {
        _ocrUiState.value = OcrUiState()
    }
}
