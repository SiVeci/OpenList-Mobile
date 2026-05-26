package com.example.alist.data.repository

import com.example.alist.data.remote.AListApiService
import com.example.alist.data.remote.model.FileListData
import com.example.alist.data.remote.model.FileListRequest
import com.example.alist.domain.repository.FileRepository
import com.example.alist.utils.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class FileRepositoryImpl @Inject constructor(
    private val apiService: AListApiService,
    private val tokenManager: TokenManager
) : FileRepository {

    override suspend fun getFileList(path: String, page: Int, perPage: Int, refresh: Boolean): Result<FileListData> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = tokenManager.currentServerUrl ?: return@withContext Result.failure(Exception("No active server"))
            val url = "$baseUrl/api/fs/list"
            
            val request = FileListRequest(path = path, page = page, per_page = perPage, refresh = refresh)
            val response = apiService.getFileList(url, request)
            if (response.code == 200 && response.data != null) {
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}