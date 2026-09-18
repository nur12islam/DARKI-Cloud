package com.darki.cloud.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transfers")
data class TransferEntity(
    @PrimaryKey val id: String,
    val type: String,
    val uri: String?,
    val fileId: String?,
    val folderId: String?,
    val name: String?,
    val mimeType: String?,
    val sizeBytes: Long?,
    val destinationUri: String?,
    val status: String,
    val attempts: Int = 0,
    val lastError: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val progressBytes: Long = 0L,
)
