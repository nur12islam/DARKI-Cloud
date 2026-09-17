package com.darki.cloud

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.darki.cloud.data.api.DarkiCloudApi
import com.darki.cloud.data.local.CloudDatabase
import com.darki.cloud.data.local.SessionStore
import com.darki.cloud.data.local.FileEntity
import com.darki.cloud.data.local.FolderEntity
import com.darki.cloud.data.repository.CloudRepository
import okhttp3.OkHttpClient

class MainActivity : ComponentActivity() {
    private lateinit var sessionStore: SessionStore
    private lateinit var repository: CloudRepository
    private lateinit var api: DarkiCloudApi
    private var pendingAuthCode: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionStore = SessionStore(applicationContext)
        val database = CloudDatabase.create(applicationContext)
        api = DarkiCloudApi(BuildConfig.DARKI_CLOUD_BASE_URL, OkHttpClient())
        repository = CloudRepository(api = api, dao = database.cloudDao())
        pendingAuthCode = extractAuthCode(intent)
        render()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingAuthCode = extractAuthCode(intent)
        render()
    }

    private fun render() {
        setContent {
            val driveViewModel: DarkiCloudViewModel = viewModel(factory = DarkiCloudViewModel.factory(repository, sessionStore))
            DarkiCloudApp(driveViewModel, pendingAuthCode, ::openTelegramLogin)
        }
    }

    private fun openTelegramLogin() = startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(api.telegramLoginUrl())))

    private fun extractAuthCode(intent: Intent?): String? {
        val uri = intent?.data ?: return null
        if (uri.scheme != "darkicloud" || uri.host != "auth") return null
        return uri.getQueryParameter("code")?.takeIf { it.isNotBlank() }
    }
}

@Composable
private fun DarkiCloudApp(viewModel: DarkiCloudViewModel, authCode: String?, onLogin: () -> Unit) {
    LaunchedEffect(authCode) { if (authCode != null) viewModel.completeTelegramLogin(authCode) }
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF050505)) {
            val folders by viewModel.folders.collectAsState(initial = emptyList())
            val files by viewModel.files.collectAsState(initial = emptyList())
            val refreshing by viewModel.isRefreshing.collectAsState(initial = false)
            val error by viewModel.error.collectAsState(initial = null)
            val authenticated by viewModel.isAuthenticated.collectAsState(initial = false)
            val stack by viewModel.folderStack.collectAsState(initial = emptyList())
            var showCreateFolder by remember { mutableStateOf(false) }

            if (authenticated && stack.isNotEmpty()) BackHandler { viewModel.navigateBack() }
            if (showCreateFolder) {
                CreateFolderDialog(
                    onDismiss = { showCreateFolder = false },
                    onCreate = { name -> showCreateFolder = false; viewModel.createFolder(name) },
                )
            }

            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    DriveTopBar(refreshing, authenticated, stack.isNotEmpty(), viewModel::navigateBack, viewModel::refreshRoot, viewModel::logout, onLogin)
                    if (authenticated) DriveContent(folders, files, error, viewModel::openFolder)
                    else LoginContent(onLogin)
                }
                if (authenticated) {
                    FloatingActionButton(
                        onClick = { showCreateFolder = true },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
                        containerColor = Color(0xFF171717),
                        contentColor = Color(0xFFB7F7FF),
                    ) { Icon(Icons.Default.CreateNewFolder, contentDescription = "New folder") }
                }
            }
        }
    }
}

@Composable
private fun DriveTopBar(
    refreshing: Boolean,
    authenticated: Boolean,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onLogin: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (authenticated && canGoBack) IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Icon(Icons.Default.Cloud, null, tint = Color(0xFFB7F7FF), modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("DARKI Cloud", fontWeight = FontWeight.SemiBold)
                Text(if (authenticated) "My Drive" else "Private cloud storage", style = MaterialTheme.typography.labelMedium, color = Color(0xFF858585))
            }
            if (authenticated) {
                IconButton(onClick = onRefresh, enabled = !refreshing) { if (refreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Refresh, "Refresh") }
                IconButton(onClick = { }) { Icon(Icons.Default.Search, "Search") }
                IconButton(onClick = onLogout) { Icon(Icons.Default.Logout, "Log out") }
            } else IconButton(onClick = onLogin) { Icon(Icons.Default.Login, "Sign in") }
        }
        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth().height(2.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, Color(0xFF3A6D76), Color.Transparent))))
    }
}

@Composable
private fun LoginContent(onLogin: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(120.dp))
        Icon(Icons.Default.Cloud, null, tint = Color(0xFFB7F7FF), modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(22.dp))
        Text("Your private cloud", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Sign in with Telegram to access your files.", color = Color(0xFF858585), modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(28.dp))
        Button(onClick = onLogin, shape = RoundedCornerShape(16.dp)) { Icon(Icons.Default.Login, null); Spacer(Modifier.width(8.dp)); Text("Continue with Telegram") }
    }
}

@Composable
private fun DriveContent(folders: List<FolderEntity>, files: List<FileEntity>, error: String?, onFolderClick: (FolderEntity) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text("My Drive", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Your private cloud storage", color = Color(0xFF858585), modifier = Modifier.padding(top = 4.dp, bottom = 18.dp))
        if (error != null) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF171111)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ErrorOutline, null, tint = Color(0xFFFFB4AB), modifier = Modifier.size(22.dp)); Spacer(Modifier.width(10.dp)); Text(error, color = Color(0xFFFFB4AB), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(12.dp))
        }
        if (folders.isEmpty() && files.isEmpty() && error == null) {
            Column(Modifier.fillMaxWidth().padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Cloud, null, tint = Color(0xFF4D5B5E), modifier = Modifier.size(52.dp)); Spacer(Modifier.height(14.dp)); Text("Your drive is empty", color = Color(0xFF9A9A9A), fontWeight = FontWeight.Medium); Text("Create a folder or upload a file to get started", color = Color(0xFF666666), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }
            return
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 100.dp)) {
            items(folders, key = { it.id }) { folder -> DriveItemRow(folder.name, "Folder", true) { onFolderClick(folder) } }
            items(files, key = { it.id }) { file -> DriveItemRow(file.name, formatSize(file.sizeBytes), false) {} }
        }
    }
}

@Composable
private fun DriveItemRow(name: String, subtitle: String, isFolder: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFF101010)).padding(horizontal = 16.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClick) { Icon(if (isFolder) Icons.Default.Folder else Icons.Default.Description, null, tint = if (isFolder) Color(0xFFB7F7FF) else Color(0xFFB0B0B0), modifier = Modifier.size(28.dp)) }
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) { Text(name, fontWeight = FontWeight.Medium); Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Color(0xFF777777), modifier = Modifier.padding(top = 3.dp)) }
    }
}

@Composable
private fun CreateFolderDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New folder") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Folder name") }) },
        confirmButton = { TextButton(onClick = { if (name.trim().isNotEmpty()) onCreate(name.trim()) }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun formatSize(bytes: Long?): String {
    if (bytes == null) return "File"
    if (bytes < 1024) return "$bytes B"
    if (bytes < 1024 * 1024) return "%.1f KB".format(bytes / 1024.0)
    if (bytes < 1024 * 1024 * 1024) return "%.1f MB".format(bytes / (1024.0 * 1024.0))
    return "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
}
