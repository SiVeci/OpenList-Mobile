package com.example.alist.data.repository

import com.example.alist.data.local.DirectoryCache
import com.example.alist.data.local.DirectoryCacheDao
import com.example.alist.data.remote.AListApiService
import com.example.alist.data.remote.model.*
import com.example.alist.domain.repository.FileRepository
import com.example.alist.utils.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class FileRepositoryImpl @Inject constructor(
    private val apiService: AListApiService,
    private val cacheDao: DirectoryCacheDao,
    private val tokenManager: TokenManager
) : FileRepository {

    override suspend fun getFileListFlow(path: String, page: Int, perPage: Int, refresh: Boolean): Flow<Result<FileListData>> = flow {
        val profileId = tokenManager.currentProfileId
        if (profileId == -1L) {
            emit(Result.failure(Exception("No active server")))
            return@flow
        }
        
        if (page == 1 && !refresh) {
            val cached = cacheDao.getCache(profileId, path)
            if (cached != null) {
                emit(Result.success(FileListData(
                    content = cached.files,
                    total = cached.files.size,
                    readme = null,
                    write = null,
                    provider = null
                )))
            }
        }
        
        try {
            val baseUrl = tokenManager.currentServerUrl ?: return@flow
            val url = "$baseUrl/api/fs/list"
            
            val request = FileListRequest(path = path, page = page, per_page = perPage, refresh = refresh)
            val response = apiService.getFileList(url, request)
            
            if (response.code == 200 && response.data != null) {
                val files = response.data.content ?: emptyList()
                if (page == 1) {
                    cacheDao.insert(DirectoryCache(profileId, path, files, System.currentTimeMillis()))
                }
                emit(Result.success(response.data))
            } else {
                emit(Result.failure(Exception(response.message)))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    override suspend fun mkdir(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = tokenManager.currentServerUrl ?: return@withContext Result.failure(Exception("No active server"))
            val response = apiService.mkdir("$baseUrl/api/fs/mkdir", MkdirRequest(path))
            if (response.code == 200) Result.success(Unit) else Result.failure(Exception(response.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun rename(newName: String, path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = tokenManager.currentServerUrl ?: return@withContext Result.failure(Exception("No active server"))
            val response = apiService.rename("$baseUrl/api/fs/rename", RenameRequest(newName, path))
            if (response.code == 200) Result.success(Unit) else Result.failure(Exception(response.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun remove(dir: String, names: List<String>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = tokenManager.currentServerUrl ?: return@withContext Result.failure(Exception("No active server"))
            val response = apiService.remove("$baseUrl/api/fs/remove", RemoveRequest(dir, names))
            if (response.code == 200) Result.success(Unit) else Result.failure(Exception(response.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
