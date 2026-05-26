package com.example.alist.domain.repository

interface AuthRepository {
    suspend fun loginAndSave(serverUrl: String, username: String, password: String): Result<String>
}
