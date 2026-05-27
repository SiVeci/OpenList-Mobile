package com.example.alist.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.alist.MainActivity
import com.example.alist.data.local.TransferStatus
import com.example.alist.data.local.TransferTaskDao
import com.example.alist.data.local.TransferType
import com.example.alist.data.remote.ProgressRequestBody
import com.example.alist.utils.TokenManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

@AndroidEntryPoint
class TransferService : Service() {

    @Inject
    lateinit var transferTaskDao: TransferTaskDao

    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var tokenManager: TokenManager

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var activeJob: Job? = null
    
    companion object {
        const val CHANNEL_ID = "transfer_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_CANCEL = "ACTION_CANCEL"
        const val EXTRA_TASK_ID = "EXTRA_TASK_ID"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private var isWorkerRunning = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val taskId = intent?.getLongExtra(EXTRA_TASK_ID, -1L) ?: -1L

        when (action) {
            ACTION_START, ACTION_RESUME -> {
                startForeground(NOTIFICATION_ID, createNotification("Starting transfer...", 0, 100))
                startWorker()
            }
            ACTION_PAUSE -> {
                if (taskId != -1L) {
                    serviceScope.launch {
                        val task = transferTaskDao.getTaskById(taskId)
                        if (task != null) {
                            transferTaskDao.update(task.copy(status = TransferStatus.PAUSED))
                        }
                    }
                }
            }
            ACTION_CANCEL -> {
                activeJob?.cancel()
                if (taskId != -1L) {
                    serviceScope.launch {
                        val task = transferTaskDao.getTaskById(taskId)
                        if (task != null) {
                            transferTaskDao.update(task.copy(status = TransferStatus.ERROR, errorMsg = "Cancelled"))
                        }
                    }
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startWorker() {
        if (isWorkerRunning) return
        isWorkerRunning = true
        
        activeJob = serviceScope.launch {
            try {
                while (isActive) {
                    val nextTask = transferTaskDao.getNextQueuedTask()
                    if (nextTask == null) {
                        break
                    }
                    if (nextTask.type == TransferType.DOWNLOAD) {
                        downloadFile(nextTask.id)
                    } else {
                        uploadFile(nextTask.id)
                    }
                }
            } finally {
                isWorkerRunning = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private suspend fun downloadFile(taskId: Long) {
        val task = transferTaskDao.getTaskById(taskId) ?: return
        
        val downloadingTask = task.copy(status = TransferStatus.DOWNLOADING)
        transferTaskDao.update(downloadingTask)

        try {
            val file = File(downloadingTask.savePath)
            val downloaded = if (file.exists()) file.length() else 0L

            val requestBuilder = Request.Builder().url(downloadingTask.fileUrl)
            if (downloaded > 0) {
                requestBuilder.addHeader("Range", "bytes=$downloaded-")
            }

            val request = requestBuilder.build()
            val response = withContext(Dispatchers.IO) {
                okHttpClient.newCall(request).execute()
            }

            if (!response.isSuccessful) {
                if (response.code == 416) {
                    transferTaskDao.update(task.copy(status = TransferStatus.SUCCESS))
                    return
                }
                throw Exception("HTTP ${response.code}")
            }

            val body = response.body ?: throw Exception("Empty body")
            val contentLength = body.contentLength()
            val total = if (downloaded > 0) downloaded + contentLength else contentLength

            var currentTask = downloadingTask.copy(totalBytes = total, downloadedBytes = downloaded)
            transferTaskDao.update(currentTask)

            body.byteStream().use { input ->
                FileOutputStream(file, downloaded > 0).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var lastUpdateTime = System.currentTimeMillis()

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (!currentCoroutineContext().isActive) throw CancellationException()

                        output.write(buffer, 0, bytesRead)
                        currentTask = currentTask.copy(downloadedBytes = currentTask.downloadedBytes + bytesRead)

                        val now = System.currentTimeMillis()
                        if (now - lastUpdateTime > 500) {
                            lastUpdateTime = now
                            transferTaskDao.update(currentTask)
                            updateNotification("Downloading: ${currentTask.fileName}", currentTask.downloadedBytes, currentTask.totalBytes)
                        }
                    }
                }
            }

            transferTaskDao.update(currentTask.copy(status = TransferStatus.SUCCESS))
            updateNotification("Download complete: ${currentTask.fileName}", 100, 100)
            delay(1000)

        } catch (e: CancellationException) {
            // Task was paused or cancelled
        } catch (e: Exception) {
            transferTaskDao.update(task.copy(status = TransferStatus.ERROR, errorMsg = e.message))
            updateNotification("Download failed: ${task.fileName}", 0, 100)
            delay(1000)
        }
    }

    private suspend fun uploadFile(taskId: Long) {
        var task = transferTaskDao.getTaskById(taskId) ?: return
        
        task = task.copy(status = TransferStatus.DOWNLOADING) // Reuse DOWNLOADING for active state
        transferTaskDao.update(task)

        try {
            val uri = Uri.parse(task.savePath)
            val inputStream = contentResolver.openInputStream(uri) ?: throw Exception("Could not open input stream")
            
            val baseUrl = tokenManager.currentServerUrl ?: throw Exception("No active server")
            val token = tokenManager.currentToken ?: throw Exception("No token")
            
            // task.fileUrl here contains the target path
            val targetPath = if (task.fileUrl.endsWith("/")) "${task.fileUrl}${task.fileName}" else "${task.fileUrl}/${task.fileName}"
            val encodedPath = java.net.URLEncoder.encode(targetPath, "UTF-8").replace("+", "%20")
            val url = "$baseUrl/api/fs/put"

            // Fix for Content-Length: If totalBytes is invalid, try to get actual size
            var actualTotalBytes = task.totalBytes
            if (actualTotalBytes <= 0) {
                try {
                    actualTotalBytes = contentResolver.openAssetFileDescriptor(uri, "r")?.length ?: inputStream.available().toLong()
                    // Update task with correct size
                    if (actualTotalBytes > 0) {
                        task = task.copy(totalBytes = actualTotalBytes)
                        transferTaskDao.update(task)
                    }
                } catch (e: Exception) {
                    // Ignore, fallback to original size
                }
            }

            val requestBody = ProgressRequestBody(
                inputStream = inputStream,
                contentLength = actualTotalBytes,
                onProgress = { bytesWritten ->
                    if (!serviceScope.isActive) return@ProgressRequestBody
                    serviceScope.launch {
                        task = task.copy(downloadedBytes = bytesWritten)
                        transferTaskDao.update(task)
                        updateNotification("Uploading: ${task.fileName}", bytesWritten, task.totalBytes)
                    }
                }
            )

            val request = Request.Builder()
                .url(url)
                // Removed duplicate Authorization header, as NetworkModule already provides an interceptor
                .addHeader("File-Path", encodedPath)
                .put(requestBody)
                .build()

            val response = withContext(Dispatchers.IO) {
                okHttpClient.newCall(request).execute()
            }

            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: ${response.message}")
            }

            transferTaskDao.update(task.copy(status = TransferStatus.SUCCESS))
            updateNotification("Upload complete: ${task.fileName}", 100, 100)
            delay(1000)

        } catch (e: CancellationException) {
             // Task was cancelled
        } catch (e: Exception) {
            transferTaskDao.update(task.copy(status = TransferStatus.ERROR, errorMsg = e.message))
            updateNotification("Upload failed: ${task.fileName}", 0, 100)
            delay(1000)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Transfer Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String, progress: Long, max: Long): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OpenList Transfer")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(max.toInt(), progress.toInt(), max == 0L)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String, progress: Long, max: Long) {
        val notification = createNotification(text, progress, max)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}
