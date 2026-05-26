package com.example.alist.data.remote

import com.example.alist.data.remote.model.AListResponse
import com.example.alist.data.remote.model.FileListData
import com.example.alist.data.remote.model.FileListRequest
import com.example.alist.data.remote.model.LoginData
import com.example.alist.data.remote.model.LoginRequest
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

interface AListApiService {
    @POST
    suspend fun login(
        @Url url: String,
        @Body request: LoginRequest
    ): AListResponse<LoginData>

    @POST
    suspend fun getFileList(
        @Url url: String,
        @Body request: FileListRequest
    ): AListResponse<FileListData>
}
