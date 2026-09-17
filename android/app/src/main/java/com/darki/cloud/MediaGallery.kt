package com.darki.cloud

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.darki.cloud.data.api.DarkiCloudApi
import com.darki.cloud.data.local.FileEntity
import com.darki.cloud.data.local.FolderEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/** Reusable grid for Drive media. Image thumbnails are decoded to <=512px per side. */
@Composable
fun MediaGallery(
    folders: List<FolderEntity>,
    files: List<FileEntity>,
    token: String?,
    api: DarkiCloudApi,
    onFolderClick: (FolderEntity) -> Unit,
    onFileClick: (FileEntity) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 120.dp),
    ) {
        items(folders, key = { it.id }) { folder ->
            GalleryTile(folder.name, "Folder", true, null, token, api) { onFolderClick(folder) }
        }
        items(files, key = { it.id }) { file ->
            GalleryTile(file.name, formatGallerySize(file.sizeBytes), false, file, token, api) { onFileClick(file) }
        }
    }
}

@Composable
private fun GalleryTile(
    name: String,
    subtitle: String,
    folder: Boolean,
    file: FileEntity?,
    token: String?,
    api: DarkiCloudApi,
    onClick: () -> Unit,
) {
    val mime = file?.mimeType ?: guessGalleryMime(file?.name)
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF101010), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.15f)
                .background(Color(0xFF171717), RoundedCornerShape(14.dp)),
            Alignment.Center,
        ) {
            when {
                folder -> Icon(Icons.Default.Folder, null, tint = Color(0xFFB7F7FF), modifier = Modifier.padding(8.dp).height(48.dp))
                mime?.startsWith("image/") == true && token != null && file != null -> GalleryThumbnail(api, token, file.id)
                mime?.startsWith("video/") == true -> Icon(Icons.Default.PlayCircle, null, tint = Color(0xFFB7F7FF), modifier = Modifier.height(52.dp))
                else -> Icon(Icons.Default.Description, null, tint = Color(0xFF9A9A9A), modifier = Modifier.height(48.dp))
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(name, maxLines = 1)
        Text(subtitle, maxLines = 1, color = Color(0xFF777777))
    }
}

@Composable
private fun GalleryThumbnail(api: DarkiCloudApi, token: String, fileId: String) {
    val context = LocalContext.current
    var bitmap by remember(fileId, token) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(fileId, token) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val temp = File.createTempFile("darki-thumb-", ".tmp", context.cacheDir)
                try {
                    val request = Request.Builder()
                        .url(api.fileContentUrl(fileId))
                        .header("Authorization", "Bearer $token")
                        .build()
                    OkHttpClient().newCall(request).execute().use { response ->
                        if (!response.isSuccessful) error("thumbnail request failed")
                        response.body?.byteStream()?.use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
                            ?: error("empty response")
                    }
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(temp.absolutePath, bounds)
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) error("invalid image")
                    var sample = 1
                    while (bounds.outWidth / sample > 512 || bounds.outHeight / sample > 512) sample *= 2
                    BitmapFactory.decodeFile(temp.absolutePath, BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                    })
                } finally {
                    temp.delete()
                }
            }.getOrNull()
        }
    }
    if (bitmap != null) Image(bitmap!!.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    else CircularProgressIndicator(Modifier.height(22.dp), strokeWidth = 2.dp)
}

private fun guessGalleryMime(name: String?): String? = when (name?.substringAfterLast('.', "")?.lowercase()) {
    "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg" -> "image/*"
    "mp4", "mkv", "webm", "mov", "avi" -> "video/*"
    else -> null
}

private fun formatGallerySize(bytes: Long?): String = when {
    bytes == null -> "File"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    else -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
}
