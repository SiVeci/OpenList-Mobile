package com.example.alist.ui.home

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.VerticalAlignBottom
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.alist.data.remote.model.AListFile
import com.example.alist.ui.components.*
import java.net.URLEncoder

import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalFocusManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToTransfer: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var renameTarget by remember { mutableStateOf<AListFile?>(null) }
    var deleteTarget by remember { mutableStateOf<AListFile?>(null) }
    var previewImageUrl by remember { mutableStateOf<String?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.uploadSharedFiles(context, uris)
        }
    }

    BackHandler(enabled = uiState.currentPath != "/" || uiState.isSelectionMode || previewImageUrl != null || uiState.previewTextContent != null || uiState.isPreviewingTextLoading) {
        if (previewImageUrl != null) {
            previewImageUrl = null
        } else if (uiState.previewTextContent != null || uiState.isPreviewingTextLoading) {
            viewModel.clearTextPreview()
        } else if (uiState.isSelectionMode) {
            viewModel.clearSelection()
        } else {
            viewModel.navigateBack()
        }
    }

    Scaffold(
        modifier = Modifier.pointerInput(Unit) {
            detectTapGestures(onTap = {
                focusManager.clearFocus()
            })
        },
        topBar = {
            if (uiState.currentProfile != null) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    HomeTopBar(
                        title = uiState.currentProfile?.aliasName?.takeIf { it.isNotBlank() } ?: "ALIST",
                        onTransferClick = onNavigateToTransfer,
                        onLogoutClick = { viewModel.logout() }
                    )
                }
            }
        },
        floatingActionButton = {
            if (uiState.profiles.isNotEmpty() && uiState.currentProfile != null) {
                // Large Purple Upload FAB
                FloatingActionButton(
                    onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = "Upload File",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        },
        floatingActionButtonPosition = FabPosition.End,
        bottomBar = {
            Column {
                // Shared Files Upload Bar
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
                                "上传 ${sharedUris.size} 个文件",
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

                // Selection Mode Contextual Bar
                AnimatedVisibility(
                    visible = uiState.isSelectionMode,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { viewModel.clearSelection() }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear Selection")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${uiState.selectedFiles.size} SELECTED",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.weight(1f)
                            )
                            
                            IconButton(
                                onClick = { viewModel.batchDownloadSelected(context) },
                                enabled = uiState.selectedFiles.none { it.is_dir }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.VerticalAlignBottom,
                                    contentDescription = "Download",
                                    tint = if (uiState.selectedFiles.any { it.is_dir }) 
                                        MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.3f) 
                                    else MaterialTheme.colorScheme.inverseOnSurface
                                )
                            }
                            IconButton(onClick = { /* TODO: Batch Copy */ }) {
                                Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy")
                            }
                            IconButton(
                                onClick = { 
                                    viewModel.batchDeleteSelected()
                                },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = Color.White)
                            }
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.currentProfile == null) {
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
        // --- Search Bar (Level 1 Tint) ---
        var showSortMenu by remember { mutableStateOf(false) }
        var showFilterMenu by remember { mutableStateOf(false) }

        Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest) {
            HomeSearchBar(
                query = uiState.filterSuffix,
                onQueryChange = { viewModel.updateFilterSuffix(it) },
                isSelectionMode = uiState.isSelectionMode,
                isAllSelected = uiState.selectedFiles.size == uiState.files.size && uiState.files.isNotEmpty(),
                onToggleSelectAll = {
                    if (uiState.selectedFiles.size == uiState.files.size) viewModel.clearSelection()
                    else viewModel.selectAll()
                },
                onFilterClick = { showFilterMenu = true },
                onSortClick = { showSortMenu = true },
                isGridView = uiState.isGridView,
                onToggleView = { viewModel.toggleViewMode() }
            )
        }

        Box {
            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Name ${if (uiState.sortBy == SortBy.Name) "✓" else ""}") },
                    onClick = { viewModel.updateSortBy(SortBy.Name); showSortMenu = false }
                )
                DropdownMenuItem(
                    text = { Text("Size ${if (uiState.sortBy == SortBy.Size) "✓" else ""}") },
                    onClick = { viewModel.updateSortBy(SortBy.Size); showSortMenu = false }
                )
                DropdownMenuItem(
                    text = { Text("Time ${if (uiState.sortBy == SortBy.Time) "✓" else ""}") },
                    onClick = { viewModel.updateSortBy(SortBy.Time); showSortMenu = false }
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(if (uiState.sortOrder == SortOrder.Asc) "Ascending" else "Descending") },
                    onClick = { 
                        viewModel.updateSortOrder(if (uiState.sortOrder == SortOrder.Asc) SortOrder.Desc else SortOrder.Asc)
                        showSortMenu = false 
                    }
                )
            }
            DropdownMenu(expanded = showFilterMenu, onDismissRequest = { showFilterMenu = false }) {
                FilterCategory.values().forEach { cat ->
                    DropdownMenuItem(
                        text = { Text("${cat.label} ${if (uiState.filterCategory == cat) "✓" else ""}") },
                        onClick = { viewModel.updateFilterCategory(cat); showFilterMenu = false }
                    )
                }
            }
        }

        // --- Breadcrumbs (Level 2 Tint) ---
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            BreadcrumbNavigation(
                path = uiState.currentPath,
                onPathClick = { path ->
                    viewModel.fetchFiles(path)
                }
            )
        }

        // --- File List (Level 3 Tint - inherited from Scaffold) ---
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
                .fillMaxWidth()
                .nestedScroll(pullToRefreshState.nestedScrollConnection)
                .clipToBounds()
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
                        title = "No files found",
                        subtitle = "This folder is empty",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    if (uiState.isGridView) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(120.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp, start = 12.dp, end = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(uiState.files, key = { it.name + it.modified }) { file ->
                                val isSelected = uiState.selectedFiles.contains(file)
                                // Note: FileGridItem is currently a legacy component, we should ideally reuse FileItemCard or update FileGridItem for selection
                                FileGridItem(
                                    file = file,
                                    viewModel = viewModel,
                                    context = context,
                                    onRenameRequest = { /* TODO */ },
                                    onDeleteRequest = { /* TODO */ },
                                    onImagePreview = onImagePreview
                                )
                                // Add click handling logic for Grid if needed, but for now we focus on List as per screenshot
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp)
                        ) {
                            items(uiState.files, key = { it.name + it.modified }) { file ->
                                val isSelected = uiState.selectedFiles.contains(file)
                                FileItemCard(
                                    file = file,
                                    isSelected = isSelected,
                                    isSelectionMode = uiState.isSelectionMode,
                                    onClick = {
                                        if (uiState.isSelectionMode) {
                                            viewModel.toggleFileSelection(file)
                                        } else {
                                            handleFileClick(file, viewModel, context, onImagePreview = onImagePreview)
                                        }
                                    },
                                    onLongClick = {
                                        if (!uiState.isSelectionMode) {
                                            viewModel.toggleSelectionMode(true)
                                            viewModel.toggleFileSelection(file)
                                        }
                                    }
                                )

                                if (file == uiState.files.last() && uiState.hasMore) {
                                    LaunchedEffect(Unit) {
                                        viewModel.loadMore()
                                    }
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
