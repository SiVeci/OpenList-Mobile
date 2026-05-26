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

    override suspend fun getTextFileContent(url: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val responseBody = apiService.downloadFile(url)
            Result.success(responseBody.string())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun uploadFile(path: String, fileName: String, inputStream: java.io.InputStream, contentLength: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = tokenManager.currentServerUrl ?: return@withContext Result.failure(Exception("No active server"))
            val token = tokenManager.currentToken ?: return@withContext Result.failure(Exception("No token"))
            
            val encodedPath = java.net.URLEncoder.encode("$path/$fileName", "UTF-8").replace("+", "%20")
            
            val url = "$baseUrl/api/fs/put"
            
            val requestBody = object : okhttp3.RequestBody() {
                override fun contentType(): okhttp3.MediaType? = okhttp3.MediaType.parse("application/octet-stream")
                override fun contentLength(): Long = contentLength
                override fun writeTo(sink: okio.BufferedSink) {
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        sink.write(buffer, 0, read)
                    }
                }
            }

            val request = okhttp3.Request.Builder()
                .url(url)
                .addHeader("Authorization", token)
                .addHeader("File-Path", encodedPath)
                .put(requestBody)
                .build()

            // We need an OkHttpClient here. We can inject it. But for simplicity, we'll create a new one or use a shared one if injected.
            // Wait, we can inject OkHttpClient in the constructor of FileRepositoryImpl
            val client = okhttp3.OkHttpClient()
            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val bodyStr = response.body?.string()
                // basic check for AList standard response
                if (bodyStr != null && bodyStr.contains("\"code\":200")) {
                     Result.success(Unit)
                } else {
                     Result.failure(Exception("Upload failed: $bodyStr"))
                }
            } else {
                Result.failure(Exception("HTTP error ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            inputStream.close()
        }
    }
}
