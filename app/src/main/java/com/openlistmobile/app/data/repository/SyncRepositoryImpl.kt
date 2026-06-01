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
import com.openlistmobile.app.data.local.SyncTrigger
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
import com.openlistmobile.app.domain.sync.LocalNode
import com.openlistmobile.app.domain.sync.RemoteNode
import com.openlistmobile.app.service.TransferService
import com.openlistmobile.app.utils.ProfileContextManager
import com.openlistmobile.app.utils.RemoteLinkBuilder
import com.openlistmobile.app.utils.TokenManager
import com.openlistmobile.app.work.SyncWorkScheduler
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
    private val profileContextManager: ProfileContextManager,
    private val syncWorkScheduler: SyncWorkScheduler,
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
            val id = syncRuleDao.insert(rule)
            syncWorkScheduler.upsertRuleWork(rule.copy(id = id))
            Result.success(id)
        } catch (e: SQLiteConstraintException) {
            Result.failure(IllegalStateException("该目录已被其他规则绑定"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteRule(id: Long) = withContext(Dispatchers.IO) {
        syncRuleDao.delete(id)
        syncWorkScheduler.cancelRuleWork(id)
    }

    override suspend fun updateRule(rule: SyncRule): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            syncRuleDao.update(rule)
            syncWorkScheduler.upsertRuleWork(rule)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateRuleStatus(id: Long, status: SyncStatus, errorMsg: String?) =
        withContext(Dispatchers.IO) {
            syncRuleDao.updateStatus(id, status, errorMsg)
        }

    override suspend fun runAutoSync(ruleId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        val rule = syncRuleDao.getRuleById(ruleId) ?: return@withContext Result.success(Unit)
        if (rule.triggerType == SyncTrigger.MANUAL) return@withContext Result.success(Unit)

        try {
            profileContextManager.withProfile(rule.profileId) {
                validateLocalAccess(rule)
                syncRuleDao.updateStatus(rule.id, SyncStatus.DIFFING, null)

                val diff = computeDiff(rule).getOrThrow()
                if (rule.isMirrorDeleteEnabled) {
                    executeMirrorDeletes(rule, diff).getOrThrow()
                }

                val taskIds = enqueueDiff(rule, diff)
                if (taskIds.isNotEmpty()) {
                    startTransferEngine()
                }

                syncRuleDao.updateSyncResult(
                    id = rule.id,
                    lastSyncTime = System.currentTimeMillis(),
                    status = SyncStatus.IDLE,
                    errorMsg = null
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            syncRuleDao.updateSyncResult(
                id = rule.id,
                lastSyncTime = rule.lastSyncTime,
                status = SyncStatus.ERROR,
                errorMsg = e.message ?: e::class.java.simpleName
            )
            Result.failure(e)
        }
    }

    override suspend fun loadBothSides(rule: SyncRule): Result<SideContents> = withContext(Dispatchers.IO) {
        try {
            Result.success(SideContents(scanRemoteTree(rule.remotePath), scanLocalTree(rule.localUri)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun computeDiff(rule: SyncRule): Result<DiffReport> = withContext(Dispatchers.IO) {
        try {
            val remote = scanRemoteTree(rule.remotePath)
            val local = scanLocalTree(rule.localUri)
            Result.success(DiffEngine.computeDiff(rule, remote, local))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun enqueueDiff(rule: SyncRule, report: DiffReport): List<Long> =
        withContext(Dispatchers.IO) {
            val baseUrl = tokenManager.currentServerUrl ?: return@withContext emptyList()
            val rootTree = DocumentFile.fromTreeUri(context, Uri.parse(rule.localUri))
            val tasks = mutableListOf<TransferTask>()

            for (item in report.toDownload) {
                val remotePath = item.remoteFullPath ?: continue
                val url = RemoteLinkBuilder.build(baseUrl, remotePath, item.remoteSign)
                // 按相对路径在本地树逐层建子目录，末层先删旧再建（覆盖语义，防 SAF 改名）
                val parentDir = ensureLocalParentDir(rootTree, item.relativePath) ?: continue
                parentDir.findFile(item.name)?.delete()
                val target = parentDir.createFile("application/octet-stream", item.name) ?: continue
                tasks += TransferTask(
                    profileId = rule.profileId,
                    fileName = item.name,
                    fileUrl = url,
                    savePath = target.uri.toString(),
                    totalBytes = item.size,
                    downloadedBytes = 0,
                    status = TransferStatus.PENDING,
                    type = TransferType.DOWNLOAD
                )
            }

            // 上传：先对所有唯一的云端父目录逐层 mkdir，再入队
            val uploadParents = report.toUpload
                .map { remoteParentPath(rule.remotePath, it.relativePath) }
                .toSet()
            uploadParents.forEach { parent ->
                ensureRemoteDir(rule.remotePath, parent)
            }
            for (item in report.toUpload) {
                val localUri = item.localUri ?: continue
                tasks += TransferTask(
                    profileId = rule.profileId,
                    fileName = item.name,
                    fileUrl = remoteParentPath(rule.remotePath, item.relativePath), // 云端目标父目录
                    savePath = localUri,
                    totalBytes = item.size,
                    downloadedBytes = 0,
                    status = TransferStatus.PENDING,
                    type = TransferType.UPLOAD
                )
            }

            if (tasks.isEmpty()) emptyList() else transferRepository.addTasks(tasks)
        }

    /** 按相对路径在本地树逐层 findFile?:createDirectory 到文件的父目录，返回父目录。 */
    private fun validateLocalAccess(rule: SyncRule) {
        val uri = Uri.parse(rule.localUri)
        val hasReadPermission = context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }
        if (!hasReadPermission) {
            throw IllegalStateException("Local folder permission is missing")
        }

        val root = DocumentFile.fromTreeUri(context, uri)
        if (root == null || !root.exists() || !root.isDirectory) {
            throw IllegalStateException("Local folder is unavailable")
        }
    }

    private fun ensureLocalParentDir(root: DocumentFile?, relativePath: String): DocumentFile? {
        if (root == null) return null
        val segments = relativePath.split("/").filter { it.isNotEmpty() }
        if (segments.size <= 1) return root // 根层文件
        var dir = root
        for (i in 0 until segments.size - 1) {
            val seg = segments[i]
            val existing = dir!!.findFile(seg)
            dir = if (existing != null && existing.isDirectory) existing else dir.createDirectory(seg)
            if (dir == null) return null
        }
        return dir
    }

    /** 云端文件所在父目录绝对路径。relativePath 含子目录时返回其父，否则返回根。 */
    private fun remoteParentPath(root: String, relativePath: String): String {
        val base = root.trimEnd('/')
        val segments = relativePath.split("/").filter { it.isNotEmpty() }
        if (segments.size <= 1) return base
        return base + "/" + segments.dropLast(1).joinToString("/")
    }

    /** 自顶向下逐层 mkdir 云端目录（幂等，已存在 AList 会报错但忽略）。 */
    private suspend fun ensureRemoteDir(root: String, fullDirPath: String) {
        val base = root.trimEnd('/')
        if (fullDirPath == base) return
        val rel = fullDirPath.removePrefix("$base/")
        val segments = rel.split("/").filter { it.isNotEmpty() }
        var cur = base
        for (seg in segments) {
            cur = "$cur/$seg"
            fileRepository.mkdir(cur) // 幂等：已存在则失败，忽略
        }
    }

    override suspend fun executeMirrorDeletes(rule: SyncRule, report: DiffReport): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                when (rule.syncMode) {
                    com.openlistmobile.app.data.local.SyncMode.DOWNLOAD_ONLY -> {
                        if (report.toDeleteLocal.isNotEmpty()) {
                            val root = DocumentFile.fromTreeUri(context, Uri.parse(rule.localUri))
                            report.toDeleteLocal.forEach { item ->
                                // 按相对路径定位后删除（对目录是递归删）
                                locateLocal(root, item.relativePath)?.delete()
                            }
                            // 清理因删除而变空的祖先目录（自底向上，不删根）
                            report.toDeleteLocal
                                .mapNotNull { parentRelative(it.relativePath) }
                                .distinct()
                                .forEach { pruneEmptyLocalDirs(root, it) }
                        }
                    }
                    com.openlistmobile.app.data.local.SyncMode.UPLOAD_ONLY -> {
                        if (report.toDeleteRemote.isNotEmpty()) {
                            // 按父目录分组批量删（AList 删目录即递归）
                            report.toDeleteRemote
                                .groupBy { remoteParentPath(rule.remotePath, it.relativePath) }
                                .forEach { (parent, items) ->
                                    fileRepository.remove(parent, items.map { it.name }).getOrThrow()
                                }
                            // 清理因删除而变空的云端祖先目录（自底向上，不删根）
                            report.toDeleteRemote
                                .mapNotNull { parentRelative(it.relativePath) }
                                .distinct()
                                .forEach { pruneEmptyRemoteDirs(rule.remotePath, it) }
                        }
                    }
                    else -> Unit // 双向模式不做镜像删除
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** 相对路径的父相对路径；根层（无父）返回 null。 */
    private fun parentRelative(relativePath: String): String? {
        val segs = relativePath.split("/").filter { it.isNotEmpty() }
        if (segs.size <= 1) return null
        return segs.dropLast(1).joinToString("/")
    }

    /** 从指定本地相对目录自底向上删空目录，直到非空或到根。 */
    private fun pruneEmptyLocalDirs(root: DocumentFile?, startRelativeDir: String) {
        if (root == null) return
        var rel: String? = startRelativeDir
        while (rel != null) {
            val dir = locateLocal(root, rel) ?: break
            if (dir.isDirectory && dir.listFiles().isEmpty()) {
                dir.delete()
                rel = parentRelative(rel)
            } else {
                break
            }
        }
    }

    /** 从指定云端相对目录自底向上删空目录，直到非空或到根。 */
    private suspend fun pruneEmptyRemoteDirs(root: String, startRelativeDir: String) {
        val base = root.trimEnd('/')
        var rel: String? = startRelativeDir
        while (rel != null) {
            val full = "$base/$rel"
            val empty = try {
                listRemote(full).isEmpty()
            } catch (e: Exception) {
                false // 列举失败（可能目录已不存在）则停止
            }
            if (empty) {
                val parent = "$base/${parentRelative(rel) ?: ""}".trimEnd('/')
                val name = rel.substringAfterLast('/')
                fileRepository.remove(parent, listOf(name))
                rel = parentRelative(rel)
            } else {
                break
            }
        }
    }

    /** 按相对路径在本地树逐层定位到目标文件/目录。 */
    private fun locateLocal(root: DocumentFile?, relativePath: String): DocumentFile? {
        if (root == null) return null
        var cur: DocumentFile? = root
        relativePath.split("/").filter { it.isNotEmpty() }.forEach { seg ->
            cur = cur?.findFile(seg) ?: return null
        }
        return cur
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

    /** 递归扫描云端目录树，拉平为带相对路径的文件节点（仅文件）。 */
    private suspend fun scanRemoteTree(rootPath: String): List<RemoteNode> {
        val result = mutableListOf<RemoteNode>()
        val base = rootPath.trimEnd('/')
        suspend fun walk(curPath: String, relPrefix: String) {
            val files = listRemote(curPath)
            for (f in files) {
                val rel = if (relPrefix.isEmpty()) f.name else "$relPrefix/${f.name}"
                if (f.is_dir) {
                    walk("$curPath/${f.name}", rel)
                } else {
                    result += RemoteNode(
                        relativePath = rel,
                        name = f.name,
                        size = f.size,
                        modified = f.modified,
                        sign = f.sign
                    )
                }
            }
        }
        walk(base, "")
        return result
    }

    private suspend fun listRemote(path: String): List<AListFile> {
        val result = fileRepository.getFileListFlow(path, page = 1, perPage = 0, refresh = true)
            .toList()
            .lastOrNull()
            ?: throw IllegalStateException("无活动服务器或未登录")
        return result.getOrElse { throw it }.content ?: emptyList()
    }

    /** 递归扫描本地 SAF 目录树，拉平为带相对路径的文件节点（仅文件）。 */
    private fun scanLocalTree(rootUri: String): List<LocalNode> {
        val root = DocumentFile.fromTreeUri(context, Uri.parse(rootUri)) ?: return emptyList()
        val result = mutableListOf<LocalNode>()
        fun walk(dir: DocumentFile, relPrefix: String) {
            for (doc in dir.listFiles()) {
                val name = doc.name ?: continue
                val rel = if (relPrefix.isEmpty()) name else "$relPrefix/$name"
                if (doc.isDirectory) {
                    walk(doc, rel)
                } else {
                    result += LocalNode(
                        relativePath = rel,
                        name = name,
                        uri = doc.uri.toString(),
                        size = doc.length(),
                        lastModified = doc.lastModified()
                    )
                }
            }
        }
        walk(root, "")
        return result
    }
}
