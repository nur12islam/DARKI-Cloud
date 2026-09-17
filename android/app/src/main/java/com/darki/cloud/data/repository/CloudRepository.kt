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

class CloudRepository(private val api: DarkiCloudApi, private val dao: CloudDao) {
    fun observeFolders(parentId: String?): Flow<List<FolderEntity>> = dao.observeFolders(parentId)
    fun observeAllFolders(): Flow<List<FolderEntity>> = dao.observeAllFolders()
    fun observeFiles(folderId: String): Flow<List<FileEntity>> = dao.observeFiles(folderId)
    fun observeDeletedFiles(): Flow<List<FileEntity>> = dao.observeDeletedFiles()
    suspend fun exchangeTelegramLogin(code: String): JSONObject = api.exchangeTelegramLogin(code)
    suspend fun loadRoot(token: String): FolderEntity { val root = api.getRootFolder(token).getJSONObject("folder"); val entity = root.toFolderEntity(root.getString("userId")); dao.upsertFolders(listOf(entity)); loadFolder(token, entity.id); return entity }
    suspend fun loadFolder(token: String, folderId: String) { val response = api.getFolder(token, folderId); val folder = response.getJSONObject("folder"); val userId = folder.getString("userId"); dao.upsertFolders(listOf(folder.toFolderEntity(userId))); val folders = response.optJSONArray("folders"); dao.upsertFolders(buildList { if (folders != null) for (i in 0 until folders.length()) add(folders.getJSONObject(i).toFolderEntity(userId)) }); val files = response.optJSONArray("files"); dao.upsertFiles(buildList { if (files != null) for (i in 0 until files.length()) add(files.getJSONObject(i).toFileEntity(userId)) }) }
    suspend fun registerDevice(token: String, deviceName: String, platform: String): DeviceEntity = api.registerDevice(token, deviceName, platform).getJSONObject("device").toDeviceEntity().also { dao.upsertDevice(it) }
    suspend fun uploadFile(token: String, folderId: String, name: String, mimeType: String?, sizeBytes: Long, deviceId: String, input: InputStream): FileEntity = api.uploadFile(token, folderId, name, mimeType, sizeBytes, deviceId, input).getJSONObject("file").also { dao.upsertFiles(listOf(it.toFileEntity(it.getString("userId")))) }.toFileEntity()
    suspend fun downloadFile(token: String, fileId: String, output: OutputStream) = api.downloadFile(token, fileId, output)
    suspend fun createFolder(token: String, parentId: String, name: String, deviceId: String): FolderEntity = api.createFolder(token, parentId, name, deviceId).getJSONObject("folder").also { dao.upsertFolders(listOf(it.toFolderEntity(it.getString("userId")))) }.toFolderEntity()
    suspend fun renameFolder(token: String, folderId: String, name: String, deviceId: String): FolderEntity = api.renameFolder(token, folderId, name, deviceId).getJSONObject("folder").also { dao.upsertFolders(listOf(it.toFolderEntity(it.getString("userId")))) }.toFolderEntity()
    suspend fun moveFolder(token: String, folderId: String, parentId: String, deviceId: String): FolderEntity = api.moveFolder(token, folderId, parentId, deviceId).getJSONObject("folder").also { dao.upsertFolders(listOf(it.toFolderEntity(it.getString("userId")))) }.toFolderEntity()
    suspend fun renameFile(token: String, fileId: String, name: String, deviceId: String): FileEntity = api.renameFile(token, fileId, name, deviceId).getJSONObject("file").also { dao.upsertFiles(listOf(it.toFileEntity(it.getString("userId")))) }.toFileEntity()
    suspend fun moveFile(token: String, fileId: String, folderId: String, deviceId: String): FileEntity = api.moveFile(token, fileId, folderId, deviceId).getJSONObject("file").also { dao.upsertFiles(listOf(it.toFileEntity(it.getString("userId")))) }.toFileEntity()
    suspend fun deleteFile(token: String, fileId: String, deviceId: String): FileEntity = api.deleteFile(token, fileId, deviceId).getJSONObject("file").also { dao.upsertFiles(listOf(it.toFileEntity(it.getString("userId")))) }.toFileEntity()
    suspend fun restoreFile(token: String, fileId: String, deviceId: String): FileEntity = api.restoreFile(token, fileId, deviceId).getJSONObject("file").also { dao.upsertFiles(listOf(it.toFileEntity(it.getString("userId")))) }.toFileEntity()

