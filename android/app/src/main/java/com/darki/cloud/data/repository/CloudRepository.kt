package com.darki.cloud.data.repository

import com.darki.cloud.data.api.DarkiCloudApi
import com.darki.cloud.data.local.CloudDao
import com.darki.cloud.data.local.DeviceEntity
import com.darki.cloud.data.local.FileEntity
import com.darki.cloud.data.local.FolderEntity
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

class CloudRepository(
    private val api: DarkiCloudApi,
    private val dao: CloudDao,
) {
    fun observeFolders(parentId: String?): Flow<List<FolderEntity>> = dao.observeFolders(parentId)
    fun observeFiles(folderId: String): Flow<List<FileEntity>> = dao.observeFiles(folderId)
    suspend fun exchangeTelegramLogin(code: String): JSONObject = api.exchangeTelegramLogin(code)

    suspend fun loadRoot(token: String): FolderEntity {
        val root = api.getRootFolder(token).getJSONObject("folder")
        val entity = root.toFolderEntity(root.getString("userId"))
        dao.upsertFolders(listOf(entity))
        loadFolder(token, entity.id)
        return entity
    }

    suspend fun loadFolder(token: String, folderId: String) {
        val response = api.getFolder(token, folderId)
        val folder = response.getJSONObject("folder")
        val userId = folder.getString("userId")
        dao.upsertFolders(listOf(folder.toFolderEntity(userId)))
        val folders = response.optJSONArray("folders")
        dao.upsertFolders(buildList { if (folders != null) for (index in 0 until folders.length()) add(folders.getJSONObject(index).toFolderEntity(userId)) })
        val files = response.optJSONArray("files")
        dao.upsertFiles(buildList { if (files != null) for (index in 0 until files.length()) add(files.getJSONObject(index).toFileEntity(userId)) })
    }

    suspend fun registerDevice(token: String, deviceName: String, platform: String): DeviceEntity {
        val entity = api.registerDevice(token, deviceName, platform).getJSONObject("device").toDeviceEntity()
        dao.upsertDevice(entity)
        return entity
    }

    suspend fun uploadFile(token: String, folderId: String, name: String, mimeType: String?, sizeBytes: Long, deviceId: String, input: InputStream): FileEntity =
        api.uploadFile(token, folderId, name, mimeType, sizeBytes, deviceId, input).getJSONObject("file").also { file ->
            dao.upsertFiles(listOf(file.toFileEntity(file.getString("userId"))))
        }.toFileEntity()

    suspend fun downloadFile(token: String, fileId: String, output: OutputStream) = api.downloadFile(token, fileId, output)

    suspend fun createFolder(token: String, parentId: String, name: String, deviceId: String): FolderEntity =
        api.createFolder(token, parentId, name, deviceId).getJSONObject("folder").also { folder ->
            val userId = folder.getString("userId")
            dao.upsertFolders(listOf(folder.toFolderEntity(userId)))
        }.toFolderEntity()

    suspend fun renameFolder(token: String, folderId: String, name: String, deviceId: String): FolderEntity =
        api.renameFolder(token, folderId, name, deviceId).getJSONObject("folder").also { folder ->
            dao.upsertFolders(listOf(folder.toFolderEntity(folder.getString("userId"))))
        }.toFolderEntity()

    suspend fun moveFolder(token: String, folderId: String, parentId: String, deviceId: String): FolderEntity =
        api.moveFolder(token, folderId, parentId, deviceId).getJSONObject("folder").also { folder ->
            dao.upsertFolders(listOf(folder.toFolderEntity(folder.getString("userId"))))
        }.toFolderEntity()

    suspend fun renameFile(token: String, fileId: String, name: String, deviceId: String): FileEntity =
        api.renameFile(token, fileId, name, deviceId).getJSONObject("file").also { file ->
            dao.upsertFiles(listOf(file.toFileEntity(file.getString("userId"))))
        }.toFileEntity()

    suspend fun moveFile(token: String, fileId: String, folderId: String, deviceId: String): FileEntity =
        api.moveFile(token, fileId, folderId, deviceId).getJSONObject("file").also { file ->
            dao.upsertFiles(listOf(file.toFileEntity(file.getString("userId"))))
        }.toFileEntity()

    suspend fun deleteFile(token: String, fileId: String, deviceId: String): FileEntity =
        api.deleteFile(token, fileId, deviceId).getJSONObject("file").also { file ->
            dao.upsertFiles(listOf(file.toFileEntity(file.getString("userId"))))
        }.toFileEntity()

    suspend fun restoreFile(token: String, fileId: String, deviceId: String): FileEntity =
        api.restoreFile(token, fileId, deviceId).getJSONObject("file").also { file ->
            dao.upsertFiles(listOf(file.toFileEntity(file.getString("userId"))))
        }.toFileEntity()

    suspend fun sync(token: String, deviceId: String, cursor: String): String {
        var currentCursor = cursor
        do {
            val response = api.pullChanges(token, deviceId, currentCursor, 100)
            val changes = response.optJSONArray("changes")
            val nextCursor = response.optString("cursor", currentCursor)
            if (changes != null && changes.length() > 0) loadRoot(token)
            if (nextCursor != currentCursor) {
                api.acknowledgeSync(token, deviceId, nextCursor)
                currentCursor = nextCursor
            }
            if (!response.optBoolean("hasMore", false)) break
        } while (true)
        return currentCursor
    }

    suspend fun logout(token: String?) {
        if (token != null) runCatching { api.logout(token) }
        dao.clearFiles(); dao.clearFolders(); dao.clearDevices(); dao.clearUsers()
    }

    private fun JSONObject.toFolderEntity(userId: String) = FolderEntity(
        id = getString("id"), userId = userId, parentId = if (isNull("parentId")) null else getString("parentId"),
        name = getString("name"), createdAt = optTimestamp("createdAt"), modifiedAt = optTimestamp("modifiedAt"), deletedAt = optTimestamp("deletedAt"),
    )
    private fun JSONObject.toFolderEntity() = FolderEntity(
        id = getString("id"), userId = getString("userId"), parentId = if (isNull("parentId")) null else getString("parentId"),
        name = getString("name"), createdAt = optTimestamp("createdAt"), modifiedAt = optTimestamp("modifiedAt"), deletedAt = optTimestamp("deletedAt"),
    )
    private fun JSONObject.toFileEntity(userId: String) = FileEntity(
        id = getString("id"), userId = userId, folderId = getString("folderId"), storageObjectId = if (isNull("storageObjectId")) null else getString("storageObjectId"),
        name = getString("name"), mimeType = if (isNull("mimeType")) null else getString("mimeType"), sizeBytes = if (isNull("sizeBytes")) null else optLong("sizeBytes"),
        sha256 = if (isNull("sha256")) null else getString("sha256"), createdAt = optTimestamp("createdAt"), modifiedAt = optTimestamp("modifiedAt"), deletedAt = optTimestamp("deletedAt"),
    )
    private fun JSONObject.toFileEntity() = FileEntity(
        id = getString("id"), userId = getString("userId"), folderId = getString("folderId"), storageObjectId = if (isNull("storageObjectId")) null else getString("storageObjectId"),
        name = getString("name"), mimeType = if (isNull("mimeType")) null else getString("mimeType"), sizeBytes = if (isNull("sizeBytes")) null else optLong("sizeBytes"),
        sha256 = if (isNull("sha256")) null else getString("sha256"), createdAt = optTimestamp("createdAt"), modifiedAt = optTimestamp("modifiedAt"), deletedAt = optTimestamp("deletedAt"),
    )
    private fun JSONObject.optTimestamp(name: String): Long? = if (isNull(name)) null else runCatching { java.time.Instant.parse(optString(name)).toEpochMilli() }.getOrNull()
}
