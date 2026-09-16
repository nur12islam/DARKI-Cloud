package com.darki.cloud.data.repository

import com.darki.cloud.data.api.DarkiCloudApi
import com.darki.cloud.data.local.CloudDao
import com.darki.cloud.data.local.DeviceEntity
import com.darki.cloud.data.local.FileEntity
import com.darki.cloud.data.local.FolderEntity
import com.darki.cloud.data.local.UserEntity
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

class CloudRepository(
    private val api: DarkiCloudApi,
    private val dao: CloudDao,
) {
    fun observeFolders(parentId: String?): Flow<List<FolderEntity>> = dao.observeFolders(parentId)

    fun observeFiles(folderId: String): Flow<List<FileEntity>> = dao.observeFiles(folderId)

    suspend fun loadFolder(token: String, folderId: String) {
        val response = api.getFolder(token, folderId)
        val folder = response.getJSONObject("folder")
        val userId = folder.optString("userId")
        dao.upsertFolders(listOf(folder.toFolderEntity(userId)))

        val folders = response.optJSONArray("folders")
        val folderEntities = buildList {
            if (folders != null) {
                for (index in 0 until folders.length()) {
                    add(folders.getJSONObject(index).toFolderEntity(userId))
                }
            }
        }
        dao.upsertFolders(folderEntities)

        val files = response.optJSONArray("files")
        val fileEntities = buildList {
            if (files != null) {
                for (index in 0 until files.length()) {
                    add(files.getJSONObject(index).toFileEntity(userId))
                }
            }
        }
        dao.upsertFiles(fileEntities)
    }

    suspend fun registerDevice(token: String, deviceName: String, platform: String): DeviceEntity {
        val device = api.registerDevice(token, deviceName, platform).getJSONObject("device")
        val entity = device.toDeviceEntity()
        dao.upsertDevice(entity)
        return entity
    }

    private fun JSONObject.toFolderEntity(userId: String) = FolderEntity(
        id = getString("id"),
        userId = userId,
        parentId = if (isNull("parentId")) null else getString("parentId"),
        name = getString("name"),
        createdAt = optTimestamp("createdAt"),
        modifiedAt = optTimestamp("modifiedAt"),
        deletedAt = optTimestamp("deletedAt"),
    )

    private fun JSONObject.toFileEntity(userId: String) = FileEntity(
        id = getString("id"),
        userId = userId,
        folderId = getString("folderId"),
        storageObjectId = if (isNull("storageObjectId")) null else getString("storageObjectId"),
        name = getString("name"),
        mimeType = if (isNull("mimeType")) null else getString("mimeType"),
        sizeBytes = if (isNull("sizeBytes")) null else optLong("sizeBytes"),
        sha256 = if (isNull("sha256")) null else getString("sha256"),
        createdAt = optTimestamp("createdAt"),
        modifiedAt = optTimestamp("modifiedAt"),
        deletedAt = optTimestamp("deletedAt"),
    )

    private fun JSONObject.toDeviceEntity() = DeviceEntity(
        id = getString("id"),
        userId = getString("userId"),
        name = getString("deviceName"),
        platform = getString("platform"),
        lastSeenAt = optTimestamp("lastSeenAt"),
        syncCursor = optLong("syncCursor", 0L),
    )

    private fun JSONObject.optTimestamp(name: String): Long? =
        if (isNull(name)) null else optString(name).let { value ->
            runCatching { java.time.Instant.parse(value).toEpochMilli() }.getOrNull()
        }
}
