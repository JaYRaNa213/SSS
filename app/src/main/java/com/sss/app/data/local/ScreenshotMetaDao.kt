package com.sss.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScreenshotMetaDao {

    @Query("SELECT * FROM screenshot_meta")
    fun observeAll(): Flow<List<ScreenshotMetaEntity>>

    @Query("SELECT * FROM screenshot_meta WHERE mediaId = :id LIMIT 1")
    suspend fun get(id: Long): ScreenshotMetaEntity?

    @Query("SELECT * FROM screenshot_meta WHERE mediaId IN (:ids)")
    suspend fun getMany(ids: List<Long>): List<ScreenshotMetaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(meta: ScreenshotMetaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(list: List<ScreenshotMetaEntity>)
}