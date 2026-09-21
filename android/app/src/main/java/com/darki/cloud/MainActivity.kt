package com.darki.cloud

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.content.ActivityNotFoundException
import androidx.core.content.FileProvider
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.darki.cloud.data.sync.SyncScheduler
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
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); SyncScheduler.ensureScheduled(applicationContext); sessionStore = SessionStore(applicationContext); val db = CloudDatabase.create(applicationContext); api = DarkiCloudApi(BuildConfig.DARKI_CLOUD_BASE_URL, OkHttpClient()); repository = CloudRepository(api, db.cloudDao()); pendingAuthCode = extractAuthCode(intent); render() }
    override fun onStart() { super.onStart(); if (sessionStore.token != null) SyncScheduler.enqueueNow(applicationContext) }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); pendingAuthCode = extractAuthCode(intent); render() }
    private fun render() { setContent { val vm: DarkiCloudViewModel = viewModel(factory = DarkiCloudViewModel.factory(repository, sessionStore, applicationContext)); DarkiCloudApp(vm, api, pendingAuthCode, ::openTelegramLogin) } }
    private fun openTelegramLogin() = startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(api.telegramLoginUrl())))
    private fun extractAuthCode(intent: Intent?): String? { val uri = intent?.data ?: return null; if (uri.scheme != "darkicloud" || uri.host != "auth") return null; return uri.getQueryParameter("code")?.takeIf { it.isNotBlank() } }
}

