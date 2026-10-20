package com.sss.app.data

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sss.app.data.local.AppDatabase

object DatabaseProvider {

    @Volatile
    private var INSTANCE: AppDatabase? = null

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS screenshots (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    folderId INTEGER NOT NULL,
                    filePath TEXT NOT NULL,
                    sequenceNumber INTEGER NOT NULL,
                    capturedAt INTEGER NOT NULL,
                    FOREIGN KEY(folderId)
                        REFERENCES folders(id)
                        ON DELETE CASCADE
                )
                """.trimIndent()
            )
            database.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_screenshots_folderId
                ON screenshots(folderId)
                """.trimIndent()
            )
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS capture_sessions (
                    id INTEGER NOT NULL,
                    folderId INTEGER NOT NULL,
                    startedAt INTEGER NOT NULL,
                    isActive INTEGER NOT NULL,
                    PRIMARY KEY(id)
                )
                """.trimIndent()
            )
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS study_documents (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    title TEXT NOT NULL,
                    content TEXT NOT NULL,
                    sourceFolderId INTEGER,
                    sourceImageCount INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    fun getDatabase(context: Context): AppDatabase {
        return INSTANCE ?: synchronized(this) {
            val instance = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "sss_database"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()

            INSTANCE = instance
            instance
        }
    }
}
