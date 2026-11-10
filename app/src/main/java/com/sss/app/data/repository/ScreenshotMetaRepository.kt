package com.sss.app.data.repository

import android.content.Context
import android.net.Uri
import com.sss.app.data.local.ScreenshotMetaDao
import com.sss.app.data.local.ScreenshotMetaEntity
import com.sss.app.ui.common.AutoCropper
import com.sss.app.util.MediaIds
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScreenshotMetaRepository @Inject constructor(
    private val dao: ScreenshotMetaDao,
    @ApplicationContext private val context: Context
) {
    val metaMap: Flow<Map<Long, ScreenshotMetaEntity>> =
        dao.observeAll().map { list -> list.associateBy { it.mediaId } }

    private val _progress = MutableStateFlow<Pair<Int, Int>?>(null)
    /** (done, total) while background analysis runs, else null. */
    val progress: StateFlow<Pair<Int, Int>?> = _progress.asStateFlow()

    private val gate = Semaphore(2)   // max 2 parallel analyses

    suspend fun setRotation(path: String, degrees: Int) {
        val id = MediaIds.fromPath(path)
        val cur = dao.get(id) ?: ScreenshotMetaEntity(mediaId = id)
        dao.upsert(cur.copy(rotation = ((degrees % 360) + 360) % 360))
    }

    /** [rotations] maps image path -> degrees (0..359) or -1 for "automatic". One database write for all. */
    suspend fun setRotations(rotations: Map<String, Int>) {
        if (rotations.isEmpty()) return
        val ids = rotations.keys.map { MediaIds.fromPath(it) }
        val existing = ids.chunked(500).flatMap { dao.getMany(it) }.associateBy { it.mediaId }
        val list = rotations.map { (path, deg) ->
            val id = MediaIds.fromPath(path)
            val cur = existing[id] ?: ScreenshotMetaEntity(mediaId = id)
            cur.copy(rotation = if (deg < 0) -1 else deg % 360)
        }
        dao.upsertAll(list)
    }

    suspend fun saveCrop(path: String, top: Float, bottom: Float, left: Float, right: Float) {
        analyzeUri(path)
        val id = MediaIds.fromPath(path)
        val cur = dao.get(id) ?: return
        dao.upsert(cur.copy(cropTop = top, cropBottom = bottom, cropLeft = left, cropRight = right, userAdjusted = true, autoAnalyzed = true))
    }

    suspend fun saveCropToAll(paths: List<String>, top: Float, bottom: Float, left: Float, right: Float) {
        analyzeMissing(paths)
        val metas = paths.map { MediaIds.fromPath(it) }.chunked(500).flatMap { dao.getMany(it) }
        dao.upsertAll(metas.map {
            it.copy(cropTop = top, cropBottom = bottom, cropLeft = left, cropRight = right, userAdjusted = true, autoAnalyzed = true)
        })
    }

    /** Analyses one screenshot (system bars + black bars). Skips if already analysed. */
    suspend fun analyzeUri(path: String, force: Boolean = false) {
        gate.withPermit {
            val id = MediaIds.fromPath(path)
            val cur = dao.get(id)
            if (!force && cur != null && cur.autoAnalyzed) return@withPermit
            val res = withContext(Dispatchers.Default) { AutoCropper.analyze(context, Uri.parse(path)) }
                ?: return@withPermit
            val base = cur ?: ScreenshotMetaEntity(mediaId = id)
            if (cur?.userAdjusted == true) {
                dao.upsert(cur.copy(imgW = res.w, imgH = res.h, autoAnalyzed = true))
            } else {
                dao.upsert(
                    base.copy(
                        cropTop = res.top, cropBottom = res.bottom, cropLeft = res.left, cropRight = res.right,
                        imgW = res.w, imgH = res.h, autoAnalyzed = true
                    )
                )
            }
        }
    }

    /** Background analysis for all screenshots of a folder that have no crop yet. */
    suspend fun analyzeMissing(paths: List<String>) {
        if (paths.isEmpty()) return
        val have = paths.map { MediaIds.fromPath(it) }.chunked(500)
            .flatMap { dao.getMany(it) }.filter { it.autoAnalyzed }.map { it.mediaId }.toSet()
        val todo = paths.filter { MediaIds.fromPath(it) !in have }
        if (todo.isEmpty()) return
        _progress.value = 0 to todo.size
        val done = AtomicInteger(0)
        try {
            coroutineScope {
                todo.map { p ->
                    async {
                        analyzeUri(p)
                        _progress.value = done.incrementAndGet() to todo.size
                    }
                }.awaitAll()
            }
        } finally {
            _progress.value = null
        }
    }
}