package com.sss.app.ui.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.sss.app.data.local.ScreenshotEntity
import java.io.File
import java.io.FileOutputStream

object PdfExportHelper {

    fun exportScreenshotsToPdf(
        context: Context,
        folderName: String,
        selectedScreenshots: List<ScreenshotEntity>,
        onSuccess: (File) -> Unit,
        onError: (Exception) -> Unit
    ) {
        try {
            val pdfDocument = PdfDocument()
            val pageWidth = 595 // A4 width in points at 72 DPI
            val pageHeight = 842 // A4 height in points at 72 DPI

            selectedScreenshots.forEachIndexed { index, screenshot ->
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                try {
                    val bitmap = loadBitmapFromUri(context, screenshot.filePath)
                    if (bitmap != null) {
                        val scaleX = pageWidth.toFloat() / bitmap.width
                        val scaleY = pageHeight.toFloat() / bitmap.height
                        val scale = minOf(scaleX, scaleY)

                        val scaledWidth = (bitmap.width * scale).toInt()
                        val scaledHeight = (bitmap.height * scale).toInt()
                        val left = (pageWidth - scaledWidth) / 2f
                        val top = (pageHeight - scaledHeight) / 2f

                        val destRect = RectF(left, top, left + scaledWidth, top + scaledHeight)
                        canvas.drawBitmap(bitmap, null, destRect, null)
                        bitmap.recycle()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                pdfDocument.finishPage(page)
            }

            val sanitizedFolderName = folderName.replace(Regex("\\s+"), "_")
            val fileName = "SSS_${sanitizedFolderName}_${System.currentTimeMillis()}.pdf"
            val file = File(context.cacheDir, fileName)
            val outputStream = FileOutputStream(file)
            pdfDocument.writeTo(outputStream)
            pdfDocument.close()
            outputStream.close()

            onSuccess(file)
        } catch (e: Exception) {
            onError(e)
        }
    }

    private fun loadBitmapFromUri(context: Context, filePath: String): Bitmap? {
        return try {
            val uri = if (filePath.startsWith("content://") || filePath.startsWith("file://")) {
                Uri.parse(filePath)
            } else {
                Uri.fromFile(File(filePath))
            }
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            null
        }
    }
}
