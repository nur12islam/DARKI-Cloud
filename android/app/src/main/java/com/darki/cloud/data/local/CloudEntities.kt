package com.darki.cloud.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val telegramUserId: Long,
    val displayName: String,
)

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val platform: String,
    val lastSeenAt: Long?,
    val syncCursor: Long = 0L,
)

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val parentId: String?,
    val name: String,
    val createdAt: Long?,
    val modifiedAt: Long?,
    val deletedAt: Long?,
)

@Entity(tableName = "files")
data class FileEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val folderId: String,
    val storageObjectId: String?,
    val name: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val sha256: String?,
    val createdAt: Long?,
    val modifiedAt: Long?,
    val deletedAt: Long?,
)