@Composable private fun DarkiCloudApp(vm: DarkiCloudViewModel, api: DarkiCloudApi, authCode: String?, onLogin: () -> Unit) {
    LaunchedEffect(authCode) { if (authCode != null) vm.completeTelegramLogin(authCode) }
    MaterialTheme { Surface(Modifier.fillMaxSize(), color = Color(0xFF050505)) {
        val folders by vm.folders.collectAsState(initial = emptyList()); val allFolders by vm.allFolders.collectAsState(initial = emptyList()); val files by vm.files.collectAsState(initial = emptyList()); val deleted by vm.deletedFiles.collectAsState(initial = emptyList()); val transfers by vm.transfers.collectAsState(initial = emptyList()); val refreshing by vm.isRefreshing.collectAsState(initial = false); val uploading by vm.isUploading.collectAsState(initial = false); val error by vm.error.collectAsState(initial = null); val authenticated by vm.isAuthenticated.collectAsState(initial = false); val stack by vm.folderStack.collectAsState(initial = emptyList()); val previewId by vm.previewFileId.collectAsState(initial = null); val previewMime by vm.previewMimeType.collectAsState(initial = null); val previewName by vm.previewName.collectAsState(initial = null); val token = vm.previewToken(); val context = LocalContext.current
        var createFolder by remember { mutableStateOf(false) }; var management by remember { mutableStateOf<ManagementTarget?>(null) }; var rename by remember { mutableStateOf<ManagementTarget?>(null) }; var move by remember { mutableStateOf<ManagementTarget?>(null) }; var delete by remember { mutableStateOf<FileEntity?>(null) }; var restore by remember { mutableStateOf<FileEntity?>(null) }; var trash by remember { mutableStateOf(false) }; var emptyTrash by remember { mutableStateOf(false) }; var permanentDelete by remember { mutableStateOf<FileEntity?>(null) }; var showTransfers by remember { mutableStateOf(false) }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { uri -> vm.uploadFile(uri, context.contentResolver) } }
        val searchLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult -> if (result.resultCode == android.app.Activity.RESULT_OK) { val data = result.data; data?.getStringExtra("folder_id")?.let(vm::openFolderById); data?.getStringExtra("file_id")?.let(vm::previewFileById) } }
        val savePicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> if (uri != null && management is ManagementTarget.FileTarget) { val file = (management as ManagementTarget.FileTarget).file; vm.enqueueDownload(file, uri, context.contentResolver); management = null } }
        if (authenticated && stack.isNotEmpty() && !trash) BackHandler { vm.navigateBack() }
        if (previewId != null && previewMime != null) MediaPreviewDialog(api, token, previewId!!, previewMime!!, previewName ?: "Preview", vm::closePreview)
        if (showTransfers) TransferDialog(transfers, vm::retryTransfer, vm::cancelTransfer) { showTransfers = false }
        if (createFolder) CreateFolderDialog({ createFolder = false }) { createFolder = false; vm.createFolder(it) }
        if (rename != null) { val target = rename!!; RenameDialog(if (target is ManagementTarget.FolderTarget) "Rename folder" else "Rename file", target.name, { rename = null }) { value -> rename = null; when (target) { is ManagementTarget.FolderTarget -> vm.renameFolder(target.folder, value); is ManagementTarget.FileTarget -> vm.renameFile(target.file, value) } } }
        if (move != null) { val target = move!!; val currentParentId = when (target) { is ManagementTarget.FolderTarget -> target.folder.id; is ManagementTarget.FileTarget -> target.file.folderId }; MoveDialog(if (target is ManagementTarget.FolderTarget) "Move folder" else "Move file", allFolders, currentParentId, { move = null }) { destination -> move = null; when (target) { is ManagementTarget.FolderTarget -> vm.moveFolder(target.folder, destination); is ManagementTarget.FileTarget -> vm.moveFile(target.file, destination) } } }
        delete?.let { file -> DeleteDialog(file, { delete = null }) { delete = null; vm.deleteFile(file) } }
        restore?.let { file -> RestoreDialog(file, { restore = null }) { restore = null; vm.restoreFile(file) } }
        permanentDelete?.let { file -> PermanentDeleteDialog(file, { permanentDelete = null }) { permanentDelete = null; vm.permanentlyDeleteFile(file) } }
        if (emptyTrash) ConfirmEmptyTrashDialog({ emptyTrash = false }) { emptyTrash = false; vm.emptyTrash() }
        if (management != null) { val target = management!!; ManagementMenu(target, onDismiss = { management = null }, onRename = { management = null; rename = target }, onMove = { management = null; move = target }, onDelete = { management = null; if (target is ManagementTarget.FileTarget) delete = target.file }, onRestore = { management = null; if (target is ManagementTarget.FileTarget) restore = target.file }, onDownload = { if (target is ManagementTarget.FileTarget) { management = null; savePicker.launch(target.file.name) } }) }
        Box(Modifier.fillMaxSize()) { Column(Modifier.fillMaxSize()) {
            DriveTopBar(refreshing, authenticated, stack.isNotEmpty() && !trash, vm::navigateBack, vm::refreshRoot, vm::logout, onLogin, onTrash = { trash = true }, showTrash = authenticated && !trash, transferCount = transfers.count { it.status != "completed" }, onTransfers = { showTransfers = true }, onSearch = { searchLauncher.launch(Intent(context, SearchActivity::class.java)) }, onPhotos = { context.startActivity(Intent(context, PhotosActivity::class.java)) })
            if (authenticated) { if (trash) TrashContent(deleted, management = { management = ManagementTarget.FileTarget(it) }, onRestore = { restore = it }, onPermanentDelete = { permanentDelete = it }, onEmptyTrash = { emptyTrash = true }, onBack = { trash = false }) else DriveContent(folders, files, error, token, vm::openFolder, vm::previewFile) { management = it } } else LoginContent(error = error, onLogin = onLogin)
        }; if (authenticated && !trash) { FloatingActionButton(onClick = { picker.launch(arrayOf("*/*")) }, Modifier.align(Alignment.BottomEnd).padding(24.dp), containerColor = Color(0xFF171717), contentColor = Color(0xFFB7F7FF)) { if (uploading) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) else Icon(Icons.Default.UploadFile, "Upload file") }; if (!uploading) FloatingActionButton(onClick = { createFolder = true }, Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 96.dp), containerColor = Color(0xFF171717), contentColor = Color(0xFFB7F7FF)) { Icon(Icons.Default.CreateNewFolder, "New folder") } } }
    } }
}

