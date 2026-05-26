package com.example.alist.domain.repository

import com.example.alist.data.remote.model.FileListData

interface FileRepository {
    suspend fun getFileList(path: String): Result<FileListData>
}
