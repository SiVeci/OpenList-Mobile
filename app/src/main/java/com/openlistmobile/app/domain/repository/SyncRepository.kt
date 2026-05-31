package com.openlistmobile.app.domain.repository

import com.openlistmobile.app.data.local.SyncRule
import com.openlistmobile.app.data.local.SyncStatus
import com.openlistmobile.app.domain.sync.DiffReport
import com.openlistmobile.app.domain.sync.LocalNode
import com.openlistmobile.app.domain.sync.RemoteNode
import kotlinx.coroutines.flow.Flow

/** 选中规则两端递归扁平文件内容（含相对路径），供双栏 UI 展示。 */
data class SideContents(
    val cloud: List<RemoteNode>,
    val local: List<LocalNode>
)

interface SyncRepository {
    val rules: Flow<List<SyncRule>>

    suspend fun getRule(id: Long): SyncRule?
    suspend fun createRule(rule: SyncRule): Result<Long>
    suspend fun updateRule(rule: SyncRule): Result<Unit>
    suspend fun deleteRule(id: Long)
    suspend fun updateRuleStatus(id: Long, status: SyncStatus, errorMsg: String? = null)

    /** 抓取两端单层目录内容（供双栏展示）。 */
    suspend fun loadBothSides(rule: SyncRule): Result<SideContents>

    /** 抓取两端单层目录并计算差异。 */
    suspend fun computeDiff(rule: SyncRule): Result<DiffReport>

    /** 将 DiffReport 的下载/上传项降解为 TransferTask 批量入队，返回新任务 id 列表。 */
    suspend fun enqueueDiff(rule: SyncRule, report: DiffReport): List<Long>

    /**
     * 执行镜像删除（真实、不可逆）。仅单向模式生效：
     * 下载模式删 toDeleteLocal（本地多余）；上传模式删 toDeleteRemote（云端多余）。
     * 双向模式不删。
     */
    suspend fun executeMirrorDeletes(rule: SyncRule, report: DiffReport): Result<Unit>

    /** 唤醒现有 TransferService 抽干队列。 */
    fun startTransferEngine()
}
