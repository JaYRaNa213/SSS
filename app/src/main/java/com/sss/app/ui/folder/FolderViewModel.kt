package com.sss.app.ui.folder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sss.app.data.local.ScreenshotEntity
import com.sss.app.data.local.StudyDocumentEntity
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
    private val studyDocumentRepository: StudyDocumentRepository
) : ViewModel() {

    fun screenshots(folderId: Long): StateFlow<List<ScreenshotEntity>> {
        return repository
            .getScreenshotsByFolder(folderId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )
    }

    fun getDocumentsByFolder(folderId: Long): Flow<List<StudyDocumentEntity>> {
        return studyDocumentRepository.getDocumentsByFolder(folderId)
    }

    fun deleteScreenshot(screenshot: ScreenshotEntity) {
        viewModelScope.launch {
            repository.deleteScreenshot(screenshot)
        }
    }

    fun deleteScreenshots(screenshots: List<ScreenshotEntity>) {
        viewModelScope.launch {
            screenshots.forEach { repository.deleteScreenshot(it) }
        }
    }

    fun addTestScreenshot(folderId: Long) {
        viewModelScope.launch {

            val currentCount =
                repository.getScreenshotCount(folderId)

            repository.addScreenshot(
                folderId = folderId,
                filePath = "test_screenshot_${currentCount + 1}.png",
                sequenceNumber = currentCount + 1
            )
        }
    }
}
