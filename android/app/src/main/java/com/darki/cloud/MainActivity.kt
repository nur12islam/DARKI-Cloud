package com.darki.cloud

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.darki.cloud.data.api.DarkiCloudApi
import com.darki.cloud.data.local.CloudDatabase
import com.darki.cloud.data.local.SessionStore
import com.darki.cloud.data.repository.CloudRepository
import okhttp3.OkHttpClient

class MainActivity : ComponentActivity() {
    private lateinit var sessionStore: SessionStore
    private lateinit var repository: CloudRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sessionStore = SessionStore(applicationContext)
        val database = CloudDatabase.create(applicationContext)
        repository = CloudRepository(
            api = DarkiCloudApi(BuildConfig.DARKI_CLOUD_BASE_URL, OkHttpClient()),
            dao = database.cloudDao(),
        )

        setContent {
            val driveViewModel: DarkiCloudViewModel = viewModel(
                factory = DarkiCloudViewModel.factory(repository, sessionStore),
            )
            DarkiCloudApp(driveViewModel)
        }
    }
}

@androidx.compose.runtime.Composable
private fun DarkiCloudApp(viewModel: DarkiCloudViewModel) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF050505)) {
            val folders by viewModel.folders.collectAsState(initial = emptyList())
            val files by viewModel.files.collectAsState(initial = emptyList())
            val refreshing by viewModel.isRefreshing.collectAsState(initial = false)
            val error by viewModel.error.collectAsState(initial = null)

            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    DriveTopBar(refreshing = refreshing, onRefresh = viewModel::refreshRoot)
                    DriveContent(folders = folders, files = files, error = error)
                }

                FloatingActionButton(
                    onClick = { },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
                    containerColor = Color(0xFF171717),
                    contentColor = Color(0xFFB7F7FF),
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun DriveTopBar(refreshing: Boolean, onRefresh: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Cloud, null, tint = Color(0xFFB7F7FF), modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("DARKI Cloud", fontWeight = FontWeight.SemiBold)
                Text("My Drive", style = MaterialTheme.typography.labelMedium, color = Color(0xFF858585))
            }
            IconButton(onClick = onRefresh, enabled = !refreshing) {
                if (refreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
            }
            IconButton(onClick = { }) { Icon(Icons.Default.Search, contentDescription = "Search") }
            IconButton(onClick = { }) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
        }

        Spacer(Modifier.height(18.dp))
        Box(
            modifier = Modifier.fillMaxWidth().height(2.dp).background(
                Brush.horizontalGradient(listOf(Color.Transparent, Color(0xFF3A6D76), Color.Transparent)),
            ),
        )
    }
}

@androidx.compose.runtime.Composable
private fun DriveContent(
    folders: List<com.darki.cloud.data.local.FolderEntity>,
    files: List<com.darki.cloud.data.local.FileEntity>,
    error: String?,
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text("My Drive", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Your private cloud storage",
            color = Color(0xFF858585),
            modifier = Modifier.padding(top = 4.dp, bottom = 18.dp),
        )

        if (error != null) {
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF171111)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.ErrorOutline, null, tint = Color(0xFFFFB4AB), modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(error, color = Color(0xFFFFB4AB), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(12.dp))
        }

        if (folders.isEmpty() && files.isEmpty() && error == null) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 80.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Default.Cloud, null, tint = Color(0xFF4D5B5E), modifier = Modifier.size(52.dp))
                Spacer(Modifier.height(14.dp))
                Text("Your drive is empty", color = Color(0xFF9A9A9A), fontWeight = FontWeight.Medium)
                Text("Upload a file or create a folder to get started", color = Color(0xFF666666), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }
            return
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 100.dp),
        ) {
            items(folders, key = { it.id }) { folder ->
                DriveItemRow(name = folder.name, subtitle = "Folder", isFolder = true)
            }
            items(files, key = { it.id }) { file ->
                DriveItemRow(name = file.name, subtitle = formatSize(file.sizeBytes), isFolder = false)
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun DriveItemRow(name: String, subtitle: String, isFolder: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFF101010)).padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (isFolder) Icons.Default.Folder else Icons.Default.Description,
            null,
            tint = if (isFolder) Color(0xFFB7F7FF) else Color(0xFFB0B0B0),
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Color(0xFF777777), modifier = Modifier.padding(top = 3.dp))
        }
    }
}

private fun formatSize(bytes: Long?): String {
    if (bytes == null) return "File"
    if (bytes < 1024) return "$bytes B"
    if (bytes < 1024 * 1024) return "%.1f KB".format(bytes / 1024.0)
    if (bytes < 1024 * 1024 * 1024) return "%.1f MB".format(bytes / (1024.0 * 1024.0))
    return "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
}
