package com.openlistmobile.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.openlistmobile.app.data.remote.model.SearchResultItem
import com.openlistmobile.app.ui.home.FilterCategory
import com.openlistmobile.app.ui.home.HomeUiState
import com.openlistmobile.app.ui.home.SizeFilter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchOverlay(
    uiState: HomeUiState,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onScopeToggle: (Boolean) -> Unit,
    onTypeFilter: (FilterCategory) -> Unit,
    onSizeFilter: (SizeFilter) -> Unit,
    onResultClick: (SearchResultItem) -> Unit,
    onLoadMore: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text("全局搜索") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "关闭")
                        }
                    }
                )

                OutlinedTextField(
                    value = uiState.searchKeywords,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    placeholder = { Text("输入关键词搜索...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() })
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Scope toggle
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = uiState.searchScopeGlobal,
                        onClick = { onScopeToggle(true) },
                        label = { Text("根目录") }
                    )
                    FilterChip(
                        selected = !uiState.searchScopeGlobal,
                        onClick = { onScopeToggle(false) },
                        label = { Text("当前目录") }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Type filter chips
                FlowRow(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterCategory.values().forEach { cat ->
                        FilterChip(
                            selected = uiState.searchTypeFilter == cat,
                            onClick = { onTypeFilter(cat) },
                            label = { Text(cat.label) }
                        )
                    }
                }

                // Size filter chips
                FlowRow(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SizeFilter.values().forEach { sf ->
                        FilterChip(
                            selected = uiState.searchSizeFilter == sf,
                            onClick = { onSizeFilter(sf) },
                            label = { Text(sf.label) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider()

                // Results area
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        uiState.isSearching && uiState.searchResults.isEmpty() -> {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                        uiState.searchError != null && uiState.searchResults.isEmpty() -> {
                            Text(
                                text = uiState.searchError ?: "",
                                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        uiState.rawSearchResults.isNotEmpty() && uiState.searchResults.isEmpty() -> {
                            Text(
                                text = "无匹配的过滤结果",
                                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        uiState.searchResults.isEmpty() && !uiState.isSearching -> {
                            Text(
                                text = "输入关键词开始搜索",
                                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 16.dp)
                            ) {
                                items(uiState.searchResults, key = { "${it.parent}/${it.name}" }) { item ->
                                    SearchResultRow(
                                        item = item,
                                        onClick = { onResultClick(item) }
                                    )

                                    if (item == uiState.searchResults.last() && uiState.rawSearchResults.size < uiState.searchTotal) {
                                        onLoadMore()
                                    }
                                }
                                if (uiState.isSearching) {
                                    item {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(item: SearchResultItem, onClick: () -> Unit) {
    val iconInfo = getSearchItemIcon(item)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = iconInfo.first,
            contentDescription = null,
            tint = iconInfo.second,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${item.parent}  ·  ${formatFileSize(item.size)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun getSearchItemIcon(item: SearchResultItem): Pair<ImageVector, Color> {
    if (item.is_dir) return Icons.Rounded.Folder to MaterialTheme.colorScheme.secondary

    val ext = item.name.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "mp3", "wav", "flac", "ogg", "m4a", "aac", "wma", "ape" -> Icons.Rounded.MusicNote to Color(0xFF9C27B0)
        "mp4", "mkv", "avi", "mov", "flv", "webm", "rmvb", "3gp", "ts" -> Icons.Rounded.Movie to Color(0xFFE91E63)
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "ico", "heic", "tiff" -> Icons.Rounded.Image to Color(0xFF00BCD4)
        "json", "yaml", "yml", "xml", "kt", "java", "py", "js", "html", "css", "sh", "bat", "c", "cpp", "h", "cs", "go", "rs", "sql" -> Icons.Rounded.Code to Color(0xFFFF9800)
        "pdf" -> Icons.Rounded.PictureAsPdf to Color(0xFFF44336)
        "doc", "docx", "odt", "rtf" -> Icons.Rounded.Description to Color(0xFF2196F3)
        "xls", "xlsx", "csv", "ods" -> Icons.Rounded.TableChart to Color(0xFF4CAF50)
        "ppt", "pptx", "odp" -> Icons.Rounded.Slideshow to Color(0xFFFF5722)
        "zip", "rar", "7z", "tar", "gz", "bz2", "xz" -> Icons.Rounded.FolderZip to Color(0xFFFFC107)
        "apk" -> Icons.Rounded.Android to Color(0xFF8BC34A)
        "txt", "md", "log", "ini", "conf" -> Icons.Rounded.Description to Color(0xFF757575)
        else -> Icons.AutoMirrored.Rounded.InsertDriveFile to MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    }
}
