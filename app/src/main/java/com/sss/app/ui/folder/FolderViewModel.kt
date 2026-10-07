package com.sss.app.ui.folder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sss.app.data.local.ScreenshotEntity
import com.sss.app.data.repository.ScreenshotRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FolderViewModel @Inject constructor(
    private val repository: ScreenshotRepository
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

    fun deleteScreenshot(screenshot: ScreenshotEntity) {
        viewModelScope.launch {
            repository.deleteScreenshot(screenshot)
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