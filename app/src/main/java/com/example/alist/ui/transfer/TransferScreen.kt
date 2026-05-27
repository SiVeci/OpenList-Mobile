package com.example.alist.ui.transfer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.alist.data.local.TransferStatus
import com.example.alist.data.local.TransferTask
import com.example.alist.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferScreen(
    onNavigateBack: () -> Unit,
    viewModel: TransferViewModel = hiltViewModel()
) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val activeTasks by viewModel.activeTasks.collectAsState()
    val finishedTasks by viewModel.finishedTasks.collectAsState()
    val failedTasks by viewModel.failedTasks.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("传输管理") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = {
                        Text(
                            "传输中 (${activeTasks.size})",
                            style = MaterialTheme.typography.labelLarge
                        )
                    },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = {
                        Text(
                            "已完成 (${finishedTasks.size})",
                            style = MaterialTheme.typography.labelLarge
                        )
                    },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Tab(
                    selected = selectedTabIndex == 2,
                    onClick = { selectedTabIndex = 2 },
                    text = {
                        Text(
                            "失败 (${failedTasks.size})",
                            style = MaterialTheme.typography.labelLarge
                        )
                    },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val list = when (selectedTabIndex) {
                0 -> activeTasks
                1 -> finishedTasks
                else -> failedTasks
            }

            if (list.isEmpty()) {
                EmptyState(
                    title = when (selectedTabIndex) {
                        0 -> "没有正在传输的任务"
                        1 -> "还没有完成的任务"
                        else -> "没有失败的任务"
                    },
                    subtitle = if (selectedTabIndex == 0) "在文件列表中点击下载即可开始" else "",
                    modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(list, key = { it.id }) { task ->
                        TransferTaskCard(
                            task = task,
                            onPause = { viewModel.pauseTask(it) },
                            onResume = { viewModel.resumeTask(it) },
                            onCancel = { viewModel.cancelTask(it) },
                            onDelete = { viewModel.deleteTaskRecord(it) }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun TransferTaskCard(
    task: TransferTask,
    onPause: (TransferTask) -> Unit,
    onResume: (TransferTask) -> Unit,
    onCancel: (TransferTask) -> Unit,
    onDelete: (TransferTask) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val statusConfig = when (task.status) {
        TransferStatus.DOWNLOADING -> StatusConfig(
            label = "下载中",
            badgeColor = MaterialTheme.colorScheme.primary,
            progressColor = MaterialTheme.colorScheme.primary
        )
        TransferStatus.PAUSED -> StatusConfig(
            label = "已暂停",
            badgeColor = MaterialTheme.colorScheme.secondary,
            progressColor = MaterialTheme.colorScheme.secondary
        )
        TransferStatus.PENDING -> StatusConfig(
            label = "等待中",
            badgeColor = MaterialTheme.colorScheme.outline,
            progressColor = MaterialTheme.colorScheme.outline
        )
        TransferStatus.SUCCESS -> StatusConfig(
            label = "已完成",
            badgeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            progressColor = MaterialTheme.colorScheme.primary
        )
        TransferStatus.ERROR -> StatusConfig(
            label = "失败",
            badgeColor = MaterialTheme.colorScheme.error,
            progressColor = MaterialTheme.colorScheme.error
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = statusConfig.badgeColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = statusConfig.label,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusConfig.badgeColor
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = task.fileName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        when (task.status) {
                            TransferStatus.DOWNLOADING -> {
                                DropdownMenuItem(
                                    text = { Text("暂停") },
                                    leadingIcon = { Icon(Icons.Default.Pause, contentDescription = null) },
                                    onClick = { onPause(task); menuExpanded = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("取消") },
                                    leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                                    onClick = { onCancel(task); menuExpanded = false }
                                )
                            }
                            TransferStatus.PAUSED -> {
                                DropdownMenuItem(
                                    text = { Text("恢复") },
                                    leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                                    onClick = { onResume(task); menuExpanded = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("取消") },
                                    leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                                    onClick = { onCancel(task); menuExpanded = false }
                                )
                            }
                            TransferStatus.PENDING -> {
                                DropdownMenuItem(
                                    text = { Text("取消") },
                                    leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                                    onClick = { onCancel(task); menuExpanded = false }
                                )
                            }
                            else -> {
                                DropdownMenuItem(
                                    text = { Text("删除记录") },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                    onClick = { onDelete(task); menuExpanded = false }
                                )
                            }
                        }
                    }
                }
            }

            if (task.status == TransferStatus.ERROR && !task.errorMsg.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = task.errorMsg ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (task.totalBytes > 0) {
                val progress = (task.downloadedBytes.toFloat() / task.totalBytes.toFloat()).coerceIn(0f, 1f)

                Spacer(modifier = Modifier.height(12.dp))

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = statusConfig.progressColor,
                    trackColor = statusConfig.progressColor.copy(alpha = 0.12f),
                    strokeCap = StrokeCap.Round,
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${formatBytes(task.downloadedBytes)} / ${formatBytes(task.totalBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = statusConfig.progressColor
                    )
                }
            }

            if (task.status == TransferStatus.PENDING && task.totalBytes <= 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "排队等待中...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

data class StatusConfig(
    val label: String,
    val badgeColor: androidx.compose.ui.graphics.Color,
    val progressColor: androidx.compose.ui.graphics.Color
)

fun formatBytes(size: Long): String {
    if (size <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(java.util.Locale.getDefault(), "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
