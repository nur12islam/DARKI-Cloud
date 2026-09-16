package com.darki.cloud

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private sealed interface DriveItem {
    val name: String

    data class Folder(override val name: String) : DriveItem
    data class File(override val name: String, val size: String) : DriveItem
}

private val previewItems = listOf(
    DriveItem.Folder("Documents"),
    DriveItem.Folder("Pictures"),
    DriveItem.Folder("Projects"),
    DriveItem.File("Welcome.pdf", "2.4 MB"),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DarkiCloudApp() }
    }
}

@Composable
private fun DarkiCloudApp() {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF050505),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    DriveTopBar()
                    DriveContent()
                }

                FloatingActionButton(
                    onClick = { },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(24.dp),
                    containerColor = Color(0xFF171717),
                    contentColor = Color(0xFFB7F7FF),
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                }
            }
        }
    }
}

@Composable
private fun DriveTopBar() {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Cloud,
                contentDescription = null,
                tint = Color(0xFFB7F7FF),
                modifier = Modifier.size(30.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("DARKI Cloud", fontWeight = FontWeight.SemiBold)
                Text(
                    "My Drive",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF858585),
                )
            }
            IconButton(onClick = { }) {
                Icon(Icons.Default.Search, contentDescription = "Search")
            }
            IconButton(onClick = { }) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }

        Spacer(Modifier.height(18.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Color(0xFF3A6D76), Color.Transparent),
                    ),
                ),
        )
    }
}

@Composable
private fun DriveContent() {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text("My Drive", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Your private cloud storage",
            color = Color(0xFF858585),
            modifier = Modifier.padding(top = 4.dp, bottom = 18.dp),
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 100.dp),
        ) {
            items(previewItems) { item -> DriveItemRow(item) }
        }
    }
}

@Composable
private fun DriveItemRow(item: DriveItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF101010))
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val isFolder = item is DriveItem.Folder
        Icon(
            if (isFolder) Icons.Default.Folder else Icons.Default.Description,
            contentDescription = null,
            tint = if (isFolder) Color(0xFFB7F7FF) else Color(0xFFB0B0B0),
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.name, fontWeight = FontWeight.Medium)
            Text(
                if (isFolder) "Folder" else (item as DriveItem.File).size,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF777777),
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}
