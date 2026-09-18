package com.darki.cloud

import android.graphics.BitmapFactory
import android.os.Bundle
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PhotosActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = SessionStore(applicationContext)
        val db = CloudDatabase.create(applicationContext)
        val api = DarkiCloudApi(BuildConfig.DARKI_CLOUD_BASE_URL, OkHttpClient())
        setContent {
            val photos by db.cloudDao().observePhotos().collectAsState(initial = emptyList())
            PhotosScreen(photos, store.token, api, ::finish)
        }
    }
}

@Composable
private fun PhotosScreen(
    photos: List<FileEntity>,
    token: String?,
    api: DarkiCloudApi,
    onBack: () -> Unit,
) {
    var selectedPhoto by remember { mutableStateOf<FileEntity?>(null) }

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
                                                    onClick = { selectedPhoto = file },
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

    selectedPhoto?.let { file ->
        PhotoViewer(file, token, api) { selectedPhoto = null }
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
    file: FileEntity,
    token: String?,
    api: DarkiCloudApi,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF050505)) {
            Box(Modifier.fillMaxSize()) {
                val bitmap = rememberPhotoBitmap(file, token, api)

                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = file.name,
                        modifier = Modifier.fillMaxWidth().align(Alignment.Center),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color(0xFFB7F7FF),
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                ) {
                    Icon(Icons.Default.Close, "Close", tint = Color.White)
                }

                Text(
                    file.name,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(20.dp),
                )
            }
        }
    }
}
