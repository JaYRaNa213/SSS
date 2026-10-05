package com.sss.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sss.app.data.local.AppDatabase
import com.sss.app.data.local.FolderDao
import com.sss.app.data.local.ScreenshotDao
import com.sss.app.data.repository.FolderRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import com.sss.app.data.repository.ScreenshotRepository
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    private val MIGRATION_1_2 = object : Migration(1, 2) {

        override fun migrate(
            database: SupportSQLiteDatabase
        ) {

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

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {

        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "sss_database"
        )
            .addMigrations(MIGRATION_1_2)
            .build()
    }

    @Provides
    fun provideFolderDao(
        database: AppDatabase
    ): FolderDao {
        return database.folderDao()
    }

    @Provides
    fun provideScreenshotDao(
        database: AppDatabase
    ): ScreenshotDao {
        return database.screenshotDao()
    }

    @Provides
    @Singleton
    fun provideFolderRepository(
        folderDao: FolderDao
    ): FolderRepository {
        return FolderRepository(folderDao)
    }
    @Provides
    @Singleton
    fun provideScreenshotRepository(
        screenshotDao: ScreenshotDao
    ): ScreenshotRepository {
        return ScreenshotRepository(screenshotDao)
    }
}