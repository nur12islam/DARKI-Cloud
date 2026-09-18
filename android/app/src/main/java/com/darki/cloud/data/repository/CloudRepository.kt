package com.darki.cloud.data.repository

import com.darki.cloud.SearchResults
import com.darki.cloud.data.api.DarkiCloudApi
import com.darki.cloud.data.local.CloudDao
import com.darki.cloud.data.local.DeviceEntity
import com.darki.cloud.data.local.FileEntity
import com.darki.cloud.data.local.FolderEntity
import com.darki.cloud.data.local.TransferEntity
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

class CloudRepository(private val api: DarkiCloudApi, private val dao: CloudDao) {
    fun observeFolders(parentId: String?): Flow<List<FolderEntity>> = dao.observeFolders(parentId)
    fun observeAllFolders(): Flow<List<FolderEntity>> = dao.observeAllFolders()
    fun observeFiles(folderId: String): Flow<List<FileEntity>> = dao.observeFiles(folderId)
    fun observeDeletedFiles(): Flow<List<FileEntity>> = dao.observeDeletedFiles()
    fun observePendingTransfers(): Flow<List<TransferEntity>> = dao.observePendingTransfers()
    suspend fun enqueueTransfer(transfer: TransferEntity) = dao.upsertTransfer(transfer)\n    suspend fun findFolder(folderId: String): FolderEntity? = dao.findFolderById(folderId)\n    suspend fun findFile(fileId: String): FileEntity? = dao.findFileById(fileId)
    suspend fun exchangeTelegramLogin(code: String): JSONObject = api.exchangeTelegramLogin(code)
    suspend fun search(token: String, query: String, limit: Int = 50): SearchResults {
        val response = api.search(token, query, limit)
        val foldersJson = response.optJSONArray("folders")
        val filesJson = response.optJSONArray("files")
        val folders = buildList { if (foldersJson != null) for (i in 0 until foldersJson.length()) { val item = foldersJson.getJSONObject(i); add(item.toFolderEntity(item.getString("userId"))) } }
        val files = buildList { if (filesJson != null) for (i in 0 until filesJson.length()) { val item = filesJson.getJSONObject(i); add(item.toFileEntity(item.getString("userId"))) } }
        return SearchResults(folders, files)
    }
    suspend fun loadRoot(token: String): FolderEntity { val root = api.getRootFolder(token).getJSONObject("folder"); val entity = root.toFolderEntity(root.getString("userId")); dao.upsertFolders(listOf(entity)); loadFolder(token, entity.id); return entity }
    suspend fun loadFolder(token: String, folderId: String) { val response = api.getFolder(token, folderId); val folder = response.getJSONObject("folder"); val userId = folder.getString("userId"); dao.upsertFolders(listOf(folder.toFolderEntity(userId))); val folders = response.optJSONArray("folders"); dao.upsertFolders(buildList { if (folders != null) for (i in 0 until folders.length()) add(folders.getJSONObject(i).toFolderEntity(userId)) }); val files = response.optJSONArray("files"); dao.upsertFiles(buildList { if (files != null) for (i in 0 until files.length()) add(files.getJSONObject(i).toFileEntity(userId)) }) }
    suspend fun registerDevice(token: String, deviceName: String, platform: String): DeviceEntity = api.registerDevice(token, deviceName, platform).getJSONObject("device").toDeviceEntity().also { dao.upsertDevice(it) }
    suspend fun uploadFile(token: String, folderId: String, name: String, mimeType: String?, sizeBytes: Long, deviceId: String, operationId: String, input: InputStream): FileEntity = api.uploadFile(token, folderId, name, mimeType, sizeBytes, deviceId, operationId, input).getJSONObject("file").also { dao.upsertFiles(listOf(it.toFileEntity(it.getString("userId")))) }.toFileEntity()
    suspend fun downloadFile(token: String, fileId: String, output: OutputStream) = api.downloadFile(token, fileId, output)
    suspend fun createFolder(token: String, parentId: String, name: String, deviceId: String): FolderEntity = api.createFolder(token, parentId, name, deviceId).getJSONObject("folder").also { dao.upsertFolders(listOf(it.toFolderEntity(it.getString("userId")))) }.toFolderEntity()
    suspend fun renameFolder(token: String, folderId: String, name: String, deviceId: String): FolderEntity = api.renameFolder(token, folderId, name, deviceId).getJSONObject("folder").also { dao.upsertFolders(listOf(it.toFolderEntity(it.getString("userId")))) }.toFolderEntity()
    suspend fun moveFolder(token: String, folderId: String, parentId: String, deviceId: String): FolderEntity = api.moveFolder(token, folderId, parentId, deviceId).getJSONObject("folder").also { dao.upsertFolders(listOf(it.toFolderEntity(it.getString("userId")))) }.toFolderEntity()
    suspend fun renameFile(token: String, fileId: String, name: String, deviceId: String): FileEntity = api.renameFile(token, fileId, name, deviceId).getJSONObject("file").also { dao.upsertFiles(listOf(it.toFileEntity(it.getString("userId")))) }.toFileEntity()
    suspend fun moveFile(token: String, fileId: String, folderId: String, deviceId: String): FileEntity = api.moveFile(token, fileId, folderId, deviceId).getJSONObject("file").also { dao.upsertFiles(listOf(it.toFileEntity(it.getString("userId")))) }.toFileEntity()
    suspend fun deleteFile(token: String, fileId: String, deviceId: String): FileEntity = api.deleteFile(token, fileId, deviceId).getJSONObject("file").also { dao.upsertFiles(listOf(it.toFileEntity(it.getString("userId")))) }.toFileEntity()
    suspend fun restoreFile(token: String, fileId: String, deviceId: String): FileEntity = api.restoreFile(token, fileId, deviceId).getJSONObject("file").also { dao.upsertFiles(listOf(it.toFileEntity(it.getString("userId")))) }.toFileEntity()
    suspend fun sync(token: String, deviceId: String, cursor: String): String { var current = cursor; do { val response = api.pullChanges(token, deviceId, current, 100); val changes = response.optJSONArray("changes"); if (changes != null) for (i in 0 until changes.length()) applySyncChange(token, changes.getJSONObject(i)); val next = response.optString("cursor", current); if (next != current) { api.acknowledgeSync(token, deviceId, next); current = next }; if (!response.optBoolean("hasMore", false)) break } while (true); return current }
    private suspend fun applySyncChange(token: String, change: JSONObject) { val type = change.optString("entityType"); val id = change.optString("entityId"); val op = change.optString("operation"); val payload = change.optJSONObject("payload") ?: JSONObject(); when (type to op) { "folder" to "create", "folder" to "update", "folder" to "move" -> refreshFolderEntity(token, id); "file" to "create", "file" to "update", "file" to "move" -> refreshFileEntity(token, id, payload); "file" to "delete" -> dao.findFileById(id)?.let { dao.markFileDeleted(id, System.currentTimeMillis()) } ?: refreshFileEntity(token, id, payload); "file" to "restore" -> dao.findFileById(id)?.let { dao.markFileRestored(id) } ?: refreshFileEntity(token, id, payload); else -> refreshByPayload(token, type, id, payload) } }
    private suspend fun refreshFolderEntity(token: String, folderId: String) { runCatching { api.getFolder(token, folderId).getJSONObject("folder").let { dao.upsertFolders(listOf(it.toFolderEntity(it.getString("userId")))) } }.onFailure { dao.findFolderById(folderId)?.let { dao.upsertFolders(listOf(it.copy(deletedAt = System.currentTimeMillis()))) } } }
    private suspend fun refreshFileEntity(token: String, fileId: String, payload: JSONObject = JSONObject()) { val folderId = dao.findFileById(fileId)?.folderId ?: payload.optString("folderId").takeIf { it.isNotBlank() } ?: return; runCatching { val response = api.getFolder(token, folderId); val files = response.optJSONArray("files") ?: return@runCatching; for (i in 0 until files.length()) { val file = files.getJSONObject(i); if (file.optString("id") == fileId) { dao.upsertFiles(listOf(file.toFileEntity(file.getString("userId")))); return@runCatching } } } }
    private suspend fun refreshByPayload(token: String, type: String, id: String, payload: JSONObject) { when (type) { "folder" -> refreshFolderEntity(token, id); "file" -> refreshFileEntity(token, id, payload) } }
    suspend fun logout(token: String?) { if (token != null) runCatching { api.logout(token) }; dao.clearTransfers(); dao.clearFiles(); dao.clearFolders(); dao.clearDevices(); dao.clearUsers() }
    private fun JSONObject.toDeviceEntity(): DeviceEntity = DeviceEntity(
        id = getString("id"),
        userId = getString("userId"),
        name = getString("name"),
        platform = getString("platform"),
        lastSeenAt = optTimestamp("lastSeenAt"),
        syncCursor = optLong("syncCursor", 0L),
    )

