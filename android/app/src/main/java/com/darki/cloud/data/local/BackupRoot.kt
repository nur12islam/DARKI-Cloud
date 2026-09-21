package com.darki.cloud.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "backup_roots")
data class BackupRootEntity(
    @PrimaryKey val id: Int = 1,
    val treeUri: String,
    val enabled: Boolean = true,
    val wifiOnly: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
)
