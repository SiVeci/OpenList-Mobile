package com.openlistmobile.app.domain.repository

import com.openlistmobile.app.data.local.TransferTask
import com.openlistmobile.app.data.local.TransferType
import kotlinx.coroutines.flow.Flow

interface TransferRepository {
    fun getAllTasks(): Flow<List<TransferTask>>
    suspend fun addTask(fileName: String, fileUrl: String, savePath: String, totalBytes: Long = 0, type: TransferType = TransferType.DOWNLOAD): Long
    suspend fun updateTask(task: TransferTask)
    suspend fun getTaskById(id: Long): TransferTask?
    suspend fun deleteTask(id: Long)
}
