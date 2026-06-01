package com.openlistmobile.app.ui.sync

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.openlistmobile.app.data.local.SyncStatus
import com.openlistmobile.app.domain.sync.DiffReport
import com.openlistmobile.app.ui.transfer.formatBytes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    onNavigateBack: () -> Unit,
    viewModel: SyncViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showRuleMenu by remember { mutableStateOf(false) }
    var showNewSheet by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<com.openlistmobile.app.data.local.SyncRule?>(null) }

    val rotationAngle by animateFloatAsState(
        targetValue = if (showRuleMenu) 180f else 0f,
        label = "arrowRotation"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { showRuleMenu = true }
                        ) {
                            Text(
                                state.selectedRule?.ruleName ?: "同步中心",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "切换规则",
                                modifier = Modifier
                                    .size(18.dp)
                                    .rotate(rotationAngle)
                            )
                        }
                        DropdownMenu(expanded = showRuleMenu, onDismissRequest = { showRuleMenu = false }) {
                            if (state.rules.isEmpty()) {
                                DropdownMenuItem(text = { Text("暂无规则") }, onClick = { showRuleMenu = false })
                            }
                            state.rules.forEach { rule ->
                                DropdownMenuItem(
                                    text = { Text(rule.ruleName) },
                                    onClick = {
                                        viewModel.selectRule(rule)
                                        showRuleMenu = false
                                    },
                                    trailingIcon = {
                                        Row {
                                            IconButton(onClick = {
                                                editingRule = rule
                                                showRuleMenu = false
                                            }) {
                                                Icon(Icons.Rounded.Settings, contentDescription = "编辑规则")
                                            }
                                            IconButton(onClick = { viewModel.deleteRule(rule); showRuleMenu = false }) {
                                                Icon(Icons.Rounded.Delete, contentDescription = "删除规则")
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showNewSheet = true }) {
                        Icon(Icons.Rounded.Add, contentDescription = "新增规则")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            SyncActionBar(
                status = state.syncStatus,
                progress = state.overallProgress,
                remaining = state.remainingCount,
                enabled = state.selectedRule != null,
                previewReady = state.previewReady,
                diff = state.lastDiff,
                onPreview = { viewModel.previewSync() },
                onExecute = { viewModel.executeSync() }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                state.selectedRule == null -> EmptyHint("点击右上角 + 新建一个同步规则")
                else -> DynamicSplitView(
                    state = state,
                    syncMode = state.selectedRule?.syncMode ?: com.openlistmobile.app.data.local.SyncMode.TWO_WAY,
                    onToggleOrientation = { viewModel.toggleSplitOrientation() }
                )
            }
        }
    }

    if (showNewSheet) {
        SyncRuleConfigSheet(
            onDismiss = { showNewSheet = false },
            onCreate = { rule, onResult -> viewModel.createRule(rule, onResult) }
        )
    }

    editingRule?.let { rule ->
        SyncRuleConfigSheet(
            existingRule = rule,
            onDismiss = { editingRule = null },
            onCreate = { _, _ -> },
            onUpdate = { updated, onResult -> viewModel.updateRule(updated, onResult) }
        )
    }

    state.pendingConfirm?.let { diff ->
        SyncConfirmDialog(
            diff = diff,
            onConfirm = { deleteApproved -> viewModel.confirmSync(deleteApproved) },
            onDismiss = { viewModel.cancelConfirm() }
        )
    }
}

@Composable
private fun DynamicSplitView(
    state: SyncUiState,
    syncMode: com.openlistmobile.app.data.local.SyncMode,
    onToggleOrientation: () -> Unit
) {
    AnimatedContent(targetState = state.isSplitVertical, label = "split") { vertical ->
        if (vertical) {
            Column(modifier = Modifier.fillMaxSize()) {
                CloudPane(state, Modifier.fillMaxWidth().weight(1f))
                SeamRow(onToggleOrientation, vertical = true, syncMode = syncMode)
                LocalPane(state, Modifier.fillMaxWidth().weight(1f))
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                CloudPane(state, Modifier.fillMaxHeight().weight(1f))
                SeamRow(onToggleOrientation, vertical = false, syncMode = syncMode)
                LocalPane(state, Modifier.fillMaxHeight().weight(1f))
            }
        }
    }
}

@Composable
private fun SeamRow(
    onToggle: () -> Unit,
    vertical: Boolean,
    syncMode: com.openlistmobile.app.data.local.SyncMode
) {
    // 布局：云端在前(左/上)，本地在后(右/下)。图标表达同步方向，点击仍切换布局朝向。
    val icon = when (syncMode) {
        com.openlistmobile.app.data.local.SyncMode.DOWNLOAD_ONLY ->
            if (vertical) Icons.Rounded.ArrowDownward else Icons.AutoMirrored.Rounded.ArrowForward
        com.openlistmobile.app.data.local.SyncMode.UPLOAD_ONLY ->
            if (vertical) Icons.Rounded.ArrowUpward else Icons.AutoMirrored.Rounded.ArrowBack
        com.openlistmobile.app.data.local.SyncMode.TWO_WAY ->
            if (vertical) Icons.Rounded.SwapVert else Icons.Rounded.SwapHoriz
    }
    Box(contentAlignment = Alignment.Center) {
        if (vertical) HorizontalDivider() else VerticalDivider()
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp).clickable { onToggle() }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = "同步方向（点击切换分屏朝向）",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun CloudPane(state: SyncUiState, modifier: Modifier) {
    Column(modifier = modifier.background(MaterialTheme.colorScheme.surface).padding(8.dp)) {
        Text("云端：${state.selectedRule?.remotePath ?: ""}", style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        if (state.isLoadingSides) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(state.cloudFiles, key = { it.relativePath }) { f ->
                    FileRow(displayPath = f.relativePath, size = f.size, badge = state.cloudBadges[f.relativePath])
                }
            }
        }
    }
}

@Composable
private fun LocalPane(state: SyncUiState, modifier: Modifier) {
    Column(modifier = modifier.background(MaterialTheme.colorScheme.background).padding(8.dp)) {
        Text("本地", style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        if (state.isLoadingSides) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(state.localEntries, key = { it.relativePath }) { f ->
                    FileRow(displayPath = f.relativePath, size = f.size, badge = state.localBadges[f.relativePath])
                }
            }
        }
    }
}

@Composable
private fun FileRow(displayPath: String, size: Long, badge: SyncBadge?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(
            Icons.Rounded.InsertDriveFile,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            Text(displayPath, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(formatBytes(size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        badge?.let { BadgeChip(it) }
    }
}

@Composable
private fun BadgeChip(badge: SyncBadge) {
    val (label, color) = when (badge) {
        SyncBadge.CLOUD_NEW -> "⬇下载" to MaterialTheme.colorScheme.primary
        SyncBadge.LOCAL_NEW -> "⬆上传" to MaterialTheme.colorScheme.tertiary
        SyncBadge.OVERWRITE -> "覆盖" to MaterialTheme.colorScheme.secondary
        SyncBadge.DELETE -> "待删除" to MaterialTheme.colorScheme.error
        SyncBadge.SKIP -> "跳过" to MaterialTheme.colorScheme.outline
        SyncBadge.SAME -> "一致" to MaterialTheme.colorScheme.outline
        SyncBadge.NONE -> return
    }
    Surface(shape = MaterialTheme.shapes.small, color = color.copy(alpha = 0.15f)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

@Composable
private fun SyncActionBar(
    status: SyncStatus,
    progress: Float,
    remaining: Int,
    enabled: Boolean,
    previewReady: Boolean,
    diff: DiffReport?,
    onPreview: () -> Unit,
    onExecute: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            if (status == SyncStatus.SYNCING || remaining > 0) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(6.dp))
                Text("剩余 $remaining 项", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(8.dp))
            }

            // 预览就绪后显示动作摘要
            if (previewReady && diff != null) {
                val overwrites = (diff.toDownload + diff.toUpload).count { it.reason.contains("覆盖") || it.reason.contains("冲突") }
                val deletes = diff.toDeleteLocal.size + diff.toDeleteRemote.size
                Text(
                    "待下载 ${diff.toDownload.size} · 待上传 ${diff.toUpload.size} · 待删除 $deletes · 待覆盖 $overwrites",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            androidx.compose.material3.Button(
                onClick = { if (previewReady) onExecute() else onPreview() },
                enabled = enabled && status != SyncStatus.DIFFING && status != SyncStatus.SYNCING,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    when {
                        status == SyncStatus.DIFFING -> "正在比对..."
                        status == SyncStatus.SYNCING -> "同步中..."
                        previewReady -> "执行同步"
                        else -> "同步预览"
                    }
                )
            }
        }
    }
}

@Composable
private fun SyncConfirmDialog(diff: DiffReport, onConfirm: (Boolean) -> Unit, onDismiss: () -> Unit) {
    val deleteItems = diff.toDeleteLocal + diff.toDeleteRemote
    val overwriteItems = (diff.toDownload + diff.toUpload).filter { it.reason.contains("覆盖") || it.reason.contains("冲突") }
    val hasDeletes = deleteItems.isNotEmpty()
    val hasOverwrites = overwriteItems.isNotEmpty()
    var deleteApproved by remember { mutableStateOf(false) }
    var overwriteApproved by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认同步") },
        text = {
            Column {
                Text("待下载：${diff.toDownload.size} 项")
                Text("待上传：${diff.toUpload.size} 项")

                if (hasOverwrites) {
                    Spacer(Modifier.height(12.dp))
                    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.small) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                "⚠ 覆盖（原文件将被替换）",
                                color = MaterialTheme.colorScheme.tertiary,
                                style = MaterialTheme.typography.titleSmall
                            )
                            overwriteItems.forEach {
                                Text("• ${it.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { overwriteApproved = !overwriteApproved }) {
                                Checkbox(checked = overwriteApproved, onCheckedChange = { overwriteApproved = it })
                                Text("我确认覆盖这些文件", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                if (hasDeletes) {
                    Spacer(Modifier.height(12.dp))
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                "⚠ 镜像删除（不可恢复）",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.titleSmall
                            )
                            deleteItems.forEach {
                                Text("• ${it.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { deleteApproved = !deleteApproved }) {
                                Checkbox(checked = deleteApproved, onCheckedChange = { deleteApproved = it })
                                Text("我确认删除这些文件", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                if (!diff.hasTransfers && !hasDeletes) {
                    Text("两端已一致，无需同步。", modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            val canConfirm = (!hasDeletes || deleteApproved) && (!hasOverwrites || overwriteApproved)
            TextButton(onClick = { onConfirm(deleteApproved) }, enabled = canConfirm) { Text("确认同步") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
