package com.sss.app.ui.ocr

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sss.app.data.local.StudyDocumentEntity
import com.sss.app.data.ocr.MultiImageOcrProcessor
import com.sss.app.data.ocr.OcrImageResult
import com.sss.app.data.ocr.OcrInput
import com.sss.app.data.ocr.OcrRepository
import com.sss.app.data.repository.ScreenshotMetaRepository
import com.sss.app.data.repository.StudyDocumentRepository
import com.sss.app.data.settings.SettingsRepository
import com.sss.app.domain.ocr.CleanupLevel
import com.sss.app.domain.ocr.NoteBuilder
import com.sss.app.util.MediaIds
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class OcrViewModel @Inject constructor(
    application: Application,
    private val studyDocumentRepository: StudyDocumentRepository,
    private val metaRepository: ScreenshotMetaRepository,
    private val settingsRepository: SettingsRepository
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
            _ocrUiState.value = OcrUiState(status = OcrStatus.ERROR, errorMessage = "Select at least one screenshot.")
            return
        }
        val orderedUris = selectedUris.toList()
        _ocrUiState.value = OcrUiState(status = OcrStatus.PROCESSING, totalImages = orderedUris.size)

        viewModelScope.launch {
            try {
                val settings = settingsRepository.settings.first()
                // make sure every screenshot has its crop, so only the content area is read
                if (settings.autoCrop) withContext(Dispatchers.IO) { metaRepository.analyzeMissing(orderedUris.map { it.toString() }) }
                val metas = metaRepository.metaMap.first()
                val inputs = orderedUris.map { u -> OcrInput(u, metas[MediaIds.fromPath(u.toString())]) }

                val pages = withContext(Dispatchers.IO) {
                    multiProcessor.processImages(inputs, settings.autoCrop) { completed, total ->
                        _ocrUiState.value = _ocrUiState.value.copy(completedImages = completed, totalImages = total)
                    }
                }

                val level = CleanupLevel.fromInt(settings.ocrCleanup)
                val content = withContext(Dispatchers.Default) { NoteBuilder.build(pages, level) }
                val failed = pages.count { it.error != null }

                val document = StudyDocumentEntity(
                    title = "$folderName Notes",
                    content = content,
                    sourceFolderId = folderId,
                    sourceImageCount = orderedUris.size
                )
                val docId = studyDocumentRepository.saveDocument(document)
                _currentDocument.value = document.copy(id = docId)

                _ocrUiState.value = OcrUiState(
                    status = if (failed == 0) OcrStatus.SUCCESS else OcrStatus.PARTIAL_SUCCESS,
                    completedImages = orderedUris.size,
                    totalImages = orderedUris.size,
                    results = pages.mapIndexed { i, p -> OcrImageResult(orderedUris[i].toString(), i, p.rawText, p.error) },
                    createdDocumentId = docId
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ocrUiState.value = OcrUiState(status = OcrStatus.ERROR, errorMessage = e.message ?: "Text extraction failed.")
            }
        }
    }

    fun loadDocument(documentId: Long) {
        viewModelScope.launch { _currentDocument.value = studyDocumentRepository.getDocumentById(documentId) }
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

    fun resetOcrState() { _ocrUiState.value = OcrUiState() }

    override fun onCleared() {
        ocrRepository.close()
        super.onCleared()
    }
}