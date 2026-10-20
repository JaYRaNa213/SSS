package com.sss.app.data.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.io.File

class OcrRepository(
    private val context: Context
) {

    suspend fun extractText(imageUri: Uri): String {
        val image = try {
            InputImage.fromFilePath(context, imageUri)
        } catch (e: Exception) {
            // Fallback for file paths
            val path = imageUri.path
            if (path != null && File(path).exists()) {
                InputImage.fromFilePath(context, Uri.fromFile(File(path)))
            } else {
                throw e
            }
        }

        val recognizer = TextRecognition.getClient(
            TextRecognizerOptions.DEFAULT_OPTIONS
        )

        return try {
            val result = recognizer.process(image).await()
            result.text
        } finally {
            recognizer.close()
        }
    }
}
