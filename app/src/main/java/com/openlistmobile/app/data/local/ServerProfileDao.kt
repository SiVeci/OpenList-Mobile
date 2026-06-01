package com.openlistmobile.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: ServerProfile): Long
    
    @Update
    suspend fun update(profile: ServerProfile)

    @Query("SELECT * FROM server_profiles WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveProfile(): ServerProfile?

    @Query("SELECT * FROM server_profiles WHERE id = :id LIMIT 1")
    suspend fun getProfileById(id: Long): ServerProfile?

    @Query("SELECT * FROM server_profiles")
    fun getAllProfilesFlow(): Flow<List<ServerProfile>>

    @Query("UPDATE server_profiles SET isActive = 0")
    suspend fun clearActiveProfiles()

    @Query("UPDATE server_profiles SET isActive = CASE WHEN id = :profileId THEN 1 ELSE 0 END")
    suspend fun setActiveProfile(profileId: Long)
    
    @Query("SELECT * FROM server_profiles WHERE serverUrl = :url AND username = :user LIMIT 1")
    suspend fun getProfileByUrlAndUser(url: String, user: String): ServerProfile?
    
    @Query("DELETE FROM server_profiles WHERE id = :id")
    suspend fun deleteProfileById(id: Long)
}
