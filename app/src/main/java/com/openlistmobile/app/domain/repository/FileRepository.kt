package com.openlistmobile.app.domain.repository

import com.openlistmobile.app.data.remote.model.AListFile
import com.openlistmobile.app.data.remote.model.FileListData
import com.openlistmobile.app.data.remote.model.SearchData
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.io.InputStream

interface FileRepository {
    suspend fun getFileListFlow(path: String, page: Int, perPage: Int, refresh: Boolean = false): Flow<Result<FileListData>>
    suspend fun mkdir(path: String): Result<Unit>
    suspend fun rename(newName: String, path: String): Result<Unit>
    suspend fun remove(dir: String, names: List<String>): Result<Unit>
    suspend fun getTextFileContent(url: String): Result<String>
    suspend fun downloadFileToCache(url: String, fileName: String, cacheDir: File): Result<File>
    suspend fun uploadFile(path: String, fileName: String, inputStream: InputStream, contentLength: Long): Result<Unit>
    suspend fun search(parent: String, keywords: String, page: Int, perPage: Int): Result<SearchData>
    suspend fun getFileInfo(path: String): Result<AListFile>
}
