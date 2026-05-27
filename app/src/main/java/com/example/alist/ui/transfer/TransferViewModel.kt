package com.example.alist.ui.transfer

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.alist.data.local.TransferStatus
import com.example.alist.data.local.TransferTask
import com.example.alist.domain.repository.TransferRepository
import com.example.alist.service.TransferService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransferViewModel @Inject constructor(
    private val transferRepository: TransferRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val activeTasks: StateFlow<List<TransferTask>> = transferRepository.getAllTasks()
        .map { list -> list.filter { it.status == TransferStatus.PENDING || it.status == TransferStatus.DOWNLOADING || it.status == TransferStatus.PAUSED } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val finishedTasks: StateFlow<List<TransferTask>> = transferRepository.getAllTasks()
        .map { list -> list.filter { it.status == TransferStatus.SUCCESS } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val failedTasks: StateFlow<List<TransferTask>> = transferRepository.getAllTasks()
        .map { list -> list.filter { it.status == TransferStatus.ERROR } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun pauseTask(task: TransferTask) {
        val intent = Intent(context, TransferService::class.java).apply {
            action = TransferService.ACTION_PAUSE
            putExtra(TransferService.EXTRA_TASK_ID, task.id)
        }
        context.startService(intent)
    }

    fun resumeTask(task: TransferTask) {
        val intent = Intent(context, TransferService::class.java).apply {
            action = TransferService.ACTION_RESUME
            putExtra(TransferService.EXTRA_TASK_ID, task.id)
        }
        context.startService(intent)
    }

    fun cancelTask(task: TransferTask) {
        val intent = Intent(context, TransferService::class.java).apply {
            action = TransferService.ACTION_CANCEL
            putExtra(TransferService.EXTRA_TASK_ID, task.id)
        }
        context.startService(intent)
    }
    
    fun deleteTaskRecord(task: TransferTask) {
        viewModelScope.launch {
            transferRepository.deleteTask(task.id)
        }
    }
}