    private fun JSONObject.toFolderEntity(userId: String) = FolderEntity(getString("id"), userId, if (isNull("parentId")) null else getString("parentId"), getString("name"), optTimestamp("createdAt"), optTimestamp("modifiedAt"), optTimestamp("deletedAt"))
    private fun JSONObject.toFolderEntity() = FolderEntity(getString("id"), getString("userId"), if (isNull("parentId")) null else getString("parentId"), getString("name"), optTimestamp("createdAt"), optTimestamp("modifiedAt"), optTimestamp("deletedAt"))
    private fun JSONObject.toFileEntity(userId: String) = FileEntity(getString("id"), userId, getString("folderId"), if (isNull("storageObjectId")) null else getString("storageObjectId"), getString("name"), if (isNull("mimeType")) null else getString("mimeType"), if (isNull("sizeBytes")) null else optLong("sizeBytes"), if (isNull("sha256")) null else getString("sha256"), optTimestamp("createdAt"), optTimestamp("modifiedAt"), optTimestamp("deletedAt"))
    private fun JSONObject.toFileEntity() = toFileEntity(getString("userId"))
    private fun JSONObject.optTimestamp(name: String): Long? = if (isNull(name)) null else runCatching { java.time.Instant.parse(optString(name)).toEpochMilli() }.getOrNull()
}
