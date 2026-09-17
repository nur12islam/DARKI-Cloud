package com.darki.cloud

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darki.cloud.data.local.FileEntity
import com.darki.cloud.data.local.FolderEntity

@Composable
fun CreateFolderDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("New folder") }, text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Folder name") }, singleLine = true) }, confirmButton = { Button(onClick = { if (name.trim().isNotEmpty()) onCreate(name.trim()) }, enabled = name.trim().isNotEmpty()) { Text("Create") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun RenameDialog(title: String, initialName: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true) }, confirmButton = { Button(onClick = { if (name.trim().isNotEmpty()) onRename(name.trim()) }, enabled = name.trim().isNotEmpty()) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun MoveDialog(title: String, folders: List<FolderEntity>, currentFolderId: String?, onDismiss: () -> Unit, onMove: (String) -> Unit) {
    val choices = folders.filter { it.id != currentFolderId }
    var expanded by remember { mutableStateOf(false) }
    var selected by remember(choices) { mutableStateOf(choices.firstOrNull()) }
    Column {
        OutlinedTextField(value = selected?.name ?: "No destination available", onValueChange = {}, readOnly = true, label = { Text("Destination") }, modifier = Modifier.fillMaxWidth().then(Modifier))
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { choices.forEach { folder -> DropdownMenuItem(text = { Text(folder.name) }, onClick = { selected = folder; expanded = false }) } }
        TextButton(onClick = { expanded = true }) { Text("Choose folder") }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Column { OutlinedTextField(value = selected?.name ?: "No destination available", onValueChange = {}, readOnly = true, label = { Text("Destination") }, modifier = Modifier.fillMaxWidth()); TextButton(onClick = { expanded = true }) { Text("Choose folder") }; DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { choices.forEach { folder -> DropdownMenuItem(text = { Text(folder.name) }, onClick = { selected = folder; expanded = false }) } } } }, confirmButton = { Button(onClick = { selected?.let { onMove(it.id) } }, enabled = selected != null) { Text("Move") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun DeleteDialog(file: FileEntity, onDismiss: () -> Unit, onDelete: () -> Unit) { AlertDialog(onDismissRequest = onDismiss, title = { Text("Move to trash?") }, text = { Text("\"${file.name}\" will be moved to Trash. You can restore it later.") }, confirmButton = { Button(onClick = onDelete) { Text("Move to trash") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }) }

@Composable
fun RestoreDialog(file: FileEntity, onDismiss: () -> Unit, onRestore: () -> Unit) { AlertDialog(onDismissRequest = onDismiss, title = { Text("Restore file?") }, text = { Text("Restore \"${file.name}\" to its previous folder?") }, confirmButton = { Button(onClick = onRestore) { Text("Restore") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }) }
