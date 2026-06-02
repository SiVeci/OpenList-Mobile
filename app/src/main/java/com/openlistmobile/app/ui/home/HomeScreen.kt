package com.openlistmobile.app.ui.home

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import kotlinx.coroutines.launch
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.Folder
import com.openlistmobile.app.ui.sync.CloudDirPickerDialog
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
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
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.openlistmobile.app.data.remote.model.AListFile
import com.openlistmobile.app.ui.components.*
import java.net.URLEncoder

import androidx.compose.ui.platform.LocalFocusManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSync: () -> Unit,
    onNavigateToTransfer: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var deleteTarget by remember { mutableStateOf<AListFile?>(null) }
    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.uploadSharedFiles(context, uris)
        }
    }

    val dirPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.setDownloadDirectory(uri, context)
        }
    }

    BackHandler(enabled = uiState.currentPath != "/" || uiState.isSelectionMode || previewImageUrl != null || uiState.previewTextContent != null || uiState.isPreviewingTextLoading || uiState.isSearchActive) {
        if (uiState.isSearchActive) {
            viewModel.closeSearch()
        } else if (previewImageUrl != null) {
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
        modifier = Modifier.clearFocusOnTap(),
        topBar = {
            if (uiState.currentProfile != null) {
                Surface(color = Color(0xFFFDFDFF)) {
                    HomeTopBar(
                        title = uiState.currentProfile?.aliasName?.takeIf { it.isNotBlank() } ?: "ALIST",
                        onSyncClick = onNavigateToSync,
                        onTransferClick = onNavigateToTransfer,
                        onSettingsClick = { showSettingsDialog = true },
                        onLogoutClick = { viewModel.logout() }
                    )
                }
            }
        },
        floatingActionButton = {
            if (uiState.profiles.isNotEmpty() && uiState.currentProfile != null) {
                Box {
                    var showFabMenu by remember { mutableStateOf(false) }
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val scale by animateFloatAsState(
                        targetValue = if (isPressed) 0.9f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "FabScale"
                    )

                    FloatingActionButton(
                        onClick = { showFabMenu = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                        shape = RoundedCornerShape(20.dp),
                        interactionSource = interactionSource,
                        modifier = Modifier
                            .size(64.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Actions",
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showFabMenu,
                        onDismissRequest = { showFabMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("新建文件夹") },
                            leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) },
                            onClick = {
                                showFabMenu = false
                                viewModel.openCreateFolderDialog()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("上传文件") },
                            leadingIcon = { Icon(Icons.Default.CloudUpload, null) },
                            onClick = {
                                showFabMenu = false
                                filePickerLauncher.launch(arrayOf("*/*"))
                            }
                        )
                    }
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
                            
                            IconButton(
                                onClick = { viewModel.copyDownloadLinks(context) },
                                enabled = uiState.selectedFiles.none { it.is_dir }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Link,
                                    contentDescription = "Copy Links",
                                    tint = if (uiState.selectedFiles.any { it.is_dir }) 
                                        MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.3f) 
                                    else MaterialTheme.colorScheme.inverseOnSurface
                                )
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
                            
                            Box {
                                var showMoreMenu by remember { mutableStateOf(false) }
                                IconButton(onClick = { showMoreMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "More")
                                }
                                DropdownMenu(
                                    expanded = showMoreMenu,
                                    onDismissRequest = { showMoreMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("移动 (Move)") },
                                        leadingIcon = { Icon(Icons.Outlined.DriveFileMove, null) },
                                        onClick = {
                                            showMoreMenu = false
                                            viewModel.openMoveDirPicker(isCopy = false)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("复制 (Copy)") },
                                        leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                                        onClick = {
                                            showMoreMenu = false
                                            viewModel.openMoveDirPicker(isCopy = true)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("重命名 (Rename)") },
                                        leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                                        enabled = uiState.selectedFiles.size == 1,
                                        onClick = {
                                            showMoreMenu = false
                                            viewModel.openRenameDialog(uiState.selectedFiles.first())
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        containerColor = Color(0xFFF7F2F9)
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
                    onRenameRequest = { viewModel.openRenameDialog(it) },
                    onDeleteRequest = { deleteTarget = it },
                    onImagePreview = { previewImageUrl = it }
                )
            }

            if (uiState.isMovingOrCopying) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black.copy(alpha = 0.3f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("正在处理...", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Dialogs ---

    if (uiState.showRenameDialog) {
        val file = uiState.renameTarget
        if (file != null) {
            var newName by remember { mutableStateOf(file.name) }
            AlertDialog(
                onDismissRequest = { viewModel.closeRenameDialog() },
                title = { Text("重命名") },
                text = {
                    Column(
                        modifier = Modifier.clearFocusOnTap()
                    ) {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text("新名称") },
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            focusManager.clearFocus(force = true)
                            viewModel.renameFile(newName.trim(), context)
                        }
                    ) {
                        Text("重命名")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            focusManager.clearFocus(force = true)
                            viewModel.closeRenameDialog()
                        }
                    ) {
                        Text("取消")
                    }
                }
            )
        }
    }

    if (uiState.showCreateFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { viewModel.closeCreateFolderDialog() },
            title = { Text("新建文件夹") },
            text = {
                Column(modifier = Modifier.clearFocusOnTap()) {
                    OutlinedTextField(
                        value = folderName,
                        onValueChange = { folderName = it },
                        label = { Text("文件夹名称") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        focusManager.clearFocus(force = true)
                        if (folderName.isNotBlank()) {
                            viewModel.createFolder(folderName.trim())
                            viewModel.closeCreateFolderDialog()
                        }
                    }
                ) {
                    Text("创建")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        focusManager.clearFocus(force = true)
                        viewModel.closeCreateFolderDialog()
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }

    if (uiState.showMoveDirPicker) {
        CloudDirPickerDialog(
            initialPath = uiState.currentPath,
            onDismiss = { viewModel.closeMoveDirPicker() },
            onConfirm = { targetPath ->
                if (uiState.isCopyOperation) {
                    viewModel.copyFiles(targetPath, context)
                } else {
                    viewModel.moveFiles(targetPath, context)
                }
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

    // --- Settings Dialog ---
    if (showSettingsDialog) {
        Dialog(
            onDismissRequest = { showSettingsDialog = false }
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp).fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 12.dp).size(28.dp)
                        )
                        Text(
                            text = "设置", 
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { showSettingsDialog = false },
                            modifier = Modifier.size(32.dp).offset(x = 8.dp, y = (-8).dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "下载目录",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { dirPickerLauncher.launch(null) }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = uiState.customDownloadDirPath ?: "系统默认 (Downloads)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
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

    // --- PDF Preview Overlay ---
    PdfPreviewOverlay(
        pdfFile = uiState.pdfPreviewFile,
        isLoading = uiState.isPdfLoading,
        fileName = uiState.pdfPreviewFileName,
        onDismiss = { viewModel.clearPdfPreview() }
    )

    // --- Media Player Overlay ---
    com.openlistmobile.app.ui.components.MediaPlayerOverlay(
        url = uiState.mediaPlaybackUrl,
        fileName = uiState.mediaPlaybackFileName,
        isAudio = uiState.mediaPlaybackIsAudio,
        onDismiss = { viewModel.clearMediaPlayback() }
    )

    // --- Search Overlay ---
    if (uiState.isSearchActive) {
        val context = LocalContext.current
        com.openlistmobile.app.ui.components.SearchOverlay(
            uiState = uiState,
            onQueryChange = viewModel::updateSearchKeywords,
            onSubmit = viewModel::performSearch,
            onScopeToggle = viewModel::setSearchScopeGlobal,
            onTypeFilter = viewModel::updateSearchTypeFilter,
            onSizeFilter = viewModel::updateSearchSizeFilter,
            onResultClick = { item -> viewModel.openSearchResult(item, context) { url -> previewImageUrl = url } },
            onLoadMore = viewModel::loadMoreSearch,
            onDismiss = viewModel::closeSearch
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
        Spacer(modifier = Modifier.height(1.dp))
        // --- Search Bar (Level 1 Tint) ---
        var showSortMenu by remember { mutableStateOf(false) }
        var showFilterMenu by remember { mutableStateOf(false) }

        Surface(color = Color(0xFFFFFFFF)) {
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
                onToggleView = { viewModel.toggleViewMode() },
                onGlobalSearchClick = { viewModel.openSearch() },
                filterMenu = {
                    DropdownMenu(expanded = showFilterMenu, onDismissRequest = { showFilterMenu = false }) {
                        FilterCategory.values().forEach { cat ->
                            DropdownMenuItem(
                                text = { Text("${cat.label} ${if (uiState.filterCategory == cat) "✓" else ""}") },
                                onClick = { viewModel.updateFilterCategory(cat); showFilterMenu = false }
                            )
                        }
                    }
                },
                sortMenu = {
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
                }
            )
        }

        Spacer(modifier = Modifier.height(1.dp))
        // --- Breadcrumbs (Level 2 Tint) ---
        Surface(color = Color(0xFFFBF9FE)) {
            BreadcrumbNavigation(
                path = uiState.currentPath,
                onPathClick = { path ->
                    viewModel.navigateToPath(path)
                }
            )
        }

        // --- File List (Level 3 Tint - inherited from Scaffold) ---
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .nestedScroll(pullToRefreshState.nestedScrollConnection)
                .clipToBounds()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = pullToRefreshState.verticalOffset
                    }
            ) {
                // 手动入场动画：当 currentPath 变化时，新内容从方向侧滑入 + 淡入
                val offsetFraction = remember { Animatable(0f) }
                val contentAlpha = remember { Animatable(1f) }
                var isFirstLoad by remember { mutableStateOf(true) }

                LaunchedEffect(uiState.currentPath) {
                    if (isFirstLoad) {
                        isFirstLoad = false
                        return@LaunchedEffect
                    }
                    val direction = if (uiState.navigationDirection == NavigationDirection.Forward) 1f else -1f
                    // 先 snap 到起始位置（从目标方向偏移进来）
                    offsetFraction.snapTo(direction * 0.33f)
                    contentAlpha.snapTo(0f)
                    // 动画滑入到最终位置
                    launch { offsetFraction.animateTo(0f, tween(250)) }
                    launch { contentAlpha.animateTo(1f, tween(250)) }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = offsetFraction.value * size.width
                            alpha = contentAlpha.value
                        }
                ) {
                    when {
                        uiState.isLoading && uiState.files.isEmpty() -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
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
                                modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center)
                            )
                        }
                        uiState.files.isEmpty() -> {
                            EmptyState(
                                title = "No files found",
                                subtitle = "This folder is empty",
                                modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center)
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
                                    items(uiState.files, key = { uiState.currentPath + "/" + it.name + it.modified }) { file ->
                                        val isSelected = uiState.selectedFiles.contains(file)
                                        FileGridItem(
                                            file = file,
                                            isSelected = isSelected,
                                            isSelectionMode = uiState.isSelectionMode,
                                            isNavigating = file.is_dir && file.name == uiState.navigatingFolderName,
                                            viewModel = viewModel,
                                            context = context,
                                            onRenameRequest = { onRenameRequest(it) },
                                            onDeleteRequest = { onDeleteRequest(it) },
                                            onImagePreview = onImagePreview,
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
                                            },
                                            modifier = Modifier.animateItemPlacement()
                                        )
                                    }
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp)
                                ) {
                                    items(uiState.files, key = { uiState.currentPath + "/" + it.name + it.modified }) { file ->
                                        val isSelected = uiState.selectedFiles.contains(file)
                                        FileItemCard(
                                            file = file,
                                            isSelected = isSelected,
                                            isSelectionMode = uiState.isSelectionMode,
                                            isNavigating = file.is_dir && file.name == uiState.navigatingFolderName,
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
                                            },
                                            modifier = Modifier.animateItemPlacement()
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
    val isText = ext in listOf("txt", "log", "csv", "ini", "conf", "properties", "toml", "json", "yaml", "yml", "xml", "html", "htm", "css", "js", "jsx", "ts", "tsx", "kt", "kts", "java", "py", "sh", "bat", "cmd", "md", "markdown", "sql", "go", "rs", "c", "h", "cpp", "hpp", "cs", "rb", "swift")
    val isVideo = ext in listOf("mp4", "mkv", "avi", "mov", "flv", "webm")
    val isAudio = ext in listOf("mp3", "wav", "flac", "ogg", "m4a", "aac")
    val isImage = ext in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
    val isPdf = ext == "pdf"
    val isOfficeDoc = ext in listOf("doc", "docx", "xls", "xlsx", "ppt", "pptx")

    if (isText) {
        viewModel.loadTextPreview(file)
        return
    }

    if (isPdf) {
        viewModel.loadPdfPreview(file, context.cacheDir)
        return
    }

    if (isOfficeDoc) {
        viewModel.openDocWithExternalApp(file, context)
        return
    }

    if (isVideo) {
        viewModel.loadMediaPlayback(file, isAudio = false)
        return
    }

    if (isAudio) {
        viewModel.loadMediaPlayback(file, isAudio = true)
        return
    }

    val directLink = viewModel.generateDirectLink(file) ?: return

    when {
        isImage -> onImagePreview(directLink)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileGridItem(
    file: AListFile,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    isNavigating: Boolean = false,
    viewModel: HomeViewModel,
    context: android.content.Context,
    onRenameRequest: (AListFile) -> Unit,
    onDeleteRequest: (AListFile) -> Unit,
    onImagePreview: (String) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fileIconInfo = getFileIconAndTint(file)
    val icon = fileIconInfo.icon
    val iconTint = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else if (file.is_dir) {
        MaterialTheme.colorScheme.primary
    } else {
        fileIconInfo.tint
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "bounceScale"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    interactionSource = interactionSource,
                    indication = androidx.compose.foundation.LocalIndication.current
                )
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val ext = file.name.substringAfterLast('.', "").lowercase()
            val isImage = !file.is_dir && ext in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "ico", "heic", "tiff")

            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (isImage) {
                    val thumbUrl = if (file.thumb.isNotBlank()) {
                        if (file.thumb.startsWith("http://") || file.thumb.startsWith("https://")) {
                            file.thumb
                        } else {
                            val baseUrl = viewModel.uiState.value.currentProfile?.serverUrl?.trimEnd('/')
                            if (baseUrl != null) {
                                if (file.thumb.startsWith("/")) "$baseUrl${file.thumb}" else "$baseUrl/${file.thumb}"
                            } else {
                                file.thumb
                            }
                        }
                    } else {
                        viewModel.generateDirectLink(file)
                    }

                    var imageState by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }

                    if (thumbUrl != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(thumbUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = file.name,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.small),
                            onState = { imageState = it }
                        )
                    }

                    if (thumbUrl == null || imageState is AsyncImagePainter.State.Loading || imageState is AsyncImagePainter.State.Empty) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconTint.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (imageState is AsyncImagePainter.State.Error) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (isNavigating) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(16.dp)
                            .align(Alignment.BottomEnd),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}