    suspend fun sync(token: String, deviceId: String, cursor: String): String {
        var current = cursor
        do {
            val response = api.pullChanges(token, deviceId, current, 100)
            val changes = response.optJSONArray("changes")
            if (changes != null) for (i in 0 until changes.length()) applySyncChange(token, changes.getJSONObject(i))
            val next = response.optString("cursor", current)
            if (next != current) { api.acknowledgeSync(token, deviceId, next); current = next }
            if (!response.optBoolean("hasMore", false)) break
        } while (true)
        return current
    }

    private suspend fun applySyncChange(token: String, change: JSONObject) {
        val entityType = change.optString("entityType")
        val entityId = change.optString("entityId")
        val operation = change.optString("operation")
        val payload = change.optJSONObject("payload") ?: JSONObject()
        when (entityType to operation) {
            "folder" to "create", "folder" to "update", "folder" to "move" -> refreshFolderEntity(token, entityId)
            "file" to "create", "file" to "update", "file" to "move" -> refreshFileEntity(token, entityId)
            "file" to "delete" -> {
                val existing = dao.findFileById(entityId)
                if (existing != null) dao.markFileDeleted(entityId, System.currentTimeMillis()) else refreshFileEntity(token, entityId)
            }
            "file" to "restore" -> {
                val existing = dao.findFileById(entityId)
                if (existing != null) dao.markFileRestored(entityId) else refreshFileEntity(token, entityId)
            }
            else -> refreshByPayload(token, entityType, entityId, payload)
        }
    }

    private suspend fun refreshFolderEntity(token: String, folderId: String) {
        runCatching { api.getFolder(token, folderId).getJSONObject("folder").let { dao.upsertFolders(listOf(it.toFolderEntity(it.getString("userId")))) } }
            .onFailure { dao.findFolderById(folderId)?.let { current -> dao.upsertFolders(listOf(current.copy(deletedAt = System.currentTimeMillis()))) } }
    }

    private suspend fun refreshFileEntity(token: String, fileId: String) {
        runCatching { api.getFolder(token, dao.findFileById(fileId)?.folderId ?: return).let { response -> val files = response.optJSONArray("files") ?: return; for (i in 0 until files.length()) { val file = files.getJSONObject(i); if (file.optString("id") == fileId) { dao.upsertFiles(listOf(file.toFileEntity(file.getString("userId")))); return } } } }
            .onFailure { }
    }

    private suspend fun refreshByPayload(token: String, entityType: String, entityId: String, payload: JSONObject) {
        when (entityType) {
            "folder" -> refreshFolderEntity(token, entityId)
            "file" -> refreshFileEntity(token, entityId)
        }
    }

    suspend fun logout(token: String?) { if (token != null) runCatching { api.logout(token) }; dao.clearFiles(); dao.clearFolders(); dao.clearDevices(); dao.clearUsers() }
    private fun JSONObject.toFolderEntity(userId: String) = FolderEntity(getString("id"), userId, if (isNull("parentId")) null else getString("parentId"), getString("name"), optTimestamp("createdAt"), optTimestamp("modifiedAt"), optTimestamp("deletedAt"))
    private fun JSONObject.toFolderEntity() = FolderEntity(getString("id"), getString("userId"), if (isNull("parentId")) null else getString("parentId"), getString("name"), optTimestamp("createdAt"), optTimestamp("modifiedAt"), optTimestamp("deletedAt"))
    private fun JSONObject.toFileEntity(userId: String) = FileEntity(getString("id"), userId, getString("folderId"), if (isNull("storageObjectId")) null else getString("storageObjectId"), getString("name"), if (isNull("mimeType")) null else getString("mimeType"), if (isNull("sizeBytes")) null else optLong("sizeBytes"), if (isNull("sha256")) null else getString("sha256"), optTimestamp("createdAt"), optTimestamp("modifiedAt"), optTimestamp("deletedAt"))
    private fun JSONObject.toFileEntity() = toFileEntity(getString("userId"))
    private fun JSONObject.optTimestamp(name: String): Long? = if (isNull(name)) null else runCatching { java.time.Instant.parse(optString(name)).toEpochMilli() }.getOrNull()
}
