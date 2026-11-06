package com.sss.app.data.repository

import com.sss.app.data.local.ScreenshotEntity
import javax.inject.Inject
import javax.inject.Singleton

data class PdfRequest(val folderName: String, val shots: List<ScreenshotEntity>)

/** Hands the selected screenshots from the Folder screen to the PDF screen. */
@Singleton
class PdfRequestHolder @Inject constructor() {
    @Volatile
    var request: PdfRequest? = null
}