package com.example.alist.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "server_profiles")
data class ServerProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val aliasName: String = "",
    val serverUrl: String,
    val username: String,
    val encryptedToken: String,
    val isActive: Boolean = false
)
