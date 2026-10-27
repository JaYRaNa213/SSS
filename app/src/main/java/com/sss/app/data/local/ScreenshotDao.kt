package com.sss.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ScreenshotDao {

    @Query(
        """
        SELECT * FROM screenshots
        WHERE folderId = :folderId
        ORDER BY sequenceNumber ASC
        """
    )
    fun getScreenshotsByFolder(
        folderId: Long
    ): Flow<List<ScreenshotEntity>>

    @Query("SELECT * FROM screenshots WHERE folderId = :folderId")
    suspend fun getScreenshotsByFolderList(folderId: Long): List<ScreenshotEntity>

    @Query("SELECT * FROM screenshots ORDER BY capturedAt DESC")
    suspend fun getAllScreenshotsList(): List<ScreenshotEntity>

    @Query("SELECT * FROM screenshots ORDER BY capturedAt DESC")
    fun getAllScreenshotsFlow(): Flow<List<ScreenshotEntity>>

    @Query("SELECT COUNT(*) FROM screenshots WHERE filePath = :filePath")
    suspend fun countByFilePath(filePath: String): Int

    @Query("SELECT COALESCE(MAX(sequenceNumber), 0) FROM screenshots WHERE folderId = :folderId")
    suspend fun getMaxSequence(folderId: Long): Int

    @Insert
    suspend fun insertScreenshot(
        screenshot: ScreenshotEntity
    ): Long

    @Update
    suspend fun updateScreenshot(
        screenshot: ScreenshotEntity
    )

    @Delete
    suspend fun deleteScreenshot(
        screenshot: ScreenshotEntity
    )

    @Query(
        "DELETE FROM screenshots WHERE folderId = :folderId"
    )
    suspend fun deleteScreenshotsByFolder(
        folderId: Long
    )

    @Query(
        "SELECT COUNT(*) FROM screenshots WHERE folderId = :folderId"
    )
    suspend fun getScreenshotCount(
        folderId: Long
    ): Int
}