private sealed interface ManagementTarget { val name: String; data class FileTarget(val file: FileEntity) : ManagementTarget { override val name get() = file.name }; data class FolderTarget(val folder: FolderEntity) : ManagementTarget { override val name get() = folder.name } }
@Composable private fun ManagementMenu(target: ManagementTarget, onDismiss: () -> Unit, onRename: () -> Unit, onMove: () -> Unit, onDelete: () -> Unit, onRestore: () -> Unit, onDownload: () -> Unit) { AlertDialog(onDismissRequest = onDismiss, title = { Text(target.name, maxLines = 1) }, text = { Column { MenuButton("Rename", Icons.Default.Edit, onRename); MenuButton("Move", Icons.Default.DriveFileMove, onMove); if (target is ManagementTarget.FileTarget) { MenuButton("Download", Icons.Default.Download, onDownload); if (target.file.deletedAt == null) MenuButton("Move to trash", Icons.Default.Delete, onDelete) else MenuButton("Restore", Icons.Default.RestoreFromTrash, onRestore) } } }, confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }) }
@Composable private fun MenuButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) { TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Icon(icon, null); Spacer(Modifier.width(12.dp)); Text(label, Modifier.weight(1f)) } }
@Composable
private fun TrashContent(
    files: List<FileEntity>,
    management: (FileEntity) -> Unit,
    onRestore: (FileEntity) -> Unit,
    onPermanentDelete: (FileEntity) -> Unit,
    onEmptyTrash: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Trash",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text("${files.size} deleted file${if (files.size == 1) "" else "s"}", color = Color(0xFF858585))
            }
            if (files.isNotEmpty()) {
                TextButton(onClick = onEmptyTrash) { Icon(Icons.Default.DeleteForever, null); Spacer(Modifier.width(4.dp)); Text("Empty") }
            }
        }

        if (files.isEmpty()) {
            EmptyState(
                icon = Icons.Default.DeleteSweep,
                title = "Trash is empty"
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                files.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        row.forEach { file ->
                            Box(Modifier.weight(1f)) {
                                DriveGridItem(
                                    name = file.name,
                                    subtitle = "Deleted",
                                    isFolder = false,
                                    file = file,
                                    token = null,
                                    onFileClick = { management(file) },
                                    onManage = { onPermanentDelete(file) }
                                )
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable private fun PermanentDeleteDialog(file: FileEntity, onDismiss: () -> Unit, onConfirm: () -> Unit) { AlertDialog(onDismissRequest = onDismiss, title = { Text("Delete permanently?") }, text = { Text("“${file.name}” will be permanently removed from your cloud storage. This cannot be undone.") }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }, confirmButton = { TextButton(onClick = onConfirm) { Text("Delete forever") } }) }
@Composable private fun ConfirmEmptyTrashDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) { AlertDialog(onDismissRequest = onDismiss, title = { Text("Empty Trash?") }, text = { Text("All deleted files will be permanently removed. This cannot be undone.") }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }, confirmButton = { TextButton(onClick = onConfirm) { Text("Empty Trash") } }) }

@Composable
private fun DriveContent(
    folders: List<FolderEntity>,
    files: List<FileEntity>,
    error: String?,
    token: String?,
    onFolderClick: (FolderEntity) -> Unit,
    onFileClick: (FileEntity) -> Unit,
    onManage: (ManagementTarget) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Text(
            text = "My Drive",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Your private cloud storage",
            color = Color(0xFFB8B8BE),
            modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
        )

        error?.let {
            Text(
                text = it,
                color = Color(0xFFFFB4AB),
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        if (folders.isEmpty() && files.isEmpty() && error == null) {
            EmptyState(
                icon = Icons.Default.Cloud,
                title = "Your drive is empty",
                subtitle = "Create a folder or upload a file to get started"
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                folders.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        row.forEach { folder ->
                            Box(Modifier.weight(1f)) {
                                DriveGridItem(
                                    name = folder.name,
                                    subtitle = "Folder",
                                    isFolder = true,
                                    file = null,
                                    token = token,
                                    onFolderClick = { onFolderClick(folder) },
                                    onManage = {
                                        onManage(ManagementTarget.FolderTarget(folder))
                                    }
                                )
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }

                files.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        row.forEach { file ->
                            Box(Modifier.weight(1f)) {
                                DriveGridItem(
                                    name = file.name,
                                    subtitle = formatSize(file.sizeBytes),
                                    isFolder = false,
                                    file = file,
                                    token = token,
                                    onFileClick = { onFileClick(file) },
                                    onManage = {
                                        onManage(ManagementTarget.FileTarget(file))
                                    }
                                )
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF4D5B5E),
            modifier = Modifier.size(52.dp)
        )
        Spacer(Modifier.height(14.dp))
        Text(title, color = Color(0xFFD0D0D5), fontWeight = FontWeight.Medium)
        subtitle?.let {
            Text(
                text = it,
                color = Color(0xFF9C9CA3),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun DriveGridItem(
    name: String,
    subtitle: String,
    isFolder: Boolean,
    file: FileEntity?,
    token: String?,
    onFolderClick: (() -> Unit)? = null,
    onFileClick: (() -> Unit)? = null,
    onManage: (() -> Unit)? = null
) {
    val mime = file?.mimeType ?: guessMime(file?.name)
    Column(
        modifier = Modifier
             .clip(RoundedCornerShape(22.dp))
            .background(Color(0x14FFFFFF))
            .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(22.dp))
            .clickable { (onFolderClick ?: onFileClick)?.invoke() }
            .padding(10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.15f)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0x0FFFFFFF))
                .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(18.dp))
        ) {
            when {
                isFolder -> {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "Folder",
                        tint = Color(0xFFB7F7FF),
                        modifier = Modifier.align(Alignment.Center).size(34.dp)
                    )
                }
                mime?.startsWith("image/") == true && token != null && file != null -> {
                    AuthenticatedThumbnail(fileId = file.id, token = token)
                }
                mime?.startsWith("video/") == true -> {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = "Video",
                        tint = Color(0xFFB7F7FF),
                        modifier = Modifier.align(Alignment.Center).size(36.dp)
                    )
                }
                else -> {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = "File",
                        tint = Color(0xFF9A9A9A),
                        modifier = Modifier.align(Alignment.Center).size(34.dp)
                    )
                }
            }

            onManage?.let { manage ->
                IconButton(
                    onClick = manage,
                    modifier = Modifier.align(Alignment.TopEnd).size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More",
                        tint = Color.White
                    )
                }
            }
        }

        Spacer(Modifier.height(9.dp))
        Text(text = name, maxLines = 1, fontWeight = FontWeight.SemiBold, color = Color(0xFFF5F5F7), style = MaterialTheme.typography.bodyMedium)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFB0B0B5),
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}

