package com.example.alist.data.repository

import com.example.alist.data.local.ServerProfile
import com.example.alist.data.local.ServerProfileDao
import com.example.alist.data.remote.AListApiService
import com.example.alist.data.remote.model.LoginRequest
import com.example.alist.domain.repository.AuthRepository
import com.example.alist.utils.KeystoreManager
import com.example.alist.utils.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val apiService: AListApiService,
    private val dao: ServerProfileDao,
    private val keystoreManager: KeystoreManager,
    private val tokenManager: TokenManager
) : AuthRepository {

    override suspend fun loginAndSave(serverUrl: String, username: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = serverUrl.trimEnd('/')
            val url = "/api/auth/login"
            
            val response = apiService.login(url, LoginRequest(username, password))
            if (response.code == 200 && response.data != null) {
                val token = response.data.token
                
                dao.clearActiveProfiles()
                
                val encryptedToken = keystoreManager.encrypt(token)
                val profile = ServerProfile(
                    serverUrl = baseUrl,
                    username = username,
                    encryptedToken = encryptedToken,
                    isActive = true
                )
                dao.insert(profile)
                
                tokenManager.currentToken = token
                tokenManager.currentServerUrl = baseUrl
                
                Result.success("Login successful")
            } else {
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
