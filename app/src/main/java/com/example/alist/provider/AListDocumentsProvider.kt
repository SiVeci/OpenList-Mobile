package com.example.alist.provider

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import com.example.alist.domain.repository.FileRepository
import com.example.alist.utils.TokenManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import kotlin.concurrent.thread

/**
 * 将 AList 挂载为 Android 系统的文档提供者 (类似系统自带的一加云盘、Google Drive)。
 */
class AListDocumentsProvider : DocumentsProvider() {

    // 因为 DocumentsProvider 由系统跨进程实例化，无法通过 Hilt 构造注入，使用 EntryPoint 手动获取依赖
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ProviderEntryPoint {
        fun fileRepository(): FileRepository
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

    override fun queryRoots(projection: Array<out String>?): Cursor {
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
            matrixCursor.newRow().apply {
                add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, documentId)
                add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, name)
                add(DocumentsContract.Document.COLUMN_MIME_TYPE, getMimeType(name))
                add(DocumentsContract.Document.COLUMN_SIZE, null)
                add(DocumentsContract.Document.COLUMN_FLAGS, 0)
            }
        }
        return matrixCursor
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val matrixCursor = MatrixCursor(projection ?: defaultDocumentProjection)
        
        // Android 系统要求 DocumentsProvider 同步返回，我们这里通过 runBlocking 阻塞后台线程以获取网络数据
        runBlocking {
            val result = entryPoint.fileRepository().getFileListFlow(parentDocumentId, 1, 0, refresh = true).firstOrNull()
            val files = result?.getOrNull()?.content ?: emptyList()
            
            for (file in files) {
                // 构建完整的系统 ID 路径
                val childId = if (parentDocumentId == "/") "/${file.name}" else "$parentDocumentId/${file.name}"
                val mimeType = if (file.is_dir) DocumentsContract.Document.MIME_TYPE_DIR else getMimeType(file.name)
                val flags = if (file.is_dir) DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE else 0
                
                matrixCursor.newRow().apply {
                    add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, childId)
                    add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, file.name)
                    add(DocumentsContract.Document.COLUMN_MIME_TYPE, mimeType)
                    add(DocumentsContract.Document.COLUMN_SIZE, if (file.is_dir) null else file.size)
                    add(DocumentsContract.Document.COLUMN_FLAGS, flags)
                    // file.modified 通常是 ISO 字符串，这里简化处理，可结合 DateTime 格式化
                    add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, System.currentTimeMillis())
                }
            }
        }
        return matrixCursor
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        // 由于是网络存储映射到本地文件，我们采用管道 Pipe 将网络流写入，供系统文件管理器读取
        val url = buildDownloadUrl(documentId)
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

    private fun buildDownloadUrl(documentId: String): String {
        // 注意：这里的真实链接获取取决于你在 AList 中是否配置了 sign。
        // 若使用了防盗链保护，你需要在 queryDocument 或使用缓存系统获得文件专属的 Sign。
        // 此处为标准公开直连示例：
        val baseUrl = entryPoint.tokenManager().currentServerUrl ?: ""
        return "$baseUrl/d$documentId"
    }

    private fun getMimeType(name: String): String {
        val extension = name.substringAfterLast('.', "")
        if (extension.isEmpty()) return "application/octet-stream"
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase()) 
            ?: "application/octet-stream"
    }
}