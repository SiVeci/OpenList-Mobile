package com.openlistmobile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class ShareReceiverActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ShareReceiver"
        private val lock = Any()
        private var _pendingShareData: Bundle? = null
        
        var pendingShareData: Bundle?
            get() = synchronized(lock) { _pendingShareData }
            set(value) = synchronized(lock) { _pendingShareData = value }
            
        fun consumePendingShareData(): Bundle? {
            synchronized(lock) {
                val data = _pendingShareData
                _pendingShareData = null
                return data
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val action = intent?.action
        val type = intent?.type

        if (action == Intent.ACTION_SEND && type != null) {
            val uri: Uri? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }

            if (uri != null) {
                Thread {
                    val cachedPath = copyUriToCache(uri)
                    if (cachedPath != null) {
                        pendingShareData = Bundle().apply {
                            putString("action", "send")
                            putString("mimeType", type)
                            putString("uri", "file://$cachedPath")
                        }
                    }
                    launchAppAndFinish()
                    ShareModule.notifyShareReceived()
                }.start()
                return // wait for thread
            }
        } else if (action == Intent.ACTION_SEND_MULTIPLE && type != null) {
            val uris: ArrayList<Uri>? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
            }

            if (uris != null && uris.isNotEmpty()) {
                Thread {
                    val cachedPaths = ArrayList<String>()
                    for (uri in uris) {
                        val path = copyUriToCache(uri)
                        if (path != null) cachedPaths.add("file://$path")
                    }
                    if (cachedPaths.isNotEmpty()) {
                        pendingShareData = Bundle().apply {
                            putString("action", "send_multiple")
                            putString("mimeType", type)
                            putStringArrayList("uris", cachedPaths)
                        }
                    }
                    launchAppAndFinish()
                    ShareModule.notifyShareReceived()
                }.start()
                return // wait for thread
            }
        }

        launchAppAndFinish()
    }

    private fun copyUriToCache(uri: Uri): String? {
        try {
            var name = "shared_file_${System.currentTimeMillis()}"
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) {
                        name = cursor.getString(nameIdx)
                    }
                }
            }

            val cacheFile = File(cacheDir, name)
            contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(cacheFile).use { outputStream ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                    }
                }
            }
            return cacheFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy shared file to cache", e)
            return null
        }
    }

    private fun launchAppAndFinish() {
        runOnUiThread {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                startActivity(launchIntent)
            }
            finish()
        }
    }
}

