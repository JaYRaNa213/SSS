package com.sss.app.data.repository

import com.sss.app.data.local.CaptureSessionEntity
import com.sss.app.data.local.SessionDao
import kotlinx.coroutines.flow.Flow

class SessionRepository(
    private val sessionDao: SessionDao
) {

    suspend fun startSession(folderId: Long) {
        sessionDao.startSession(
            CaptureSessionEntity(
                folderId = folderId,
                isActive = true
            )
        )
    }

    suspend fun getActiveSession(): CaptureSessionEntity? {
        return sessionDao.getActiveSession()
    }

    fun observeActiveSession(): Flow<CaptureSessionEntity?> {
        return sessionDao.observeActiveSession()
    }

    suspend fun switchActiveFolder(folderId: Long) {
        sessionDao.switchActiveFolder(folderId)
    }

    suspend fun stopSession() {
        sessionDao.stopSession()
    }

    suspend fun deleteSession() {
        sessionDao.deleteSession()
    }
}
