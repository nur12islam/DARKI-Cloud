package com.darki.cloud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.darki.cloud.data.local.CloudDatabase
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
    val folders: Flow<List<FolderEntity>> = repository.observeFolders(null)
    val files: Flow<List<FileEntity>> = folders.flatMapLatest { roots ->
        roots.firstOrNull()?.let(repository::observeFiles) ?: flowOf(emptyList())
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
