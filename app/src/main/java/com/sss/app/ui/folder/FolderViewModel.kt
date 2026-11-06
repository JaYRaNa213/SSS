package com.sss.app.ui.folder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sss.app.data.local.FolderEntity
import com.sss.app.data.local.ScreenshotEntity
import com.sss.app.data.local.ScreenshotMetaEntity
import com.sss.app.data.local.StudyDocumentEntity
import com.sss.app.data.repository.AllScreenshotsRepository
import com.sss.app.data.repository.FolderRepository
import com.sss.app.data.repository.FolderResult
import com.sss.app.data.repository.PdfRequest
import com.sss.app.data.repository.PdfRequestHolder
import com.sss.app.data.repository.ScreenshotMetaRepository
import com.sss.app.data.repository.ScreenshotRepository
import com.sss.app.data.repository.SessionRepository
import com.sss.app.data.repository.StudyDocumentRepository
import com.sss.app.data.repository.UndoDeleteManager
import com.sss.app.data.settings.AppSettings
import com.sss.app.data.settings.SettingsKeys
import com.sss.app.data.settings.SettingsRepository
import com.sss.app.data.settings.SortOrder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FolderViewModel @Inject constructor(
    private val repository: ScreenshotRepository,
    private val studyDocumentRepository: StudyDocumentRepository,
    private val allScreenshotsRepository: AllScreenshotsRepository,
    private val folderRepository: FolderRepository,
    private val sessionRepository: SessionRepository,
    private val undoManager: UndoDeleteManager,
    private val settingsRepository: SettingsRepository,
    private val metaRepository: ScreenshotMetaRepository,
    private val pdfHolder: PdfRequestHolder
) : ViewModel() {

    private val screenshotFlows = mutableMapOf<Long, StateFlow<List<ScreenshotEntity>>>()

    fun screenshots(folderId: Long): StateFlow<List<ScreenshotEntity>> =
        screenshotFlows.getOrPut(folderId) {
            repository.getScreenshotsByFolder(folderId)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
        }

    val settings: StateFlow<AppSettings> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val metaMap: StateFlow<Map<Long, ScreenshotMetaEntity>> =
        metaRepository.metaMap.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val analysisProgress: StateFlow<Pair<Int, Int>?> = metaRepository.progress

    val folders: StateFlow<List<FolderEntity>> =
        folderRepository.folders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val undo = undoManager.state

    fun folder(id: Long): Flow<FolderEntity?> = if (id == -1L) flowOf(null) else folderRepository.folderFlow(id)

    fun getDocumentsByFolder(folderId: Long): Flow<List<StudyDocumentEntity>> =
        studyDocumentRepository.getDocumentsByFolder(folderId)

    // ---- delete / undo -------------------------------------------------------------
    fun delete(items: List<ScreenshotEntity>) { viewModelScope.launch { undoManager.delete(items) } }
    fun undoDelete() { viewModelScope.launch { undoManager.undo() } }
    fun consumeUndo(token: Long) = undoManager.consumed(token)

    // ---- folders -------------------------------------------------------------------
    fun rename(id: Long, raw: String, onResult: (FolderResult) -> Unit) {
        viewModelScope.launch { onResult(folderRepository.renameFolder(id, raw)) }
    }

    fun deleteFolder(id: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            val folder = folderRepository.getFoldersOnce().firstOrNull { it.id == id } ?: return@launch
            val active = sessionRepository.getActiveSession()
            if (active != null && active.isActive && active.folderId == id) sessionRepository.stopSession()
            val paths = repository.getScreenshotsByFolderList(id).map { it.filePath }
            if (paths.isNotEmpty()) allScreenshotsRepository.hide(paths)
            folderRepository.deleteFolder(folder)
            onDone()
        }
    }

    fun moveTo(items: List<ScreenshotEntity>, targetId: Long, fromOther: Boolean, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            if (fromOther) {
                items.sortedBy { it.capturedAt }.forEach { repository.addScreenshotIfAbsent(targetId, it.filePath, it.capturedAt) }
            } else {
                repository.moveScreenshots(items, targetId)
            }
            onDone()
        }
    }

    fun setSort(isOther: Boolean, order: SortOrder) {
        viewModelScope.launch {
            settingsRepository.set(if (isOther) SettingsKeys.SORT_OTHER else SettingsKeys.SORT_FOLDERS, order.name)
        }
    }

    // ---- crop / rotation -----------------------------------------------------------
    fun analyze(paths: List<String>) { viewModelScope.launch { metaRepository.analyzeMissing(paths) } }

    /** [currentEffective] is the rotation shown right now (saved or auto). */
    fun rotate(path: String, currentEffective: Int) {
        viewModelScope.launch { metaRepository.setRotation(path, currentEffective + 90) }
    }

    fun saveCrop(path: String, t: Float, b: Float, l: Float, r: Float, applyToAll: List<String>?) {
        viewModelScope.launch {
            if (applyToAll != null) metaRepository.saveCropToAll(applyToAll, t, b, l, r)
            else metaRepository.saveCrop(path, t, b, l, r)
        }
    }

    // ---- pdf -----------------------------------------------------------------------
    fun openPdf(folderName: String, shots: List<ScreenshotEntity>) {
        pdfHolder.request = PdfRequest(folderName, shots)
    }
}