package com.darki.cloud.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [UserEntity::class, DeviceEntity::class, FolderEntity::class, FileEntity::class, TransferEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class CloudDatabase : RoomDatabase() {
    abstract fun cloudDao(): CloudDao

    companion object {
        fun create(context: Context): CloudDatabase =
            Room.databaseBuilder(context, CloudDatabase::class.java, "darki-cloud.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
