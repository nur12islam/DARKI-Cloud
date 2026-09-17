package com.darki.cloud

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.darki.cloud.data.api.DarkiCloudApi
import com.darki.cloud.data.local.CloudDatabase
import com.darki.cloud.data.local.FileEntity
import com.darki.cloud.data.local.FolderEntity
import com.darki.cloud.data.local.SessionStore
import com.darki.cloud.data.repository.CloudRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

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
            DarkiCloudApp(driveViewModel, api, pendingAuthCode, ::openTelegramLogin)
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
private fun DarkiCloudApp(viewModel: DarkiCloudViewModel, api: DarkiCloudApi, authCode: String?, onLogin: () -> Unit) {
    LaunchedEffect(authCode) { if (authCode != null) viewModel.completeTelegramLogin(authCode) }
    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF050505)) {
            val folders by viewModel.folders.collectAsState(initial = emptyList())
            val files by viewModel.files.collectAsState(initial = emptyList())
            val refreshing by viewModel.isRefreshing.collectAsState(initial = false)
            val uploading by viewModel.isUploading.collectAsState(initial = false)
            val downloading by viewModel.isDownloading.collectAsState(initial = false)
            val previewFileId by viewModel.previewFileId.collectAsState(initial = null)
            val previewMimeType by viewModel.previewMimeType.collectAsState(initial = null)
            val previewName by viewModel.previewName.collectAsState(initial = null)
            val error by viewModel.error.collectAsState(initial = null)
            val authenticated by viewModel.isAuthenticated.collectAsState(initial = false)
            val stack by viewModel.folderStack.collectAsState(initial = emptyList())
            val token = viewModel.previewToken()
            val context = LocalContext.current
            var showCreateFolder by remember { mutableStateOf(false) }
            val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) viewModel.uploadFile(uri, context.contentResolver) }

            if (authenticated && stack.isNotEmpty()) BackHandler { viewModel.navigateBack() }
            if (previewFileId != null && previewMimeType != null) MediaPreviewDialog(api, token, previewFileId!!, previewMimeType!!, previewName ?: "Preview", viewModel::closePreview)
            if (showCreateFolder) CreateFolderDialog({ showCreateFolder = false }) { name -> showCreateFolder = false; viewModel.createFolder(name) }

            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    DriveTopBar(refreshing, authenticated, stack.isNotEmpty(), viewModel::navigateBack, viewModel::refreshRoot, viewModel::logout, onLogin)
                    if (authenticated) DriveContent(folders, files, error, token, viewModel::openFolder, viewModel::previewFile)
                    else LoginContent(onLogin)
                }
                if (authenticated) {
                    FloatingActionButton(onClick = { picker.launch(arrayOf("*/*")) }, Modifier.align(Alignment.BottomEnd).padding(24.dp), containerColor = Color(0xFF171717), contentColor = Color(0xFFB7F7FF)) { if (uploading) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) else Icon(Icons.Default.UploadFile, "Upload file") }
                    if (!uploading) FloatingActionButton(onClick = { showCreateFolder = true }, Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 96.dp), containerColor = Color(0xFF171717), contentColor = Color(0xFFB7F7FF)) { Icon(Icons.Default.CreateNewFolder, "New folder") }
                }
            }
        }
    }
}

@Composable
private fun MediaPreviewDialog(api: DarkiCloudApi, token: String?, fileId: String, mimeType: String, name: String, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Row(verticalAlignment = Alignment.CenterVertically) { Text(name, Modifier.weight(1f), maxLines = 1); IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") } } }, text = {
        if (token == null) Text("Your session has expired. Please sign in again.") else when {
            mimeType.startsWith("video/") -> VideoPreview(api, token, fileId)
            mimeType.startsWith("image/") -> SafeImagePreview(api, token, fileId)
            else -> Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Description, null, Modifier.size(56.dp), tint = Color(0xFFB7F7FF)); Spacer(Modifier.height(12.dp)); Text("Preview is not available for this file type.") }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}

