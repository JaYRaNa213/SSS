package com.sss.app.data.repository

import com.sss.app.data.local.ScreenshotDao
import com.sss.app.data.local.ScreenshotEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ScreenshotRepository(
    private val screenshotDao: ScreenshotDao
) {

    companion object {
        private val insertMutex = Mutex()
    }

    fun getScreenshotsByFolder(folderId: Long): Flow<List<ScreenshotEntity>> {
        return screenshotDao.getScreenshotsByFolder(folderId)
    }

    suspend fun getScreenshotsByFolderList(folderId: Long): List<ScreenshotEntity> {
        return screenshotDao.getScreenshotsByFolderList(folderId)
    }

    suspend fun getAllScreenshotsList(): List<ScreenshotEntity> {
        return screenshotDao.getAllScreenshotsList()
    }

    fun getAllScreenshotsFlow(): Flow<List<ScreenshotEntity>> {
        return screenshotDao.getAllScreenshotsFlow()
    }

    suspend fun addScreenshotIfAbsent(
        folderId: Long,
        filePath: String,
        capturedAt: Long = System.currentTimeMillis()
    ): Boolean = insertMutex.withLock {
        if (screenshotDao.countByFilePath(filePath) > 0) return@withLock false

        val next = screenshotDao.getMaxSequence(folderId) + 1
        screenshotDao.insertScreenshot(
            ScreenshotEntity(
                folderId = folderId,
                filePath = filePath,
                sequenceNumber = next,
                capturedAt = capturedAt
            )
        )
        true
    }

    @Deprecated("Use addScreenshotIfAbsent()")
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

    suspend fun updateScreenshot(screenshot: ScreenshotEntity) {
        screenshotDao.updateScreenshot(screenshot)
    }

    suspend fun deleteScreenshot(screenshot: ScreenshotEntity) {
        screenshotDao.deleteScreenshot(screenshot)
    }

    suspend fun deleteScreenshotsByFolder(folderId: Long) {
        screenshotDao.deleteScreenshotsByFolder(folderId)
    }

    suspend fun getScreenshotCount(folderId: Long): Int {
        return screenshotDao.getScreenshotCount(folderId)
    }
}
