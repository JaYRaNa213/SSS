package com.sss.app.data.repository

import com.sss.app.data.local.FolderDao
import com.sss.app.data.local.FolderEntity
import com.sss.app.data.local.FolderWithCount
import com.sss.app.domain.folder.FolderNameValidator
import com.sss.app.domain.folder.NameCheck
import kotlinx.coroutines.flow.Flow

sealed interface FolderResult {
    data class Success(val id: Long, val name: String) : FolderResult
    data class Invalid(val message: String) : FolderResult
}

class FolderRepository(
    private val folderDao: FolderDao
) {
    val folders: Flow<List<FolderEntity>> = folderDao.getAllFolders()
    val foldersWithCount: Flow<List<FolderWithCount>> = folderDao.getFoldersWithCount()

    fun folderFlow(id: Long): Flow<FolderEntity?> = folderDao.getFolderFlow(id)

    suspend fun getFoldersOnce(): List<FolderEntity> = folderDao.getFoldersOnce()

    /** Old API, kept for compatibility. Prefer createFolder(). */
    suspend fun addFolder(name: String): Long = folderDao.insertFolder(FolderEntity(name = name))

    /** Validates (empty / length / reserved / duplicate) and creates. */
    suspend fun createFolder(raw: String): FolderResult =
        when (val c = FolderNameValidator.check(raw, folderDao.getFoldersOnce())) {
            is NameCheck.Error -> FolderResult.Invalid(c.message)
            is NameCheck.Ok -> FolderResult.Success(folderDao.insertFolder(FolderEntity(name = c.name)), c.name)
        }

    suspend fun renameFolder(id: Long, raw: String): FolderResult =
        when (val c = FolderNameValidator.check(raw, folderDao.getFoldersOnce(), ignoreId = id)) {
            is NameCheck.Error -> FolderResult.Invalid(c.message)
            is NameCheck.Ok -> {
                folderDao.renameFolder(id, c.name)
                FolderResult.Success(id, c.name)
            }
        }

    suspend fun updateFolder(folder: FolderEntity) = folderDao.updateFolder(folder)
    suspend fun deleteFolder(folder: FolderEntity) = folderDao.deleteFolder(folder)
}