package com.darki.cloud.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [UserEntity::class, DeviceEntity::class, FolderEntity::class, FileEntity::class, TransferEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class CloudDatabase : RoomDatabase() {
    abstract fun cloudDao(): CloudDao

    companion object {
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Version 3 changes migration policy only; the version-2 schema is unchanged.
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE transfers ADD COLUMN progressBytes INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun create(context: Context): CloudDatabase =
            Room.databaseBuilder(context, CloudDatabase::class.java, "darki-cloud.db")
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}
