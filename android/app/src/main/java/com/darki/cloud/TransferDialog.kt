package com.darki.cloud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darki.cloud.data.local.TransferEntity

@Composable
fun TransferDialog(
    transfers: List<TransferEntity>,
    onRetry: (TransferEntity) -> Unit,
    onCancel: (TransferEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Transfers") },
        text = {
            if (transfers.isEmpty()) {
                Text("No active or failed transfers.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(transfers, key = { it.id }) { transfer ->
                        TransferRow(transfer, onRetry, onCancel)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    )
}

@Composable
private fun TransferRow(transfer: TransferEntity, onRetry: (TransferEntity) -> Unit, onCancel: (TransferEntity) -> Unit) {
    val uploading = transfer.type == "upload"
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                if (uploading) Icons.Default.CloudUpload else Icons.Default.CloudDownload,
                contentDescription = null,
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    transfer.name ?: if (uploading) "Upload" else "Download",
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    transfer.status.replaceFirstChar { it.uppercase() } +
                        if (transfer.attempts > 0) " • ${transfer.attempts} attempt${if (transfer.attempts == 1) "" else "s"}" else "",
                    style = MaterialTheme.typography.labelMedium,
                )
                if (transfer.status == "running") {
                    val total = transfer.sizeBytes ?: 0L
                    if (total > 0L) {
                        LinearProgressIndicator(
                            progress = { (transfer.progressBytes.toFloat() / total.toFloat()).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                        Text("${formatBytes(transfer.progressBytes)} / ${formatBytes(total)}", style = MaterialTheme.typography.labelSmall)
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    }
                }
                if (!transfer.lastError.isNullOrBlank()) {
                    Text(
                        transfer.lastError!!,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                transfer.status == "completed" -> Text("Completed", style = MaterialTheme.typography.labelMedium)
                transfer.status == "failed" -> Button(onClick = { onRetry(transfer) }) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Text("Retry", Modifier.padding(start = 6.dp))
                }
                transfer.status == "queued" || transfer.status == "running" ->
                    TextButton(onClick = { onCancel(transfer) }) { Text("Cancel") }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024f * 1024f))
    bytes >= 1024L -> String.format(java.util.Locale.US, "%.0f KB", bytes / 1024f)
    else -> "$bytes B"
}
