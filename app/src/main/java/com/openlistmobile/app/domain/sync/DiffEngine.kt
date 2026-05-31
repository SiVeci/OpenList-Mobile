package com.openlistmobile.app.domain.sync

import com.openlistmobile.app.data.local.ConflictStrategy
import com.openlistmobile.app.data.local.SyncMode
import com.openlistmobile.app.data.local.SyncRule
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.math.abs

/**
 * 纯函数差异计算器：对比云端与本地的扁平文件节点列表（已递归展开，含相对路径），按规则生成 DiffReport。
 * 不做任何 I/O，便于单元测试。目录在扫描阶段已展开为各自的文件节点，这里只比对文件。
 */
object DiffEngine {

    private const val TIME_TOLERANCE_MS = 2000L

    fun computeDiff(rule: SyncRule, remote: List<RemoteNode>, local: List<LocalNode>): DiffReport {
        val toDownload = mutableListOf<SyncItem>()
        val toUpload = mutableListOf<SyncItem>()
        val toDeleteLocal = mutableListOf<SyncItem>()
        val toDeleteRemote = mutableListOf<SyncItem>()
        val skipped = mutableListOf<SyncItem>()

        val remoteByPath = remote.associateBy { it.relativePath }
        val localByPath = local.associateBy { it.relativePath }

        for (path in (remoteByPath.keys + localByPath.keys)) {
            val r = remoteByPath[path]
            val l = localByPath[path]
            when {
                r != null && l == null -> {
                    if (allowsDownload(rule.syncMode)) {
                        toDownload += downloadItem(rule, r, "云端新增")
                    } else if (rule.syncMode == SyncMode.UPLOAD_ONLY && rule.isMirrorDeleteEnabled) {
                        toDeleteRemote += SyncItem(r.name, r.size, relativePath = r.relativePath, remoteFullPath = joinRemote(rule.remotePath, r.relativePath), remoteSign = r.sign, reason = "镜像删除：云端多余")
                    }
                }
                r == null && l != null -> {
                    if (allowsUpload(rule.syncMode)) {
                        toUpload += uploadItem(l, "本地新增")
                    } else if (rule.syncMode == SyncMode.DOWNLOAD_ONLY && rule.isMirrorDeleteEnabled) {
                        toDeleteLocal += SyncItem(l.name, l.size, relativePath = l.relativePath, localUri = l.uri, reason = "镜像删除：本地多余")
                    }
                }
                r != null && l != null -> {
                    if (isSame(r, l, rule.ignoreModifiedTime)) {
                        skipped += SyncItem(r.name, r.size, relativePath = path, remoteFullPath = joinRemote(rule.remotePath, path), localUri = l.uri, reason = "一致，跳过")
                    } else {
                        resolveConflict(rule, r, l, toDownload, toUpload, skipped)
                    }
                }
            }
        }

        return DiffReport(toDownload, toUpload, toDeleteLocal, toDeleteRemote, skipped)
    }

    private fun allowsDownload(mode: SyncMode): Boolean =
        mode == SyncMode.DOWNLOAD_ONLY || mode == SyncMode.TWO_WAY

    private fun allowsUpload(mode: SyncMode): Boolean =
        mode == SyncMode.UPLOAD_ONLY || mode == SyncMode.TWO_WAY

    private fun isSame(r: RemoteNode, l: LocalNode, ignoreTime: Boolean): Boolean {
        if (r.size != l.size) return false
        if (ignoreTime) return true
        val rt = parseIso(r.modified) ?: return true // 时间无法解析时仅以大小判定
        return abs(rt - l.lastModified) <= TIME_TOLERANCE_MS
    }

    private fun resolveConflict(
        rule: SyncRule,
        r: RemoteNode,
        l: LocalNode,
        toDownload: MutableList<SyncItem>,
        toUpload: MutableList<SyncItem>,
        skipped: MutableList<SyncItem>
    ) {
        when (rule.syncMode) {
            SyncMode.DOWNLOAD_ONLY -> toDownload += downloadItem(rule, r, "云端覆盖本地")
            SyncMode.UPLOAD_ONLY -> toUpload += uploadItem(l, "本地覆盖云端")
            SyncMode.TWO_WAY -> when (rule.conflictStrategy) {
                ConflictStrategy.CLOUD_WINS -> toDownload += downloadItem(rule, r, "冲突：以云端为准")
                ConflictStrategy.LOCAL_WINS -> toUpload += uploadItem(l, "冲突：以本地为准")
                ConflictStrategy.SKIP -> skipped += SyncItem(r.name, r.size, relativePath = r.relativePath, remoteFullPath = joinRemote(rule.remotePath, r.relativePath), localUri = l.uri, reason = "冲突：跳过")
                ConflictStrategy.NEWEST_WINS -> {
                    val rt = parseIso(r.modified)
                    val lt = l.lastModified
                    when {
                        rt == null -> skipped += SyncItem(r.name, r.size, relativePath = r.relativePath, localUri = l.uri, reason = "冲突：云端时间无法解析，跳过")
                        rt - lt > TIME_TOLERANCE_MS -> toDownload += downloadItem(rule, r, "冲突：云端较新")
                        lt - rt > TIME_TOLERANCE_MS -> toUpload += uploadItem(l, "冲突：本地较新")
                        else -> skipped += SyncItem(r.name, r.size, relativePath = r.relativePath, localUri = l.uri, reason = "冲突：时间相近，跳过")
                    }
                }
            }
        }
    }

    private fun downloadItem(rule: SyncRule, r: RemoteNode, reason: String): SyncItem = SyncItem(
        name = r.name,
        size = r.size,
        relativePath = r.relativePath,
        remoteFullPath = joinRemote(rule.remotePath, r.relativePath),
        remoteSign = r.sign,
        remoteModified = r.modified,
        reason = reason
    )

    private fun uploadItem(l: LocalNode, reason: String): SyncItem = SyncItem(
        name = l.name,
        size = l.size,
        relativePath = l.relativePath,
        localUri = l.uri,
        localModified = l.lastModified,
        reason = reason
    )

    private fun joinRemote(dir: String, relativePath: String): String {
        val base = dir.trimEnd('/')
        return "$base/$relativePath"
    }

    private fun parseIso(s: String?): Long? {
        if (s.isNullOrBlank()) return null
        return try {
            OffsetDateTime.parse(s).toInstant().toEpochMilli()
        } catch (e: Exception) {
            try {
                Instant.parse(s).toEpochMilli()
            } catch (e2: Exception) {
                null
            }
        }
    }
}
