package com.sss.app.domain.ocr

/** A line that was left out of the notes. [page] is the screenshot number (1-based). */
data class Ignored(val page: Int, val text: String)

sealed interface NoteBlock {
    data class Heading(val level: Int, val text: String) : NoteBlock
    data class Paragraph(val text: String) : NoteBlock
    data class Bullet(val indent: Int, val text: String) : NoteBlock
    data class Numbered(val indent: Int, val label: String, val text: String) : NoteBlock
    data class Marker(val page: Int) : NoteBlock          // "--- Screenshot N ---"
}

/**
 * Notes are stored as light Markdown text, no database change:
 *   --- Screenshot 1 ---  /  # H1  ## H2  ### H3  /  - bullet  /  1. numbered  /  plain paragraph
 * and, after "=== Ignored text ===", one "page|text" line per hidden line.
 */
object NoteFormat {

    const val IGNORED_MARK = "=== Ignored text ==="

    private val HEADING = Regex("^(#{1,3})\\s+(.+)$")
    private val BULLET = Regex("^(\\s*)[-*\u2022]\\s+(.+)$")
    private val NUMBERED = Regex("^(\\s*)(\\d{1,3}[.)]|\\([0-9a-z]{1,2}\\)|[a-h][.)])\\s+(.+)$")
    private val MARKER = Regex("^---\\s*Screenshot\\s+(\\d+)\\s*---$")

    // ---------------------------------------------------------------- ignored section
    fun split(content: String): Pair<String, List<Ignored>> {
        val idx = content.indexOf("\n$IGNORED_MARK")
        if (idx < 0) return content to emptyList()
        val main = content.substring(0, idx).trimEnd()
        val tail = content.substring(idx + 1 + IGNORED_MARK.length)
        val items = tail.lines().mapNotNull { l ->
            val p = l.indexOf('|')
            if (p <= 0) null else l.substring(0, p).trim().toIntOrNull()?.let { Ignored(it, l.substring(p + 1)) }
        }
        return main to items
    }

    fun join(main: String, ignored: List<Ignored>): String =
        if (ignored.isEmpty()) main
        else main.trimEnd() + "\n\n" + IGNORED_MARK + "\n" + ignored.joinToString("\n") { "${it.page}|${it.text}" }

    /** Puts an ignored line back as a paragraph at the end of its screenshot section. */
    fun restore(content: String, item: Ignored): String {
        val (main, ignored) = split(content)
        val remaining = ignored.toMutableList()
        if (!remaining.remove(item)) return content
        val lines = main.lines().toMutableList()
        val start = lines.indexOfFirst { MARKER.matchEntire(it.trim())?.groupValues?.get(1)?.toIntOrNull() == item.page }
        var insertAt = lines.size
        if (start >= 0) {
            for (i in start + 1 until lines.size) if (MARKER.matches(lines[i].trim())) { insertAt = i; break }
        }
        var pos = insertAt
        while (pos > 0 && lines[pos - 1].isBlank()) pos--
        lines.add(pos, "")
        lines.add(pos + 1, item.text)
        return join(lines.joinToString("\n"), remaining)
    }

    // ---------------------------------------------------------------- parse / render
    fun parse(main: String): List<NoteBlock> {
        val out = ArrayList<NoteBlock>()
        val para = StringBuilder()
        fun flush() {
            if (para.isNotEmpty()) { out.add(NoteBlock.Paragraph(para.toString().trim())); para.clear() }
        }
        for (raw in main.lines()) {
            val line = raw.trimEnd()
            if (line.isBlank()) { flush(); continue }
            val marker = MARKER.matchEntire(line.trim())
            val head = HEADING.matchEntire(line)
            val bullet = BULLET.matchEntire(line)
            val num = NUMBERED.matchEntire(line)
            when {
                marker != null -> { flush(); out.add(NoteBlock.Marker(marker.groupValues[1].toInt())) }
                head != null -> { flush(); out.add(NoteBlock.Heading(head.groupValues[1].length, head.groupValues[2].trim())) }
                bullet != null -> { flush(); out.add(NoteBlock.Bullet((bullet.groupValues[1].length / 2).coerceIn(0, 2), bullet.groupValues[2].trim())) }
                num != null -> { flush(); out.add(NoteBlock.Numbered((num.groupValues[1].length / 2).coerceIn(0, 2), num.groupValues[2], num.groupValues[3].trim())) }
                else -> { if (para.isNotEmpty()) para.append('\n'); para.append(line.trim()) }
            }
        }
        flush()
        return if (out.none { it is NoteBlock.Heading }) upgradeLegacy(out) else out
    }

    /** Notes made by older versions are plain lines. Give them the old heading rules so they still look decent. */
    private fun upgradeLegacy(blocks: List<NoteBlock>): List<NoteBlock> = blocks.flatMap { b ->
        if (b is NoteBlock.Paragraph && b.text.contains('\n')) {
            b.text.lines().flatMap { l ->
                HeadingDetector.classify(l).map { c ->
                    if (c.type == TextBlockType.HEADING) NoteBlock.Heading(2, c.text.removeSuffix(":")) else NoteBlock.Paragraph(c.text)
                }
            }
        } else listOf(b)
    }

    fun render(blocks: List<NoteBlock>): String {
        val sb = StringBuilder()
        var prevList = false
        for (b in blocks) {
            val isList = b is NoteBlock.Bullet || b is NoteBlock.Numbered
            if (sb.isNotEmpty()) sb.append(if (isList && prevList) "\n" else "\n\n")
            sb.append(
                when (b) {
                    is NoteBlock.Heading -> "#".repeat(b.level) + " " + b.text
                    is NoteBlock.Paragraph -> b.text
                    is NoteBlock.Bullet -> "  ".repeat(b.indent) + "- " + b.text
                    is NoteBlock.Numbered -> "  ".repeat(b.indent) + b.label + " " + b.text
                    is NoteBlock.Marker -> "--- Screenshot ${b.page} ---"
                }
            )
            prevList = isList
        }
        return sb.toString()
    }

    /** Clean text for Copy and Share: no # signs, bullets as dots. */
    fun toPlain(main: String, withMarkers: Boolean = false): String {
        val sb = StringBuilder()
        var prevList = false
        for (b in parse(main)) {
            val isList = b is NoteBlock.Bullet || b is NoteBlock.Numbered
            if (!isList && prevList) sb.append("\n")
            when (b) {
                is NoteBlock.Heading -> { if (sb.isNotEmpty()) sb.append("\n"); sb.append(b.text).append("\n\n") }
                is NoteBlock.Paragraph -> sb.append(b.text).append("\n\n")
                is NoteBlock.Bullet -> sb.append("  ".repeat(b.indent)).append("\u2022 ").append(b.text).append("\n")
                is NoteBlock.Numbered -> sb.append("  ".repeat(b.indent)).append(b.label).append(' ').append(b.text).append("\n")
                is NoteBlock.Marker -> if (withMarkers) { if (sb.isNotEmpty()) sb.append("\n"); sb.append("\u2014 Screenshot ${b.page} \u2014\n\n") }
            }
            prevList = isList
        }
        return sb.toString().trim()
    }
}