package com.sss.app.ui.folder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sss.app.data.local.ScreenshotEntity
import com.sss.app.data.local.StudyDocumentEntity
import com.sss.app.data.repository.AllScreenshotsRepository
import com.sss.app.data.repository.ScreenshotRepository
import com.sss.app.data.repository.StudyDocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FolderViewModel @Inject constructor(
    private val repository: ScreenshotRepository,
    private val studyDocumentRepository: StudyDocumentRepository,
    // already provided to Hilt (AllScreenshotsViewModel injects it) -> no module change needed
    private val allScreenshotsRepository: AllScreenshotsRepository
) : ViewModel() {

    // One StateFlow per folder. The old code created a brand-new stateIn()
    // (and a new coroutine) on every call.
    private val screenshotFlows = mutableMapOf<Long, StateFlow<List<ScreenshotEntity>>>()

    fun screenshots(folderId: Long): StateFlow<List<ScreenshotEntity>> {
        return screenshotFlows.getOrPut(folderId) {
            repository
                .getScreenshotsByFolder(folderId)
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = emptyList()
                )
        }
    }

    fun getDocumentsByFolder(folderId: Long): Flow<List<StudyDocumentEntity>> {
        return studyDocumentRepository.getDocumentsByFolder(folderId)
    }

    /**
     * Removes the screenshot from the folder only (file stays on the phone).
     * It is also added to the hidden list so it does NOT jump into the "Other" folder.
     */
    fun deleteScreenshot(screenshot: ScreenshotEntity) {
        viewModelScope.launch {
            allScreenshotsRepository.hide(listOf(screenshot.filePath))
            repository.deleteScreenshot(screenshot)
        }
    }

    fun deleteScreenshots(screenshots: List<ScreenshotEntity>) {
        viewModelScope.launch {
            allScreenshotsRepository.hide(screenshots.map { it.filePath })
            screenshots.forEach { repository.deleteScreenshot(it) }
        }
    }

    // addTestScreenshot() removed: it inserted fake "test_screenshot_1.png" rows.
}