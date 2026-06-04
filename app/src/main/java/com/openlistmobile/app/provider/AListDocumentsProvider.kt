package com.openlistmobile.app.provider

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import com.openlistmobile.app.data.remote.AListApiService
import com.openlistmobile.app.data.remote.model.FileListRequest
import com.openlistmobile.app.data.remote.model.MkdirRequest
import com.openlistmobile.app.data.remote.model.RemoveRequest
import com.openlistmobile.app.data.remote.model.RenameRequest
import com.openlistmobile.app.domain.repository.AuthRepository
import com.openlistmobile.app.utils.RemoteLinkBuilder
import com.openlistmobile.app.utils.TokenManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

import android.os.Handler
import android.os.HandlerThread
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URLEncoder
import kotlin.concurrent.thread

/**
 * 将 AList 挂载为 Android 系统的文档提供者 (类似系统自带的一加云盘、Google Drive)。
 */
class AListDocumentsProvider : DocumentsProvider() {

    // 因为 DocumentsProvider 由系统跨进程实例化，无法通过 Hilt 构造注入，使用 EntryPoint 手动获取依赖
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ProviderEntryPoint {
        fun authRepository(): AuthRepository
        fun apiService(): AListApiService
        fun tokenManager(): TokenManager
        fun okHttpClient(): OkHttpClient
    }

    private val entryPoint: ProviderEntryPoint by lazy {
        EntryPointAccessors.fromApplication(context!!.applicationContext, ProviderEntryPoint::class.java)
    }

    private val defaultRootProjection = arrayOf(
        DocumentsContract.Root.COLUMN_ROOT_ID,
        DocumentsContract.Root.COLUMN_DOCUMENT_ID,
        DocumentsContract.Root.COLUMN_TITLE,
        DocumentsContract.Root.COLUMN_FLAGS,
        DocumentsContract.Root.COLUMN_ICON
    )

