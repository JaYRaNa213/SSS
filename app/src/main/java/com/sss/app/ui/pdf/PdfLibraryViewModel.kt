package com.sss.app.ui.pdf

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sss.app.data.pdf.PdfDeleteResult
import com.sss.app.data.pdf.PdfItem
import com.sss.app.data.pdf.PdfLibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PdfSort(val label: String) {
    NEWEST("Newest first"), OLDEST("Oldest first"), NAME("Name A to Z"), SIZE("Largest first")
}

@HiltViewModel
class PdfLibraryViewModel @Inject constructor(
    private val repo: PdfLibraryRepository
) : ViewModel() {

    private val all = MutableStateFlow<List<PdfItem>>(emptyList())
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    val query = MutableStateFlow("")
    val sort = MutableStateFlow(PdfSort.NEWEST)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val total: StateFlow<Int> = all.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val items: StateFlow<List<PdfItem>> = combine(all, query, sort) { list, q, s ->
        val filtered = if (q.isBlank()) list else list.filter { it.name.contains(q.trim(), ignoreCase = true) }
        when (s) {
            PdfSort.NEWEST -> filtered.sortedByDescending { it.modifiedMs }
            PdfSort.OLDEST -> filtered.sortedBy { it.modifiedMs }
            PdfSort.NAME -> filtered.sortedBy { it.name.lowercase() }
            PdfSort.SIZE -> filtered.sortedByDescending { it.sizeBytes }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            all.value = repo.list()
            _loading.value = false
        }
    }

    fun rename(item: PdfItem, raw: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val error = repo.rename(item, raw)
            if (error != null) _message.value = error
            else {
                _message.value = "Renamed"
                refresh()
                onDone()
            }
        }
    }

    /** onNeedsConfirm: launch the system delete dialog with this sender. */
    fun delete(item: PdfItem, onNeedsConfirm: (IntentSender) -> Unit, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            when (val r = repo.delete(item)) {
                PdfDeleteResult.Done -> { _message.value = "PDF deleted"; refresh(); onDeleted() }
                is PdfDeleteResult.NeedsConfirm -> onNeedsConfirm(r.sender)
                is PdfDeleteResult.Failed -> _message.value = r.message
            }
        }
    }

    fun consumeMessage() { _message.value = null }
}