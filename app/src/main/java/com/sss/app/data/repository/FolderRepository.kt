package com.sss.app.data.repository

import com.sss.app.data.local.FolderDao
import com.sss.app.data.local.FolderEntity
import com.sss.app.data.local.FolderWithCount
import com.sss.app.domain.folder.FolderNameValidator
import com.sss.app.domain.folder.FolderTree
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

    /** Validates (empty / length / reserved / duplicate among siblings / depth) and creates. */
    suspend fun createFolder(raw: String, colorIndex: Int = 0, parentId: Long? = null): FolderResult {
        val all = folderDao.getFoldersOnce()
        if (parentId != null) {
            if (all.none { it.id == parentId }) return FolderResult.Invalid("The parent folder no longer exists")
            if (FolderTree.depth(all, parentId) >= FolderTree.MAX_DEPTH)
                return FolderResult.Invalid("Folders can be nested ${FolderTree.MAX_DEPTH} levels deep")
        }
        return when (val c = FolderNameValidator.check(raw, all, parentId = parentId)) {
            is NameCheck.Error -> FolderResult.Invalid(c.message)
            is NameCheck.Ok -> FolderResult.Success(
                folderDao.insertFolder(FolderEntity(name = c.name, colorIndex = colorIndex, parentId = parentId)), c.name
            )
        }
    }

    suspend fun renameFolder(id: Long, raw: String): FolderResult {
        val all = folderDao.getFoldersOnce()
        val self = all.firstOrNull { it.id == id } ?: return FolderResult.Invalid("Folder not found")
        return when (val c = FolderNameValidator.check(raw, all, ignoreId = id, parentId = self.parentId)) {
            is NameCheck.Error -> FolderResult.Invalid(c.message)
            is NameCheck.Ok -> { folderDao.renameFolder(id, c.name); FolderResult.Success(id, c.name) }
        }
    }

    /** Moves a folder (with everything inside it) under [newParentId]. null = top level. */
    suspend fun moveFolder(id: Long, newParentId: Long?): FolderResult {
        val all = folderDao.getFoldersOnce()
        val self = all.firstOrNull { it.id == id } ?: return FolderResult.Invalid("Folder not found")
        if (self.parentId == newParentId) return FolderResult.Success(id, self.name)
        if (newParentId != null) {
            if (all.none { it.id == newParentId }) return FolderResult.Invalid("The target folder no longer exists")
            if (newParentId == id || newParentId in FolderTree.descendantIds(all, id))
                return FolderResult.Invalid("A folder can't go inside itself")
            if (FolderTree.depth(all, newParentId) + FolderTree.height(all, id) > FolderTree.MAX_DEPTH)
                return FolderResult.Invalid("That would nest folders more than ${FolderTree.MAX_DEPTH} levels deep")
        }
        val clash = all.any { it.id != id && it.parentId == newParentId && it.name.equals(self.name, ignoreCase = true) }
        if (clash) return FolderResult.Invalid("A folder named '${self.name}' already exists there")
        folderDao.setParent(id, newParentId)
        return FolderResult.Success(id, self.name)
    }

    suspend fun updateFolder(folder: FolderEntity) = folderDao.updateFolder(folder)
    suspend fun deleteFolder(folder: FolderEntity) = folderDao.deleteFolder(folder)

    /** Deletes the given folders. Their screenshot rows go with them (foreign key cascade). */
    suspend fun deleteFolderTree(ids: List<Long>) { if (ids.isNotEmpty()) folderDao.deleteByIds(ids) }
}