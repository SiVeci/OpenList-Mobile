package com.example.alist.ui.home

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.alist.data.remote.model.AListFile
import java.net.URLEncoder
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Dialog & Overlay States
    var showMkdirDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<AListFile?>(null) }
    var deleteTarget by remember { mutableStateOf<AListFile?>(null) }
    var previewImageUrl by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = uiState.currentPath != "/" || previewImageUrl != null) {
        if (previewImageUrl != null) {
            previewImageUrl = null
        } else {
            viewModel.navigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(uiState.currentProfile?.serverUrl ?: "OpenList")
                        if (uiState.currentPath != "/") {
                            Text(
                                text = uiState.currentPath,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (uiState.currentPath != "/") {
                        IconButton(onClick = { viewModel.navigateBack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (uiState.profiles.isNotEmpty()) {
                        var expanded by remember { mutableStateOf(false) }
                        IconButton(onClick = { expanded = true }) {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Switch Server")
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            uiState.profiles.forEach { profile ->
                                DropdownMenuItem(
                                    text = { Text(profile.serverUrl) },
                                    onClick = {
                                        viewModel.switchProfile(profile)
                                        expanded = false
                                    },
                                    leadingIcon = {
                                        if (profile.id == uiState.currentProfile?.id) {
                                            Text("✓", color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (uiState.profiles.isNotEmpty() && uiState.currentProfile != null) {
                FloatingActionButton(onClick = { showMkdirDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "New Folder")
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.profiles.isEmpty()) {
                LoginView(viewModel, uiState)
            } else {
                FileBrowserView(
                    viewModel = viewModel,
                    uiState = uiState,
                    onRenameRequest = { renameTarget = it },
                    onDeleteRequest = { deleteTarget = it },
                    onImagePreview = { previewImageUrl = it }
                )
            }
        }
    }

    // --- Dialogs ---
    if (showMkdirDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showMkdirDialog = false },
            title = { Text("新建文件夹") },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("文件夹名称") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (folderName.isNotBlank()) {
                        viewModel.createFolder(folderName.trim())
                    }
                    showMkdirDialog = false
                }) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showMkdirDialog = false }) { Text("取消") }
            }
        )
    }

    renameTarget?.let { file ->
        var newName by remember { mutableStateOf(file.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("新名称") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank() && newName != file.name) {
                        viewModel.renameFile(file, newName.trim())
                    }
                    renameTarget = null
                }) { Text("重命名") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("取消") }
            }
        )
    }

    deleteTarget?.let { file ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除确认") },
            text = { Text("确定要删除 '${file.name}' 吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeFile(file)
                    deleteTarget = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }

    // --- Image Preview Overlay ---
    previewImageUrl?.let { url ->
        Dialog(
            onDismissRequest = { previewImageUrl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                AsyncImage(
                    model = url,
                    contentDescription = "Image Preview",
                    modifier = Modifier.fillMaxSize()
                )
                IconButton(
                    onClick = { previewImageUrl = null },
                    modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun LoginView(viewModel: HomeViewModel, uiState: HomeUiState) {
    var serverUrl by remember { mutableStateOf("https://al.chirmyram.com") }
    var username by remember { mutableStateOf("admin") }
    var password by remember { mutableStateOf("123456") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(value = serverUrl, onValueChange = { serverUrl = it }, label = { Text("Server URL") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())

        Button(
            onClick = { viewModel.testLoginAndFetch(serverUrl, username, password) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isLoading
        ) {
            Text(if (uiState.isLoading) "Logging in..." else "Login")
        }

        if (uiState.error != null) {
            Text("Error: ${uiState.error}", color = MaterialTheme.colorScheme.error)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserView(
    viewModel: HomeViewModel,
    uiState: HomeUiState,
    onRenameRequest: (AListFile) -> Unit,
    onDeleteRequest: (AListFile) -> Unit,
    onImagePreview: (String) -> Unit
) {
    val context = LocalContext.current
    val pullToRefreshState = rememberPullToRefreshState()

    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            viewModel.refresh()
        }
    }

    LaunchedEffect(uiState.isRefreshing) {
        if (!uiState.isRefreshing) {
            pullToRefreshState.endRefresh()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterCategory.values().forEach { cat ->
                    FilterChip(
                        selected = uiState.filterCategory == cat,
                        onClick = { viewModel.updateFilterCategory(cat) },
                        label = { Text(cat.label) }
                    )
                }
            }
            Box {
                var expanded by remember { mutableStateOf(false) }
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Default.Sort, contentDescription = "Sort")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text("按名称 ${if(uiState.sortBy == SortBy.Name) "✓" else ""}") }, onClick = { viewModel.updateSortBy(SortBy.Name); expanded = false })
                    DropdownMenuItem(text = { Text("按大小 ${if(uiState.sortBy == SortBy.Size) "✓" else ""}") }, onClick = { viewModel.updateSortBy(SortBy.Size); expanded = false })
                    DropdownMenuItem(text = { Text("按时间 ${if(uiState.sortBy == SortBy.Time) "✓" else ""}") }, onClick = { viewModel.updateSortBy(SortBy.Time); expanded = false })
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(if (uiState.sortOrder == SortOrder.Asc) "正序 (点击切换)" else "倒序 (点击切换)") },
                        onClick = {
                            viewModel.updateSortOrder(if (uiState.sortOrder == SortOrder.Asc) SortOrder.Desc else SortOrder.Asc)
                            expanded = false
                        }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("文件夹置顶") },
                        trailingIcon = { Switch(checked = uiState.foldersOnTop, onCheckedChange = null) },
                        onClick = { viewModel.updateFoldersOnTop(!uiState.foldersOnTop); expanded = false }
                    )
                }
            }
        }
        OutlinedTextField(
            value = uiState.filterSuffix,
            onValueChange = { viewModel.updateFilterSuffix(it) },
            placeholder = { Text("精确后缀过滤 (如 .mp4)") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(56.dp),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))

        Box(modifier = Modifier
            .weight(1f)
            .nestedScroll(pullToRefreshState.nestedScrollConnection)) {
            if (uiState.isLoading && uiState.files.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.error != null && uiState.files.isEmpty()) {
                Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(uiState.error!!, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { viewModel.refresh() }) { Text("Retry") }
                }
            } else if (uiState.files.isEmpty()) {
                Text("Empty Directory", modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.files.size) { index ->
                        val file = uiState.files[index]
                        
                        val ext = file.name.substringAfterLast('.').lowercase()
                        val isVideo = ext in listOf("mp4", "mkv", "avi", "mov", "flv", "webm")
                        val isImage = ext in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
                        val isDoc = ext in listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx")

                        val icon = if (file.is_dir) Icons.Rounded.Folder
                        else when {
                            isVideo -> Icons.Rounded.Movie
                            isImage -> Icons.Rounded.Image
                            else -> Icons.Rounded.InsertDriveFile
                        }

                        val timeString = try {
                            file.modified.substringBefore("T") + " " + file.modified.substringAfter("T").substringBeforeLast(":")
                        } catch (e: Exception) {
                            file.modified
                        }
                        
                        val sizeString = if (file.is_dir) "" else formatFileSize(file.size)
                        
                        ListItem(
                            headlineContent = { Text(file.name) },
                            supportingContent = { Text("$timeString  $sizeString".trim()) },
                            leadingContent = { Icon(icon, contentDescription = null, tint = if (file.is_dir) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) },
                            trailingContent = {
                                Box {
                                    var moreMenuExpanded by remember { mutableStateOf(false) }
                                    IconButton(onClick = { moreMenuExpanded = true }) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                                    }
                                    DropdownMenu(expanded = moreMenuExpanded, onDismissRequest = { moreMenuExpanded = false }) {
                                        DropdownMenuItem(
                                            text = { Text("重命名") },
                                            onClick = {
                                                moreMenuExpanded = false
                                                onRenameRequest(file)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                                            onClick = {
                                                moreMenuExpanded = false
                                                onDeleteRequest(file)
                                            }
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.clickable {
                                if (file.is_dir) {
                                    viewModel.navigateToFolder(file.name)
                                } else {
                                    val directLink = viewModel.generateDirectLink(file) ?: return@clickable
                                    when {
                                        isImage -> {
                                            onImagePreview(directLink)
                                        }
                                        isVideo -> {
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(Uri.parse(directLink), "video/*")
                                            }
                                            context.startActivity(Intent.createChooser(intent, "选择播放器"))
                                        }
                                        isDoc -> {
                                            val encodedUrl = URLEncoder.encode(directLink, "UTF-8")
                                            val previewUrl = "https://view.officeapps.live.com/op/view.aspx?src=$encodedUrl"
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(previewUrl))
                                            context.startActivity(intent)
                                        }
                                    }
                                }
                            }
                        )

                        if (index == uiState.files.size - 1 && uiState.hasMore) {
                            LaunchedEffect(Unit) {
                                viewModel.loadMore()
                            }
                            Box(modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    item {
                        // Pad bottom for FAB
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }

            PullToRefreshContainer(
                state = pullToRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

fun formatFileSize(size: Long): String {
    if (size <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(Locale.getDefault(), "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}