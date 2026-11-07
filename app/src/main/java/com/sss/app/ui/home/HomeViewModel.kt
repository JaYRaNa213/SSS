package com.sss.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sss.app.data.local.FolderEntity
import com.sss.app.data.local.FolderWithCount
import com.sss.app.data.repository.AllScreenshotsRepository
import com.sss.app.data.repository.FolderRepository
import com.sss.app.data.repository.FolderResult
import com.sss.app.data.repository.ScreenshotRepository
import com.sss.app.data.repository.SessionRepository
import com.sss.app.data.settings.AppSettings
import com.sss.app.data.settings.SettingsKeys
import com.sss.app.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: FolderRepository,
    private val screenshotRepository: ScreenshotRepository,
    private val allScreenshotsRepository: AllScreenshotsRepository,
    private val sessionRepository: SessionRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val foldersWithCount: StateFlow<List<FolderWithCount>> =
        repository.foldersWithCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<AppSettings> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    /** Screenshots saved today in the folder that is capturing right now. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val todayCount: StateFlow<Int> = sessionRepository.observeActiveSession()
        .flatMapLatest { s ->
            if (s != null && s.isActive) screenshotRepository.getScreenshotsByFolder(s.folderId).map { list ->
                val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                list.count { it.capturedAt >= start }
            } else flowOf(0)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun createFolder(raw: String, colorIndex: Int, onResult: (FolderResult) -> Unit) {
        viewModelScope.launch { onResult(repository.createFolder(raw, colorIndex)) }
    }

    fun togglePause() {
        viewModelScope.launch { settingsRepository.set(SettingsKeys.CAPTURE_PAUSED, !settings.value.capturePaused) }
    }

    fun deleteFolder(folder: FolderEntity) {
        viewModelScope.launch {
            val active = sessionRepository.getActiveSession()
            if (active != null && active.isActive && active.folderId == folder.id) sessionRepository.stopSession()
            try {
                val paths = screenshotRepository.getScreenshotsByFolderList(folder.id).map { it.filePath }
                if (paths.isNotEmpty()) allScreenshotsRepository.hide(paths)
            } catch (e: Exception) { /* ignore */ }
            repository.deleteFolder(folder)
        }
    }
}