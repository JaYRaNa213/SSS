package com.sss.app.ui.capture

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sss.app.capture.ScreenshotObserver
import com.sss.app.data.local.CaptureSessionEntity
import com.sss.app.data.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CaptureViewModel @Inject constructor(
    application: Application,
    private val repository: SessionRepository
) : AndroidViewModel(application) {

    private val _activeSession =
        MutableStateFlow<CaptureSessionEntity?>(null)

    val activeSession: StateFlow<CaptureSessionEntity?> =
        _activeSession.asStateFlow()

    private val screenshotObserver =
        ScreenshotObserver(
            context = application,
            onScreenshotDetected = { uri ->
                onScreenshotDetected(uri)
            }
        )

    fun loadActiveSession() {
        viewModelScope.launch {
            _activeSession.value =
                repository.getActiveSession()
        }
    }

    fun startCapture(folderId: Long) {
        viewModelScope.launch {

            repository.startSession(folderId)

            _activeSession.value =
                repository.getActiveSession()

            screenshotObserver.start()
        }
    }

    fun stopCapture() {
        viewModelScope.launch {

            screenshotObserver.stop()

            repository.stopSession()

            _activeSession.value = null
        }
    }

    private fun onScreenshotDetected(uri: Uri) {

        // Temporary test.
        // Room storage will be connected in the next step.

        println("SSS Screenshot detected: $uri")
    }

    override fun onCleared() {
        screenshotObserver.stop()
        super.onCleared()
    }
}