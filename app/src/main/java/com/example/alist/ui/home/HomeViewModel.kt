package com.example.alist.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.alist.data.local.ServerProfile
import com.example.alist.data.remote.model.AListFile
import com.example.alist.domain.repository.AuthRepository
import com.example.alist.domain.repository.FileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val profiles: List<ServerProfile> = emptyList(),
    val currentProfile: ServerProfile? = null,
    val currentPath: String = "/",
    val files: List<AListFile> = emptyList(),
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

    init {
        viewModelScope.launch {
            authRepository.initActiveProfile()
            authRepository.getAllProfiles().collect { profiles ->
                val active = profiles.find { it.isActive }
                _uiState.value = _uiState.value.copy(
                    profiles = profiles,
                    currentProfile = active
                )
                if (active != null && _uiState.value.files.isEmpty() && !_uiState.value.isLoading) {
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
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = !isRefresh,
                isRefreshing = isRefresh,
                error = null,
                currentPath = path,
                page = 1
            )
            
            val result = fileRepository.getFileList(path, page = 1, perPage = 30, refresh = isRefresh)
            result.onSuccess { data ->
                val newFiles = data.content ?: emptyList()
                _uiState.value = _uiState.value.copy(
                    files = newFiles,
                    isLoading = false,
                    isRefreshing = false,
                    hasMore = newFiles.size < data.total
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = error.message
                )
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMore) return

        viewModelScope.launch {
            val nextPage = state.page + 1
            _uiState.value = state.copy(isLoadingMore = true, page = nextPage)
            
            val result = fileRepository.getFileList(state.currentPath, page = nextPage, perPage = 30)
            result.onSuccess { data ->
                val moreFiles = data.content ?: emptyList()
                val allFiles = state.files + moreFiles
                _uiState.value = _uiState.value.copy(
                    files = allFiles,
                    isLoadingMore = false,
                    hasMore = allFiles.size < data.total
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoadingMore = false,
                    error = error.message,
                    page = state.page // revert page on error
                )
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
    
    // For fallback login when no profiles exist
    fun testLoginAndFetch(serverUrl: String, user: String, pass: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val loginResult = authRepository.loginAndSave(serverUrl, user, pass)
            if (loginResult.isFailure) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = loginResult.exceptionOrNull()?.message ?: "Login failed"
                )
            }
            // Success case will naturally trigger Flow collection of profiles in init{} block
        }
    }
}