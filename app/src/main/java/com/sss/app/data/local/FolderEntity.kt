package com.sss.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "folders", indices = [Index(value = ["parentId"])])
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val colorIndex: Int = 0,
    /** null = top-level folder. No foreign key on purpose: the repository deletes whole subtrees. */
    val parentId: Long? = null
)