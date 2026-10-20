package com.sss.app.ui.ocr

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.sss.app.domain.ocr.HeadingDetector
import com.sss.app.domain.ocr.TextBlockType
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TextPdfExporter {

    fun generatePdf(
        context: Context,
        title: String,
        content: String,
        imageCount: Int,
        outputStream: OutputStream
    ) {
        val pdfDocument = PdfDocument()

        val pageWidth = 595 // A4 width in points at 72 DPI
        val pageHeight = 842 // A4 height in points at 72 DPI
        val margin = 40f
        val contentWidth = pageWidth - (margin * 2)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 10f
            typeface = Typeface.DEFAULT
        }

        val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 11f
            typeface = Typeface.DEFAULT
        }

        val pageNumPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.GRAY
            textSize = 9f
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.CENTER
        }

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY
            strokeWidth = 1f
        }

        try {
            var currentPageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas = page.canvas
            var y = margin

            // Draw Header on first page
            canvas.drawText(title, margin, y + 20f, titlePaint)
            y += 35f

            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            val dateStr = "Created on ${dateFormat.format(Date())} • $imageCount screenshot${if (imageCount == 1) "" else "s"}"
            canvas.drawText(dateStr, margin, y, metaPaint)
            y += 15f

            canvas.drawLine(margin, y, pageWidth - margin, y, linePaint)
            y += 20f

            // Process text blocks
            val classifiedLines = HeadingDetector.classify(content)

            for (classified in classifiedLines) {
                val paint = if (classified.type == TextBlockType.HEADING) headingPaint else bodyPaint
                val lineHeight = paint.fontSpacing + 4f

                // Word wrapping for long lines
                val wrappedLines = wrapText(classified.text, paint, contentWidth)

                for (wrappedLine in wrappedLines) {
                    // Check if we need a new page
                    if (y + lineHeight > pageHeight - margin - 20f) {
                        // Draw page number
                        canvas.drawText("Page $currentPageNumber", pageWidth / 2f, pageHeight - 20f, pageNumPaint)
                        pdfDocument.finishPage(page)

                        currentPageNumber++
                        pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()
                        page = pdfDocument.startPage(pageInfo)
                        canvas = page.canvas
                        y = margin + 20f
                    }

                    canvas.drawText(wrappedLine, margin, y, paint)
                    y += lineHeight
                }

                if (classified.type == TextBlockType.HEADING) {
                    y += 4f
                } else {
                    y += 2f
                }
            }

            // Draw page number on last page
            canvas.drawText("Page $currentPageNumber", pageWidth / 2f, pageHeight - 20f, pageNumPaint)
            pdfDocument.finishPage(page)

            pdfDocument.writeTo(outputStream)
        } finally {
            try {
                pdfDocument.close()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun generatePdfToCache(
        context: Context,
        title: String,
        content: String,
        imageCount: Int
    ): File {
        val sanitized = title.replace(Regex("[^a-zA-Z0-9_.-]"), "_")
        val file = File(context.cacheDir, "4S_Notes_${sanitized}_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { out ->
            generatePdf(context, title, content, imageCount, out)
        }
        return file
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "${currentLine} $word"
            if (paint.measureText(testLine) <= maxWidth) {
                currentLine.append(if (currentLine.isEmpty()) word else " $word")
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                    currentLine = StringBuilder(word)
                } else {
                    // Single word longer than line
                    lines.add(word)
                }
            }
        }

        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }

        return if (lines.isEmpty()) listOf(text) else lines
    }
}
