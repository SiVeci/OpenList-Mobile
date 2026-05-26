package com.openlistmobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.app.NotificationCompat
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class DownloadService : Service() {

    companion object {
        const val CHANNEL_ID = "openlist_download_channel"
        const val CHANNEL_NAME = "Downloads"
        const val EXTRA_URL = "url"
        const val EXTRA_HEADERS = "headers"
        const val EXTRA_TREE_URI = "tree_uri"
        const val EXTRA_FILE_NAME = "file_name"
        const val EXTRA_FILE_SIZE = "file_size"
        const val EXTRA_MIME_TYPE = "mime_type"
        const val ACTION_START = "com.openlistmobile.ACTION_START_DOWNLOAD"
        const val ACTION_CANCEL = "com.openlistmobile.ACTION_CANCEL_DOWNLOAD"
        private const val TAG = "DownloadService"
        private var notificationId = 1000
    }

    private val activeDownloads = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            val fileNameToCancel = intent.getStringExtra(EXTRA_FILE_NAME)
            if (fileNameToCancel != null) {
                activeDownloads[fileNameToCancel] = true // marked as cancelled
                val params = Arguments.createMap().apply {
                    putString("fileName", fileNameToCancel)
                }
                sendEvent("onDownloadCancelled", params)
            }
            // We shouldn't stop self immediately if there are other downloads. 
            // We'll let the worker thread finish and clean up.
            if (activeDownloads.isEmpty()) {
                stopSelf()
            }
            return START_NOT_STICKY
        }

        val url = intent?.getStringExtra(EXTRA_URL) ?: run {
            if (activeDownloads.isEmpty()) stopSelf()
            return START_NOT_STICKY
        }
        val treeUriStr = intent.getStringExtra(EXTRA_TREE_URI) ?: run {
            if (activeDownloads.isEmpty()) stopSelf()
            return START_NOT_STICKY
        }
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: "download"
        val fileSize = intent.getLongExtra(EXTRA_FILE_SIZE, 0L)
        val mimeType = intent.getStringExtra(EXTRA_MIME_TYPE) ?: "application/octet-stream"
        val headerAuth = intent.getStringExtra(EXTRA_HEADERS) ?: ""

        val notifId = ++notificationId
        activeDownloads[fileName] = false // false means not cancelled

        val notification = buildNotification(notifId, fileName, 0, fileSize)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(notifId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(notifId, notification)
        }

        Thread {
            performDownload(url, treeUriStr, fileName, fileSize, mimeType, headerAuth, notifId)
        }.start()

        return START_NOT_STICKY
    }

    private fun performDownload(
        url: String,
        treeUriStr: String,
        fileName: String,
        fileSize: Long,
        mimeType: String,
        headerAuth: String,
        notifId: Int
    ) {
        try {
            trustAllCertificates()
            val treeUri = Uri.parse(treeUriStr)
            val contentResolver = contentResolver
            val docId = if (DocumentsContract.isDocumentUri(this, treeUri)) {
                DocumentsContract.getDocumentId(treeUri)
            } else {
                DocumentsContract.getTreeDocumentId(treeUri)
            }
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)

            val newDocUri = DocumentsContract.createDocument(contentResolver, parentUri, mimeType, fileName)
                ?: throw Exception("Failed to create document")

            var currentUrl = getSafeUrl(url)
            var connection: java.net.HttpURLConnection? = null
            var redirectCount = 0
            val maxRedirects = 5

            val originalUriForPortRecovery = Uri.parse(url)

            while (redirectCount < maxRedirects) {
                val conn = java.net.URL(currentUrl).openConnection() as java.net.HttpURLConnection
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                
                val currentHost = java.net.URL(currentUrl).host
                val originalHost = java.net.URL(url).host
                if (headerAuth.isNotEmpty() && currentHost == originalHost) {
                    conn.setRequestProperty("Authorization", headerAuth)
                    conn.setRequestProperty("AList-Token", headerAuth)
                }
                
                conn.connectTimeout = 30000
                conn.readTimeout = 30000
                conn.instanceFollowRedirects = false // we handle it manually
                
                val status = conn.responseCode
                if (status == java.net.HttpURLConnection.HTTP_MOVED_TEMP || 
                    status == java.net.HttpURLConnection.HTTP_MOVED_PERM || 
                    status == java.net.HttpURLConnection.HTTP_SEE_OTHER ||
                    status == 307 || status == 308) {
                    
                    var newUrlStr = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (newUrlStr == null) throw Exception("Redirect without Location header")
                    
                    val originalScheme = originalUriForPortRecovery.scheme
                    val originalPort = originalUriForPortRecovery.port

                    if (!newUrlStr.startsWith("http")) {
                        newUrlStr = java.net.URL(java.net.URL(currentUrl), newUrlStr).toString()
                    }
                    
                    val redirectUri = Uri.parse(newUrlStr)
                    
                    if (redirectUri.host == originalHost) {
                        val builder = redirectUri.buildUpon()
                        if (originalScheme != null) {
                            builder.scheme(originalScheme)
                        }
                        if (originalPort != -1) {
                            builder.encodedAuthority("$originalHost:$originalPort")
                        }
                        newUrlStr = builder.build().toString()
                    }

                    currentUrl = getSafeUrl(newUrlStr)
                    redirectCount++
                    continue
                }
                
                if (status != java.net.HttpURLConnection.HTTP_OK) {
                    val snippet = try {
                        conn.errorStream?.bufferedReader()?.use { it.readText().take(200) } ?: conn.inputStream?.bufferedReader()?.use { it.readText().take(200) } ?: "No response body"
                    } catch (e: Exception) { "Could not read error body" }
                    conn.disconnect()
                    throw Exception("DIAGNOSTIC HTTP $status:\nURL: $currentUrl\nSnippet: $snippet...")
                }
                
                connection = conn
                break
            }

            if (connection == null) throw Exception("Failed to connect after $maxRedirects redirects")




            val contentType = connection.contentType ?: "unknown"
            val contentLength = connection.contentLength
            if (!mimeType.contains("html", ignoreCase = true) && contentType.contains("text/html", ignoreCase = true)) {
                val snippet = try {
                    connection.inputStream.bufferedReader().use { it.readText().take(200) }
                } catch (e: Exception) {
                    "Could not read snippet"
                }
                
                connection.disconnect()
                try { DocumentsContract.deleteDocument(contentResolver, newDocUri) } catch (e: Exception) {}
                
                val errorMsg = "DIAGNOSTIC INFO:\nReqURL: $currentUrl\nRespCode: ${connection.responseCode}\nType: $contentType\nLen: $contentLength\nExpectedLen: $fileSize\nSnippet: $snippet..."
                throw Exception(errorMsg)
            }

            val inputStream = connection.inputStream
            val outputStream = contentResolver.openOutputStream(newDocUri)
                ?: throw Exception("Failed to open output stream")

            outputStream.use { os ->
                inputStream.use { is2 ->
                    val buffer = ByteArray(8192)
                    var totalRead = 0L
                    var lastProgress = -1

                    while (true) {
                        if (activeDownloads[fileName] == true) { // cancelled
                            DocumentsContract.deleteDocument(contentResolver, newDocUri)
                            break
                        }

                        val read = is2.read(buffer)
                        if (read == -1) break

                        os.write(buffer, 0, read)
                        totalRead += read

                        if (fileSize > 0) {
                            val progress = ((totalRead * 100) / fileSize).toInt()
                            if (progress != lastProgress) {
                                lastProgress = progress
                                updateNotification(notifId, fileName, progress, fileSize)

                                val params = Arguments.createMap().apply {
                                    putString("fileName", fileName)
                                    putInt("progress", progress)
                                    putDouble("bytesRead", totalRead.toDouble())
                                    putDouble("totalBytes", fileSize.toDouble())
                                }
                                sendEvent("onDownloadProgress", params)
                            }
                        }
                    }
                }
            }

            if (activeDownloads[fileName] == false) { // not cancelled
                updateNotification(notifId, fileName, 100, fileSize, true)

                val params = Arguments.createMap().apply {
                    putString("fileName", fileName)
                    putString("uri", newDocUri.toString())
                    putInt("progress", 100)
                }
                sendEvent("onDownloadComplete", params)
            }

            connection.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)

            val params = Arguments.createMap().apply {
                putString("fileName", fileName)
                putString("error", e.message)
            }
            sendEvent("onDownloadError", params)
        } finally {
            activeDownloads.remove(fileName)
            
            // Only stop foreground/self if this was the last active download
            if (activeDownloads.isEmpty()) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            } else {
                // Just remove this specific notification
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.cancel(notifId)
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "File download progress"
            setShowBadge(false)
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(notifId: Int, fileName: String, progress: Int, total: Long, complete: Boolean = false): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (complete) "$fileName - Complete" else "Downloading $fileName")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(!complete)
            .setProgress(100, progress, progress == 0 && total == 0L)

        if (complete) {
            builder.setSmallIcon(android.R.drawable.stat_sys_download_done)
        }

        return builder.build()
    }

    private fun updateNotification(notifId: Int, fileName: String, progress: Int, total: Long, complete: Boolean = false) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notifId, buildNotification(notifId, fileName, progress, total, complete))
    }

    private fun sendEvent(eventName: String, params: WritableMap?) {
        DownloadModule.reactContextInstance
            ?.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            ?.emit(eventName, params)
    }

    
    private fun getSafeUrl(urlStr: String): String {
        return urlStr.replace(" ", "%20")
    }

    private fun trustAllCertificates() {
        try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
            })

            val sc = SSLContext.getInstance("TLS")
            sc.init(null, trustAllCerts, java.security.SecureRandom())
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.socketFactory)
            HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trust all certificates", e)
        }
    }
}
