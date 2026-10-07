package com.sss.app.ui.capture

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sss.app.capture.ScreenshotObserver
import com.sss.app.data.local.CaptureSessionEntity
import com.sss.app.data.repository.ScreenshotRepository
import com.sss.app.data.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeSessionScreenshotCount: StateFlow<Int> = _activeSession
        .flatMapLatest { session ->
            if (session != null) {
                screenshotRepository.getScreenshotsByFolder(session.folderId)
                    .map { it.size }
            } else {
                flowOf(0)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0
        )

    private val screenshotObserver =
        ScreenshotObserver(
            context = application,
            onScreenshotDetected = { uri ->
                onScreenshotDetected(uri)
            }
        )

    fun loadActiveSession() {
        viewModelScope.launch {
            val session = repository.getActiveSession()
            _activeSession.value = session
            if (session != null && session.isActive) {
                screenshotObserver.start()
            }
        }
    }

    fun startCapture(folderId: Long) {
        viewModelScope.launch {
            val current = repository.getActiveSession()
            if (current != null && current.folderId == folderId && current.isActive) {
                _activeSession.value = current
                return@launch
            }

            if (current != null && current.isActive) {
                screenshotObserver.stop()
                repository.stopSession()
            }

            repository.startSession(folderId)
            _activeSession.value = repository.getActiveSession()
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