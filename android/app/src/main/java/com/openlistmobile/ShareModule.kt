package com.openlistmobile

import android.os.Bundle
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.modules.core.DeviceEventManagerModule

@ReactModule(name = ShareModule.NAME)
class ShareModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    companion object {
        const val NAME = "ShareModule"
        var reactContextInstance: ReactApplicationContext? = null
        
        fun notifyShareReceived() {
            reactContextInstance?.let { context ->
                val data = ShareReceiverActivity.consumePendingShareData() ?: return
                val result: WritableMap = Arguments.createMap().apply {
                    putString("action", data.getString("action"))
                    putString("mimeType", data.getString("mimeType"))
                    putString("uri", data.getString("uri"))

                    val uris = data.getStringArrayList("uris")
                    if (uris != null) {
                        val arr: WritableArray = Arguments.createArray()
                        for (u in uris) {
                            arr.pushString(u)
                        }
                        putArray("uris", arr)
                    }
                }
                context
                    .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                    .emit("onShareReceived", result)
            }
        }
    }
    
    init {
        reactContextInstance = reactContext
    }

    override fun getName(): String = NAME

    @ReactMethod
    fun getPendingShare(promise: Promise) {
        val data = ShareReceiverActivity.consumePendingShareData()
        if (data == null) {
            promise.resolve(null)
            return
        }

        val result: WritableMap = Arguments.createMap().apply {
            putString("action", data.getString("action"))
            putString("mimeType", data.getString("mimeType"))
            putString("uri", data.getString("uri"))

            val uris = data.getStringArrayList("uris")
            if (uris != null) {
                val arr: WritableArray = Arguments.createArray()
                for (u in uris) {
                    arr.pushString(u)
                }
                putArray("uris", arr)
            }
        }

        promise.resolve(result)
    }

    @ReactMethod
    fun addListener(eventName: String) {}

    @ReactMethod
    fun removeListeners(count: Double) {}
}