@Composable
private fun AuthenticatedThumbnail(fileId: String, token: String) {
    val context = LocalContext.current
    var bitmap by remember(fileId, token) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(fileId, token) {
        withContext(Dispatchers.IO) {
            runCatching {
                val temp = File.createTempFile("darki-thumb-", ".img", context.cacheDir)
                try {
                    OkHttpClient().newCall(
                        Request.Builder().url(BuildConfig.DARKI_CLOUD_BASE_URL.trimEnd('/') + "/api/v1/files/$fileId/content")
                            .header("Authorization", "Bearer $token").build()
                    ).execute().use { response ->
                        if (!response.isSuccessful) return@runCatching
                        response.body?.byteStream()?.use { input -> temp.outputStream().use { output -> input.copyTo(output) } } ?: return@runCatching
                    }
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(temp.absolutePath, bounds)
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching
                    var sample = 1
                    while (bounds.outWidth / sample > 512 || bounds.outHeight / sample > 512) sample *= 2
                    bitmap = BitmapFactory.decodeFile(temp.absolutePath, BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                    })
                } finally { temp.delete() }
            }
        }
    }
    val image = bitmap
    if (image != null) {
        Image(bitmap = image.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(imageVector = Icons.Default.Image, contentDescription = null, tint = Color(0xFFB7F7FF), modifier = Modifier.size(44.dp))
        }
    }
}

@Composable
private fun MediaPreviewDialog(api: DarkiCloudApi, token: String?, fileId: String, mimeType: String, name: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = name, maxLines = 1) },
        text = {
            when {
                token == null -> Text("Your session has expired.")
                mimeType.startsWith("video/") -> VideoPreview(api, token, fileId)
                mimeType.startsWith("image/") -> SafeImagePreview(api, token, fileId)
                mimeType == "application/pdf" -> PdfPreview(api, token, fileId)
                isDocumentMime(mimeType) -> ExternalDocumentPreview(api, token, fileId, name)
                else -> Text("Preview is not available for this file type.")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

private fun isDocumentMime(mimeType: String): Boolean = mimeType in setOf(
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/msword",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.ms-excel",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "application/vnd.ms-powerpoint",
    "text/plain",
)

@Composable
private fun ExternalDocumentPreview(api: DarkiCloudApi, token: String, fileId: String, name: String) {
    val context = LocalContext.current
    var opening by remember(fileId, token) { mutableStateOf(false) }
    var openRequested by remember(fileId, token) { mutableStateOf(false) }
    if (openRequested) {
        LaunchedExternalOpen(context, api, token, fileId, name) {
            opening = false
            openRequested = false
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
        Icon(Icons.Default.Description, null, tint = Color(0xFFB7F7FF), modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(14.dp))
        Text("Open this document with an app installed on your device.", color = Color(0xFFD5D5DA))
        Spacer(Modifier.height(16.dp))
        Button(enabled = !opening, onClick = {
            opening = true
            openRequested = true
        }) { Text(if (opening) "Preparing…" else "Open with another app") }
    }
}

@Composable
private fun LaunchedExternalOpen(
    context: android.content.Context,
    api: DarkiCloudApi,
    token: String,
    fileId: String,
    name: String,
    onDone: () -> Unit,
) {
    LaunchedEffect(fileId) {
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.cacheDir, "shared").apply { mkdirs() }
                val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val out = File(dir, safeName)
                OkHttpClient().newCall(
                    Request.Builder().url(api.fileContentUrl(fileId))
                        .header("Authorization", "Bearer $token").build()
                ).execute().use { response ->
                    if (!response.isSuccessful) error("Download failed")
                    response.body?.byteStream()?.use { input -> out.outputStream().use { output -> input.copyTo(output) } }
                        ?: error("Empty response")
                }
                val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", out)
                val mime = guessMime(name) ?: "application/octet-stream"
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mime)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                try { context.startActivity(intent) } catch (_: ActivityNotFoundException) { }
            }
        }
        onDone()
    }
}

