package com.sss.app.data.repository

import com.sss.app.data.local.StudyDocumentDao
import com.sss.app.data.local.StudyDocumentEntity
import kotlinx.coroutines.flow.Flow

class StudyDocumentRepository(
    private val studyDocumentDao: StudyDocumentDao
) {

    val allDocuments: Flow<List<StudyDocumentEntity>> =
        studyDocumentDao.getAllDocuments()

    fun getDocumentsByFolder(folderId: Long): Flow<List<StudyDocumentEntity>> {
        return studyDocumentDao.getDocumentsByFolder(folderId)
    }

    suspend fun getDocumentById(id: Long): StudyDocumentEntity? {
        return studyDocumentDao.getDocumentById(id)
    }

    fun observeDocumentById(id: Long): Flow<StudyDocumentEntity?> {
        return studyDocumentDao.observeDocumentById(id)
    }

    suspend fun saveDocument(document: StudyDocumentEntity): Long {
        return studyDocumentDao.insertDocument(document)
    }

    suspend fun updateDocument(document: StudyDocumentEntity) {
        studyDocumentDao.updateDocument(document)
    }

    suspend fun deleteDocument(document: StudyDocumentEntity) {
        studyDocumentDao.deleteDocument(document)
    }
}
