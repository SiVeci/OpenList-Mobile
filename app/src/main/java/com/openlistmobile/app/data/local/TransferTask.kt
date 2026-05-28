package com.openlistmobile.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TransferStatus {
    PENDING, DOWNLOADING, PAUSED, SUCCESS, ERROR
}

enum class TransferType {
    DOWNLOAD, UPLOAD
}

@Entity(tableName = "transfer_tasks")
data class TransferTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val fileUrl: String,
    val savePath: String,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val status: TransferStatus,
    val errorMsg: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val type: TransferType = TransferType.DOWNLOAD
)
