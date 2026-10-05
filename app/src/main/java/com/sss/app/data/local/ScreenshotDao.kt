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