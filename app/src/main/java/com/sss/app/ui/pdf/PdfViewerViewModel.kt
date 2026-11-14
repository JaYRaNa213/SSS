package com.sss.app.ui.pdf

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sss.app.data.pdf.PdfItem
import com.sss.app.data.pdf.PdfLibraryRepository
import com.sss.app.data.pdf.PdfPageRenderer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PdfViewerState(
    val loading: Boolean = true,
    val error: String? = null,
    val item: PdfItem? = null,
    val aspects: List<Float> = emptyList()
)

@HiltViewModel
class PdfViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val repo: PdfLibraryRepository
) : ViewModel() {

    private val id: Long = savedStateHandle.get<Long>("id") ?: -1L

    private val _state = MutableStateFlow(PdfViewerState())
    val state: StateFlow<PdfViewerState> = _state.asStateFlow()

    private var renderer: PdfPageRenderer? = null
    @Volatile private var renderWidthPx = 1080

    // About 56 MB of rendered pages. Lower this number if you see out-of-memory problems on low-end phones.
    private val cache = object : LruCache<Int, Bitmap>(56 * 1024 * 1024) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount
    }

    init { load() }

    private fun load() {
        viewModelScope.launch {
            val item = repo.get(id)
            if (item == null) {
                _state.value = PdfViewerState(loading = false, error = "This PDF no longer exists")
                return@launch
            }
            val r = PdfPageRenderer(context, item.uri)
            val error = r.open()
            if (error != null) {
                r.close()
                _state.value = PdfViewerState(loading = false, error = error, item = item)
                return@launch
            }
            renderer = r
            _state.value = PdfViewerState(loading = false, item = item, aspects = r.aspects)
        }
    }

    /** Called by the screen with the width of the screen in pixels. A new width clears the page cache. */
    fun setRenderWidth(px: Int) {
        val w = px.coerceIn(720, 1800)
        if (w != renderWidthPx) {
            renderWidthPx = w
            cache.evictAll()
        }
    }

    fun cached(index: Int): Bitmap? = cache.get(index)

    suspend fun render(index: Int): Bitmap? {
        cache.get(index)?.let { return it }
        val w = renderWidthPx
        val bmp = renderer?.render(index, w) ?: return null
        if (w == renderWidthPx) cache.put(index, bmp)
        return bmp
    }

    fun reloadItem() {
        viewModelScope.launch {
            repo.get(id)?.let { fresh -> _state.update { it.copy(item = fresh) } }
        }
    }

    override fun onCleared() {
        renderer?.close()
        renderer = null
        cache.evictAll()
        super.onCleared()
    }
}