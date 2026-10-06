package com.sss.app.ui.capture

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sss.app.capture.ScreenshotObserver
import com.sss.app.data.local.CaptureSessionEntity
import com.sss.app.data.repository.SessionRepository
import com.sss.app.data.repository.ScreenshotRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CaptureViewModel @Inject constructor(
    application: Application,
    private val repository: SessionRepository,
    private val screenshotRepository: ScreenshotRepository
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
        viewModelScope.launch {
            val session = repository.getActiveSession()

            if (session == null) {
                println("SSS No active session. Screenshot not saved.")
                return@launch
            }

            val screenshotCount =
                screenshotRepository.getScreenshotCount(session.folderId)

            val sequenceNumber = screenshotCount + 1

            screenshotRepository.addScreenshot(
                folderId = session.folderId,
                filePath = uri.toString(),
                sequenceNumber = sequenceNumber
            )

            println(
                "SSS Screenshot saved: folderId=${session.folderId}, " +
                        "sequence=$sequenceNumber, uri=$uri"
            )
        }
    }

    override fun onCleared() {
        screenshotObserver.stop()
        super.onCleared()
    }
}