package com.darki.cloud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.darki.cloud.data.api.DarkiCloudApiException
import com.darki.cloud.data.local.FileEntity
import com.darki.cloud.data.local.FolderEntity
import com.darki.cloud.data.local.SessionStore
import com.darki.cloud.data.repository.CloudRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

class DarkiCloudViewModel(
    private val repository: CloudRepository,
    private val sessionStore: SessionStore,
) : ViewModel() {
    private val _isAuthenticated = MutableStateFlow(sessionStore.token != null)
    val isAuthenticated: Flow<Boolean> = _isAuthenticated

    private val root: Flow<FolderEntity?> =
        repository.observeFolders(null).flatMapLatest { roots -> flowOf(roots.firstOrNull()) }

    val folders: Flow<List<FolderEntity>> = root.flatMapLatest { rootFolder ->
        rootFolder?.let { repository.observeFolders(it.id) } ?: flowOf(emptyList())
    }

    val files: Flow<List<FileEntity>> = root.flatMapLatest { rootFolder ->
        rootFolder?.let { repository.observeFiles(it.id) } ?: flowOf(emptyList())
    }

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: Flow<Boolean> = _isRefreshing

    private val _error = MutableStateFlow<String?>(null)
    val error: Flow<String?> = _error

    init { refreshRoot() }

    fun completeTelegramLogin(code: String) {
        viewModelScope.launch {
            _error.value = null
            runCatching {
                val response = repository.exchangeTelegramLogin(code)
                val token = response.getString("token")
                sessionStore.token = token
                sessionStore.userId = response.getJSONObject("user").getString("id")
                val rootFolder = repository.loadRoot(token)
                sessionStore.rootFolderId = rootFolder.id
                _isAuthenticated.value = true
            }.onFailure { _error.value = it.message ?: "Unable to complete Telegram login" }
        }
    }

    fun logout() {
        val token = sessionStore.token
        viewModelScope.launch {
            runCatching { repository.logout(token) }
                .onFailure { _error.value = it.message ?: "Unable to contact server during logout" }
            sessionStore.clear()
            _isAuthenticated.value = false
        }
    }

    fun refreshRoot() {
        val token = sessionStore.token ?: return
        viewModelScope.launch {
            _isRefreshing.value = true
            _error.value = null
            runCatching { repository.loadRoot(token) }
                .onFailure { error ->
                    if (error is DarkiCloudApiException && error.statusCode == 401) {
                        sessionStore.clear()
                        _isAuthenticated.value = false
                    }
                    _error.value = error.message ?: "Unable to refresh cloud data"
                }
            _isRefreshing.value = false
        }
    }

    companion object {
        fun factory(repository: CloudRepository, sessionStore: SessionStore): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    DarkiCloudViewModel(repository, sessionStore) as T
            }
    }
}
