package com.darki.cloud.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CloudDao {
    @Upsert suspend fun upsertUser(user: UserEntity)
    @Upsert suspend fun upsertDevice(device: DeviceEntity)
    @Upsert suspend fun upsertFolders(folders: List<FolderEntity>)
    @Upsert suspend fun upsertFiles(files: List<FileEntity>)

    @Query("SELECT * FROM folders WHERE parentId IS :parentId AND deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    fun observeFolders(parentId: String?): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    fun observeAllFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM files WHERE folderId = :folderId AND deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    fun observeFiles(folderId: String): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE deletedAt IS NOT NULL ORDER BY modifiedAt DESC")
    fun observeDeletedFiles(): Flow<List<FileEntity>>

    @Query("UPDATE devices SET syncCursor = :cursor WHERE id = :deviceId")
    suspend fun updateCursor(deviceId: String, cursor: Long)
    @Query("DELETE FROM users") suspend fun clearUsers()
    @Query("DELETE FROM devices") suspend fun clearDevices()
    @Query("DELETE FROM folders") suspend fun clearFolders()
    @Query("DELETE FROM files") suspend fun clearFiles()
}
