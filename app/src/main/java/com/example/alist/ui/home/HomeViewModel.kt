package com.example.alist.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.alist.domain.repository.AuthRepository
import com.example.alist.domain.repository.FileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class UiState {
    object Idle : UiState()
    object Loading : UiState()
    data class Success(val result: String) : UiState()
    data class Error(val message: String) : UiState()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val fileRepository: FileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun testLoginAndFetch(serverUrl: String, user: String, pass: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            
            val loginResult = authRepository.loginAndSave(serverUrl, user, pass)
            loginResult.onSuccess {
                val fileResult = fileRepository.getFileList("/")
                fileResult.onSuccess { fileData ->
                    val names = fileData.content?.map { it.name }?.joinToString(", ") ?: "Empty directory"
                    _uiState.value = UiState.Success("Login OK!\nFiles in root: $names")
                }.onFailure { error ->
                    _uiState.value = UiState.Error("Login OK, but fetch failed: ${error.message}")
                }
            }.onFailure { error ->
                _uiState.value = UiState.Error("Login failed: ${error.message}")
            }
        }
    }
}