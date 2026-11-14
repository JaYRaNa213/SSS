package com.sss.app.domain.ocr

import com.sss.app.data.ocr.OcrLine
import com.sss.app.data.ocr.OcrPage
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

enum class CleanupLevel {
    OFF, SMART, STRICT;

    companion object {
        fun fromInt(v: Int): CleanupLevel = entries.getOrElse(v) { SMART }
    }
}

/**
 * Turns raw OCR lines (with positions) into structured notes:
 *  1) drops unrelated lines (kept in the "ignored" list), 2) finds headings by text size,
 *  3) merges wrapped lines into paragraphs, 4) finds bullets and numbered lists.
 */
object NoteBuilder {

    // ---- tuning knobs ----------------------------------------------------------------------
    private const val H1_RATIO = 1.6f          // line height / normal text height
    private const val H2_RATIO = 1.3f
    private const val H3_RATIO = 1.15f
    private const val EDGE_TOP = 0.07f         // share of image height
    private const val EDGE_BOTTOM = 0.91f
    private const val EDGE_TOP_STRICT = 0.12f
    private const val EDGE_BOTTOM_STRICT = 0.86f
    private const val REPEAT_SHARE = 0.6f      // seen on this share of screenshots (and at least 3) = watermark
    private const val WRAP_FILL = 0.78f        // a line this full is assumed to wrap into the next one

    // ---- unrelated text patterns -----------------------------------------------------------
    private val CHROME = listOf(
        Regex("""^\d{1,2}:\d{2}(:\d{2})?(\s?[AaPp][Mm])?$"""),                              // 11:57
        Regex("""^\d{1,2}:\d{2}(:\d{2})?\s*[/|]\s*\d{1,2}:\d{2}(:\d{2})?$"""),            // 3:12 / 14:20
        Regex("""^\d{1,3}\s?%$"""),                                                           // battery
        Regex("""^(5G|4G|3G|LTE|VoLTE|VoWiFi|Wi-?Fi|\d+(\.\d+)?\s?(KB|MB)/s)$""", RegexOption.IGNORE_CASE),
        Regex(
            """^(sign up|sign in|log in|subscribe|subscribed|share|download|save|like|dislike|skip|skip ads?|ads?|live|autoplay|cast|settings|more|reply|comments?|shorts|home|search|menu|next|back|close|cancel|ok|done|follow|join|buy now)$""",
            RegexOption.IGNORE_CASE
        ),
        Regex("""^[\d.,]+\s?[KMB]?\s?(views?|likes?|subscribers?|comments?|watching)$""", RegexOption.IGNORE_CASE),
        Regex("""^(https?://|www\.)\S+$""", RegexOption.IGNORE_CASE),
        Regex("""^[^\p{L}\p{N}]+$"""),                                                        // only symbols
        Regex("""^\d{1,3}$""")                                                                // lone small number
    )

    // ---- list patterns ---------------------------------------------------------------------
    private val BULLET_GLYPH = Regex("^([\u2022\u00B7\u25CF\u25AA\u25A0\u25E6\u25CB\u27A2\u25BA\u25B6])\\s*(.+)$")
    private val BULLET_DASH = Regex("^([-\u2013\u2014*+])\\s+(.+)$")
    private val LETTER_BULLET = Regex("^([eo\u00B0])\\s+(\\S.*)$")     // OCR often reads a bullet dot as "e" or "o"
    private val NUMBERED = Regex("^(\\d{1,2}[.)]|\\([0-9a-z]{1,2}\\)|[a-h][.)])\\s+(\\S.*)$")
    private val CHAPTER = Regex("^(chapter|unit|module|section|part|topic|lesson|lecture)\\s+[\\w.-]+", RegexOption.IGNORE_CASE)

    // ================================================================================== public
    fun build(pages: List<OcrPage>, level: CleanupLevel): String {
        val repeated = if (level != CleanupLevel.OFF) repeatedKeys(pages) else emptySet()
        val blocks = ArrayList<NoteBlock>()
        val ignored = ArrayList<Ignored>()
        pages.forEachIndexed { i, page ->
            val n = i + 1
            blocks.add(NoteBlock.Marker(n))
            if (page.error != null) {
                blocks.add(NoteBlock.Paragraph("[OCR Failed: ${page.error}]"))
                return@forEachIndexed
            }
            val (kept, dropped) = filterLines(page, level, repeated)
            dropped.forEach { ignored.add(Ignored(n, it)) }
            val structured = structure(page, kept)
            if (structured.isEmpty()) blocks.add(NoteBlock.Paragraph("[No text recognized in this image]"))
            else blocks.addAll(structured)
        }
        return NoteFormat.join(NoteFormat.render(blocks), ignored)
    }

    // ============================================================================== filtering
    private fun clean(t: String): String = t.replace(Regex("\\s+"), " ").trim().trim('|', '_', '~').trim()

    private fun key(t: String): String = t.lowercase().filter { it.isLetterOrDigit() }

