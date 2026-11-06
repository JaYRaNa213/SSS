package com.sss.app.data.repository

import com.sss.app.data.local.ScreenshotEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class UndoState(val message: String, val items: List<ScreenshotEntity>, val token: Long)

/**
 * Delete = remove from the folder (the file stays in the phone gallery) + hide from "Other".
 * Undo restores the exact rows (same id / folder / sequence / date) and un-hides them.
 * NOT used for "Other": those are real files and use the system delete dialog (no undo).
 */
@Singleton
class UndoDeleteManager @Inject constructor(
    private val screenshots: ScreenshotRepository,
    private val allScreenshots: AllScreenshotsRepository
) {
    private val _state = MutableStateFlow<UndoState?>(null)
    val state: StateFlow<UndoState?> = _state.asStateFlow()

    suspend fun delete(items: List<ScreenshotEntity>) {
        if (items.isEmpty()) return
        allScreenshots.hide(items.map { it.filePath })
        items.forEach { screenshots.deleteScreenshot(it) }
        val msg = if (items.size == 1) "1 screenshot deleted" else "${items.size} screenshots deleted"
        _state.value = UndoState(msg, items, System.nanoTime())
    }

    suspend fun undo() {
        val s = _state.value ?: return
        allScreenshots.unhide(s.items.map { it.filePath })
        screenshots.insertAll(s.items)
        _state.value = null
    }

    fun consumed(token: Long) {
        if (_state.value?.token == token) _state.value = null
    }
}