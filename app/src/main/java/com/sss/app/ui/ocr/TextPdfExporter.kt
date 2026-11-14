package com.sss.app.ui.ocr

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.sss.app.domain.ocr.NoteBlock
import com.sss.app.domain.ocr.NoteFormat
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val PW = 595      // A4 at 72 dpi
private const val PH = 842

object TextPdfExporter {

    private fun paint(size: Float, bold: Boolean = false, color: Int = Color.BLACK) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
    }

    private class Writer(private val doc: PdfDocument, val margin: Float, private val numPaint: Paint) {
        private var pageNo = 0
        private lateinit var page: PdfDocument.Page
        lateinit var canvas: Canvas
        var y = 0f

        fun newPage() {
            if (pageNo > 0) finishPage()
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PW, PH, pageNo).create())
            canvas = page.canvas
            y = margin
        }

        private fun finishPage() {
            canvas.drawText("Page $pageNo", PW / 2f, PH - 22f, numPaint)
            doc.finishPage(page)
        }

        fun finish() { if (pageNo > 0) finishPage() }

        fun ensure(space: Float) { if (y + space > PH - margin - 10f) newPage() }

        fun line(text: String, p: Paint, x: Float, lead: Float) {
            ensure(lead)
            y += lead
            canvas.drawText(text, x, y, p)
        }
    }

    fun generatePdf(
        context: Context,
        title: String,
        content: String,
        imageCount: Int,
        outputStream: OutputStream,
        showMarkers: Boolean = false
    ) {
        val blocks = NoteFormat.parse(NoteFormat.split(content).first)
        val margin = 44f
        val textW = PW - margin * 2

        val titleP = paint(22f, bold = true)
        val metaP = paint(10f, color = Color.DKGRAY)
        val h1 = paint(18f, bold = true)
        val h2 = paint(14.5f, bold = true)
        val h3 = paint(12f, bold = true, color = Color.rgb(120, 85, 0))
        val body = paint(11f)
        val markerP = paint(8.5f, bold = true, color = Color.GRAY)
        val rule = Paint().apply { color = Color.LTGRAY; strokeWidth = 1f }
        val accent = Paint().apply { color = Color.rgb(254, 200, 74) }
        val bodyLead = body.textSize * 1.4f

        val doc = PdfDocument()
        val w = Writer(doc, margin, paint(9f, color = Color.GRAY).apply { textAlign = Paint.Align.CENTER })
        try {
            w.newPage()
            // ---- header
            for (ln in wrapAll(title, titleP, textW)) w.line(ln, titleP, margin, titleP.textSize * 1.3f)
            val dateStr = "Created on ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date())} \u2022 " +
                "$imageCount screenshot${if (imageCount == 1) "" else "s"}"
            w.line(dateStr, metaP, margin, 16f)
            w.y += 6f
            w.canvas.drawLine(margin, w.y, PW - margin, w.y, rule)
            w.y += 10f

            // ---- blocks
            for (b in blocks) {
                when (b) {
                    is NoteBlock.Heading -> {
                        val p = when (b.level) { 1 -> h1; 2 -> h2; else -> h3 }
                        val before = when (b.level) { 1 -> 16f; 2 -> 12f; else -> 9f }
                        val after = when (b.level) { 1 -> 6f; 2 -> 4f; else -> 3f }
                        val lead = p.textSize * 1.3f
                        val lines = wrapAll(b.text, p, textW)
                        w.ensure(before + lead * (lines.size + 2))          // keep a heading together with its first lines
                        w.y += before
                        lines.forEach { w.line(it, p, margin, lead) }
                        if (b.level == 1) {
                            w.canvas.drawRect(margin, w.y + 5f, margin + 36f, w.y + 7.5f, accent)
                            w.y += 10f
                        }
                        w.y += after
                    }
                    is NoteBlock.Paragraph -> {
                        wrapAll(b.text, body, textW).forEach { w.line(it, body, margin, bodyLead) }
                        w.y += 6f
                    }
                    is NoteBlock.Bullet -> {
                        val x = margin + 6f + b.indent * 16f
                        val lines = wrapAll(b.text, body, textW - (x - margin) - 14f)
                        lines.forEachIndexed { i, ln ->
                            if (i == 0) { w.ensure(bodyLead); w.canvas.drawText("\u2022", x, w.y + bodyLead, body) }
                            w.line(ln, body, x + 14f, bodyLead)
                        }
                        w.y += 3f
                    }
                    is NoteBlock.Numbered -> {
                        val x = margin + 6f + b.indent * 16f
                        val lines = wrapAll(b.text, body, textW - (x - margin) - 20f)
                        lines.forEachIndexed { i, ln ->
                            if (i == 0) { w.ensure(bodyLead); w.canvas.drawText(b.label, x, w.y + bodyLead, body) }
                            w.line(ln, body, x + 20f, bodyLead)
                        }
                        w.y += 3f
                    }
                    is NoteBlock.Marker -> if (showMarkers) {
                        w.ensure(40f)
                        w.y += 12f
                        w.line("SCREENSHOT ${b.page}", markerP, margin, 11f)
                        w.canvas.drawLine(margin, w.y + 4f, PW - margin, w.y + 4f, rule)
                        w.y += 8f
                    }
                }
            }
            w.finish()
            doc.writeTo(outputStream)
        } finally {
            try { doc.close() } catch (e: Exception) { /* ignore */ }
        }
    }

    fun generatePdfToCache(context: Context, title: String, content: String, imageCount: Int, showMarkers: Boolean = false): File {
        val sanitized = title.replace(Regex("[^a-zA-Z0-9_.-]"), "_")
        val file = File(context.cacheDir, "4S_Notes_${sanitized}_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { out -> generatePdf(context, title, content, imageCount, out, showMarkers) }
        return file
    }

    private fun wrapAll(text: String, paint: Paint, maxWidth: Float): List<String> =
        text.split("\n").flatMap { wrapText(it, paint, maxWidth) }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(" ").filter { it.isNotEmpty() }
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            val test = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(test) <= maxWidth) {
                if (current.isNotEmpty()) current.append(' ')
                current.append(word)
            } else {
                if (current.isNotEmpty()) { lines.add(current.toString()); current = StringBuilder(word) } else lines.add(word)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return if (lines.isEmpty()) listOf("") else lines
    }
}