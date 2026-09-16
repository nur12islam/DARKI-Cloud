package com.darki.cloud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.darki.cloud.data.local.FileEntity
import com.darki.cloud.data.local.FolderEntity
import com.darki.cloud.data.local.SessionStore
import com.darki.cloud.data.repository.CloudRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

class DarkiCloudViewModel(
    private val repository: CloudRepository,
    private val sessionStore: SessionStore,
) : ViewModel() {
    private val root: Flow<FolderEntity?> =
        repository.observeFolders(null).flatMapLatest { roots -> flowOf(roots.firstOrNull()) }

    val folders: Flow<List<FolderEntity>> = root.flatMapLatest { rootFolder ->
        rootFolder?.let { repository.observeFolders(it.id) } ?: flowOf(emptyList())
    }

    val files: Flow<List<FileEntity>> = root.flatMapLatest { rootFolder ->
        rootFolder?.let { repository.observeFiles(it.id) } ?: flowOf(emptyList())
    }

    init {
        refreshRoot()
    }

    fun refreshRoot() {
        val token = sessionStore.token ?: return
        viewModelScope.launch {
            runCatching { repository.loadRoot(token) }
        }
    }

    companion object {
        fun factory(
            repository: CloudRepository,
            sessionStore: SessionStore,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                DarkiCloudViewModel(repository, sessionStore) as T
        }
    }
}
