package com.darki.cloud

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.darki.cloud.data.api.DarkiCloudApi
import com.darki.cloud.data.local.CloudDatabase
import com.darki.cloud.data.local.SessionStore
import com.darki.cloud.data.repository.CloudRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

class SearchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = SessionStore(applicationContext)
        val db = CloudDatabase.create(applicationContext)
        val repository = CloudRepository(DarkiCloudApi(BuildConfig.DARKI_CLOUD_BASE_URL, OkHttpClient()), db.cloudDao())
        setContent { SearchScreen(store, repository, ::finish, ::selectResult) }
    }

    private fun selectResult(folderId: String?, fileId: String?) {
        setResult(RESULT_OK, Intent().apply {
            folderId?.let { putExtra("folder_id", it) }
            fileId?.let { putExtra("file_id", it) }
        })
        finish()
    }
}

@Composable
private fun SearchScreen(
    store: SessionStore,
    repository: CloudRepository,
    onBack: () -> Unit,
    onSelect: (String?, String?) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<SearchResults?>(null) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(query) {
        val token = store.token ?: return@LaunchedEffect
        val q = query.trim()
        if (q.isEmpty()) { results = null; error = null; return@LaunchedEffect }
        searching = true
        error = null
        withContext(Dispatchers.IO) {
            runCatching { repository.search(token, q) }
                .onSuccess { results = it }
                .onFailure { error = it.message ?: "Unable to search cloud" }
        }
        searching = false
    }

    Surface(Modifier.fillMaxSize(), color = Color(0xFF050505)) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("Search files and folders") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                )
            }
            Spacer(Modifier.height(20.dp))
            when {
                searching -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                error != null -> Text(error!!, color = Color(0xFFFFB4AB))
                results == null -> Text("Search your private cloud", color = Color(0xFF858585))
                results!!.folders.isEmpty() && results!!.files.isEmpty() -> Text("No matching files or folders", color = Color(0xFF858585))
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(results!!.folders, key = { it.id }) { folder ->
                        SearchResultRow(folder.name, "Folder", true) { onSelect(folder.id, null) }
                    }
                    items(results!!.files, key = { it.id }) { file ->
                        SearchResultRow(file.name, file.mimeType ?: "File", false) { onSelect(null, file.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    name: String,
    subtitle: String,
    folder: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(if (folder) Icons.Default.Folder else Icons.Default.Description, null, tint = Color(0xFFB7F7FF))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Color(0xFF777777))
        }
        Icon(Icons.Default.ChevronRight, "Open", tint = Color(0xFF777777))
    }
}
