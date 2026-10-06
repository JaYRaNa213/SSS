package com.sss.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
@Database(
    entities = [
        FolderEntity::class,
        ScreenshotEntity::class,
        CaptureSessionEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun folderDao(): FolderDao

    abstract fun screenshotDao(): ScreenshotDao

    abstract fun sessionDao(): SessionDao
}