package com.sss.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "screenshot_meta")
data class ScreenshotMetaEntity(
    @PrimaryKey val mediaId: Long,
    val rotation: Int = -1,
    val cropTop: Float = 0f,
    val cropBottom: Float = 0f,
    val cropLeft: Float = 0f,
    val cropRight: Float = 0f,
    val autoAnalyzed: Boolean = false,
    val userAdjusted: Boolean = false,
    val imgW: Int = 0,
    val imgH: Int = 0
)