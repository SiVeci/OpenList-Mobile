package com.example.alist.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.alist.data.local.ServerProfile
import com.example.alist.data.remote.model.AListFile
import com.example.alist.domain.repository.AuthRepository
import com.example.alist.domain.repository.FileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    val page: Int = 1
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val fileRepository: FileRepository
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
                _uiState.update { it.copy(
                    profiles = profiles,
                    currentProfile = active
                ) }
                if (active != null && _uiState.value.rawFiles.isEmpty() && !_uiState.value.isLoading) {
                    fetchFiles("/", isRefresh = false)
                }
            }
        }
    }

    fun switchProfile(profile: ServerProfile) {
        viewModelScope.launch {
            authRepository.switchProfile(profile.id)
            pathStack.clear()
            fetchFiles("/", isRefresh = false)
        }
    }

    fun refresh() {
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
                rawFiles = emptyList() // clear raw files to avoid ghost rendering when navigating
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
            
            // Collect page > 1 (only network emit)
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
                            page = state.page // revert page on error
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
        fetchFiles(newPath)
    }

    fun navigateBack(): Boolean {
        if (pathStack.isNotEmpty()) {
            val previousPath = pathStack.removeLast()
            fetchFiles(previousPath)
            return true
        }
        return false
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

        // 1. Filter Category
        if (state.filterCategory != FilterCategory.All) {
            filtered = filtered.filter { file ->
                if (file.is_dir) return@filter false // Usually we don't apply these filters to folders, or we can hide folders. Let's hide folders if filtering.
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

        // 2. Filter Suffix
        if (state.filterSuffix.isNotBlank()) {
            val s = state.filterSuffix.trim().removePrefix(".").lowercase()
            filtered = filtered.filter { 
                it.name.lowercase().endsWith(".$s") || (it.is_dir && state.filterCategory == FilterCategory.All)
            }
        }

        // 3. Sort
        val comparator = Comparator<AListFile> { a, b ->
            val res = when (state.sortBy) {
                SortBy.Name -> a.name.compareTo(b.name, ignoreCase = true)
                SortBy.Size -> a.size.compareTo(b.size)
                SortBy.Time -> (a.modified ?: "").compareTo(b.modified ?: "")
            }
            if (state.sortOrder == SortOrder.Desc) -res else res
        }

        // 4. Folders on Top
        val sorted = if (state.foldersOnTop && state.filterCategory == FilterCategory.All && state.filterSuffix.isBlank()) {
            // Folders on top mostly makes sense when not filtering
            val folders = filtered.filter { it.is_dir }.sortedWith(comparator)
            val files = filtered.filter { !it.is_dir }.sortedWith(comparator)
            folders + files
        } else {
            filtered.sortedWith(comparator)
        }

        _uiState.update { it.copy(files = sorted) }
    }

    // For fallback login when no profiles exist
    fun testLoginAndFetch(serverUrl: String, user: String, pass: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val loginResult = authRepository.loginAndSave(serverUrl, user, pass)
            if (loginResult.isFailure) {
                _uiState.update { it.copy(
                    isLoading = false,
                    error = loginResult.exceptionOrNull()?.message ?: "Login failed"
                ) }
            }
        }
    }
}