    /** Short lines that appear on most of the selected screenshots: channel names, watermarks, player overlays. */
    private fun repeatedKeys(pages: List<OcrPage>): Set<String> {
        val ok = pages.filter { it.error == null && it.lines.isNotEmpty() }
        if (ok.size < 3) return emptySet()
        val threshold = maxOf(3, ceil(ok.size * REPEAT_SHARE).toInt())
        val counts = HashMap<String, Int>()
        ok.forEach { p ->
            p.lines.map { key(clean(it.text)) }.filter { it.length >= 2 }.toSet().forEach { counts[it] = (counts[it] ?: 0) + 1 }
        }
        return counts.filter { it.value >= threshold }.keys
    }

    private fun filterLines(page: OcrPage, level: CleanupLevel, repeated: Set<String>): Pair<List<OcrLine>, List<String>> {
        if (level == CleanupLevel.OFF) {
            return page.lines.map { it.copy(text = clean(it.text)) }.filter { it.text.isNotEmpty() } to emptyList()
        }
        val strict = level == CleanupLevel.STRICT
        val body = bodyHeight(page.lines)
        val ph = page.height.coerceAtLeast(1)
        val kept = ArrayList<OcrLine>()
        val dropped = ArrayList<String>()
        for (l in page.lines) {
            val t = clean(l.text)
            if (t.isEmpty()) continue
            val words = t.split(' ').size
            val cy = (l.top + l.bottom) / 2f / ph
            val edge = cy < (if (strict) EDGE_TOP_STRICT else EDGE_TOP) || cy > (if (strict) EDGE_BOTTOM_STRICT else EDGE_BOTTOM)
            val small = l.height < body * (if (strict) 0.8f else 0.7f)
            val drop = isChrome(t) || lowQuality(t, words, strict) ||
                (edge && small && words <= (if (strict) 8 else 5)) ||
                (strict && l.height < body * 0.5f && t.length < 40) ||
                (strict && words <= 2 && small) ||
                (words <= 6 && key(t) in repeated)
            if (drop) dropped.add(t) else kept.add(l.copy(text = t))
        }
        return kept to dropped
    }

    private fun isChrome(t: String): Boolean {
        val s = t.trim().trimEnd('.', ',', ';')
        return CHROME.any { it.matches(s) }
    }

    private fun lowQuality(t: String, words: Int, strict: Boolean): Boolean {
        val alnum = t.count { it.isLetterOrDigit() }
        if (t.length >= 3 && alnum.toFloat() / t.length < (if (strict) 0.65f else 0.5f)) return true
        if (t.length <= 2 && !t[0].isDigit()) return true
        if (words >= 3) {
            val singles = t.split(' ').count { it.length == 1 }
            if (singles * 10 > words * 6) return true      // mostly single characters = OCR noise
        }
        return false
    }

    /** Typical text height: the height that half of all characters on the page are at or below. */
    private fun bodyHeight(lines: List<OcrLine>): Float {
        val ls = lines.filter { it.text.length >= 3 }.ifEmpty { lines }
        if (ls.isEmpty()) return 1f
        val sorted = ls.sortedBy { it.height }
        val total = sorted.sumOf { it.text.length }
        var acc = 0
        for (l in sorted) {
            acc += l.text.length
            if (acc * 2 >= total) return l.height.toFloat()
        }
        return sorted.last().height.toFloat()
    }

    private fun columnWidth(lines: List<OcrLine>): Float {
        val w = lines.map { it.width }.sorted()
        return w[((w.size - 1) * 0.9f).roundToInt()].toFloat()
    }

    // ============================================================================ structuring
    private class Info(val line: OcrLine, val text: String, val bullet: Boolean, val label: String?)

    private fun patternHeading(t: String): Int {
        val words = t.split(' ').size
        if (words > 12) return 0
        if (words <= 10 && CHAPTER.containsMatchIn(t)) return 2
        val letters = t.filter { it.isLetter() }
        if (letters.length >= 4 && (words >= 2 || letters.length >= 6) && words <= 8) {
            if (letters.count { it.isUpperCase() }.toFloat() / letters.length >= 0.85f) return 3
        }
        if (t.endsWith(":") && words <= 8 && t.length >= 4) return 3
        return 0
    }

