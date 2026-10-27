package com.sss.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sss.app.data.local.FolderEntity
import com.sss.app.data.local.FolderWithCount
import com.sss.app.data.repository.AllScreenshotsRepository
import com.sss.app.data.repository.FolderRepository
import com.sss.app.data.repository.ScreenshotRepository
import com.sss.app.data.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: FolderRepository,
    private val screenshotRepository: ScreenshotRepository,
    private val allScreenshotsRepository: AllScreenshotsRepository,
    private val sessionRepository: SessionRepository
) : ViewModel() {

    val foldersWithCount: StateFlow<List<FolderWithCount>> =
        repository.foldersWithCount.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun addFolder(name: String) {
        viewModelScope.launch {
            repository.addFolder(name)
        }
    }

    fun deleteFolder(folder: FolderEntity) {
        viewModelScope.launch {
            val active = sessionRepository.getActiveSession()
            if (active != null && active.isActive && active.folderId == folder.id) {
                sessionRepository.stopSession()
            }

            try {
                val folderShots = screenshotRepository.getScreenshotsByFolderList(folder.id)
                val paths = folderShots.map { it.filePath }
                if (paths.isNotEmpty()) {
                    allScreenshotsRepository.hide(paths)
                }
            } catch (e: Exception) {
                // Ignore errors
            }
            repository.deleteFolder(folder)
        }
    }
}
