package com.example.alist.domain.repository

import com.example.alist.data.remote.model.FileListData
import kotlinx.coroutines.flow.Flow

interface FileRepository {
    suspend fun getFileListFlow(path: String, page: Int, perPage: Int, refresh: Boolean = false): Flow<Result<FileListData>>
}