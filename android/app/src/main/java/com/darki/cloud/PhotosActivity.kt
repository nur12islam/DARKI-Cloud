package com.darki.cloud

import android.graphics.BitmapFactory
import android.os.Bundle
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.dp
import com.darki.cloud.data.api.DarkiCloudApi
import com.darki.cloud.data.local.CloudDatabase
import com.darki.cloud.data.local.FileEntity
import com.darki.cloud.data.local.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class PhotosActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = SessionStore(applicationContext)
        val db = CloudDatabase.create(applicationContext)
        val api = DarkiCloudApi(BuildConfig.DARKI_CLOUD_BASE_URL, OkHttpClient())
        setContent {
            val photos by db.cloudDao().observePhotos().collectAsState(initial = emptyList())
            PhotosScreen(photos, store.token, store.deviceId, api, ::finish)
        }
    }
}

@Composable
private fun PhotosScreen(
    photos: List<FileEntity>,
    token: String?,
    deviceId: String?,
    api: DarkiCloudApi,
    onBack: () -> Unit,
) {
    var selectedIndex by remember { mutableIntStateOf(-1) }

    Surface(Modifier.fillMaxSize(), color = Color(0xFF050505)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Back")
                }
                Column {
                    Text("Photos", style = MaterialTheme.typography.headlineMedium)
                    Text("${photos.size} photos", color = Color(0xFF858585))
                }
            }

            if (photos.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(top = 100.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Default.Image, null, tint = Color(0xFF4D5B5E), modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("No photos yet", color = Color(0xFF858585))
                }
            } else {
                val groups = photos.groupBy { photoDateLabel(it.modifiedAt ?: it.createdAt) }
                LazyColumn(
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    groups.forEach { (label, group) ->
                        item {
                            Text(
                                label,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            )
                        }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                group.chunked(3).forEach { row ->
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        row.forEach { file ->
                                            Box(Modifier.weight(1f)) {
                                                PhotoTile(
                                                    file = file,
                                                    token = token,
                                                    api = api,
                                                    onClick = { selectedIndex = photos.indexOf(file) },
                                                )
                                            }
                                        }
                                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (selectedIndex in photos.indices) {
        PhotoViewer(
            photos = photos,
            initialIndex = selectedIndex,
            token = token,
            deviceId = deviceId,
            api = api,
            onDismiss = { selectedIndex = -1 },
        )
    }
}

private fun photoDateLabel(timestamp: Long?): String =
    timestamp?.let { SimpleDateFormat("MMMM d, yyyy", Locale.US).format(Date(it)) } ?: "Unknown date"

@Composable
private fun PhotoTile(
    file: FileEntity,
    token: String?,
    api: DarkiCloudApi,
    onClick: () -> Unit,
) {
    val bitmap = rememberPhotoBitmap(file, token, api)
    Box(
        Modifier
            .aspectRatio(1f)
            .background(Color(0xFF111111))
            .clickable(onClick = onClick),
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = file.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun rememberPhotoBitmap(
    file: FileEntity,
    token: String?,
    api: DarkiCloudApi,
): android.graphics.Bitmap? {
    val context = LocalContext.current
    var bitmap by remember(file.id, token) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(file.id, token) {
        if (token == null) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            runCatching {
                val temp = File.createTempFile("darki-photo-", ".img", context.cacheDir)
                try {
                    OkHttpClient().newCall(
                        Request.Builder()
                            .url(api.fileContentUrl(file.id))
                            .header("Authorization", "Bearer $token")
                            .build(),
                    ).execute().use { response ->
                        if (!response.isSuccessful) return@runCatching
                        response.body?.byteStream()?.use { input ->
                            temp.outputStream().use { output -> input.copyTo(output) }
                        } ?: return@runCatching
                    }

                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(temp.absolutePath, bounds)

                    var sample = 1
                    while (bounds.outWidth / sample > 1200 || bounds.outHeight / sample > 1200) sample *= 2

                    bitmap = BitmapFactory.decodeFile(
                        temp.absolutePath,
                        BitmapFactory.Options().apply { inSampleSize = sample },
                    )
                } finally {
                    temp.delete()
                }
            }
        }
    }

    return bitmap
}

@Composable
private fun PhotoViewer(
    photos: List<FileEntity>,
    initialIndex: Int,
    token: String?,
    deviceId: String?,
    api: DarkiCloudApi,
    onDismiss: () -> Unit,
) {
    var currentIndex by remember(initialIndex) { mutableIntStateOf(initialIndex) }
    var scale by remember(currentIndex) { mutableFloatStateOf(1f) }
    var offsetX by remember(currentIndex) { mutableFloatStateOf(0f) }
    var offsetY by remember(currentIndex) { mutableFloatStateOf(0f) }

    val file = photos.getOrNull(currentIndex) ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(file.mimeType ?: "image/jpeg"),
    ) { uri ->
        if (uri != null && token != null) {
            scope.launch {
                actionMessage = runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        api.downloadFile(token, file.id, output)
                    } ?: error("Unable to open destination")
                    "Saved to device"
                }.getOrElse { "Save failed" }
            }
        }
    }
    fun sharePhoto() {
        if (token == null) return
        scope.launch {
            actionMessage = runCatching {
                val dir = File(context.cacheDir, "shared").apply { mkdirs() }
                val output = File(dir, file.name)
                output.outputStream().use { api.downloadFile(token, file.id, it) }
                val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", output)
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = file.mimeType ?: "image/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, "Share photo"))
                "Ready to share"
            }.getOrElse { "Share failed" }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF050505)) {
            Box(Modifier.fillMaxSize()) {
                val bitmap = rememberPhotoBitmap(file, token, api)

                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = file.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(scale, currentIndex) {
                                if (scale <= 1.01f) {
                                    detectDragGestures(
                                        onDragEnd = {
                                            if (abs(offsetX) > 120f) {
                                                val direction = if (offsetX < 0f) 1 else -1
                                                val next = (currentIndex + direction).coerceIn(0, photos.lastIndex)
                                                if (next != currentIndex) currentIndex = next
                                                offsetX = 0f
                                            }
                                        },
                                        onDragCancel = { offsetX = 0f },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            offsetX += dragAmount.x
                                        },
                                    )
                                } else {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        scale = (scale * zoom).coerceIn(1f, 5f)
                                        if (scale > 1f) {
                                            offsetX += pan.x
                                            offsetY += pan.y
                                        } else {
                                            offsetX = 0f
                                            offsetY = 0f
                                        }
                                    }
                                }
                            }
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offsetX
                                translationY = offsetY
                            },
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color(0xFFB7F7FF),
                    )
                }

                Row(
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                ) {
                    IconButton(onClick = { sharePhoto() }) {
                        Icon(Icons.Default.Share, "Share", tint = Color.White)
                    }
                    IconButton(
                        onClick = {
                            saveLauncher.launch(file.name)
                        },
                    ) {
                        Icon(Icons.Default.Download, "Save", tint = Color.White)
                    }
                    IconButton(
                        onClick = { confirmDelete = true },
                    ) {
                        Icon(Icons.Default.Delete, "Delete", tint = Color.White)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close", tint = Color.White)
                    }
                }

                if (confirmDelete) {
                    AlertDialog(
                        onDismissRequest = { confirmDelete = false },
                        title = { Text("Move to Trash?") },
                        text = { Text("This photo will be removed from your Drive.") },
                        confirmButton = {
                            TextButton(onClick = {
                                confirmDelete = false
                                if (token != null && deviceId != null) {
                                    scope.launch {
                                        actionMessage = runCatching {
                                            api.deleteFile(token, file.id, deviceId)
                                            onDismiss()
                                            "Moved to Trash"
                                        }.getOrElse { "Delete failed" }
                                    }
                                }
                            }) { Text("Move to Trash") }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
                        },
                    )
                }

                Text(
                    actionMessage ?: ((currentIndex + 1).toString() + " / " + photos.size + "  •  " + file.name),
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(20.dp),
                )
            }
        }
    }
}