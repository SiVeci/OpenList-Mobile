package com.example.alist.ui.home

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.alist.data.remote.model.AListFile
import com.example.alist.ui.components.EmptyState
import com.example.alist.ui.components.ErrorState
import com.example.alist.ui.components.FileItemCard
import com.example.alist.ui.components.TextPreviewOverlay
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToTransfer: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var showMkdirDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<AListFile?>(null) }
    var deleteTarget by remember { mutableStateOf<AListFile?>(null) }
    var previewImageUrl by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = uiState.currentPath != "/" || previewImageUrl != null || uiState.previewTextContent != null || uiState.isPreviewingTextLoading) {
        if (previewImageUrl != null) {
            previewImageUrl = null
        } else if (uiState.previewTextContent != null || uiState.isPreviewingTextLoading) {
            viewModel.clearTextPreview()
        } else {
            viewModel.navigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.currentProfile?.serverUrl ?: "OpenList",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (uiState.currentPath != "/") {
                            Text(
                                text = uiState.currentPath,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
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
                        IconButton(onClick = onNavigateToTransfer) {
                            Icon(Icons.Default.Download, contentDescription = "Transfer Manager")
                        }
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            if (uiState.profiles.isNotEmpty() && uiState.currentProfile != null) {
                FloatingActionButton(
                    onClick = { showMkdirDialog = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Folder")
                }
            }
        },
        bottomBar = {
            val sharedUris by viewModel.shareManager.sharedUris.collectAsState()
            if (sharedUris.isNotEmpty()) {
                Surface(
                    tonalElevation = 3.dp,
                    shadowElevation = 8.dp,
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "上传 ${sharedUris.size} 个文件到当前目录",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { viewModel.shareManager.clearSharedUris() }) {
                                Text("取消")
                            }
                            Button(onClick = { viewModel.uploadSharedFiles(context, sharedUris) }) {
                                Text("上传")
                            }
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
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

    // --- Text Preview Overlay ---
    TextPreviewOverlay(
        content = uiState.previewTextContent,
        isLoading = uiState.isPreviewingTextLoading,
        fileName = uiState.previewTextFileName,
        onDismiss = { viewModel.clearTextPreview() }
    )
}

@Composable
fun LoginView(viewModel: HomeViewModel, uiState: HomeUiState) {
    var serverUrl by remember { mutableStateOf("https://al.chirmyram.com") }
    var username by remember { mutableStateOf("admin") }
    var password by remember { mutableStateOf("123456") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "OpenList",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "连接到您的 AList 服务器",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            label = { Text("Server URL") },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        )
        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = { viewModel.testLoginAndFetch(serverUrl, username, password) },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled = !uiState.isLoading,
            shape = MaterialTheme.shapes.medium
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(if (uiState.isLoading) "连接中..." else "登录")
        }

        if (uiState.error != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Text(
                    text = uiState.error!!,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall
                )
            }
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
        // --- Filter & Sort Bar ---
        Surface(
            tonalElevation = 1.dp,
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp, top = 4.dp),
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
                                label = {
                                    Text(
                                        cat.label,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                shape = MaterialTheme.shapes.small
                            )
                        }
                    }
                    Box {
                        var expanded by remember { mutableStateOf(false) }
                        IconButton(onClick = { expanded = true }) {
                            Icon(Icons.Default.Sort, contentDescription = "Sort")
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            DropdownMenuItem(
                                text = { Text("按名称 ${if (uiState.sortBy == SortBy.Name) "✓" else ""}") },
                                onClick = { viewModel.updateSortBy(SortBy.Name); expanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("按大小 ${if (uiState.sortBy == SortBy.Size) "✓" else ""}") },
                                onClick = { viewModel.updateSortBy(SortBy.Size); expanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("按时间 ${if (uiState.sortBy == SortBy.Time) "✓" else ""}") },
                                onClick = { viewModel.updateSortBy(SortBy.Time); expanded = false }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = {
                                    Text(if (uiState.sortOrder == SortOrder.Asc) "正序 (点击切换)" else "倒序 (点击切换)")
                                },
                                onClick = {
                                    viewModel.updateSortOrder(
                                        if (uiState.sortOrder == SortOrder.Asc) SortOrder.Desc else SortOrder.Asc
                                    )
                                    expanded = false
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("文件夹置顶") },
                                trailingIcon = {
                                    Switch(
                                        checked = uiState.foldersOnTop,
                                        onCheckedChange = null
                                    )
                                },
                                onClick = {
                                    viewModel.updateFoldersOnTop(!uiState.foldersOnTop)
                                    expanded = false
                                }
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
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // --- File List ---
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .nestedScroll(pullToRefreshState.nestedScrollConnection)
        ) {
            when {
                uiState.isLoading && uiState.files.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "加载中...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                uiState.error != null && uiState.files.isEmpty() -> {
                    ErrorState(
                        message = uiState.error!!,
                        onRetry = { viewModel.refresh() },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.files.isEmpty() -> {
                    EmptyState(
                        title = "空目录",
                        subtitle = "点击右下角按钮新建文件夹",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        if (maxWidth > 600.dp) {
                            val columns = if (maxWidth > 900.dp) 3 else 2
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(columns),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(uiState.files.size) { index ->
                                    FileGridItem(
                                        file = uiState.files[index],
                                        viewModel = viewModel,
                                        context = context,
                                        onRenameRequest = onRenameRequest,
                                        onDeleteRequest = onDeleteRequest,
                                        onImagePreview = onImagePreview
                                    )
                                }
                                item {
                                    Spacer(modifier = Modifier.height(80.dp))
                                }
                            }
                        } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(uiState.files.size) { index ->
                                val file = uiState.files[index]
                                FileItemCard(
                                    file = file,
                                    onClick = {
                                        handleFileClick(file, viewModel, context, onImagePreview = onImagePreview)
                                    },
                                    trailingContent = {
                                        var moreMenuExpanded by remember { mutableStateOf(false) }
                                        Box {
                                            IconButton(onClick = { moreMenuExpanded = true }) {
                                                Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                                            }
                                            DropdownMenu(
                                                expanded = moreMenuExpanded,
                                                onDismissRequest = { moreMenuExpanded = false }
                                            ) {
                                                if (!file.is_dir) {
                                                    DropdownMenuItem(
                                                        text = { Text("分享直链") },
                                                        onClick = {
                                                            moreMenuExpanded = false
                                                            val url = viewModel.generateDirectLink(file)
                                                            if (url != null) {
                                                                val sendIntent = Intent().apply {
                                                                    action = Intent.ACTION_SEND
                                                                    putExtra(Intent.EXTRA_TEXT, url)
                                                                    type = "text/plain"
                                                                }
                                                                val shareIntent = Intent.createChooser(sendIntent, "分享直链")
                                                                context.startActivity(shareIntent)
                                                            }
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("下载文件") },
                                                        onClick = {
                                                            moreMenuExpanded = false
                                                            if (!com.example.alist.utils.PermissionUtils.hasNotificationPermission(context)) {
                                                                com.example.alist.utils.PermissionUtils.requestNotificationPermission(context as android.app.Activity, 1001)
                                                            } else {
                                                                viewModel.startDownload(context, file)
                                                            }
                                                        }
                                                    )
                                                }
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
                                    }
                                )

                                if (index == uiState.files.size - 1 && uiState.hasMore) {
                                    LaunchedEffect(Unit) {
                                        viewModel.loadMore()
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp
                                        )
                                    }
                                }
                            }
                            item {
                                Spacer(modifier = Modifier.height(80.dp))
                            }
                        }
                    }
                    }
                }
            }

            PullToRefreshContainer(
                state = pullToRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun handleFileClick(
    file: AListFile,
    viewModel: HomeViewModel,
    context: android.content.Context,
    onImagePreview: (String) -> Unit
) {
    if (file.is_dir) {
        viewModel.navigateToFolder(file.name)
        return
    }

    val ext = file.name.substringAfterLast('.').lowercase()
    val isText = ext in listOf("txt", "json", "yaml", "yml", "xml", "js", "kt", "md", "ini", "conf", "sh", "bat", "log", "csv")
    val isVideo = ext in listOf("mp4", "mkv", "avi", "mov", "flv", "webm")
    val isImage = ext in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
    val isDoc = ext in listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx")

    if (isText) {
        viewModel.loadTextPreview(file)
        return
    }

    val directLink = viewModel.generateDirectLink(file) ?: return

    when {
        isImage -> onImagePreview(directLink)
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

@Composable
fun FileGridItem(
    file: AListFile,
    viewModel: HomeViewModel,
    context: android.content.Context,
    onRenameRequest: (AListFile) -> Unit,
    onDeleteRequest: (AListFile) -> Unit,
    onImagePreview: (String) -> Unit
) {
    val ext = file.name.substringAfterLast('.').lowercase()
    val isVideo = ext in listOf("mp4", "mkv", "avi", "mov", "flv", "webm")
    val isImage = ext in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp")

    val icon = if (file.is_dir) Icons.Rounded.Folder
    else when {
        isVideo -> Icons.Rounded.Movie
        isImage -> Icons.Rounded.Image
        else -> Icons.Rounded.InsertDriveFile
    }

    val iconTint = if (file.is_dir) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant

    val timeString = try {
        file.modified.substringBefore("T") + " " + file.modified.substringAfter("T").substringBeforeLast(":")
    } catch (e: Exception) {
        file.modified
    }

    val sizeString = if (file.is_dir) "" else com.example.alist.ui.components.formatFileSize(file.size)
    val metaText = "$timeString  $sizeString".trim()

    Card(
        onClick = {
            handleFileClick(file, viewModel, context, onImagePreview = onImagePreview)
        },
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
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.small)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                textAlign = TextAlign.Center
            )
            if (metaText.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = metaText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 1
                )
            }
        }
    }
}
