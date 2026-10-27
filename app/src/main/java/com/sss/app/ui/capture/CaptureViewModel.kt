package com.sss.app.ui.capture

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

/**
 * The ScreenshotObserver no longer lives here.
 * A ViewModel is destroyed when the Activity goes away, which silently stopped
 * screenshot detection while the bubble still said "Active".
 * Detection now runs inside OverlayBubbleService (foreground service).
 *
 * This ViewModel only edits the session row in the database. Both the UI and the
 * service react to that row.
 */
@HiltViewModel
class CaptureViewModel @Inject constructor(
    application: Application,
    private val repository: SessionRepository,
    private val screenshotRepository: ScreenshotRepository
) : AndroidViewModel(application) {

    private val _activeSession = MutableStateFlow<CaptureSessionEntity?>(null)
    val activeSession: StateFlow<CaptureSessionEntity?> = _activeSession.asStateFlow()

    init {
        // Follow the database, so a folder switched from the floating panel
        // (or a capture stopped from the notification) shows up in the app immediately.
        viewModelScope.launch {
            repository.observeActiveSession().collect { session ->
                _activeSession.value = session?.takeIf { it.isActive }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeSessionScreenshotCount: StateFlow<Int> = _activeSession
        .flatMapLatest { session ->
            if (session != null) {
                screenshotRepository.getScreenshotsByFolder(session.folderId).map { it.size }
            } else {
                flowOf(0)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0
        )

    /** Kept so existing calls still compile. The flow in init() already keeps state fresh. */
    fun loadActiveSession() {
        viewModelScope.launch {
            _activeSession.value = repository.getActiveSession()?.takeIf { it.isActive }
        }
    }

    fun startCapture(folderId: Long) {
        viewModelScope.launch {
            val current = repository.getActiveSession()
            when {
                // already capturing into this folder
                current != null && current.isActive && current.folderId == folderId -> Unit
                // capturing into another folder -> just switch, don't stop/start anything
                current != null && current.isActive -> repository.switchActiveFolder(folderId)
                else -> repository.startSession(folderId)
            }
        }
    }

    fun stopCapture() {
        viewModelScope.launch {
            repository.stopSession()
            _activeSession.value = null
        }
    }
}