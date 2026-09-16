package com.darki.cloud.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val telegramUserId: String,
    val displayName: String,
)

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val platform: String,
    val syncCursor: String = "0",
)

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String,
    val parentId: String?,
    val name: String,
    val modifiedAt: String,
    val deletedAt: String?,
)

@Entity(tableName = "files")
data class FileEntity(
    @PrimaryKey val id: String,
    val folderId: String,
    val name: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val sha256: String?,
    val modifiedAt: String,
    val deletedAt: String?,
)
