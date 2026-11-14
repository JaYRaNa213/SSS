package com.sss.app.ui.ocr

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sss.app.domain.ocr.NoteBlock
import com.sss.app.ui.theme.Accent
import com.sss.app.ui.theme.Line
import com.sss.app.ui.theme.TextMuted
import com.sss.app.ui.theme.TextPrimary

/** Read mode: big headings, normal body text, accent bullets. Text can be selected and copied. */
@Composable
fun NoteView(blocks: List<NoteBlock>, showMarkers: Boolean, modifier: Modifier = Modifier) {
    SelectionContainer {
        LazyColumn(modifier, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)) {
            itemsIndexed(blocks) { i, b ->
                when (b) {
                    is NoteBlock.Heading -> HeadingText(b, first = i == 0 || blocks[i - 1] is NoteBlock.Marker)
                    is NoteBlock.Paragraph -> Text(
                        b.text, color = TextPrimary, fontSize = 15.sp, lineHeight = 23.sp,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                    is NoteBlock.Bullet -> Row(Modifier.padding(start = (b.indent * 18).dp, bottom = 6.dp)) {
                        Box(Modifier.padding(top = 9.dp).size(6.dp).clip(CircleShape).background(Accent))
                        Spacer(Modifier.width(10.dp))
                        Text(b.text, color = TextPrimary, fontSize = 15.sp, lineHeight = 22.sp)
                    }
                    is NoteBlock.Numbered -> Row(Modifier.padding(start = (b.indent * 18).dp, bottom = 6.dp)) {
                        Text(b.label, color = Accent, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.width(30.dp))
                        Text(b.text, color = TextPrimary, fontSize = 15.sp, lineHeight = 22.sp)
                    }
                    is NoteBlock.Marker -> { if (showMarkers) MarkerRow(b.page) }
                }
            }
        }
    }
}

@Composable
private fun HeadingText(b: NoteBlock.Heading, first: Boolean) {
    val size: TextUnit
    val weight: FontWeight
    val top: Dp
    when (b.level) {
        1 -> { size = 26.sp; weight = FontWeight.Bold; top = if (first) 4.dp else 22.dp }
        2 -> { size = 21.sp; weight = FontWeight.Bold; top = 16.dp }
        else -> { size = 17.sp; weight = FontWeight.SemiBold; top = 12.dp }
    }
    Column(Modifier.padding(top = top, bottom = 8.dp)) {
        Text(
            b.text, color = if (b.level == 3) Accent else TextPrimary,
            fontSize = size, fontWeight = weight, lineHeight = size * 1.25f
        )
        if (b.level == 1) Box(Modifier.padding(top = 8.dp).width(40.dp).height(3.dp).clip(CircleShape).background(Accent))
    }
}

@Composable
private fun MarkerRow(page: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(Line))
        Text("  Screenshot $page  ", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Box(Modifier.weight(1f).height(1.dp).background(Line))
    }
}

/** Edit mode: same text, but headings are drawn big and markers dimmed. Text length is unchanged. */
class NoteVisualTransformation(private val dim: Color, private val accent: Color) : VisualTransformation {
    private val head = Regex("^(#{1,3})\\s")
    private val marker = Regex("^---\\s*Screenshot\\s+\\d+\\s*---$")
    private val bullet = Regex("^\\s*([-*\u2022])\\s")

    override fun filter(text: AnnotatedString): TransformedText {
        val src = text.text
        val styled = buildAnnotatedString {
            append(src)
            var offset = 0
            for (line in src.split("\n")) {
                val end = offset + line.length
                val h = head.find(line)
                if (h != null) {
                    val size = when (h.groupValues[1].length) { 1 -> 24.sp; 2 -> 20.sp; else -> 17.sp }
                    addStyle(SpanStyle(fontSize = size, fontWeight = FontWeight.Bold), offset, end)
                    addStyle(SpanStyle(color = dim), offset, offset + h.value.length)
                } else if (marker.matches(line.trim())) {
                    addStyle(SpanStyle(color = dim, fontSize = 12.sp), offset, end)
                } else {
                    val b = bullet.find(line)
                    if (b != null) {
                        val r = b.groups[1]!!.range
                        addStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold), offset + r.first, offset + r.last + 1)
                    }
                }
                offset = end + 1
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}

/**
 * Format bar: sets or removes a prefix ("# ", "## ", "### ", "- ", "1. ", or "" for normal text)
 * on the line of the cursor, or on every selected line. Pressing the same format again removes it.
 */
fun applyLinePrefix(v: TextFieldValue, prefix: String): TextFieldValue {
    val text = v.text
    val sel = v.selection
    val lineStart = text.lastIndexOf('\n', sel.min - 1) + 1
    val lineEnd = text.indexOf('\n', sel.max).let { if (it < 0) text.length else it }
    val lines = text.substring(lineStart, lineEnd).split("\n")
    val strip = Regex("^(#{1,3}\\s+|[-*\u2022]\\s+|\\d{1,3}[.)]\\s+)")
    val numbered = Regex("^\\d{1,3}[.)]\\s")
    val has = prefix.isNotEmpty() && lines.all { if (prefix == "1. ") numbered.containsMatchIn(it) else it.startsWith(prefix) }
    val changed = lines.mapIndexed { i, l ->
        val base = l.replace(strip, "")
        when {
            prefix.isEmpty() || has -> base
            prefix == "1. " -> "${i + 1}. $base"
            else -> prefix + base
        }
    }.joinToString("\n")
    val newText = text.substring(0, lineStart) + changed + text.substring(lineEnd)
    return v.copy(text = newText, selection = TextRange(lineStart + changed.length))
}