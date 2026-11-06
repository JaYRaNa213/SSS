package com.sss.app.ui.pdf

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sss.app.data.local.ScreenshotEntity
import com.sss.app.data.local.ScreenshotMetaEntity
import com.sss.app.data.repository.PdfRequestHolder
import com.sss.app.data.repository.ScreenshotMetaRepository
import com.sss.app.data.settings.SettingsRepository
import com.sss.app.data.settings.SortOrder
import com.sss.app.ui.common.MetaMath
import com.sss.app.ui.folder.sortedFor
import com.sss.app.util.MediaIds
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

sealed interface PdfRun {
    data object Idle : PdfRun
    data class Running(val done: Int, val total: Int) : PdfRun
    data class Done(val saved: PdfSaver.Saved) : PdfRun
    data class Failed(val message: String) : PdfRun
}

/**
 * The PDF work lives in this ViewModel. If the user leaves the PDF screen the ViewModel is cleared,
 * the job is cancelled, and PdfSaver deletes the half-written file (clean cancel).
 */
@HiltViewModel
class PdfViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    holder: PdfRequestHolder,
    private val metaRepository: ScreenshotMetaRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val request = holder.request
    val folderName: String = request?.folderName ?: "Screenshots"
    private val base: List<ScreenshotEntity> = request?.shots ?: emptyList()

    var name by mutableStateOf(PdfSaver.sanitize("${folderName}_${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())}"))
    var options by mutableStateOf(PdfOptions())
    var pages by mutableStateOf(base)
    var run by mutableStateOf<PdfRun>(PdfRun.Idle)

    val metaMap: StateFlow<Map<Long, ScreenshotMetaEntity>> =
        metaRepository.metaMap.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private var job: Job? = null

    init {
        viewModelScope.launch {
            val s = settingsRepository.settings.first()
            options = options.copy(
                perPage = s.pdfPerPage.coerceIn(1, 2),
                quality = PdfQuality.entries.getOrElse(s.pdfQuality) { PdfQuality.MEDIUM }
            )
        }
    }

    fun setOrder(order: PdfOrder) {
        options = options.copy(order = order)
        pages = when (order) {
            PdfOrder.SELECTED -> base
            PdfOrder.OLDEST -> base.sortedFor(SortOrder.OLDEST)
            PdfOrder.NEWEST -> base.sortedFor(SortOrder.NEWEST)
        }
    }

    fun move(i: Int, delta: Int) {
        val j = i + delta
        if (j !in pages.indices) return
        pages = pages.toMutableList().also { val t = it[i]; it[i] = it[j]; it[j] = t }
    }

    fun remove(i: Int) {
        if (i in pages.indices) pages = pages.toMutableList().also { it.removeAt(i) }
    }

    fun estimateMb(): Float = pages.size * when (options.quality) {
        PdfQuality.HIGH -> 0.7f; PdfQuality.MEDIUM -> 0.25f; PdfQuality.LOW -> 0.09f
    }

    fun finalName(): String = PdfSaver.uniqueName(context, PdfSaver.sanitize(name))

    fun create() {
        if (job?.isActive == true || pages.isEmpty()) return
        val finalName = finalName()
        if (finalName.isBlank()) return
        val snapshotPages = pages
        val snapshotOptions = options
        job = viewModelScope.launch {
            run = PdfRun.Running(0, snapshotPages.size)
            try {
                val s = settingsRepository.settings.first()
                val metas = metaRepository.metaMap.first()
                val sources = snapshotPages.map { sh ->
                    val m = metas[MediaIds.fromPath(sh.filePath)]
                    PdfPageSource(sh.filePath, m, MetaMath.effectiveRotation(m, snapshotOptions.useCrop, s.autoRotateWide))
                }
                val saved = withContext(Dispatchers.IO) {
                    val ctx = currentCoroutineContext()
                    PdfSaver.save(context, finalName) { out ->
                        PdfBuilder.build(context, sources, snapshotOptions, out,
                            onProgress = { d, t -> run = PdfRun.Running(d, t) },
                            checkCancel = { ctx.ensureActive() })
                    }
                }
                run = PdfRun.Done(saved)
            } catch (e: kotlinx.coroutines.CancellationException) {
                run = PdfRun.Idle
                throw e
            } catch (e: Exception) {
                run = PdfRun.Failed(e.message ?: "Unknown error")
            }
        }
    }

    fun cancel() { job?.cancel(); run = PdfRun.Idle }
    fun dismissResult() { run = PdfRun.Idle }
}