package com.example.alist.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [ServerProfile::class, DirectoryCache::class, TransferTask::class], version = 3, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serverProfileDao(): ServerProfileDao
    abstract fun directoryCacheDao(): DirectoryCacheDao
    abstract fun transferTaskDao(): TransferTaskDao
}
