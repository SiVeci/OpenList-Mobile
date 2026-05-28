package com.openlistmobile.app.data.local

import androidx.room.Entity
import com.openlistmobile.app.data.remote.model.AListFile

@Entity(tableName = "directory_cache", primaryKeys = ["profileId", "path"])
data class DirectoryCache(
    val profileId: Long,
    val path: String,
    val files: List<AListFile>,
    val timestamp: Long
)
