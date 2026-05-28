package com.openlistmobile.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DirectoryCacheDao {
    @Query("SELECT * FROM directory_cache WHERE profileId = :profileId AND path = :path")
    suspend fun getCache(profileId: Long, path: String): DirectoryCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cache: DirectoryCache)
}
