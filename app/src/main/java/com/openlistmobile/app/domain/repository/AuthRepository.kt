package com.openlistmobile.app.domain.repository

import com.openlistmobile.app.data.local.ServerProfile
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    suspend fun loginAndSave(aliasName: String, serverUrl: String, username: String, password: String): Result<String>
    suspend fun initActiveProfile()
    fun getAllProfiles(): Flow<List<ServerProfile>>
    suspend fun switchProfile(profileId: Long): Result<Unit>
    suspend fun logout()
    suspend fun deleteProfile(profileId: Long)
}