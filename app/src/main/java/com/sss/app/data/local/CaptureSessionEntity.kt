package com.sss.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "capture_sessions")
data class CaptureSessionEntity(

    @PrimaryKey
    val id: Int = 1,

    val folderId: Long,

    val startedAt: Long = System.currentTimeMillis(),

    val isActive: Boolean = true
)