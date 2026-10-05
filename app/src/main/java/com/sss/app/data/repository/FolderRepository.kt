package com.sss.app.data.repository

import com.sss.app.data.local.FolderDao
import com.sss.app.data.local.FolderEntity
import kotlinx.coroutines.flow.Flow

class FolderRepository(
    private val folderDao: FolderDao
) {

    val folders: Flow<List<FolderEntity>> =
        folderDao.getAllFolders()

    suspend fun addFolder(name: String) {

        folderDao.insertFolder(
            FolderEntity(
                name = name
            )
        )
    }

    suspend fun updateFolder(folder: FolderEntity) {

        folderDao.updateFolder(folder)
    }

    suspend fun deleteFolder(folder: FolderEntity) {

        folderDao.deleteFolder(folder)
    }
}