@Composable
private fun SafeImagePreview(api: DarkiCloudApi, token: String, fileId: String) {
    val context = LocalContext.current
    var bitmap by remember(fileId, token) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var loading by remember(fileId, token) { mutableStateOf(true) }
    var failed by remember(fileId, token) { mutableStateOf(false) }
    LaunchedEffect(fileId, token) {
        loading = true; failed = false; bitmap = null
        try {
            bitmap = withContext(Dispatchers.IO) {
                val temp = File.createTempFile("darki-image-", ".preview", context.cacheDir)
                try {
                    OkHttpClient().newCall(Request.Builder().url(api.fileContentUrl(fileId)).header("Authorization", "Bearer $token").build()).execute().use { response ->
                        if (!response.isSuccessful) error("Image request failed")
                        response.body?.byteStream()?.use { input -> temp.outputStream().use { output -> input.copyTo(output) } } ?: error("Empty image response")
                    }
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(temp.absolutePath, bounds)
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) error("Invalid image")
                    var sample = 1
                    while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
                    BitmapFactory.decodeFile(temp.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = android.graphics.Bitmap.Config.RGB_565 }) ?: error("Unable to decode image")
                } finally { temp.delete() }
            }
        } catch (_: Throwable) { failed = true }
        loading = false
    }
    when { loading -> Box(Modifier.fillMaxWidth().height(360.dp), Alignment.Center) { CircularProgressIndicator() }; bitmap != null -> Image(bitmap!!.asImageBitmap(), null, Modifier.fillMaxWidth().height(420.dp), contentScale = ContentScale.Fit); failed -> Text("Unable to preview this image. Try downloading it instead.") }
}

@Composable
private fun VideoPreview(api: DarkiCloudApi, token: String, fileId: String) {
    val context = LocalContext.current
    val player = remember(api, token, fileId) {
        val httpFactory = DefaultHttpDataSource.Factory().setDefaultRequestProperties(mapOf("Authorization" to "Bearer $token"))
        ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, httpFactory))).build().apply { setMediaItem(MediaItem.fromUri(api.fileContentUrl(fileId))); prepare(); playWhenReady = true }
    }
    DisposableEffect(player) { onDispose { player.stop(); player.release() } }
    AndroidView(factory = { PlayerView(it).apply { player = player; useController = true; layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) } }, Modifier.fillMaxWidth().height(300.dp))
}

@Composable
private fun DriveTopBar(refreshing: Boolean, authenticated: Boolean, canGoBack: Boolean, onBack: () -> Unit, onRefresh: () -> Unit, onLogout: () -> Unit, onLogin: () -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { if (authenticated && canGoBack) IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }; Icon(Icons.Default.Cloud, null, tint = Color(0xFFB7F7FF), Modifier.size(30.dp)); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("DARKI Cloud", fontWeight = FontWeight.SemiBold); Text(if (authenticated) "My Drive" else "Private cloud storage", style = MaterialTheme.typography.labelMedium, color = Color(0xFF858585)) }; if (authenticated) { IconButton(onClick = onRefresh, enabled = !refreshing) { if (refreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Refresh, "Refresh") }; IconButton(onClick = { }) { Icon(Icons.Default.Search, "Search") }; IconButton(onClick = onLogout) { Icon(Icons.Default.Logout, "Log out") } } else IconButton(onClick = onLogin) { Icon(Icons.Default.Login, "Sign in") } }; Spacer(Modifier.height(18.dp)); Box(Modifier.fillMaxWidth().height(2.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, Color(0xFF3A6D76), Color.Transparent)))) }
}

@Composable
private fun LoginContent(onLogin: () -> Unit) { Column(Modifier.fillMaxSize().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) { Spacer(Modifier.height(120.dp)); Icon(Icons.Default.Cloud, null, tint = Color(0xFFB7F7FF), Modifier.size(72.dp)); Spacer(Modifier.height(22.dp)); Text("Your private cloud", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("Sign in with Telegram to access your files.", color = Color(0xFF858585), Modifier.padding(top = 8.dp)); Spacer(Modifier.height(28.dp)); Button(onClick = onLogin, shape = RoundedCornerShape(16.dp)) { Icon(Icons.Default.Login, null); Spacer(Modifier.width(8.dp)); Text("Continue with Telegram") } } }

@Composable
private fun DriveContent(folders: List<FolderEntity>, files: List<FileEntity>, error: String?, token: String?, onFolderClick: (FolderEntity) -> Unit, onFileClick: (FileEntity) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text("My Drive", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("Your private cloud storage", color = Color(0xFF858585), Modifier.padding(top = 4.dp, bottom = 18.dp))
        if (error != null) { Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF171111)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.ErrorOutline, null, tint = Color(0xFFFFB4AB), Modifier.size(22.dp)); Spacer(Modifier.width(10.dp)); Text(error, color = Color(0xFFFFB4AB), style = MaterialTheme.typography.bodySmall) }; Spacer(Modifier.height(12.dp)) }
        if (folders.isEmpty() && files.isEmpty() && error == null) { Column(Modifier.fillMaxWidth().padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Cloud, null, tint = Color(0xFF4D5B5E), Modifier.size(52.dp)); Spacer(Modifier.height(14.dp)); Text("Your drive is empty", color = Color(0xFF9A9A9A), fontWeight = FontWeight.Medium); Text("Create a folder or upload a file to get started", color = Color(0xFF666666), style = MaterialTheme.typography.bodySmall, Modifier.padding(top = 6.dp)) }; return }
        LazyVerticalGrid(columns = GridCells.Adaptive(150.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 120.dp)) {
            items(folders, key = { it.id }) { folder -> DriveGridItem(folder.name, "Folder", true, null, token, onFolderClick = { onFolderClick(folder) }) }
            items(files, key = { it.id }) { file -> DriveGridItem(file.name, formatSize(file.sizeBytes), false, file, token, onFileClick = { onFileClick(file) }) }
        }
    }
}

