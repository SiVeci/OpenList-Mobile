package com.example.alist.data.remote

import com.example.alist.data.remote.model.*
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

    @POST
    suspend fun mkdir(
        @Url url: String,
        @Body request: MkdirRequest
    ): AListResponse<Any>

    @POST
    suspend fun rename(
        @Url url: String,
        @Body request: RenameRequest
    ): AListResponse<Any>

    @POST
    suspend fun remove(
        @Url url: String,
        @Body request: RemoveRequest
    ): AListResponse<Any>

    @retrofit2.http.GET
    suspend fun downloadFile(@Url url: String): okhttp3.ResponseBody
}
