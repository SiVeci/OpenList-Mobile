package com.example.alist.domain.repository

import com.example.alist.data.local.TransferTask
import kotlinx.coroutines.flow.Flow

interface TransferRepository {
    fun getAllTasks(): Flow<List<TransferTask>>
    suspend fun addTask(fileName: String, fileUrl: String, savePath: String): Long
    suspend fun updateTask(task: TransferTask)
    suspend fun getTaskById(id: Long): TransferTask?
    suspend fun deleteTask(id: Long)
}
