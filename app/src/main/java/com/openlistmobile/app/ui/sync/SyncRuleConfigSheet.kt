package com.openlistmobile.app.ui.sync

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.openlistmobile.app.data.local.ConflictStrategy
import com.openlistmobile.app.data.local.SyncMode
import com.openlistmobile.app.data.local.SyncRule
import com.openlistmobile.app.ui.components.clearFocusOnTap

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SyncRuleConfigSheet(
    existingRule: SyncRule? = null,
    onDismiss: () -> Unit,
    onCreate: (SyncRule, onResult: (Result<Long>) -> Unit) -> Unit,
    onUpdate: (SyncRule, onResult: (Result<Unit>) -> Unit) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val isEdit = existingRule != null

    var remotePath by remember { mutableStateOf(existingRule?.remotePath ?: "") }
    var localUri by remember { mutableStateOf(existingRule?.localUri ?: "") }
    var ruleName by remember { mutableStateOf(existingRule?.ruleName ?: "") }
    var syncMode by remember { mutableStateOf(existingRule?.syncMode ?: SyncMode.TWO_WAY) }
    var mirrorDelete by remember { mutableStateOf(existingRule?.isMirrorDeleteEnabled ?: false) }
    var conflictStrategy by remember {
        mutableStateOf(
            existingRule?.conflictStrategy ?: ConflictStrategy.NEWEST_WINS
        )
    }
    var ignoreTime by remember { mutableStateOf(existingRule?.ignoreModifiedTime ?: false) }
    var showCloudPicker by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val lockUpwardScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // If there's leftover upward scroll delta (available.y < 0), consume it
                // to prevent the ModalBottomSheet from bouncing/jumping up.
                return if (available.y < 0) available else Offset.Zero
            }
        }
    }

    val dirPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val flags =
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (_: Exception) {
            }
            localUri = uri.toString()
            if (ruleName.isBlank()) ruleName = readableLocalName(uri)
        }
    }

    if (showCloudPicker) {
        CloudDirPickerDialog(
            initialPath = remotePath.ifBlank { "/" },
            onDismiss = { showCloudPicker = false },
            onConfirm = { path ->
                remotePath = path
                showCloudPicker = false
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        windowInsets = WindowInsets(0)
    ) {
        CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clearFocusOnTap()
                    .nestedScroll(lockUpwardScrollConnection)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
            ) {
                Text(
                    if (isEdit) "编辑同步规则" else "新建同步规则",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = ruleName,
                    onValueChange = { ruleName = it },
                    label = { Text("规则名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))
                Text("目录映射", style = MaterialTheme.typography.titleSmall)

                ReadOnlyPathField(
                    label = "云端目录",
                    value = remotePath,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!isEdit) {
                    OutlinedButton(
                        onClick = {
                            focusManager.clearFocus(force = true)
                            showCloudPicker = true
                        },
                        modifier = Modifier.padding(top = 2.dp).height(36.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 12.dp,
                            vertical = 0.dp
                        )
                    ) {
                        Text("选择云端目录", style = MaterialTheme.typography.labelLarge)
                    }
                }

                ReadOnlyPathField(
                    label = "本地目录",
                    value = localUri,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )
                if (!isEdit) {
                    OutlinedButton(
                        onClick = {
                            focusManager.clearFocus(force = true)
                            dirPicker.launch(null)
                        },
                        modifier = Modifier.padding(top = 2.dp).height(36.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 12.dp,
                            vertical = 0.dp
                        )
                    ) {
                        Text("选择本地目录 (SAF)", style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    Text(
                        "基于1对1安全映射规则，路径不可更改，需删除规则后重建。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Spacer(Modifier.height(8.dp))
                Text("同步模式", style = MaterialTheme.typography.titleSmall)
                RuleRadio("云端 ➔ 本地（下载）", syncMode == SyncMode.DOWNLOAD_ONLY) {
                    syncMode = SyncMode.DOWNLOAD_ONLY
                }
                RuleRadio("本地 ➔ 云端（上传）", syncMode == SyncMode.UPLOAD_ONLY) {
                    syncMode = SyncMode.UPLOAD_ONLY
                }
                RuleRadio("双向同步", syncMode == SyncMode.TWO_WAY) { syncMode = SyncMode.TWO_WAY }

                // 镜像删除仅单向模式可用
                if (syncMode != SyncMode.TWO_WAY) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { mirrorDelete = !mirrorDelete }) {
                        Checkbox(checked = mirrorDelete, onCheckedChange = { mirrorDelete = it })
                        Text(
                            if (syncMode == SyncMode.DOWNLOAD_ONLY) "镜像模式：删除本地多余文件"
                            else "镜像模式：删除云端多余文件",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                if (syncMode == SyncMode.TWO_WAY) {
                    Spacer(Modifier.height(4.dp))
                    Text("冲突处理策略", style = MaterialTheme.typography.titleSmall)
                    RuleRadio(
                        "以最新修改时间为准",
                        conflictStrategy == ConflictStrategy.NEWEST_WINS
                    ) { conflictStrategy = ConflictStrategy.NEWEST_WINS }
                    RuleRadio(
                        "跳过冲突文件",
                        conflictStrategy == ConflictStrategy.SKIP
                    ) { conflictStrategy = ConflictStrategy.SKIP }
                    RuleRadio(
                        "始终以云端为准",
                        conflictStrategy == ConflictStrategy.CLOUD_WINS
                    ) { conflictStrategy = ConflictStrategy.CLOUD_WINS }
                    RuleRadio(
                        "始终以本地为准",
                        conflictStrategy == ConflictStrategy.LOCAL_WINS
                    ) { conflictStrategy = ConflictStrategy.LOCAL_WINS }
                }

                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = ignoreTime,
                        onCheckedChange = { ignoreTime = it },
                        modifier = Modifier.scale(0.8f)
                    )
                    Text(
                        "仅对比文件大小，忽略修改时间",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                Text(
                    "定时自动同步与网络约束本期暂未启用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )

                errorMsg?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = {
                        focusManager.clearFocus(force = true)
                        onDismiss()
                    }) { Text("取消") }
                    Button(onClick = {
                        focusManager.clearFocus(force = true)
                        when {
                            remotePath.isBlank() -> errorMsg = "请选择云端目录"
                            localUri.isBlank() -> errorMsg = "请选择本地目录"
                            isEdit -> {
                                val updated = existingRule!!.copy(
                                    ruleName = ruleName.ifBlank { "未命名规则" },
                                    syncMode = syncMode,
                                    isMirrorDeleteEnabled = mirrorDelete && syncMode != SyncMode.TWO_WAY,
                                    conflictStrategy = conflictStrategy,
                                    ignoreModifiedTime = ignoreTime
                                )
                                onUpdate(updated) { result ->
                                    result.fold(
                                        onSuccess = { onDismiss() },
                                        onFailure = { e -> errorMsg = e.message }
                                    )
                                }
                            }

                            else -> {
                                val rule = SyncRule(
                                    profileId = 0, // ViewModel 注入
                                    ruleName = ruleName.ifBlank { "未命名规则" },
                                    remotePath = remotePath,
                                    localUri = localUri,
                                    syncMode = syncMode,
                                    isMirrorDeleteEnabled = mirrorDelete && syncMode != SyncMode.TWO_WAY,
                                    conflictStrategy = conflictStrategy,
                                    ignoreModifiedTime = ignoreTime
                                )
                                onCreate(rule) { result ->
                                    result.fold(
                                        onSuccess = { onDismiss() },
                                        onFailure = { e -> errorMsg = e.message }
                                    )
                                }
                            }
                        }
                    }) { Text("保存") }
                }
            }
        }
    }
}
@Composable
private fun ReadOnlyPathField(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
        ) {
            Text(
                text = value.ifBlank { "未选择" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun RuleRadio(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(36.dp).clickable { onClick() }
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/** 从 SAF tree uri 提取一个可读的目录名作为默认规则名。 */
private fun readableLocalName(uri: Uri): String {
    val path = uri.path ?: return "同步规则"
    val seg = path.substringAfterLast(":").substringAfterLast("/")
    return seg.ifBlank { "同步规则" }
}
