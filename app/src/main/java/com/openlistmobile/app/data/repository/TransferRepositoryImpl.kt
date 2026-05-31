package com.openlistmobile.app.data.repository

import com.openlistmobile.app.data.local.TransferStatus
import com.openlistmobile.app.data.local.TransferTask
import com.openlistmobile.app.data.local.TransferTaskDao
import com.openlistmobile.app.data.local.TransferType
import com.openlistmobile.app.domain.repository.TransferRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class TransferRepositoryImpl @Inject constructor(
    private val dao: TransferTaskDao
) : TransferRepository {

    override fun getAllTasks(): Flow<List<TransferTask>> = dao.getAllTasksFlow()

    override suspend fun addTask(fileName: String, fileUrl: String, savePath: String, totalBytes: Long, type: TransferType): Long = withContext(Dispatchers.IO) {
        val task = TransferTask(
            fileName = fileName,
            fileUrl = fileUrl,
            savePath = savePath,
            totalBytes = totalBytes,
            downloadedBytes = 0,
            status = TransferStatus.PENDING,
            type = type
        )
        dao.insert(task)
    }

    override suspend fun addTasks(tasks: List<TransferTask>): List<Long> = withContext(Dispatchers.IO) {
        dao.insertAll(tasks)
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
