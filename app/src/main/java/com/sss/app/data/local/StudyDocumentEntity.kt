package com.sss.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "study_documents")
data class StudyDocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val content: String,
    val sourceFolderId: Long?,
    val sourceImageCount: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
