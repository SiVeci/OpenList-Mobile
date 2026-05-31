package com.openlistmobile.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class SyncMode { DOWNLOAD_ONLY, UPLOAD_ONLY, TWO_WAY }
enum class ConflictStrategy { NEWEST_WINS, SKIP, CLOUD_WINS, LOCAL_WINS }
enum class SyncTrigger { MANUAL, PERIODIC_6H, PERIODIC_12H, PERIODIC_24H }
enum class SyncStatus { IDLE, DIFFING, SYNCING, ERROR }

@Entity(
    tableName = "sync_rules",
    indices = [
        Index(value = ["remotePath"], unique = true),
        Index(value = ["localUri"], unique = true)
    ]
)
data class SyncRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val ruleName: String,
    val remotePath: String,
    val localUri: String,

    val syncMode: SyncMode = SyncMode.TWO_WAY,
    val isMirrorDeleteEnabled: Boolean = false,
    val conflictStrategy: ConflictStrategy = ConflictStrategy.NEWEST_WINS,
    val ignoreModifiedTime: Boolean = false,

    val triggerType: SyncTrigger = SyncTrigger.MANUAL,
    val requiresWiFi: Boolean = true,
    val requiresCharging: Boolean = false,

    val lastSyncTime: Long = 0L,
    val status: SyncStatus = SyncStatus.IDLE,
    val errorMsg: String? = null
)
