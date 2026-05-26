package com.example.alist.data.repository

import com.example.alist.data.local.TransferStatus
import com.example.alist.data.local.TransferTask
import com.example.alist.data.local.TransferTaskDao
import com.example.alist.domain.repository.TransferRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class TransferRepositoryImpl @Inject constructor(
    private val dao: TransferTaskDao
) : TransferRepository {

    override fun getAllTasks(): Flow<List<TransferTask>> = dao.getAllTasksFlow()

    override suspend fun addTask(fileName: String, fileUrl: String, savePath: String): Long = withContext(Dispatchers.IO) {
        val task = TransferTask(
            fileName = fileName,
            fileUrl = fileUrl,
            savePath = savePath,
            totalBytes = 0,
            downloadedBytes = 0,
            status = TransferStatus.PENDING
        )
        dao.insert(task)
    }

    override suspend fun updateTask(task: TransferTask) = withContext(Dispatchers.IO) {
        dao.update(task)
    }

    override suspend fun getTaskById(id: Long): TransferTask? = withContext(Dispatchers.IO) {
        dao.getTaskById(id)
    }

    override suspend fun deleteTask(id: Long) = withContext(Dispatchers.IO) {
        dao.delete(id)
    }
}
