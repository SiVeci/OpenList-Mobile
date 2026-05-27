package com.example.alist.ui.home

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.alist.data.local.ServerProfile
import com.example.alist.data.remote.model.AListFile
import com.example.alist.domain.repository.AuthRepository
import com.example.alist.domain.repository.FileRepository
import com.example.alist.domain.repository.TransferRepository
import com.example.alist.service.TransferService
import com.example.alist.data.local.TransferType
import com.example.alist.utils.ShareManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URLEncoder
import javax.inject.Inject

enum class FilterCategory(val label: String) {
    All("全部"),
    Video("视频"),
    Image("图片"),
    Audio("音频"),
    Document("文档"),
    Other("其他")
}

enum class SortBy { Name, Size, Time }
enum class SortOrder { Asc, Desc }

data class HomeUiState(
    val profiles: List<ServerProfile> = emptyList(),
    val currentProfile: ServerProfile? = null,
    val currentPath: String = "/",
    
    val rawFiles: List<AListFile> = emptyList(),
    val files: List<AListFile> = emptyList(),
    
    val filterCategory: FilterCategory = FilterCategory.All,
    val filterSuffix: String = "",
    val sortBy: SortBy = SortBy.Name,
    val sortOrder: SortOrder = SortOrder.Asc,
    val foldersOnTop: Boolean = true,
    
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = false,
    val page: Int = 1,
    
    val previewTextContent: String? = null,
    val previewTextFileName: String? = null,
    val isPreviewingTextLoading: Boolean = false,

    val isSelectionMode: Boolean = false,
    val selectedFiles: Set<AListFile> = emptySet(),
    val isGridView: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val fileRepository: FileRepository,
    private val transferRepository: TransferRepository,
    val shareManager: ShareManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    private val pathStack = mutableListOf<String>()
    private var fetchJob: Job? = null

    init {
        viewModelScope.launch {
            authRepository.initActiveProfile()
            authRepository.getAllProfiles().collect { profiles ->
                val active = profiles.find { it.isActive }
                val previousProfileId = _uiState.value.currentProfile?.id
                
                _uiState.update { it.copy(
                    profiles = profiles,
                    currentProfile = active
                ) }
                
                // Trigger fetch if a profile becomes active or changes, and we don't have files yet
                if (active != null && (active.id != previousProfileId || _uiState.value.rawFiles.isEmpty())) {
                    fetchFiles("/", isRefresh = false)
                }
            }
        }
    }

    fun switchProfile(profile: ServerProfile) {
        viewModelScope.launch {
            authRepository.switchProfile(profile.id)
            pathStack.clear()
            clearSelection()
            fetchFiles("/", isRefresh = false)
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            pathStack.clear()
            clearSelection()
            _uiState.update { it.copy(
                files = emptyList(),
                currentPath = "/",
                currentProfile = null
            ) }
        }
    }
    
    fun deleteProfile(profile: ServerProfile) {
        viewModelScope.launch {
            authRepository.deleteProfile(profile.id)
            if (_uiState.value.currentProfile?.id == profile.id) {
                pathStack.clear()
                clearSelection()
                _uiState.update { it.copy(
                    files = emptyList(),
                    currentPath = "/",
                    currentProfile = null
                ) }
            }
        }
    }

    fun refresh() {
        clearSelection()
        fetchFiles(_uiState.value.currentPath, isRefresh = true)
    }

    fun fetchFiles(path: String, isRefresh: Boolean = false) {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            _uiState.update { it.copy(
                isLoading = !isRefresh,
                isRefreshing = isRefresh,
                error = null,
                currentPath = path,
                page = 1,
                rawFiles = emptyList()
            ) }
            
            fileRepository.getFileListFlow(path, page = 1, perPage = 30, refresh = isRefresh).collect { result ->
                result.onSuccess { data ->
                    val newFiles = data.content ?: emptyList()
                    _uiState.update { state -> 
                        state.copy(
                            rawFiles = newFiles,
                            isLoading = false,
                            isRefreshing = false,
                            hasMore = newFiles.size < data.total
                        ) 
                    }
                    applyFiltersAndSort()
                }.onFailure { error ->
                    _uiState.update { state -> 
                        state.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = error.message
                        )
                    }
                }
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMore) return

        viewModelScope.launch {
            val nextPage = state.page + 1
            _uiState.update { it.copy(isLoadingMore = true, page = nextPage) }
            
            fileRepository.getFileListFlow(state.currentPath, page = nextPage, perPage = 30, refresh = false).collect { result ->
                result.onSuccess { data ->
                    val moreFiles = data.content ?: emptyList()
                    _uiState.update { s ->
                        val allFiles = s.rawFiles + moreFiles
                        s.copy(
                            rawFiles = allFiles,
                            isLoadingMore = false,
                            hasMore = allFiles.size < data.total
                        )
                    }
                    applyFiltersAndSort()
                }.onFailure { error ->
                    _uiState.update { s ->
                        s.copy(
                            isLoadingMore = false,
                            error = error.message,
                            page = state.page
                        )
                    }
                }
            }
        }
    }

    fun navigateToFolder(folderName: String) {
        val state = _uiState.value
        pathStack.add(state.currentPath)
        val newPath = if (state.currentPath == "/") "/$folderName" else "${state.currentPath}/$folderName"
        clearSelection()
        fetchFiles(newPath)
    }

    fun navigateBack(): Boolean {
        if (_uiState.value.isSelectionMode) {
            clearSelection()
            return true
        }
        if (pathStack.isNotEmpty()) {
            val previousPath = pathStack.removeLast()
            clearSelection()
            fetchFiles(previousPath)
            return true
        }
        return false
    }

    // --- Multi-Selection ---

    fun toggleSelectionMode(enabled: Boolean) {
        _uiState.update { it.copy(isSelectionMode = enabled, selectedFiles = emptySet()) }
    }

    fun toggleFileSelection(file: AListFile) {
        _uiState.update { state ->
            val newSelected = if (state.selectedFiles.contains(file)) {
                state.selectedFiles - file
            } else {
                state.selectedFiles + file
            }
            state.copy(
                selectedFiles = newSelected,
                isSelectionMode = newSelected.isNotEmpty()
            )
        }
    }

    fun selectAll() {
        _uiState.update { state ->
            state.copy(
                selectedFiles = state.files.toSet(),
                isSelectionMode = state.files.isNotEmpty()
            )
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(isSelectionMode = false, selectedFiles = emptySet()) }
    }

    fun toggleViewMode() {
        _uiState.update { it.copy(isGridView = !_uiState.value.isGridView) }
    }

    fun batchDeleteSelected() {
        val selected = _uiState.value.selectedFiles.toList()
        if (selected.isEmpty()) return
        
        viewModelScope.launch {
            val names = selected.map { it.name }
            fileRepository.remove(_uiState.value.currentPath, names).onSuccess {
                clearSelection()
                refresh()
            }
        }
    }

    fun batchDownloadSelected(context: android.content.Context) {
        val selected = _uiState.value.selectedFiles.filter { !it.is_dir }
        if (selected.isEmpty()) return

        selected.forEach { file ->
            startDownload(context, file)
        }
        clearSelection()
    }

    // --- Phase 5: CRUD Operations ---

    fun createFolder(folderName: String) {
        viewModelScope.launch {
            val current = _uiState.value.currentPath
            val targetPath = if (current == "/") "/$folderName" else "$current/$folderName"
            fileRepository.mkdir(targetPath).onSuccess { refresh() }
        }
    }

    fun renameFile(file: AListFile, newName: String) {
        viewModelScope.launch {
            val current = _uiState.value.currentPath
            val targetPath = if (current == "/") "/${file.name}" else "$current/${file.name}"
            fileRepository.rename(newName, targetPath).onSuccess { refresh() }
        }
    }

    fun removeFile(file: AListFile) {
        viewModelScope.launch {
            fileRepository.remove(_uiState.value.currentPath, listOf(file.name)).onSuccess { refresh() }
        }
    }

    // --- Phase 5: Media Preview / Direct Link Generation ---
    fun loadTextPreview(file: AListFile) {
        val url = generateDirectLink(file) ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isPreviewingTextLoading = true, previewTextFileName = file.name, previewTextContent = null) }
            val result = fileRepository.getTextFileContent(url)
            _uiState.update { 
                it.copy(
                    isPreviewingTextLoading = false,
                    previewTextContent = result.getOrNull() ?: "Failed to load content: ${result.exceptionOrNull()?.message}"
                ) 
            }
        }
    }

    fun clearTextPreview() {
        _uiState.update { it.copy(previewTextContent = null, previewTextFileName = null, isPreviewingTextLoading = false) }
    }

    // --- Phase 7: Upload ---
    fun uploadSharedFiles(context: Context, uris: List<android.net.Uri>) {
        val currentPath = _uiState.value.currentPath
        viewModelScope.launch {
            try {
                for (uri in uris) {
                    val contentResolver = context.contentResolver
                    val cursor = contentResolver.query(uri, null, null, null, null)
                    var fileName = "uploaded_file_${System.currentTimeMillis()}"
                    var fileSize = -1L
                    if (cursor != null && cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        if (nameIndex != -1) fileName = cursor.getString(nameIndex)
                        if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                        cursor.close()
                    }

                    // Insert task to DB
                    val taskId = transferRepository.addTask(
                        fileName = fileName,
                        fileUrl = currentPath, // target remote path
                        savePath = uri.toString(), // local file uri
                        totalBytes = fileSize,
                        type = TransferType.UPLOAD
                    )
                    
                    // Start TransferService
                    val intent = Intent(context, TransferService::class.java).apply {
                        action = TransferService.ACTION_START
                        putExtra(TransferService.EXTRA_TASK_ID, taskId)
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                }
                shareManager.clearSharedUris()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun generateDirectLink(file: AListFile): String? {
        val baseUrl = _uiState.value.currentProfile?.serverUrl?.trimEnd('/') ?: return null
        val current = _uiState.value.currentPath
        val fullPath = if (current == "/") "/${file.name}" else "$current/${file.name}"
        
        val encodedSegments = fullPath.split("/").map {
            if (it.isEmpty()) "" else URLEncoder.encode(it, "UTF-8").replace("+", "%20")
        }
        val encodedPath = encodedSegments.joinToString("/")
        
        val signQuery = if (file.sign.isNotBlank()) "?sign=${file.sign}" else ""
        return "$baseUrl/d$encodedPath$signQuery"
    }

    // --- Phase 6: Background Download ---
    fun startDownload(context: Context, file: AListFile) {
        val url = generateDirectLink(file) ?: return
        viewModelScope.launch {
            val saveDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
            val savePath = "$saveDir/${file.name}"
            val taskId = transferRepository.addTask(file.name, url, savePath)
            
            val intent = Intent(context, TransferService::class.java).apply {
                action = TransferService.ACTION_START
                putExtra(TransferService.EXTRA_TASK_ID, taskId)
            }
            context.startForegroundService(intent)
        }
    }

    // -- Filter and Sort Updates --
    
    fun updateFilterCategory(category: FilterCategory) {
        _uiState.update { it.copy(filterCategory = category) }
        applyFiltersAndSort()
    }

    fun updateFilterSuffix(suffix: String) {
        _uiState.update { it.copy(filterSuffix = suffix) }
        applyFiltersAndSort()
    }

    fun updateSortBy(sortBy: SortBy) {
        _uiState.update { it.copy(sortBy = sortBy) }
        applyFiltersAndSort()
    }

    fun updateSortOrder(sortOrder: SortOrder) {
        _uiState.update { it.copy(sortOrder = sortOrder) }
        applyFiltersAndSort()
    }

    fun updateFoldersOnTop(onTop: Boolean) {
        _uiState.update { it.copy(foldersOnTop = onTop) }
        applyFiltersAndSort()
    }

    private fun applyFiltersAndSort() {
        val state = _uiState.value
        var filtered = state.rawFiles

        // 1. Search Filter (contains query in name)
        if (state.filterSuffix.isNotBlank()) {
            val query = state.filterSuffix.trim().lowercase()
            filtered = filtered.filter { 
                it.name.lowercase().contains(query)
            }
        }

        // 2. Category Filter
        if (state.filterCategory != FilterCategory.All) {
            filtered = filtered.filter { file ->
                if (file.is_dir) return@filter false
                val ext = file.name.substringAfterLast('.', "").lowercase()
                when (state.filterCategory) {
                    FilterCategory.Video -> ext in listOf("mp4", "mkv", "avi", "mov", "flv", "webm")
                    FilterCategory.Image -> ext in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
                    FilterCategory.Audio -> ext in listOf("mp3", "flac", "wav", "ogg", "m4a")
                    FilterCategory.Document -> ext in listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md")
                    FilterCategory.Other -> ext !in listOf("mp4", "mkv", "avi", "mov", "flv", "webm", "jpg", "jpeg", "png", "gif", "webp", "bmp", "mp3", "flac", "wav", "ogg", "m4a", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md")
                    else -> true
                }
            }
        }

        val comparator = Comparator<AListFile> { a, b ->
            val res = when (state.sortBy) {
                SortBy.Name -> a.name.compareTo(b.name, ignoreCase = true)
                SortBy.Size -> a.size.compareTo(b.size)
                SortBy.Time -> (a.modified ?: "").compareTo(b.modified ?: "")
            }
            if (state.sortOrder == SortOrder.Desc) -res else res
        }

        val sorted = if (state.foldersOnTop && state.filterCategory == FilterCategory.All && state.filterSuffix.isBlank()) {
            val folders = filtered.filter { it.is_dir }.sortedWith(comparator)
            val files = filtered.filter { !it.is_dir }.sortedWith(comparator)
            folders + files
        } else {
            filtered.sortedWith(comparator)
        }

        _uiState.update { it.copy(files = sorted) }
    }
    fun testLoginAndFetch(aliasName: String, serverUrl: String, user: String, pass: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val loginResult = authRepository.loginAndSave(aliasName, serverUrl, user, pass)
            if (loginResult.isFailure) {
                _uiState.update { it.copy(
                    isLoading = false,
                    error = loginResult.exceptionOrNull()?.message ?: "Login failed"
                ) }
            } else {
                // Success: Ensure we load files immediately
                fetchFiles("/", isRefresh = false)
            }
        }
    }
}