@Composable
private fun PdfPreview(api: DarkiCloudApi, token: String, fileId: String) {
    val context = LocalContext.current
    var pdfFile by remember(fileId, token) { mutableStateOf<File?>(null) }
    var error by remember(fileId, token) { mutableStateOf<String?>(null) }
    var page by remember(fileId, token) { mutableIntStateOf(0) }
    LaunchedEffect(fileId, token) {
        withContext(Dispatchers.IO) {
            runCatching {
                val file = File.createTempFile("darki-pdf-", ".pdf", context.cacheDir)
                OkHttpClient().newCall(
                    Request.Builder().url(api.fileContentUrl(fileId))
                        .header("Authorization", "Bearer $token").build()
                ).execute().use { response ->
                    if (!response.isSuccessful) error("PDF download failed")
                    response.body?.byteStream()?.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
                        ?: error("Empty PDF response")
                }
                pdfFile = file
            }.onFailure { error = it.message ?: "Unable to open PDF" }
        }
    }
    val file = pdfFile
    if (file == null) {
        Box(Modifier.fillMaxWidth().height(360.dp), contentAlignment = Alignment.Center) {
            if (error == null) CircularProgressIndicator() else Text(error!!)
        }
    } else {
        PdfPage(file, page)
        val renderer = remember(file) {
            runCatching {
                android.graphics.pdf.PdfRenderer(
                    android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                )
            }.getOrNull()
        }
        val count = renderer?.pageCount ?: 0
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(enabled = page > 0, onClick = { page-- }) { Text("Previous") }
            Text(if (count > 0) (page + 1).toString() + " / " + count else "PDF")
            TextButton(enabled = page + 1 < count, onClick = { page++ }) { Text("Next") }
        }
        DisposableEffect(renderer) { onDispose { renderer?.close() } }
    }
}