    private val defaultDocumentProjection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_FLAGS,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED
    )

    override fun onCreate(): Boolean = true

    private fun ensureInitialized() {
        val tokenManager = entryPoint.tokenManager()
        if (tokenManager.currentProfileId == -1L) {
            runBlocking {
                entryPoint.authRepository().initActiveProfile()
            }
        }
    }

    override fun queryRoots(projection: Array<out String>?): Cursor {
        ensureInitialized()
        val matrixCursor = MatrixCursor(projection ?: defaultRootProjection)
        val tokenManager = entryPoint.tokenManager()
        
        // 只有在已登录时，才挂载驱动器
        if (tokenManager.currentProfileId != -1L && !tokenManager.currentServerUrl.isNullOrEmpty()) {
            val flags = DocumentsContract.Root.FLAG_SUPPORTS_CREATE or
                        DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD
            
            matrixCursor.newRow().apply {
                add(DocumentsContract.Root.COLUMN_ROOT_ID, "alist_root")
                add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, "/")
                add(DocumentsContract.Root.COLUMN_TITLE, "OpenList (${tokenManager.currentServerUrl})")
                add(DocumentsContract.Root.COLUMN_FLAGS, flags)
                add(DocumentsContract.Root.COLUMN_ICON, android.R.mipmap.sym_def_app_icon)
            }
        }
        return matrixCursor
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val matrixCursor = MatrixCursor(projection ?: defaultDocumentProjection)
        
        if (documentId == "/") {
            matrixCursor.newRow().apply {
                add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, documentId)
                add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, "Root")
                add(DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.MIME_TYPE_DIR)
                add(DocumentsContract.Document.COLUMN_SIZE, 0)
                add(DocumentsContract.Document.COLUMN_FLAGS, DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE)
                add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, System.currentTimeMillis())
            }
        } else {
            // DocumentProvider 对于单个文件查询的实现往往依赖于父目录中的缓存或数据库，
            // 简单实现时若非根目录，由于单文件拉取需走 API 不便于封装，可用最小化参数构建占位。
            // 实际工程中建议本地建立轻量级 SQLite 缓存 AListFile 节点。
            val name = documentId.substringAfterLast("/")
            // 该文档总是经由可写目录列表导航而来，乐观地授予写/删/改名权限；
            // 服务端若实际只读，对应操作会在执行时失败，不会崩溃。
            val flags = DocumentsContract.Document.FLAG_SUPPORTS_WRITE or
                        DocumentsContract.Document.FLAG_SUPPORTS_DELETE or
                        DocumentsContract.Document.FLAG_SUPPORTS_RENAME
            matrixCursor.newRow().apply {
                add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, documentId)
                add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, name)
                add(DocumentsContract.Document.COLUMN_MIME_TYPE, getMimeType(name))
                add(DocumentsContract.Document.COLUMN_SIZE, null)
                add(DocumentsContract.Document.COLUMN_FLAGS, flags)
            }
        }
        return matrixCursor
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        ensureInitialized()
        val matrixCursor = MatrixCursor(projection ?: defaultDocumentProjection)

        try {
            runBlocking {
                val baseUrl = entryPoint.tokenManager().currentServerUrl ?: return@runBlocking
                val url = "$baseUrl/api/fs/list"
                val request = FileListRequest(path = parentDocumentId, page = 1, per_page = 0, refresh = true)
                val response = entryPoint.apiService().getFileList(url, request)

                if (response.code == 200 && response.data != null) {
                    val files = response.data.content ?: emptyList()
                    val writable = response.data.write == true
                    for (file in files) {
                        val childId = if (parentDocumentId == "/") "/${file.name}" else "$parentDocumentId/${file.name}"
                        val mimeType = if (file.is_dir) DocumentsContract.Document.MIME_TYPE_DIR else getMimeType(file.name)
                        val flags = if (file.is_dir) {
                            if (writable) {
                                DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE or
                                    DocumentsContract.Document.FLAG_SUPPORTS_DELETE or
                                    DocumentsContract.Document.FLAG_SUPPORTS_RENAME
                            } else 0
                        } else {
                            if (writable) {
                                DocumentsContract.Document.FLAG_SUPPORTS_WRITE or
                                    DocumentsContract.Document.FLAG_SUPPORTS_DELETE or
                                    DocumentsContract.Document.FLAG_SUPPORTS_RENAME
                            } else 0
                        }

                        matrixCursor.newRow().apply {
                            add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, childId)
                            add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, file.name)
                            add(DocumentsContract.Document.COLUMN_MIME_TYPE, mimeType)
                            add(DocumentsContract.Document.COLUMN_SIZE, if (file.is_dir) null else file.size)
                            add(DocumentsContract.Document.COLUMN_FLAGS, flags)
                            add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, System.currentTimeMillis())
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Return empty cursor on error to avoid crashing the system file manager
        }
        return matrixCursor
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        // 写模式：落到本地临时文件供调用方读写，关闭时整体上传回 OpenList
        if (mode.contains("w")) {
            return openForWrite(documentId, mode)
        }

        // 只读：网络存储映射到本地文件，用管道 Pipe 将网络流写入，供系统文件管理器读取
        val url = runBlocking { getSignedUrl(documentId) }
        val request = Request.Builder().url(url).build()

        val pfd = ParcelFileDescriptor.createReliablePipe()
        val readFd = pfd[0]  // 系统拿去读
        val writeFd = pfd[1] // 我们后台拿去写

        thread {
            try {
                val response = entryPoint.okHttpClient().newCall(request).execute()
                if (response.isSuccessful) {
                    val inputStream: InputStream = response.body?.byteStream() ?: throw Exception("Empty body")
                    val outputStream = ParcelFileDescriptor.AutoCloseOutputStream(writeFd)
                    inputStream.copyTo(outputStream)
                    outputStream.close()
                    inputStream.close()
                } else {
                    writeFd.closeWithError("Network error: ${response.code}")
                }
            } catch (e: Exception) {
                writeFd.closeWithError(e.message)
            }
        }
        return readFd
    }

    /**
     * 写模式打开：将文件落到 cache 临时文件，调用方读写完毕（关闭 fd）后整体 PUT 回 OpenList。
     * 非截断模式（rw / wa）先把现有内容拉到临时文件，避免覆盖丢失原内容。
     */
    private fun openForWrite(documentId: String, mode: String): ParcelFileDescriptor {
        val ctx = context!!
        val safeName = documentId.substringAfterLast("/").ifEmpty { "file" }
        val tempFile = File.createTempFile("saf_", "_$safeName", ctx.cacheDir)

        val needExisting = (mode.contains("r") || mode.contains("a")) && !mode.contains("t")
        if (needExisting) {
            try {
                val url = runBlocking { getSignedUrl(documentId) }
                entryPoint.okHttpClient().newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (response.isSuccessful) {
                        response.body?.byteStream()?.use { input ->
                            FileOutputStream(tempFile).use { out -> input.copyTo(out) }
                        }
                    }
                }
            } catch (e: Exception) {
                // 拉取失败则以空文件开始
            }
        }

        val handlerThread = HandlerThread("saf-write").apply { start() }
        val handler = Handler(handlerThread.looper)
        return ParcelFileDescriptor.open(
            tempFile,
            ParcelFileDescriptor.parseMode(mode),
            handler
        ) { err ->
            try {
                // 仅当调用方写入未出错时才回传内容
                if (err == null) {
                    uploadToOpenList(documentId, tempFile)
                    notifyParentChanged(documentId)
                }
            } catch (e: Exception) {
                // 上传失败：此阶段已无法把错误回传给调用方，仅清理
            } finally {
                tempFile.delete()
                handlerThread.quitSafely()
            }
        }
    }

    override fun createDocument(
        parentDocumentId: String,
        mimeType: String,
        displayName: String
    ): String {
        val newPath = if (parentDocumentId == "/") "/$displayName" else "$parentDocumentId/$displayName"
        runBlocking {
            val baseUrl = entryPoint.tokenManager().currentServerUrl ?: throw IllegalStateException("No active server")
            if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                val resp = entryPoint.apiService().mkdir("$baseUrl/api/fs/mkdir", MkdirRequest(path = newPath))
                if (resp.code != 200) throw IllegalStateException(resp.message)
            } else {
                // 新建空文件：上传一个 0 字节文件占位
                val empty = File.createTempFile("saf_new_", null, context!!.cacheDir)
                try {
                    uploadToOpenList(newPath, empty)
                } finally {
                    empty.delete()
                }
            }
        }
        notifyParentChanged(newPath)
        return newPath
    }

    override fun deleteDocument(documentId: String) {
        val parent = documentId.substringBeforeLast("/").ifEmpty { "/" }
        val name = documentId.substringAfterLast("/")
        runBlocking {
            val baseUrl = entryPoint.tokenManager().currentServerUrl ?: throw IllegalStateException("No active server")
            val resp = entryPoint.apiService().remove(
                "$baseUrl/api/fs/remove",
                RemoveRequest(dir = parent, names = listOf(name))
            )
            if (resp.code != 200) throw IllegalStateException(resp.message)
        }
        notifyParentChanged(documentId)
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        val parent = documentId.substringBeforeLast("/").ifEmpty { "/" }
        runBlocking {
            val baseUrl = entryPoint.tokenManager().currentServerUrl ?: throw IllegalStateException("No active server")
            val resp = entryPoint.apiService().rename(
                "$baseUrl/api/fs/rename",
                RenameRequest(name = displayName, path = documentId)
            )
            if (resp.code != 200) throw IllegalStateException(resp.message)
        }
        val newId = if (parent == "/") "/$displayName" else "$parent/$displayName"
        notifyParentChanged(newId)
        return newId
    }

    /**
     * 镜像 TransferService.uploadFile 的 PUT 逻辑：把本地文件整体上传到 OpenList 指定路径。
     * Authorization 头由 NetworkModule 的拦截器自动附加。
     */
    private fun uploadToOpenList(remotePath: String, file: File) {
        val baseUrl = entryPoint.tokenManager().currentServerUrl ?: throw IllegalStateException("No active server")
        val encodedPath = URLEncoder.encode(remotePath, "UTF-8").replace("+", "%20")
        val body = file.asRequestBody("application/octet-stream".toMediaTypeOrNull())
        val request = Request.Builder()
            .url("$baseUrl/api/fs/put")
            .addHeader("File-Path", encodedPath)
            .addHeader("Connection", "close")
            .put(body)
            .build()
        entryPoint.okHttpClient().newCall(request).execute().use {
            if (!it.isSuccessful) throw IllegalStateException("HTTP ${it.code}")
        }
    }

    /** 通知系统该文档所在目录已变化，触发文件管理器刷新。 */
    private fun notifyParentChanged(documentId: String) {
        val parent = documentId.substringBeforeLast("/").ifEmpty { "/" }
        val authority = "${context!!.packageName}.documents"
        val uri = DocumentsContract.buildChildDocumentsUri(authority, parent)
        context!!.contentResolver.notifyChange(uri, null)
    }

    private fun buildDownloadUrl(documentId: String): String {
        // 注意：这里的真实链接获取取决于你在 AList 中是否配置了 sign。
        // 若使用了防盗链保护，你需要在 queryDocument 或使用缓存系统获得文件专属的 Sign。
        // 此处为标准公开直连示例：
        val baseUrl = entryPoint.tokenManager().currentServerUrl ?: ""
        return "$baseUrl/d$documentId"
    }

    /**
     * 获取带签名的文件 URL，用于防盗链适配
     * 使用 sign 通过 RemoteLinkBuilder 构建 URL，确保保留端口号
     */
    private suspend fun getSignedUrl(documentId: String): String {
        val baseUrl = entryPoint.tokenManager().currentServerUrl ?: ""
        val url = "$baseUrl/api/fs/get"
        val request = FileListRequest(path = documentId)
        
        return try {
            val response = entryPoint.apiService().getFileInfo(url, request)
            if (response.code == 200 && response.data != null) {
                val fileInfo = response.data
                // 使用 sign 构建 URL（保留端口号）
                return RemoteLinkBuilder.build(baseUrl, documentId, fileInfo.sign)
            }
            // API 返回失败，降级使用无签名 URL
            buildDownloadUrl(documentId)
        } catch (e: Exception) {
            // 异常降级：使用无签名 URL
            buildDownloadUrl(documentId)
        }
    }

    private fun getMimeType(name: String): String {
        val extension = name.substringAfterLast('.', "")
        if (extension.isEmpty()) return "application/octet-stream"
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase()) 
            ?: "application/octet-stream"
    }
}