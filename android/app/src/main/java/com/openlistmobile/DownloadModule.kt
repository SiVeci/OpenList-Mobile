package com.openlistmobile

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import com.facebook.react.module.annotations.ReactModule

@ReactModule(name = DownloadModule.NAME)
class DownloadModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    companion object {
        const val NAME = "DownloadModule"
        private const val TAG = "DownloadModule"
        var reactContextInstance: ReactApplicationContext? = null
    }

    init {
        reactContextInstance = reactContext
    }

    override fun getName(): String = NAME

    @ReactMethod
    fun startDownload(
        url: String,
        treeUri: String,
        fileName: String,
        fileSize: Double,
        mimeType: String,
        authHeader: String,
        promise: Promise
    ) {
        try {
            val intent = Intent(reactApplicationContext, DownloadService::class.java).apply {
                action = DownloadService.ACTION_START
                putExtra(DownloadService.EXTRA_URL, url)
                putExtra(DownloadService.EXTRA_TREE_URI, treeUri)
                putExtra(DownloadService.EXTRA_FILE_NAME, fileName)
                putExtra(DownloadService.EXTRA_FILE_SIZE, fileSize.toLong())
                putExtra(DownloadService.EXTRA_MIME_TYPE, mimeType)
                putExtra(DownloadService.EXTRA_HEADERS, authHeader)
            }
            reactApplicationContext.startService(intent)
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("DOWNLOAD_START_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun cancelDownload(fileName: String, promise: Promise) {
        try {
            val intent = Intent(reactApplicationContext, DownloadService::class.java).apply {
                action = DownloadService.ACTION_CANCEL
                putExtra(DownloadService.EXTRA_FILE_NAME, fileName)
            }
            reactApplicationContext.startService(intent)
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("DOWNLOAD_CANCEL_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun addListener(eventName: String) {}

    @ReactMethod
    fun removeListeners(count: Double) {}
}
