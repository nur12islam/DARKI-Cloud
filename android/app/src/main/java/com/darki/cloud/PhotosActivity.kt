package com.darki.cloud

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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

class PhotosActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = SessionStore(applicationContext)
        val db = CloudDatabase.create(applicationContext)
        val api = DarkiCloudApi(BuildConfig.DARKI_CLOUD_BASE_URL, OkHttpClient())
        setContent { val photos by db.cloudDao().observePhotos().collectAsState(initial = emptyList()); PhotosScreen(photos, store.token, api, ::finish) }
    }
}

@Composable
private fun PhotosScreen(photos: List<FileEntity>, token: String?, api: DarkiCloudApi, onBack: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = Color(0xFF050505)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                Column { Text("Photos", style = MaterialTheme.typography.headlineMedium); Text("${photos.size} photos", color = Color(0xFF858585)) }
            }
            if (photos.isEmpty()) {
                Column(Modifier.fillMaxSize().padding(top = 100.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Image, null, tint = Color(0xFF4D5B5E), modifier = Modifier.size(56.dp)); Spacer(Modifier.height(12.dp)); Text("No photos yet", color = Color(0xFF858585))
                }
            } else LazyVerticalGrid(columns = GridCells.Fixed(3), contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { items(photos, key = { it.id }) { file -> PhotoTile(file, token, api) } }
        }
    }
}

@Composable
private fun PhotoTile(file: FileEntity, token: String?, api: DarkiCloudApi) {
    val context = LocalContext.current
    var bitmap by remember(file.id, token) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(file.id, token) {
        if (token == null) return@LaunchedEffect
        withContext(Dispatchers.IO) { runCatching {
            val temp = File.createTempFile("darki-photo-", ".img", context.cacheDir)
            try {
                OkHttpClient().newCall(Request.Builder().url(api.fileContentUrl(file.id)).header("Authorization", "Bearer $token").build()).execute().use { response ->
                    if (!response.isSuccessful) return@runCatching
                    response.body?.byteStream()?.use { input -> temp.outputStream().use { output -> input.copyTo(output) } } ?: return@runCatching
                }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(temp.absolutePath, bounds)
                var sample = 1; while (bounds.outWidth / sample > 600 || bounds.outHeight / sample > 600) sample *= 2
                bitmap = BitmapFactory.decodeFile(temp.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            } finally { temp.delete() }
        } }
    }
    Box(Modifier.aspectRatio(1f).background(Color(0xFF111111))) { bitmap?.let { Image(it.asImageBitmap(), file.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } }
}
