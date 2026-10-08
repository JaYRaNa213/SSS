package com.sss.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun startSession(session: CaptureSessionEntity)

    @Query("""
        SELECT * FROM capture_sessions
        WHERE id = 1 AND isActive = 1
        LIMIT 1
    """)
    suspend fun getActiveSession(): CaptureSessionEntity?

    @Query("""
        SELECT * FROM capture_sessions
        WHERE id = 1 AND isActive = 1
        LIMIT 1
    """)
    fun observeActiveSession(): Flow<CaptureSessionEntity?>

    @Query("""
        UPDATE capture_sessions
        SET folderId = :folderId
        WHERE id = 1 AND isActive = 1
    """)
    suspend fun switchActiveFolder(folderId: Long)

    @Query("""
        UPDATE capture_sessions
        SET isActive = 0
        WHERE id = 1
    """)
    suspend fun stopSession()

    @Query("DELETE FROM capture_sessions")
    suspend fun deleteSession()
}
