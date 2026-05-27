package com.example.alist.data.remote

import kotlinx.coroutines.isActive
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.InputStream
import kotlin.coroutines.coroutineContext

class ProgressRequestBody(
    private val inputStream: InputStream,
    private val contentLength: Long,
    private val onProgress: (Long) -> Unit
) : RequestBody() {

    override fun contentType(): MediaType? = "application/octet-stream".toMediaTypeOrNull()

    override fun contentLength(): Long = contentLength

    override fun writeTo(sink: BufferedSink) {
        val buffer = ByteArray(8192)
        var read: Int
        var totalBytesRead = 0L
        var lastUpdateTime = System.currentTimeMillis()

        while (inputStream.read(buffer).also { read = it } != -1) {
            sink.write(buffer, 0, read)
            totalBytesRead += read

            val now = System.currentTimeMillis()
            if (now - lastUpdateTime > 500 || totalBytesRead == contentLength) {
                lastUpdateTime = now
                onProgress(totalBytesRead)
            }
        }
    }
}
