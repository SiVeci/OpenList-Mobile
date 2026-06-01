package com.openlistmobile.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncRuleDao {
    // ABORT: 违反 remotePath/localUri 唯一索引时抛 SQLiteConstraintException，供上层捕获并提示“该目录已被其他规则绑定”
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(rule: SyncRule): Long

    @Update
    suspend fun update(rule: SyncRule)

    @Query("UPDATE sync_rules SET status = :status, errorMsg = :errorMsg WHERE id = :id")
    suspend fun updateStatus(id: Long, status: SyncStatus, errorMsg: String? = null)

    @Query("SELECT * FROM sync_rules ORDER BY id ASC")
    fun getAllRulesFlow(): Flow<List<SyncRule>>

    @Query("SELECT * FROM sync_rules ORDER BY id ASC")
    suspend fun getAllRulesOnce(): List<SyncRule>

    @Query("SELECT * FROM sync_rules WHERE id = :id")
    suspend fun getRuleById(id: Long): SyncRule?

    @Query("UPDATE sync_rules SET lastSyncTime = :lastSyncTime, status = :status, errorMsg = :errorMsg WHERE id = :id")
    suspend fun updateSyncResult(
        id: Long,
        lastSyncTime: Long,
        status: SyncStatus,
        errorMsg: String? = null
    )

    @Query("DELETE FROM sync_rules WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM sync_rules WHERE remotePath = :remotePath")
    suspend fun countByRemotePath(remotePath: String): Int

    @Query("SELECT COUNT(*) FROM sync_rules WHERE localUri = :localUri")
    suspend fun countByLocalUri(localUri: String): Int
}
