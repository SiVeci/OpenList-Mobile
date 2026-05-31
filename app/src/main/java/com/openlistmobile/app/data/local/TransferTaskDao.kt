package com.openlistmobile.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferTaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TransferTask): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<TransferTask>): List<Long>

    @Update
    suspend fun update(task: TransferTask)

    @Query("SELECT * FROM transfer_tasks ORDER BY timestamp DESC")
    fun getAllTasksFlow(): Flow<List<TransferTask>>

    @Query("SELECT * FROM transfer_tasks WHERE status = :status")
    suspend fun getTasksByStatus(status: TransferStatus): List<TransferTask>
    
    @Query("SELECT * FROM transfer_tasks WHERE id = :id")
    suspend fun getTaskById(id: Long): TransferTask?

    @Query("SELECT * FROM transfer_tasks WHERE status = 'PENDING' ORDER BY timestamp ASC LIMIT 1")
    suspend fun getNextQueuedTask(): TransferTask?

    @Query("DELETE FROM transfer_tasks WHERE id = :id")
    suspend fun delete(id: Long)
}