@Composable
private fun PdfPage(file: File, pageIndex: Int) {
    var bitmap by remember(file, pageIndex) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(file, pageIndex) {
        withContext(Dispatchers.IO) {
            runCatching {
                val descriptor = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = android.graphics.pdf.PdfRenderer(descriptor)
                renderer.openPage(pageIndex).use { page ->
                    val width = 1200
                    val height = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                    val image = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                    image.eraseColor(android.graphics.Color.WHITE)
                    page.render(image, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap = image
                }
                renderer.close()
                descriptor.close()
            }
        }
    }
    bitmap?.let {
        Image(it.asImageBitmap(), null, Modifier.fillMaxWidth().height(420.dp), contentScale = ContentScale.Fit)
    } ?: Box(Modifier.fillMaxWidth().height(420.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun SafeImagePreview(api: DarkiCloudApi, token: String, fileId: String) {
    val context = LocalContext.current
    var bitmap by remember(fileId, token) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var loading by remember(fileId, token) { mutableStateOf(true) }
    LaunchedEffect(fileId, token) {
        loading = true
        withContext(Dispatchers.IO) {
            runCatching {
                val temp = File.createTempFile("darki-image-", ".preview", context.cacheDir)
                try {
                    OkHttpClient().newCall(Request.Builder().url(api.fileContentUrl(fileId)).header("Authorization", "Bearer $token").build())
                        .execute().use { response ->
                            if (!response.isSuccessful) error("Image request failed")
                            response.body?.byteStream()?.use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
                                ?: error("Empty image response")
                        }
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(temp.absolutePath, bounds)
                    var sample = 1
                    while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
                    bitmap = BitmapFactory.decodeFile(temp.absolutePath, BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                    })
                } finally { temp.delete() }
            }
        }
        loading = false
    }
    if (loading) {
        Box(Modifier.fillMaxWidth().height(360.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else {
        val image = bitmap
        if (image != null) {
            Image(bitmap = image.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth().height(420.dp), contentScale = ContentScale.Fit)
        } else Text("Unable to preview this image. Try downloading it instead.")
    }
}

@Composable
private fun VideoPreview(api: DarkiCloudApi, token: String, fileId: String) {
    val context = LocalContext.current
    val exoPlayer = remember(api, token, fileId) {
        val http = DefaultHttpDataSource.Factory().setDefaultRequestProperties(mapOf("Authorization" to "Bearer $token"))
        ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http))).build().apply {
            setMediaItem(MediaItem.fromUri(api.fileContentUrl(fileId)))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.stop(); exoPlayer.release() }
    }
    AndroidView(
        factory = { context -> PlayerView(context).apply { player = exoPlayer; useController = true } },
        modifier = Modifier.fillMaxWidth().height(300.dp)
    )
}

@Composable
private fun DriveTopBar(
    refreshing: Boolean, authenticated: Boolean, canGoBack: Boolean,
    onBack: () -> Unit, onRefresh: () -> Unit, onLogout: () -> Unit, onLogin: () -> Unit,
    onTrash: () -> Unit, showTrash: Boolean, transferCount: Int, onTransfers: () -> Unit, onSearch: () -> Unit, onPhotos: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (authenticated && canGoBack) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            }
            Icon(
                imageVector = Icons.Default.Cloud,
                contentDescription = null,
                tint = Color(0xFFB7F7FF),
                modifier = Modifier.size(30.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("DARKI Cloud", fontWeight = FontWeight.SemiBold)
                Text(
                    if (authenticated) "My Drive" else "Private cloud storage",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF858585)
                )
            }
            if (authenticated) {
                Box {
                    IconButton(onClick = onTransfers) {
                        Icon(Icons.Default.CloudSync, contentDescription = "Transfers")
                    }
                    if (transferCount > 0) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = (-2).dp, y = 2.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFB7F7FF),
                        ) {
                            Text(
                                text = transferCount.coerceAtMost(99).toString(),
                                color = Color(0xFF050505),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                IconButton(onClick = onRefresh, enabled = !refreshing) {
                    if (refreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Refresh, "Refresh")
                }
                IconButton(onClick = onLogout) { Icon(Icons.Default.MoreVert, "More") }
            } else {
                IconButton(onClick = onLogin) { Icon(Icons.Default.Login, "Sign in") }
            }
        }

        if (authenticated) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onTransfers,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (transferCount > 0) "Transfers • $transferCount" else "Transfers")
                }
                OutlinedButton(
                    onClick = onSearch,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Search")
                }
                OutlinedButton(
                    onClick = onPhotos,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Photos")
                }
                if (showTrash) {
                    IconButton(onClick = onTrash) {
                        Icon(Icons.Default.DeleteSweep, "Trash")
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginContent(error: String?, onLogin: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(120.dp))
        Icon(imageVector = Icons.Default.Cloud, contentDescription = null, tint = Color(0xFFB7F7FF), modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(22.dp))
        Text("Your private cloud", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Sign in with Telegram to access your files.", color = Color(0xFF858585), modifier = Modifier.padding(top = 8.dp))
        error?.let {
            Spacer(Modifier.height(16.dp))
            Text(
                text = it,
                color = Color(0xFFFFB4AB),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
        Spacer(Modifier.height(28.dp))
        Button(onClick = onLogin, shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Default.Login, null)
            Spacer(Modifier.width(8.dp))
            Text("Continue with Telegram")
        }
    }
}

private fun guessMime(name: String?): String? = when (name?.substringAfterLast('.', "")?.lowercase()) { "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg" -> "image/*"; "mp4", "mkv", "webm", "mov", "avi" -> "video/*"; else -> null }
private fun formatSize(size: Long?): String = when { size == null -> "Unknown size"; size < 1024 -> "$size B"; size < 1024 * 1024 -> "%.1f KB".format(size / 1024.0); size < 1024L * 1024L * 1024L -> "%.1f MB".format(size / (1024.0 * 1024.0)); else -> "%.1f GB".format(size / (1024.0 * 1024.0 * 1024.0)) }
