package com.sss.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sss.app.data.local.AppDatabase
import com.sss.app.data.local.FolderDao
import com.sss.app.data.local.ScreenshotDao
import com.sss.app.data.local.SessionDao
import com.sss.app.data.local.StudyDocumentDao
import com.sss.app.data.repository.AllScreenshotsRepository
import com.sss.app.data.repository.FolderRepository
import com.sss.app.data.repository.ScreenshotRepository
import com.sss.app.data.repository.SessionRepository
import com.sss.app.data.repository.StudyDocumentRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

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
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4
            )
            .fallbackToDestructiveMigrationOnDowngrade()
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
    fun provideSessionDao(
        database: AppDatabase
    ): SessionDao {
        return database.sessionDao()
    }

    @Provides
    fun provideStudyDocumentDao(
        database: AppDatabase
    ): StudyDocumentDao {
        return database.studyDocumentDao()
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

    @Provides
    @Singleton
    fun provideSessionRepository(
        sessionDao: SessionDao
    ): SessionRepository =
        SessionRepository(sessionDao)

    @Provides
    @Singleton
    fun provideStudyDocumentRepository(
        studyDocumentDao: StudyDocumentDao
    ): StudyDocumentRepository {
        return StudyDocumentRepository(studyDocumentDao)
    }

    @Provides
    @Singleton
    fun provideAllScreenshotsRepository(
        @ApplicationContext context: Context,
        screenshotRepository: ScreenshotRepository
    ): AllScreenshotsRepository {
        return AllScreenshotsRepository(context, screenshotRepository)
    }
}
