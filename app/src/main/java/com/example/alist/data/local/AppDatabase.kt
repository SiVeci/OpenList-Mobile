package com.example.alist.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ServerProfile::class, DirectoryCache::class, TransferTask::class], version = 5, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serverProfileDao(): ServerProfileDao
    abstract fun directoryCacheDao(): DirectoryCacheDao
    abstract fun transferTaskDao(): TransferTaskDao

    companion object {
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transfer_tasks ADD COLUMN type TEXT NOT NULL DEFAULT 'DOWNLOAD'")
            }
        }
    }
}
