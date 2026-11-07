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
    val latestScreenshotPath: String?,
    val secondScreenshotPath: String?,
    val lastUpdatedAt: Long?
)

@Dao
interface FolderDao {

    @Query("SELECT * FROM folders ORDER BY createdAt ASC")
    fun getAllFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders ORDER BY createdAt ASC")
    suspend fun getFoldersOnce(): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE id = :id LIMIT 1")
    fun getFolderFlow(id: Long): Flow<FolderEntity?>

    @Query(
        """
        SELECT
            f.id AS id,
            f.name AS name,
            f.createdAt AS createdAt,
            f.colorIndex AS colorIndex,
            COUNT(s.id) AS screenshotCount,
            (SELECT filePath FROM screenshots WHERE folderId = f.id ORDER BY capturedAt DESC, id DESC LIMIT 1) AS latestScreenshotPath,
            (SELECT filePath FROM screenshots WHERE folderId = f.id ORDER BY capturedAt DESC, id DESC LIMIT 1 OFFSET 1) AS secondScreenshotPath,
            MAX(s.capturedAt) AS lastUpdatedAt
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

    @Query("UPDATE folders SET name = :name WHERE id = :id")
    suspend fun renameFolder(id: Long, name: String)

    @Delete
    suspend fun deleteFolder(folder: FolderEntity)
}