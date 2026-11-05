package com.sss.app.ui.folder

import com.sss.app.data.local.ScreenshotEntity
import com.sss.app.data.settings.SortOrder
import com.sss.app.util.DateLabels

fun List<ScreenshotEntity>.sortedFor(order: SortOrder): List<ScreenshotEntity> = when (order) {
    SortOrder.OLDEST -> sortedWith(compareBy<ScreenshotEntity> { it.capturedAt }.thenBy { it.id })
    SortOrder.NEWEST -> sortedWith(compareByDescending<ScreenshotEntity> { it.capturedAt }.thenByDescending { it.id })
}

sealed interface GridEntry {
    data class Header(val key: String, val label: String, val count: Int, val ids: List<Long>) : GridEntry
    data class Shot(val shot: ScreenshotEntity, val index: Int) : GridEntry
}

/** [sorted] must already be sorted. `index` = position inside [sorted] (what the viewer receives). */
fun buildEntries(sorted: List<ScreenshotEntity>, showHeaders: Boolean): List<GridEntry> {
    if (!showHeaders) return sorted.mapIndexed { i, s -> GridEntry.Shot(s, i) }
    val out = ArrayList<GridEntry>(sorted.size + 16)
    var i = 0
    while (i < sorted.size) {
        val day = DateLabels.day(sorted[i].capturedAt)
        var j = i
        while (j < sorted.size && DateLabels.day(sorted[j].capturedAt) == day) j++
        val group = sorted.subList(i, j)
        out.add(GridEntry.Header(day.toString(), DateLabels.dayLabel(day), group.size, group.map { it.id }))
        for (k in i until j) out.add(GridEntry.Shot(sorted[k], k))
        i = j
    }
    return out
}