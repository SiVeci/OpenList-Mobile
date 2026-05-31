package com.openlistmobile.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ServerProfile::class, DirectoryCache::class, TransferTask::class, SyncRule::class], version = 6, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serverProfileDao(): ServerProfileDao
    abstract fun directoryCacheDao(): DirectoryCacheDao
    abstract fun transferTaskDao(): TransferTaskDao
    abstract fun syncRuleDao(): SyncRuleDao

    companion object {
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transfer_tasks ADD COLUMN type TEXT NOT NULL DEFAULT 'DOWNLOAD'")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `sync_rules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `profileId` INTEGER NOT NULL, `ruleName` TEXT NOT NULL, `remotePath` TEXT NOT NULL, `localUri` TEXT NOT NULL, `syncMode` TEXT NOT NULL, `isMirrorDeleteEnabled` INTEGER NOT NULL, `conflictStrategy` TEXT NOT NULL, `ignoreModifiedTime` INTEGER NOT NULL, `triggerType` TEXT NOT NULL, `requiresWiFi` INTEGER NOT NULL, `requiresCharging` INTEGER NOT NULL, `lastSyncTime` INTEGER NOT NULL, `status` TEXT NOT NULL, `errorMsg` TEXT)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_rules_remotePath` ON `sync_rules` (`remotePath`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_rules_localUri` ON `sync_rules` (`localUri`)")
            }
        }
    }
}
