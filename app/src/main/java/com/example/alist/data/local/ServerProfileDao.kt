package com.example.alist.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ServerProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: ServerProfile): Long

    @Query("SELECT * FROM server_profiles WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveProfile(): ServerProfile?

    @Query("UPDATE server_profiles SET isActive = 0")
    suspend fun clearActiveProfiles()
}