    private fun structure(page: OcrPage, lines: List<OcrLine>): List<NoteBlock> {
        if (lines.isEmpty()) return emptyList()
        val body = bodyHeight(lines)
        val col = columnWidth(lines)
        val letterBullets = lines.count { LETTER_BULLET.matches(it.text) } >= 2

        // 1) bullets and numbers
        val infos = lines.map { l ->
            var text = l.text
            var bullet = false
            var label: String? = null
            val g = BULLET_GLYPH.matchEntire(text) ?: BULLET_DASH.matchEntire(text)
                ?: (if (letterBullets) LETTER_BULLET.matchEntire(text) else null)
            if (g != null) {
                text = g.groupValues[2]
                bullet = true
            } else {
                NUMBERED.matchEntire(text)?.let { label = it.groupValues[1]; text = it.groupValues[2] }
            }
            Info(l, text, bullet, label)
        }

        // 2) heading level per line (0 = normal text)
        val n = infos.size
        val levels = IntArray(n)
        for (i in 0 until n) {
            val inf = infos[i]
            if (inf.bullet || inf.label != null) continue
            val words = inf.text.split(' ').size
            val ratio = inf.line.height / body
            var lv = when {
                words <= 12 && ratio >= H1_RATIO -> 1
                words <= 14 && ratio >= H2_RATIO -> 2
                words <= 10 && ratio >= H3_RATIO -> 3
                else -> 0
            }
            if (lv == 0) lv = patternHeading(inf.text)
            levels[i] = lv
        }
        // the first big line near the top is the page title
        if (levels.none { it == 1 } && levels[0] in 2..3 && infos[0].line.top.toFloat() / page.height.coerceAtLeast(1) < 0.4f) levels[0] = 1
        // if almost everything looks like a heading, the size estimate is off: keep only clear ones
        if (n >= 5 && levels.count { it > 0 } * 10 > n * 4) {
            for (i in 0 until n) if (levels[i] > 0 && infos[i].line.height / body < 1.5f && patternHeading(infos[i].text) == 0) levels[i] = 0
        }

        // 3) assemble blocks
        val listBase = infos.filter { it.bullet || it.label != null }.minOfOrNull { it.line.left } ?: 0
        val blocks = ArrayList<NoteBlock>()
        var para: StringBuilder? = null
        var paraLine: OcrLine? = null
        var headLine: OcrLine? = null
        var listIdx = -1
        var listLine: OcrLine? = null

        fun flush() {
            val p = para
            if (p != null) blocks.add(NoteBlock.Paragraph(p.toString()))
            para = null
            paraLine = null
        }

        for (i in 0 until n) {
            val inf = infos[i]
            val l = inf.line

            if (levels[i] > 0) {
                flush(); listIdx = -1; listLine = null
                val prevB = blocks.lastOrNull()
                val hl = headLine
                if (prevB is NoteBlock.Heading && prevB.level == levels[i] && hl != null && headingWraps(hl, l)) {
                    blocks[blocks.lastIndex] = NoteBlock.Heading(prevB.level, joinWrapped(prevB.text, inf.text))
                } else {
                    blocks.add(NoteBlock.Heading(levels[i], inf.text))
                }
                headLine = l
                continue
            }
            headLine = null

            if (inf.bullet || inf.label != null) {
                flush()
                val indent = ((l.left - listBase) / (body * 1.8f)).toInt().coerceIn(0, 2)
                val lab = inf.label
                blocks.add(if (lab == null) NoteBlock.Bullet(indent, inf.text) else NoteBlock.Numbered(indent, lab, inf.text))
                listIdx = blocks.lastIndex
                listLine = l
                continue
            }

            // plain line: wraps a list item, continues the paragraph, or starts a new paragraph
            val ll = listLine
            if (ll != null && listIdx >= 0 && continues(ll, l, col, body, listContinuation = true)) {
                blocks[listIdx] = appendTo(blocks[listIdx], inf.text)
                listLine = l
                continue
            }
            listIdx = -1
            listLine = null
            val p = para
            val pl = paraLine
            if (p != null && pl != null && continues(pl, l, col, body)) {
                para = StringBuilder(joinWrapped(p.toString(), inf.text))
            } else {
                flush()
                para = StringBuilder(inf.text)
            }
            paraLine = l
        }
        flush()
        return blocks
    }

    private fun headingWraps(prev: OcrLine, cur: OcrLine): Boolean =
        prev.block == cur.block &&
            (cur.top - prev.bottom) <= prev.height * 0.8f &&
            abs(cur.height - prev.height) <= prev.height * 0.25f

    private fun continues(prev: OcrLine, cur: OcrLine, col: Float, body: Float, listContinuation: Boolean = false): Boolean {
        if (prev.block != cur.block) return false
        if (cur.top - prev.bottom > body * 0.9f) return false
        if (abs(cur.height - prev.height) > prev.height * 0.35f) return false
        if (!listContinuation && abs(cur.left - prev.left) > body * 2f) return false
        val prevFull = prev.width >= col * WRAP_FILL
        val endsSentence = prev.text.last() in ".?!:;"
        val startsLower = cur.text.first().isLowerCase()
        return prevFull || (!endsSentence && startsLower)
    }

    private fun joinWrapped(a: String, b: String): String =
        if (a.endsWith("-") && a.length > 1 && a[a.length - 2].isLetter() && b.firstOrNull()?.isLowerCase() == true) a.dropLast(1) + b
        else "$a $b"

    private fun appendTo(block: NoteBlock, text: String): NoteBlock = when (block) {
        is NoteBlock.Bullet -> block.copy(text = joinWrapped(block.text, text))
        is NoteBlock.Numbered -> block.copy(text = joinWrapped(block.text, text))
        else -> block
    }
}