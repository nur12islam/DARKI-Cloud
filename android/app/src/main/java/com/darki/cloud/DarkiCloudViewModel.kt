package com.darki.cloud

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.darki.cloud.data.api.DarkiCloudApiException
import com.darki.cloud.data.local.FileEntity
import com.darki.cloud.data.local.FolderEntity
import com.darki.cloud.data.local.SessionStore
import com.darki.cloud.data.local.TransferEntity
import com.darki.cloud.data.repository.CloudRepository
import com.darki.cloud.data.transfer.TransferScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.util.UUID

class DarkiCloudViewModel(private val repository: CloudRepository, private val sessionStore: SessionStore, private val appContext: Context) : ViewModel() {
    private val _isAuthenticated = MutableStateFlow(sessionStore.token != null); val isAuthenticated: Flow<Boolean> = _isAuthenticated
    private val _currentFolderId = MutableStateFlow(sessionStore.rootFolderId); val currentFolderId: Flow<String?> = _currentFolderId
    private val _folderStack = MutableStateFlow<List<FolderEntity>>(emptyList()); val folderStack: Flow<List<FolderEntity>> = _folderStack
    val folders: Flow<List<FolderEntity>> = _currentFolderId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeFolders(id) }
    val allFolders: Flow<List<FolderEntity>> = repository.observeAllFolders()
    val files: Flow<List<FileEntity>> = _currentFolderId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeFiles(id) }
    val deletedFiles: Flow<List<FileEntity>> = repository.observeDeletedFiles()
    val transfers = repository.observePendingTransfers()
    private val _searchResults = MutableStateFlow<SearchResults?>(null); val searchResults: Flow<SearchResults?> = _searchResults
    private val _searching = MutableStateFlow(false); val searching: Flow<Boolean> = _searching
    private val _isRefreshing = MutableStateFlow(false); val isRefreshing: Flow<Boolean> = _isRefreshing
    private val _isUploading = MutableStateFlow(false); val isUploading: Flow<Boolean> = _isUploading
    private val _isDownloading = MutableStateFlow(false); val isDownloading: Flow<Boolean> = _isDownloading
    private val _previewFileId = MutableStateFlow<String?>(null); val previewFileId: Flow<String?> = _previewFileId
    private val _previewMimeType = MutableStateFlow<String?>(null); val previewMimeType: Flow<String?> = _previewMimeType
    private val _previewName = MutableStateFlow<String?>(null); val previewName: Flow<String?> = _previewName
    private val _error = MutableStateFlow<String?>(null); val error: Flow<String?> = _error
    init { refreshRoot() }
    fun completeTelegramLogin(code: String) { viewModelScope.launch { _error.value = null; runCatching { val response = repository.exchangeTelegramLogin(code); sessionStore.token = response.getString("token"); sessionStore.userId = response.getJSONObject("user").getString("id"); val root = repository.loadRoot(sessionStore.token!!); sessionStore.rootFolderId = root.id; _currentFolderId.value = root.id; ensureDeviceAndSync(sessionStore.token!!); _isAuthenticated.value = true }.onFailure { _error.value = it.message ?: "Unable to complete Telegram login" } } }
    fun logout() { val token = sessionStore.token; viewModelScope.launch { runCatching { repository.logout(token) }; closePreview(); _searchResults.value = null; sessionStore.clear(); _currentFolderId.value = null; _folderStack.value = emptyList(); _isAuthenticated.value = false } }
    fun refreshRoot() { val token = sessionStore.token ?: return; viewModelScope.launch { _isRefreshing.value = true; _error.value = null; runCatching { val root = repository.loadRoot(token); sessionStore.rootFolderId = root.id; if (_currentFolderId.value == null) _currentFolderId.value = root.id; ensureDeviceAndSync(token) }.onFailure { error -> if (error is DarkiCloudApiException && error.statusCode == 401) { closePreview(); sessionStore.clear(); _isAuthenticated.value = false }; _error.value = error.message ?: "Unable to synchronize cloud data" }; _isRefreshing.value = false } }
    fun search(query: String) { val token = sessionStore.token ?: return; val normalized = query.trim(); if (normalized.isEmpty()) { _searchResults.value = null; return }; viewModelScope.launch { _searching.value = true; _error.value = null; runCatching { repository.search(token, normalized) }.onSuccess { _searchResults.value = it }.onFailure { _error.value = it.message ?: "Unable to search cloud" }; _searching.value = false } }
    fun clearSearch() { _searchResults.value = null }
    fun openFolder(folder: FolderEntity) { _searchResults.value = null; viewModelScope.launch { _folderStack.value = _folderStack.value + folder; _currentFolderId.value = folder.id; sessionStore.token?.let { runCatching { repository.loadFolder(it, folder.id) } } } }\n    fun openFolderById(folderId: String) { val token = sessionStore.token ?: return; viewModelScope.launch { runCatching { repository.loadFolder(token, folderId); repository.findFolder(folderId) }.onSuccess { folder -> if (folder != null && folder.id != sessionStore.rootFolderId) _folderStack.value = _folderStack.value + folder; _currentFolderId.value = folderId }.onFailure { _error.value = it.message ?: "Unable to open folder" } } }\n    fun previewFileById(fileId: String) { viewModelScope.launch { repository.findFile(fileId)?.let { previewFile(it) } } }
    fun navigateBack(): Boolean { val stack = _folderStack.value; if (stack.isEmpty()) return false; val next = stack.dropLast(1); _folderStack.value = next; _currentFolderId.value = next.lastOrNull()?.id ?: sessionStore.rootFolderId; return true }
    fun createFolder(name: String) { val token = sessionStore.token ?: return; val parentId = _currentFolderId.value ?: return; val deviceId = sessionStore.deviceId ?: return; action("Unable to create folder") { repository.createFolder(token, parentId, name, deviceId); repository.loadFolder(token, parentId) } }
    fun renameFolder(folder: FolderEntity, name: String) { val token = sessionStore.token ?: return; val device = sessionStore.deviceId ?: return; action("Unable to rename folder") { repository.renameFolder(token, folder.id, name, device); repository.loadFolder(token, folder.parentId ?: sessionStore.rootFolderId!!) } }
    fun moveFolder(folder: FolderEntity, parentId: String) { val token = sessionStore.token ?: return; val device = sessionStore.deviceId ?: return; action("Unable to move folder") { repository.moveFolder(token, folder.id, parentId, device); refreshCurrentFolder(token) } }
    fun renameFile(file: FileEntity, name: String) { val token = sessionStore.token ?: return; val device = sessionStore.deviceId ?: return; action("Unable to rename file") { repository.renameFile(token, file.id, name, device); repository.loadFolder(token, file.folderId) } }
    fun moveFile(file: FileEntity, folderId: String) { val token = sessionStore.token ?: return; val device = sessionStore.deviceId ?: return; action("Unable to move file") { repository.moveFile(token, file.id, folderId, device); refreshCurrentFolder(token) } }
    fun deleteFile(file: FileEntity) { val token = sessionStore.token ?: return; val device = sessionStore.deviceId ?: return; action("Unable to delete file") { repository.deleteFile(token, file.id, device); repository.loadFolder(token, file.folderId) } }
    fun restoreFile(file: FileEntity) { val token = sessionStore.token ?: return; val device = sessionStore.deviceId ?: return; action("Unable to restore file") { repository.restoreFile(token, file.id, device); repository.loadFolder(token, file.folderId) } }
    fun uploadFile(uri: Uri, resolver: ContentResolver) {
        val folderId = _currentFolderId.value ?: return
        val metadata = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor -> if (!cursor.moveToFirst()) null else { val ni = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME); val si = cursor.getColumnIndex(OpenableColumns.SIZE); val name = if (ni >= 0) cursor.getString(ni) else null; val size = if (si >= 0 && !cursor.isNull(si)) cursor.getLong(si) else null; if (name.isNullOrBlank() || size == null || size < 0) null else name to size } }
        if (metadata == null) { _error.value = "Unable to read the selected file"; return }
        runCatching { resolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val (name, size) = metadata
        val now = System.currentTimeMillis()
        val transfer = TransferEntity(UUID.randomUUID().toString(), "upload", uri.toString(), null, folderId, name, resolver.getType(uri), size, null, "queued", 0, null, now, now)
        viewModelScope.launch { _error.value = null; runCatching { repository.enqueueTransfer(transfer); TransferScheduler.enqueue(appContext, transfer) }.onFailure { _error.value = it.message ?: "Unable to queue upload" } }
    }
    fun enqueueDownload(file: FileEntity, destination: Uri, resolver: ContentResolver) {
        runCatching { resolver.takePersistableUriPermission(destination, android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        val now = System.currentTimeMillis()
        val transfer = TransferEntity(UUID.randomUUID().toString(), "download", null, file.id, null, file.name, file.mimeType, file.sizeBytes, destination.toString(), "queued", 0, null, now, now)
        viewModelScope.launch { _error.value = null; runCatching { repository.enqueueTransfer(transfer); TransferScheduler.enqueue(appContext, transfer) }.onFailure { _error.value = it.message ?: "Unable to queue download" } }
    }
    fun retryTransfer(transfer: TransferEntity) { val reset = transfer.copy(status = "queued", attempts = 0, lastError = null, updatedAt = System.currentTimeMillis()); viewModelScope.launch { runCatching { repository.enqueueTransfer(reset); TransferScheduler.retry(appContext, reset) }.onFailure { _error.value = it.message ?: "Unable to retry transfer" } } }
    fun previewFile(file: FileEntity) { if (sessionStore.token == null) return; _error.value = null; _previewFileId.value = file.id; _previewMimeType.value = file.mimeType ?: guessMimeType(file.name); _previewName.value = file.name }
    fun previewToken(): String? = sessionStore.token
    fun closePreview() { _previewFileId.value = null; _previewMimeType.value = null; _previewName.value = null }
    private fun action(message: String, block: suspend () -> Unit) { viewModelScope.launch { _error.value = null; runCatching { block() }.onFailure { error -> if (error is DarkiCloudApiException && error.statusCode == 401) { sessionStore.clear(); _isAuthenticated.value = false }; _error.value = error.message ?: message } } }
    private suspend fun refreshCurrentFolder(token: String) { _currentFolderId.value?.let { repository.loadFolder(token, it) } }
    private fun guessMimeType(name: String): String? = when (name.substringAfterLast('.', "").lowercase()) { "jpg", "jpeg" -> "image/jpeg"; "png" -> "image/png"; "gif" -> "image/gif"; "webp" -> "image/webp"; "bmp" -> "image/bmp"; "svg" -> "image/svg+xml"; "mp4" -> "video/mp4"; "mkv" -> "video/x-matroska"; "webm" -> "video/webm"; "mov" -> "video/quicktime"; "avi" -> "video/x-msvideo"; else -> null }
    private suspend fun ensureDeviceAndSync(token: String) { val deviceId = sessionStore.deviceId ?: repository.registerDevice(token, "${Build.MANUFACTURER} ${Build.MODEL}", "Android ${Build.VERSION.RELEASE}").also { sessionStore.deviceId = it.id }.id; val nextCursor = repository.sync(token, deviceId, sessionStore.cursor); sessionStore.cursor = nextCursor }
    override fun onCleared() { closePreview(); super.onCleared() }
    companion object { fun factory(repository: CloudRepository, sessionStore: SessionStore, context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory { @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = DarkiCloudViewModel(repository, sessionStore, context.applicationContext) as T } }
}

data class SearchResults(val folders: List<FolderEntity>, val files: List<FileEntity>)
