package com.sss.app.domain.folder

import com.sss.app.data.local.FolderEntity
import com.sss.app.data.local.FolderWithCount

/** Pure helpers for the folder tree. Folders are small in number, so everything works in memory. */
object FolderTree {

    /** English > Vocab > Unit 1 > Week 1 */
    const val MAX_DEPTH = 4

    data class Row<T>(val item: T, val depth: Int)   // depth 0 = top level

    /** A folder whose parent no longer exists is treated as top-level (safety net). */
    private fun parentOf(f: FolderEntity, ids: Set<Long>): Long? = f.parentId?.takeIf { it in ids }

    fun children(all: List<FolderEntity>, parentId: Long?): List<FolderEntity> {
        val ids = all.map { it.id }.toSet()
        return all.filter { parentOf(it, ids) == parentId }.sortedBy { it.createdAt }
    }

    fun descendantIds(all: List<FolderEntity>, id: Long): List<Long> {
        val out = ArrayList<Long>()
        val seen = HashSet<Long>().apply { add(id) }
        val queue = ArrayDeque<Long>().apply { add(id) }
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            for (f in all) if (f.parentId == cur && seen.add(f.id)) { out.add(f.id); queue.add(f.id) }
        }
        return out
    }

    fun subtreeIds(all: List<FolderEntity>, id: Long): List<Long> = listOf(id) + descendantIds(all, id)

    /** Level of a folder: top-level = 1. 0 for null. */
    fun depth(all: List<FolderEntity>, id: Long?): Int {
        var d = 0
        var cur: Long? = id
        val seen = HashSet<Long>()
        while (cur != null && seen.add(cur)) {
            val cid: Long = cur
            val f = all.firstOrNull { it.id == cid } ?: break
            d++
            cur = f.parentId
        }
        return d
    }

    /** Levels in the subtree starting at [id]: a leaf = 1. */
    fun height(all: List<FolderEntity>, id: Long): Int {
        val kids = all.filter { it.parentId == id }
        return if (kids.isEmpty()) 1 else 1 + kids.maxOf { height(all, it.id) }
    }

    /** Top-level ancestor first, the folder itself last. */
    fun path(all: List<FolderEntity>, id: Long): List<FolderEntity> {
        val out = ArrayList<FolderEntity>()
        var cur: Long? = id
        val seen = HashSet<Long>()
        while (cur != null && seen.add(cur)) {
            val cid: Long = cur
            val f = all.firstOrNull { it.id == cid } ?: break
            out.add(0, f)
            cur = f.parentId
        }
        return out
    }

    fun pathLabel(all: List<FolderEntity>, id: Long, sep: String = " \u203A "): String =
        path(all, id).joinToString(sep) { it.name }

    /** Depth-first list for pickers and menus. Folders in [exclude] are skipped together with their children. */
    fun flattenEntities(all: List<FolderEntity>, exclude: Set<Long> = emptySet()): List<Row<FolderEntity>> {
        val out = ArrayList<Row<FolderEntity>>()
        fun walk(parentId: Long?, depth: Int) {
            for (f in children(all, parentId)) {
                if (f.id in exclude) continue
                out.add(Row(f, depth))
                if (depth + 1 < MAX_DEPTH) walk(f.id, depth + 1)
            }
        }
        walk(null, 0)
        return out
    }

    /** Depth-first list for Home. [expanded] = null shows everything, otherwise only expanded branches. */
    fun flattenCounts(all: List<FolderWithCount>, expanded: Set<Long>? = null): List<Row<FolderWithCount>> {
        val ids = all.map { it.folder.id }.toSet()
        val byParent = all.groupBy { it.folder.parentId?.takeIf { p -> p in ids } }
            .mapValues { (_, v) -> v.sortedBy { it.folder.createdAt } }
        val out = ArrayList<Row<FolderWithCount>>()
        fun walk(parentId: Long?, depth: Int) {
            for (f in byParent[parentId].orEmpty()) {
                out.add(Row(f, depth))
                if (expanded == null || f.folder.id in expanded) walk(f.folder.id, depth + 1)
            }
        }
        walk(null, 0)
        return out
    }

    fun subtreeItems(all: List<FolderWithCount>, id: Long): List<FolderWithCount> {
        val ids = subtreeIds(all.map { it.folder }, id).toSet()
        return all.filter { it.folder.id in ids }
    }

    fun totalCount(all: List<FolderWithCount>, id: Long): Int = subtreeItems(all, id).sumOf { it.screenshotCount }

    fun lastUpdated(all: List<FolderWithCount>, id: Long): Long? =
        subtreeItems(all, id).mapNotNull { it.lastUpdatedAt }.maxOrNull()

    /** Two thumbnails for a folder card. Falls back to the newest screenshots of its subfolders. */
    fun thumbs(all: List<FolderWithCount>, id: Long): Pair<String?, String?> {
        val pool = subtreeItems(all, id).sortedByDescending { it.lastUpdatedAt ?: 0L }
        val own = pool.firstOrNull { it.folder.id == id }
        val first = own?.latestScreenshotPath ?: pool.firstNotNullOfOrNull { it.latestScreenshotPath }
        val second = own?.secondScreenshotPath
            ?: pool.firstOrNull { it.folder.id != id && it.latestScreenshotPath != null && it.latestScreenshotPath != first }?.latestScreenshotPath
        return first to second
    }
}