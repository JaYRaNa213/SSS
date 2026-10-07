package com.sss.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class FolderWithCount(
    @Embedded val folder: FolderEntity,
    val screenshotCount: Int,
    val latestScreenshotPath: String?
)

@Dao
interface FolderDao {

    @Query("SELECT * FROM folders ORDER BY createdAt ASC")
    fun getAllFolders(): Flow<List<FolderEntity>>

    @Query(
        """
        SELECT 
            f.id AS id,
            f.name AS name,
            f.createdAt AS createdAt,
            COUNT(s.id) AS screenshotCount,
            (SELECT filePath FROM screenshots WHERE folderId = f.id ORDER BY sequenceNumber DESC LIMIT 1) AS latestScreenshotPath
        FROM folders f
        LEFT JOIN screenshots s ON f.id = s.folderId
        GROUP BY f.id
        ORDER BY f.createdAt ASC
        """
    )
    fun getFoldersWithCount(): Flow<List<FolderWithCount>>

    @Insert
    suspend fun insertFolder(folder: FolderEntity): Long

    @Update
    suspend fun updateFolder(folder: FolderEntity)

    @Delete
    suspend fun deleteFolder(folder: FolderEntity)
}