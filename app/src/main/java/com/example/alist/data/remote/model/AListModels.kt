package com.example.alist.data.remote.model

data class AListResponse<T>(
    val code: Int,
    val message: String,
    val data: T?
)

data class LoginRequest(
    val username: String,
    val password: String
)

data class LoginData(
    val token: String
)

data class FileListRequest(
    val path: String,
    val password: String = "",
    val page: Int = 1,
    val per_page: Int = 0,
    val refresh: Boolean = false
)

data class FileListData(
    val content: List<AListFile>?,
    val total: Int,
    val readme: String?,
    val write: Boolean?,
    val provider: String?
)

data class AListFile(
    val name: String,
    val size: Long,
    val is_dir: Boolean,
    val modified: String,
    val sign: String,
    val thumb: String,
    val type: Int
)

data class MkdirRequest(
    val path: String
)

data class RenameRequest(
    val name: String,
    val path: String
)

data class RemoveRequest(
    val dir: String,
    val names: List<String>
)
