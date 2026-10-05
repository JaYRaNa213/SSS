package com.sss.app.data.repository

import com.sss.app.data.local.ScreenshotDao
import com.sss.app.data.local.ScreenshotEntity
import kotlinx.coroutines.flow.Flow

class ScreenshotRepository(
    private val screenshotDao: ScreenshotDao
) {

    fun getScreenshotsByFolder(
        folderId: Long
    ): Flow<List<ScreenshotEntity>> {

        return screenshotDao.getScreenshotsByFolder(folderId)
    }

    suspend fun addScreenshot(
        folderId: Long,
        filePath: String,
        sequenceNumber: Int
    ) {

        screenshotDao.insertScreenshot(
            ScreenshotEntity(
                folderId = folderId,
                filePath = filePath,
                sequenceNumber = sequenceNumber
            )
        )
    }

    suspend fun updateScreenshot(
        screenshot: ScreenshotEntity
    ) {
        screenshotDao.updateScreenshot(screenshot)
    }

    suspend fun deleteScreenshot(
        screenshot: ScreenshotEntity
    ) {
        screenshotDao.deleteScreenshot(screenshot)
    }

    suspend fun deleteScreenshotsByFolder(
        folderId: Long
    ) {
        screenshotDao.deleteScreenshotsByFolder(folderId)
    }

    suspend fun getScreenshotCount(
        folderId: Long
    ): Int {
        return screenshotDao.getScreenshotCount(folderId)
    }
}