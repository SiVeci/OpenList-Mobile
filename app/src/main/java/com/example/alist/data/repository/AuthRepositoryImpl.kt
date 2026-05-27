package com.example.alist.data.repository

import com.example.alist.data.local.ServerProfile
import com.example.alist.data.local.ServerProfileDao
import com.example.alist.data.remote.AListApiService
import com.example.alist.data.remote.model.LoginRequest
import com.example.alist.domain.repository.AuthRepository
import com.example.alist.utils.KeystoreManager
import com.example.alist.utils.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val apiService: AListApiService,
    private val dao: ServerProfileDao,
    private val keystoreManager: KeystoreManager,
    private val tokenManager: TokenManager
) : AuthRepository {

    override suspend fun initActiveProfile() = withContext(Dispatchers.IO) {
        val active = dao.getActiveProfile()
        if (active != null) {
            tokenManager.currentProfileId = active.id
            tokenManager.currentServerUrl = active.serverUrl
            tokenManager.currentToken = keystoreManager.decrypt(active.encryptedToken)
        }
    }

    override fun getAllProfiles(): Flow<List<ServerProfile>> = dao.getAllProfilesFlow()

    override suspend fun switchProfile(profileId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        dao.setActiveProfile(profileId)
        val active = dao.getActiveProfile()
        if (active != null) {
            tokenManager.currentProfileId = active.id
            tokenManager.currentServerUrl = active.serverUrl
            tokenManager.currentToken = keystoreManager.decrypt(active.encryptedToken)
            Result.success(Unit)
        } else {
            Result.failure(Exception("Profile not found"))
        }
    }

    override suspend fun logout() = withContext(Dispatchers.IO) {
        dao.clearActiveProfiles()
        tokenManager.currentProfileId = -1L
        tokenManager.currentToken = null
        tokenManager.currentServerUrl = null
    }

    override suspend fun loginAndSave(aliasName: String, serverUrl: String, username: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            var baseUrl = serverUrl.trim().trimEnd('/')
            if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
                baseUrl = "http://$baseUrl"
            }
            val url = "$baseUrl/api/auth/login"
            
            val response = apiService.login(url, LoginRequest(username, password))
            if (response.code == 200 && response.data != null) {
                val token = response.data.token
                
                dao.clearActiveProfiles()
                
                val encryptedToken = keystoreManager.encrypt(token)
                val profile = ServerProfile(
                    aliasName = aliasName,
                    serverUrl = baseUrl,
                    username = username,
                    encryptedToken = encryptedToken,
                    isActive = true
                )
                val profileId = dao.insert(profile)
                
                tokenManager.currentProfileId = profileId
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