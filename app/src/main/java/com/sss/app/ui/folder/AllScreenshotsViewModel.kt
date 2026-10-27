package com.sss.app.ui.folder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sss.app.data.local.ScreenshotEntity
import com.sss.app.data.repository.AllScreenshotsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AllScreenshotsViewModel @Inject constructor(
    private val allScreenshotsRepository: AllScreenshotsRepository
) : ViewModel() {

    private val _allScreenshots = MutableStateFlow<List<ScreenshotEntity>>(emptyList())
    val allScreenshots: StateFlow<List<ScreenshotEntity>> = _allScreenshots.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadAllScreenshots() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val list = allScreenshotsRepository.getAllScreenshots()
                _allScreenshots.value = list
            } catch (e: Exception) {
                _allScreenshots.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }
}