@Composable
private fun DriveGridItem(name: String, subtitle: String, isFolder: Boolean, file: FileEntity?, token: String?, onFolderClick: (() -> Unit)? = null, onFileClick: (() -> Unit)? = null) {
    val mime = file?.mimeType ?: guessMime(file?.name)
    val click = onFolderClick ?: onFileClick ?: {}
    Column(Modifier.clip(RoundedCornerShape(18.dp)).background(Color(0xFF101010)).clickable(onClick = click).padding(10.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.15f).clip(RoundedCornerShape(14.dp)).background(Color(0xFF171717)), Alignment.Center) {
            when {
                isFolder -> Icon(Icons.Default.Folder, null, tint = Color(0xFFB7F7FF), Modifier.size(48.dp))
                mime?.startsWith("image/") == true && token != null -> AuthenticatedThumbnail(file!!.id, file.mimeType ?: guessMime(file.name), token)
                mime?.startsWith("video/") == true -> { Icon(Icons.Default.PlayCircle, null, tint = Color(0xFF9A9A9A), Modifier.size(50.dp)); Icon(Icons.Default.PlayArrow, null, tint = Color.White, Modifier.size(22.dp)) }
                else -> Icon(Icons.Default.Description, null, tint = Color(0xFF9A9A9A), Modifier.size(48.dp))
            }
        }
        Spacer(Modifier.height(9.dp)); Text(name, maxLines = 1, fontWeight = FontWeight.Medium); Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Color(0xFF777777), Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun AuthenticatedThumbnail(fileId: String, mimeType: String?, token: String) {
    val context = LocalContext.current
    var bitmap by remember(fileId, token) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(fileId, token) {
        withContext(Dispatchers.IO) {
            runCatching {
                val temp = File.createTempFile("darki-thumb-", ".img", context.cacheDir)
                try {
                    OkHttpClient().newCall(Request.Builder().url(BuildConfig.DARKI_CLOUD_BASE_URL.trimEnd('/') + "/api/v1/files/$fileId/content").header("Authorization", "Bearer $token").build()).execute().use { response ->
                        if (!response.isSuccessful) return@runCatching
                        response.body?.byteStream()?.use { input -> temp.outputStream().use { output -> input.copyTo(output) } } ?: return@runCatching
                    }
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(temp.absolutePath, bounds)
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching
                    var sample = 1; while (bounds.outWidth / sample > 512 || bounds.outHeight / sample > 512) sample *= 2
                    bitmap = BitmapFactory.decodeFile(temp.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = android.graphics.Bitmap.Config.RGB_565 })
                } finally { temp.delete() }
            }
        }
    }
    if (bitmap != null) Image(bitmap!!.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else Icon(Icons.Default.Image, null, tint = Color(0xFFB7F7FF), Modifier.size(44.dp))
}

private fun guessMime(name: String?): String? = when (name?.substringAfterLast('.', "")?.lowercase()) { "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg" -> "image/*"; "mp4", "mkv", "webm", "mov", "avi" -> "video/*"; else -> null }

@Composable
private fun CreateFolderDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) { var name by remember { mutableStateOf("") }; AlertDialog(onDismissRequest = onDismiss, title = { Text("New folder") }, text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Folder name") }) }, confirmButton = { TextButton(onClick = { if (name.trim().isNotEmpty()) onCreate(name.trim()) }) { Text("Create") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }) }

private fun formatSize(bytes: Long?): String { if (bytes == null) return "File"; if (bytes < 1024) return "$bytes B"; if (bytes < 1024 * 1024) return "%.1f KB".format(bytes / 1024.0); if (bytes < 1024 * 1024 * 1024) return "%.1f MB".format(bytes / (1024.0 * 1024.0)); return "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0)) }
