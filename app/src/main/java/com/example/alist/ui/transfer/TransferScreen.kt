package com.example.alist.ui.transfer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.alist.data.local.TransferStatus
import com.example.alist.data.local.TransferTask
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferScreen(
    onNavigateBack: () -> Unit,
    viewModel: TransferViewModel = hiltViewModel()
) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val activeTasks by viewModel.activeTasks.collectAsState()
    val finishedTasks by viewModel.finishedTasks.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("传输管理") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text("正在传输 (${activeTasks.size})") }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text("已完成 (${finishedTasks.size})") }
                )
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                val list = if (selectedTabIndex == 0) activeTasks else finishedTasks
                items(list, key = { it.id }) { task ->
                    TransferTaskItem(
                        task = task,
                        onPause = { viewModel.pauseTask(it) },
                        onResume = { viewModel.resumeTask(it) },
                        onCancel = { viewModel.cancelTask(it) },
                        onDelete = { viewModel.deleteTaskRecord(it) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun TransferTaskItem(
    task: TransferTask,
    onPause: (TransferTask) -> Unit,
    onResume: (TransferTask) -> Unit,
    onCancel: (TransferTask) -> Unit,
    onDelete: (TransferTask) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(task.fileName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            
            when (task.status) {
                TransferStatus.DOWNLOADING -> {
                    IconButton(onClick = { onPause(task) }) { Icon(Icons.Default.Pause, contentDescription = "Pause") }
                    IconButton(onClick = { onCancel(task) }) { Icon(Icons.Default.Close, contentDescription = "Cancel") }
                }
                TransferStatus.PAUSED -> {
                    IconButton(onClick = { onResume(task) }) { Icon(Icons.Default.PlayArrow, contentDescription = "Resume") }
                    IconButton(onClick = { onCancel(task) }) { Icon(Icons.Default.Close, contentDescription = "Cancel") }
                }
                TransferStatus.PENDING -> {
                    Text("等待中", style = MaterialTheme.typography.bodySmall)
                }
                else -> {
                    IconButton(onClick = { onDelete(task) }) { Icon(Icons.Default.Delete, contentDescription = "Delete Record") }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        if (task.status == TransferStatus.ERROR) {
            Text("错误: ${task.errorMsg}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        } else if (task.totalBytes > 0) {
            val progress = if (task.totalBytes > 0) task.downloadedBytes.toFloat() / task.totalBytes.toFloat() else 0f
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${formatBytes(task.downloadedBytes)} / ${formatBytes(task.totalBytes)}", style = MaterialTheme.typography.bodySmall)
                Text("${(progress * 100).toInt()} %", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

fun formatBytes(size: Long): String {
    if (size <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(Locale.getDefault(), "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
