package com.openlistmobile.app.data.repository

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import android.os.Build
import androidx.documentfile.provider.DocumentFile
import com.openlistmobile.app.data.local.SyncRule
import com.openlistmobile.app.data.local.SyncRuleDao
import com.openlistmobile.app.data.local.SyncStatus
import com.openlistmobile.app.data.local.TransferStatus
import com.openlistmobile.app.data.local.TransferTask
import com.openlistmobile.app.data.local.TransferType
import com.openlistmobile.app.data.remote.model.AListFile
import com.openlistmobile.app.domain.repository.FileRepository
import com.openlistmobile.app.domain.repository.SideContents
import com.openlistmobile.app.domain.repository.SyncRepository
import com.openlistmobile.app.domain.repository.TransferRepository
import com.openlistmobile.app.domain.sync.DiffEngine
import com.openlistmobile.app.domain.sync.DiffReport
import com.openlistmobile.app.domain.sync.LocalEntry
import com.openlistmobile.app.service.TransferService
import com.openlistmobile.app.utils.RemoteLinkBuilder
import com.openlistmobile.app.utils.TokenManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SyncRepositoryImpl @Inject constructor(
    private val syncRuleDao: SyncRuleDao,
    private val fileRepository: FileRepository,
    private val transferRepository: TransferRepository,
    private val tokenManager: TokenManager,
    @ApplicationContext private val context: Context
) : SyncRepository {

    override val rules: Flow<List<SyncRule>> = syncRuleDao.getAllRulesFlow()

    override suspend fun getRule(id: Long): SyncRule? = withContext(Dispatchers.IO) {
        syncRuleDao.getRuleById(id)
    }

    override suspend fun createRule(rule: SyncRule): Result<Long> = withContext(Dispatchers.IO) {
        try {
            if (syncRuleDao.countByRemotePath(rule.remotePath) > 0) {
                return@withContext Result.failure(IllegalStateException("该云端目录已被其他规则绑定"))
            }
            if (syncRuleDao.countByLocalUri(rule.localUri) > 0) {
                return@withContext Result.failure(IllegalStateException("该本地目录已被其他规则绑定"))
            }
            Result.success(syncRuleDao.insert(rule))
        } catch (e: SQLiteConstraintException) {
            Result.failure(IllegalStateException("该目录已被其他规则绑定"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteRule(id: Long) = withContext(Dispatchers.IO) {
        syncRuleDao.delete(id)
    }

    override suspend fun updateRule(rule: SyncRule): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            syncRuleDao.update(rule)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateRuleStatus(id: Long, status: SyncStatus, errorMsg: String?) =
        withContext(Dispatchers.IO) {
            syncRuleDao.updateStatus(id, status, errorMsg)
        }

    override suspend fun loadBothSides(rule: SyncRule): Result<SideContents> = withContext(Dispatchers.IO) {
        try {
            Result.success(SideContents(scanRemote(rule.remotePath), scanLocal(rule.localUri)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun computeDiff(rule: SyncRule): Result<DiffReport> = withContext(Dispatchers.IO) {
        try {
            val remote = scanRemote(rule.remotePath)
            val local = scanLocal(rule.localUri)
            Result.success(DiffEngine.computeDiff(rule, remote, local))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun enqueueDiff(rule: SyncRule, report: DiffReport): List<Long> =
        withContext(Dispatchers.IO) {
            val baseUrl = tokenManager.currentServerUrl ?: return@withContext emptyList()
            val tree = DocumentFile.fromTreeUri(context, Uri.parse(rule.localUri))
            val tasks = mutableListOf<TransferTask>()

            for (item in report.toDownload) {
                val remotePath = item.remoteFullPath ?: continue
                val url = RemoteLinkBuilder.build(baseUrl, remotePath, item.remoteSign)
                // 覆盖语义：先删旧再建，避免 SAF 自动改名与续传写坏
                tree?.findFile(item.name)?.delete()
                val target = tree?.createFile("application/octet-stream", item.name) ?: continue
                tasks += TransferTask(
                    fileName = item.name,
                    fileUrl = url,
                    savePath = target.uri.toString(),
                    totalBytes = item.size,
                    downloadedBytes = 0,
                    status = TransferStatus.PENDING,
                    type = TransferType.DOWNLOAD
                )
            }

            for (item in report.toUpload) {
                val localUri = item.localUri ?: continue
                tasks += TransferTask(
                    fileName = item.name,
                    fileUrl = rule.remotePath, // 上传目标目录，TransferService 内部再拼文件名
                    savePath = localUri,
                    totalBytes = item.size,
                    downloadedBytes = 0,
                    status = TransferStatus.PENDING,
                    type = TransferType.UPLOAD
                )
            }

            if (tasks.isEmpty()) emptyList() else transferRepository.addTasks(tasks)
        }

    override suspend fun executeMirrorDeletes(rule: SyncRule, report: DiffReport): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                when (rule.syncMode) {
                    com.openlistmobile.app.data.local.SyncMode.DOWNLOAD_ONLY -> {
                        if (report.toDeleteLocal.isNotEmpty()) {
                            val tree = DocumentFile.fromTreeUri(context, Uri.parse(rule.localUri))
                            report.toDeleteLocal.forEach { item ->
                                tree?.findFile(item.name)?.delete()
                            }
                        }
                    }
                    com.openlistmobile.app.data.local.SyncMode.UPLOAD_ONLY -> {
                        if (report.toDeleteRemote.isNotEmpty()) {
                            val names = report.toDeleteRemote.map { it.name }
                            fileRepository.remove(rule.remotePath, names).getOrThrow()
                        }
                    }
                    else -> Unit // 双向模式不做镜像删除
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override fun startTransferEngine() {
        val intent = Intent(context, TransferService::class.java).apply {
            action = TransferService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private suspend fun scanRemote(path: String): List<AListFile> {
        val result = fileRepository.getFileListFlow(path, page = 1, perPage = 0, refresh = true)
            .toList()
            .lastOrNull()
            ?: throw IllegalStateException("无活动服务器或未登录")
        return result.getOrElse { throw it }.content ?: emptyList()
    }

    private fun scanLocal(localUri: String): List<LocalEntry> {
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(localUri)) ?: return emptyList()
        return tree.listFiles().mapNotNull { doc ->
            val name = doc.name ?: return@mapNotNull null
            LocalEntry(
                name = name,
                uri = doc.uri.toString(),
                size = if (doc.isDirectory) 0L else doc.length(),
                lastModified = doc.lastModified(),
                isDir = doc.isDirectory
            )
        }
    }
}
