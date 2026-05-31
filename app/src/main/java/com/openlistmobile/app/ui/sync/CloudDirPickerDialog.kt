package com.openlistmobile.app.ui.sync

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openlistmobile.app.data.remote.model.AListFile
import com.openlistmobile.app.domain.repository.FileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CloudDirPickerState(
    val currentPath: String = "/",
    val dirs: List<AListFile> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class CloudDirPickerViewModel @Inject constructor(
    private val fileRepository: FileRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CloudDirPickerState())
    val state: StateFlow<CloudDirPickerState> = _state.asStateFlow()

    fun load(path: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, currentPath = path) }
            fileRepository.getFileListFlow(path, page = 1, perPage = 0, refresh = true).collect { result ->
                result.fold(
                    onSuccess = { data ->
                        val dirs = (data.content ?: emptyList()).filter { it.is_dir }
                        _state.update { it.copy(isLoading = false, dirs = dirs) }
                    },
                    onFailure = { e ->
                        _state.update { it.copy(isLoading = false, error = e.message) }
                    }
                )
            }
        }
    }

    fun enter(name: String) {
        val cur = _state.value.currentPath
        load(if (cur == "/") "/$name" else "$cur/$name")
    }

    fun up() {
        val cur = _state.value.currentPath
        if (cur == "/") return
        load(cur.substringBeforeLast("/").ifEmpty { "/" })
    }
}

@Composable
fun CloudDirPickerDialog(
    initialPath: String = "/",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    viewModel: CloudDirPickerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.load(initialPath)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择云端目录") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.currentPath != "/") {
                        TextButton(onClick = { viewModel.up() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "上一级")
                            Text("上一级")
                        }
                    }
                    Text(
                        text = state.currentPath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                when {
                    state.isLoading -> Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.Center
                    ) { CircularProgressIndicator() }
                    state.error != null -> Text(
                        "加载失败: ${state.error}",
                        color = MaterialTheme.colorScheme.error
                    )
                    state.dirs.isEmpty() -> Text(
                        "（此目录下没有子目录）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                    else -> LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(state.dirs, key = { it.name }) { dir ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.enter(dir.name) }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(dir.name, modifier = Modifier.padding(start = 12.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.currentPath) }) { Text("选择此目录") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
