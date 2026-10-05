package com.sss.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "screenshots",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["folderId"])
    ]
)
data class ScreenshotEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val folderId: Long,

    val filePath: String,

    val sequenceNumber: Int,

    val capturedAt: Long = System.currentTimeMillis()
)