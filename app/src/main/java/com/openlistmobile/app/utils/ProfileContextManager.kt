package com.openlistmobile.app.utils

import com.openlistmobile.app.data.local.ServerProfileDao
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileContextManager @Inject constructor(
    private val serverProfileDao: ServerProfileDao,
    private val keystoreManager: KeystoreManager,
    private val tokenManager: TokenManager
) {
    private val mutex = Mutex()

    suspend fun <T> withProfile(profileId: Long, block: suspend () -> T): T {
        return mutex.withLock {
            val snapshot = captureCurrentContext()
            try {
                applyProfileInternal(profileId).getOrThrow()
                block()
            } finally {
                restoreContext(snapshot)
            }
        }
    }

    fun applyProfile(profileId: Long): Result<Unit> {
        return runBlocking {
            mutex.withLock {
                applyProfileInternal(profileId)
            }
        }
    }

    private suspend fun applyProfileInternal(profileId: Long): Result<Unit> {
        if (profileId <= 0L) {
            return Result.failure(IllegalArgumentException("Invalid profileId: $profileId"))
        }

        val profile = serverProfileDao.getProfileById(profileId)
            ?: return Result.failure(IllegalStateException("Profile not found: $profileId"))
        val token = keystoreManager.decrypt(profile.encryptedToken)
            ?: return Result.failure(IllegalStateException("Unable to decrypt token for profile: $profileId"))

        tokenManager.currentProfileId = profile.id
        tokenManager.currentServerUrl = profile.serverUrl
        tokenManager.currentToken = token
        return Result.success(Unit)
    }

    private fun captureCurrentContext(): ProfileSnapshot {
        return ProfileSnapshot(
            profileId = tokenManager.currentProfileId,
            serverUrl = tokenManager.currentServerUrl,
            token = tokenManager.currentToken
        )
    }

    private fun restoreContext(snapshot: ProfileSnapshot) {
        tokenManager.currentProfileId = snapshot.profileId
        tokenManager.currentServerUrl = snapshot.serverUrl
        tokenManager.currentToken = snapshot.token
    }

    private data class ProfileSnapshot(
        val profileId: Long,
        val serverUrl: String?,
        val token: String?
    )
}
