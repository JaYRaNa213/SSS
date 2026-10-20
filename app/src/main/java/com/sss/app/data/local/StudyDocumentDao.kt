package com.sss.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyDocumentDao {

    @Query("SELECT * FROM study_documents ORDER BY updatedAt DESC")
    fun getAllDocuments(): Flow<List<StudyDocumentEntity>>

    @Query("SELECT * FROM study_documents WHERE sourceFolderId = :folderId ORDER BY updatedAt DESC")
    fun getDocumentsByFolder(folderId: Long): Flow<List<StudyDocumentEntity>>

    @Query("SELECT * FROM study_documents WHERE id = :id LIMIT 1")
    suspend fun getDocumentById(id: Long): StudyDocumentEntity?

    @Query("SELECT * FROM study_documents WHERE id = :id LIMIT 1")
    fun observeDocumentById(id: Long): Flow<StudyDocumentEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: StudyDocumentEntity): Long

    @Update
    suspend fun updateDocument(document: StudyDocumentEntity)

    @Delete
    suspend fun deleteDocument(document: StudyDocumentEntity)
}
