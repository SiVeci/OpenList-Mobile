package com.openlistmobile

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.BaseActivityEventListener
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import com.facebook.react.module.annotations.ReactModule
import java.io.File
import java.io.FileOutputStream
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

@ReactModule(name = SAFModule.NAME)
class SAFModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    companion object {
        const val NAME = "SAFModule"
        private const val PICK_DIR_REQUEST_CODE = 1001
        private const val PICK_FILE_REQUEST_CODE = 1002
        private const val TAG = "SAFModule"
    }

    private var pickDirPromise: Promise? = null
    private var pickFilePromise: Promise? = null

    private val activityEventListener = object : BaseActivityEventListener() {
        override fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?) {
            if (requestCode == PICK_DIR_REQUEST_CODE) {
                val promise = pickDirPromise
                pickDirPromise = null
                if (promise == null) return

                if (resultCode != Activity.RESULT_OK || data == null) {
                    promise.reject("CANCELLED", "Directory picker cancelled")
                    return
                }

                val treeUri = data.data ?: run {
                    promise.reject("NO_URI", "No URI returned")
                    return
                }

                try {
                    val contentResolver = reactApplicationContext.contentResolver
                    val takeFlags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    contentResolver.takePersistableUriPermission(treeUri, takeFlags)

                    val result = Arguments.createMap().apply {
                        putString("uri", treeUri.toString())
                        putString("name", getDirName(contentResolver, treeUri))
                    }
                    promise.resolve(result)
                } catch (e: Exception) {
                    promise.reject("PERMISSION_ERROR", e.message, e)
                }
            } else if (requestCode == PICK_FILE_REQUEST_CODE) {
                val promise = pickFilePromise
                pickFilePromise = null
                if (promise == null) return

                if (resultCode != Activity.RESULT_OK || data == null) {
                    promise.reject("CANCELLED", "File picker cancelled")
                    return
                }

                val uri = data.data ?: run {
                    promise.reject("NO_URI", "No URI returned")
                    return
                }

                try {
                    val contentResolver = reactApplicationContext.contentResolver
                    val result = Arguments.createMap().apply {
                        putString("uri", uri.toString())
                        
                        var name = "unknown"
                        var size = 0L
                        val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
                        
                        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                                if (nameIdx >= 0) name = cursor.getString(nameIdx)
                                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
                            }
                        }
                        
                        putString("name", name)
                        putDouble("size", size.toDouble())
                        putString("mimeType", mimeType)
                    }
                    promise.resolve(result)
                } catch (e: Exception) {
                    promise.reject("PICK_FILE_ERROR", e.message, e)
                }
            }
        }
    }

    init {
        reactContext.addActivityEventListener(activityEventListener)
    }

    override fun getName(): String = NAME

    @ReactMethod
    fun pickDirectory(promise: Promise) {
        val activity = getCurrentActivity()
        if (activity == null) {
            promise.reject("NO_ACTIVITY", "No current activity")
            return
        }
        pickDirPromise = promise
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
                )
            }
            activity.startActivityForResult(intent, PICK_DIR_REQUEST_CODE)
        } catch (e: Exception) {
            pickDirPromise = null
            promise.reject("INTENT_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun pickFile(promise: Promise) {
        val activity = getCurrentActivity()
        if (activity == null) {
            promise.reject("NO_ACTIVITY", "No current activity")
            return
        }
        pickFilePromise = promise
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            activity.startActivityForResult(intent, PICK_FILE_REQUEST_CODE)
        } catch (e: Exception) {
            pickFilePromise = null
            promise.reject("INTENT_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun listFiles(treeUriStr: String, promise: Promise) {
        try {
            val treeUri = Uri.parse(treeUriStr)
            val contentResolver = reactApplicationContext.contentResolver
            val docId = if (DocumentsContract.isDocumentUri(reactApplicationContext, treeUri)) {
                DocumentsContract.getDocumentId(treeUri)
            } else {
                DocumentsContract.getTreeDocumentId(treeUri)
            }
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            val result: WritableArray = Arguments.createArray()

            contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                    DocumentsContract.Document.COLUMN_FLAGS
                ),
                null, null,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val docId = cursor.getString(0)
                    val name = cursor.getString(1)
                    val mimeType = cursor.getString(2)
                    val size = if (cursor.isNull(3)) 0L else cursor.getLong(3)
                    val lastModified = if (cursor.isNull(4)) 0L else cursor.getLong(4)
                    val flags = if (cursor.isNull(5)) 0 else cursor.getInt(5)

                    val isDir = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
                    val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)

                    val item = Arguments.createMap().apply {
                        putString("name", name)
                        putBoolean("is_dir", isDir)
                        putDouble("size", size.toDouble())
                        putDouble("modified", lastModified.toDouble())
                        putString("uri", documentUri.toString())
                        putString("mimeType", mimeType ?: "")
                        putString("documentId", docId)
                    }
                    result.pushMap(item)
                }
            }

            promise.resolve(result)
        } catch (e: Exception) {
            promise.reject("LIST_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun readFile(uriStr: String, promise: Promise) {
        try {
            val uri = Uri.parse(uriStr)
            val contentResolver = reactApplicationContext.contentResolver
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw Exception("Cannot open input stream")
            promise.resolve(bytes)
        } catch (e: Exception) {
            promise.reject("READ_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun writeFile(treeUriStr: String, fileName: String, base64Data: String, mimeType: String, promise: Promise) {
        try {
            val treeUri = Uri.parse(treeUriStr)
            val contentResolver = reactApplicationContext.contentResolver
            val docId = if (DocumentsContract.isDocumentUri(reactApplicationContext, treeUri)) {
                DocumentsContract.getDocumentId(treeUri)
            } else {
                DocumentsContract.getTreeDocumentId(treeUri)
            }
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)

            val newDocUri = DocumentsContract.createDocument(contentResolver, parentUri, mimeType, fileName)
                ?: throw Exception("Failed to create document")

            contentResolver.openOutputStream(newDocUri)?.use { os ->
                val bytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                os.write(bytes)
                os.flush()
            } ?: throw Exception("Failed to open output stream")

            promise.resolve(newDocUri.toString())
        } catch (e: Exception) {
            promise.reject("WRITE_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun deleteFile(uriStr: String, promise: Promise) {
        try {
            val uri = Uri.parse(uriStr)
            val contentResolver = reactApplicationContext.contentResolver
            val deleted = DocumentsContract.deleteDocument(contentResolver, uri)
            promise.resolve(deleted)
        } catch (e: Exception) {
            promise.reject("DELETE_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun getFileDetail(uriStr: String, promise: Promise) {
        try {
            val uri = Uri.parse(uriStr)
            val contentResolver = reactApplicationContext.contentResolver
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) else ""
                    val size = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else 0L
                    val result = Arguments.createMap().apply {
                        putString("name", name)
                        putDouble("size", size.toDouble())
                        putString("uri", uriStr)
                    }
                    promise.resolve(result)
                } else {
                    promise.reject("NOT_FOUND", "File not found")
                }
            } ?: promise.reject("QUERY_ERROR", "Cannot query URI")
        } catch (e: Exception) {
            promise.reject("DETAIL_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun downloadFileToSAF(url: String, treeUriStr: String, fileName: String, mimeType: String, headerAuth: String, promise: Promise) {
        Thread {
            try {
                trustAllCertificates()
                val treeUri = Uri.parse(treeUriStr)
                val contentResolver = reactApplicationContext.contentResolver
                val docId = if (DocumentsContract.isDocumentUri(reactApplicationContext, treeUri)) {
                    DocumentsContract.getDocumentId(treeUri)
                } else {
                    DocumentsContract.getTreeDocumentId(treeUri)
                }
                val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)

                val newDocUri = DocumentsContract.createDocument(contentResolver, parentUri, mimeType, fileName)
                    ?: throw Exception("Failed to create document")

                var currentUrl = url.replace(" ", "%20")
                var connection: java.net.HttpURLConnection? = null
                var redirectCount = 0
                val maxRedirects = 5

                while (redirectCount < maxRedirects) {
                    val conn = java.net.URL(currentUrl).openConnection() as java.net.HttpURLConnection
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    if (headerAuth.isNotEmpty()) {
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
                        
                        var newUrl = conn.getHeaderField("Location")
                        conn.disconnect()
                        if (newUrl == null) throw Exception("Redirect without Location header")
                        newUrl = newUrl.replace(" ", "%20")
                        currentUrl = newUrl
                        redirectCount++
                        continue
                    }
                    
                    if (status != java.net.HttpURLConnection.HTTP_OK) {
                        conn.disconnect()
                        throw Exception("Server returned HTTP $status")
                    }
                    
                    connection = conn
                    break
                }

                if (connection == null) throw Exception("Failed to connect after $maxRedirects redirects")

                val contentType = connection.contentType ?: ""
                if (!mimeType.contains("html", ignoreCase = true) && contentType.contains("text/html", ignoreCase = true)) {
                    connection.disconnect()
                    DocumentsContract.deleteDocument(contentResolver, newDocUri)
                    throw Exception("Error: Server returned an HTML page instead of the file.")
                }

                connection.inputStream.use { inputStream ->
                    contentResolver.openOutputStream(newDocUri)?.use { outputStream ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (inputStream.read(buffer).also { read = it } != -1) {
                            outputStream.write(buffer, 0, read)
                        }
                        outputStream.flush()
                    } ?: throw Exception("Failed to open output stream")
                }
                connection.disconnect()
                promise.resolve(newDocUri.toString())
            } catch (e: Exception) {
                promise.reject("DOWNLOAD_ERROR", e.message, e)
            }
        }.start()
    }

    private fun getDirName(contentResolver: ContentResolver, treeUri: Uri): String {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val parts = docId.split(":")
            parts.lastOrNull() ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun trustAllCertificates() {
        try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
            })

            val sc = SSLContext.getInstance("SSL")
            sc.init(null, trustAllCerts, java.security.SecureRandom())
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.socketFactory)
            HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trust all certificates", e)
        }
    }
}
