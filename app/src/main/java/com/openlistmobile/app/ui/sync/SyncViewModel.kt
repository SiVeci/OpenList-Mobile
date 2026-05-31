package com.openlistmobile.app.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openlistmobile.app.data.local.SyncMode
import com.openlistmobile.app.data.local.SyncRule
import com.openlistmobile.app.data.local.SyncStatus
import com.openlistmobile.app.data.local.TransferStatus
import com.openlistmobile.app.data.remote.model.AListFile
import com.openlistmobile.app.domain.repository.SyncRepository
import com.openlistmobile.app.domain.repository.TransferRepository
import com.openlistmobile.app.domain.sync.DiffReport
import com.openlistmobile.app.domain.sync.LocalEntry
import com.openlistmobile.app.utils.SettingsManager
import com.openlistmobile.app.utils.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 双栏文件项的差异角标。 */
enum class SyncBadge { CLOUD_NEW, LOCAL_NEW, OVERWRITE, DELETE, SKIP, SAME, NONE }

data class SyncUiState(
    val rules: List<SyncRule> = emptyList(),
    val selectedRule: SyncRule? = null,

    val cloudFiles: List<AListFile> = emptyList(),
    val localEntries: List<LocalEntry> = emptyList(),
    val cloudBadges: Map<String, SyncBadge> = emptyMap(),
    val localBadges: Map<String, SyncBadge> = emptyMap(),

    val lastDiff: DiffReport? = null,
    val pendingConfirm: DiffReport? = null, // 非空时显示确认弹窗
    val previewReady: Boolean = false,      // true=已预览，按钮显“执行同步”

    val isSplitVertical: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.IDLE,
    val overallProgress: Float = 0f,
    val remainingCount: Int = 0,

    val isLoadingSides: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val syncRepository: SyncRepository,
    private val transferRepository: TransferRepository,
    private val settingsManager: SettingsManager,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SyncUiState(isSplitVertical = settingsManager.isSyncSplitVertical))
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    // 本次同步入队的任务 id（内存追踪，用于进度统计）
    private var enqueuedIds: Set<Long> = emptySet()

    init {
        viewModelScope.launch {
            syncRepository.rules.collect { rules ->
                _uiState.update { state ->
                    // rules 变化时，保持当前选中；若未选或被删则自动选第一个
                    val selected = state.selectedRule?.let { cur -> rules.find { it.id == cur.id } }
                        ?: rules.firstOrNull()
                    state.copy(rules = rules, selectedRule = selected)
                }
                // 首次或选中项变化时加载两端内容
                _uiState.value.selectedRule?.let { loadSides(it) }
            }
        }
        observeProgress()
    }

    private fun observeProgress() {
        viewModelScope.launch {
            transferRepository.getAllTasks().collect { tasks ->
                val mine = tasks.filter { it.id in enqueuedIds }
                if (mine.isEmpty()) {
                    _uiState.update { it.copy(overallProgress = 0f, remainingCount = 0) }
                    return@collect
                }
                val done = mine.count { it.status == TransferStatus.SUCCESS }
                val remaining = mine.count { it.status != TransferStatus.SUCCESS && it.status != TransferStatus.ERROR }
                val progress = done.toFloat() / mine.size.toFloat()
                val allFinished = mine.all { it.status == TransferStatus.SUCCESS || it.status == TransferStatus.ERROR }
                _uiState.update {
                    it.copy(
                        overallProgress = progress,
                        remainingCount = remaining,
                        syncStatus = if (allFinished) SyncStatus.IDLE else it.syncStatus
                    )
                }
            }
        }
    }

    fun selectRule(rule: SyncRule) {
        if (_uiState.value.selectedRule?.id == rule.id) return
        _uiState.update { it.copy(selectedRule = rule, cloudBadges = emptyMap(), localBadges = emptyMap(), lastDiff = null, previewReady = false) }
        loadSides(rule)
    }

    private fun loadSides(rule: SyncRule) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSides = true, message = null) }
            val result = syncRepository.loadBothSides(rule)
            _uiState.update { state ->
                result.fold(
                    onSuccess = { sides ->
                        state.copy(
                            isLoadingSides = false,
                            cloudFiles = sides.cloud,
                            localEntries = sides.local
                        )
                    },
                    onFailure = { e ->
                        state.copy(isLoadingSides = false, message = "加载目录失败: ${e.message}")
                    }
                )
            }
        }
    }

    /** 第一步：算 Diff、渲染双栏角标。不弹窗、不执行。 */
    fun previewSync() {
        val rule = _uiState.value.selectedRule ?: run {
            _uiState.update { it.copy(message = "请先选择一个同步规则") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(syncStatus = SyncStatus.DIFFING, message = null) }
            val result = syncRepository.computeDiff(rule)
            result.fold(
                onSuccess = { diff ->
                    val (cloudBadges, localBadges) = buildBadges(diff)
                    _uiState.update {
                        it.copy(
                            syncStatus = SyncStatus.IDLE,
                            lastDiff = diff,
                            cloudBadges = cloudBadges,
                            localBadges = localBadges,
                            previewReady = true
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(syncStatus = SyncStatus.ERROR, message = "Diff 失败: ${e.message}") }
                }
            )
        }
    }

    /** 第二步：基于已预览的 Diff 执行。无删除/覆盖直接执行；有则弹确认窗。 */
    fun executeSync() {
        val diff = _uiState.value.lastDiff ?: return
        if (hasDeletes(diff) || hasOverwrites(diff)) {
            _uiState.update { it.copy(pendingConfirm = diff) }
        } else {
            runSync(diff, deleteApproved = false)
        }
    }

    /** 用户在确认弹窗点确认。deleteApproved=勾选了删除确认。覆盖勾选只在 UI 层把关，到这里即放行。 */
    fun confirmSync(deleteApproved: Boolean) {
        val diff = _uiState.value.pendingConfirm ?: return
        _uiState.update { it.copy(pendingConfirm = null) }
        runSync(diff, deleteApproved)
    }

    private fun runSync(diff: DiffReport, deleteApproved: Boolean) {
        val rule = _uiState.value.selectedRule ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(syncStatus = SyncStatus.SYNCING) }

            // 1) 镜像删除（仅单向 + 用户勾选确认）
            val hasDeletes = hasDeletes(diff)
            if (deleteApproved && hasDeletes) {
                syncRepository.executeMirrorDeletes(rule, diff).onFailure { e ->
                    _uiState.update { it.copy(message = "镜像删除部分失败: ${e.message}") }
                }
            }

            // 2) 入队下载/上传 + 唤醒引擎
            if (diff.hasTransfers) {
                val ids = syncRepository.enqueueDiff(rule, diff)
                enqueuedIds = enqueuedIds + ids
                syncRepository.startTransferEngine()
                _uiState.update { it.copy(message = "已入队 ${ids.size} 个传输任务") }
            } else {
                _uiState.update { it.copy(syncStatus = SyncStatus.IDLE) }
                if (!hasDeletes) _uiState.update { it.copy(message = "两端已一致，无需传输") }
            }

            syncRepository.updateRuleStatus(rule.id, SyncStatus.IDLE)
            // 执行后复位预览态并刷新两端内容
            _uiState.update { it.copy(previewReady = false, cloudBadges = emptyMap(), localBadges = emptyMap(), lastDiff = null) }
            loadSides(rule)
        }
    }

    private fun hasDeletes(diff: DiffReport): Boolean =
        diff.toDeleteLocal.isNotEmpty() || diff.toDeleteRemote.isNotEmpty()

    private fun hasOverwrites(diff: DiffReport): Boolean =
        (diff.toDownload + diff.toUpload).any { it.reason.contains("覆盖") || it.reason.contains("冲突") }

    fun cancelConfirm() {
        _uiState.update { it.copy(pendingConfirm = null, syncStatus = SyncStatus.IDLE) }
    }

    fun toggleSplitOrientation() {
        val newVal = !_uiState.value.isSplitVertical
        settingsManager.isSyncSplitVertical = newVal
        _uiState.update { it.copy(isSplitVertical = newVal) }
    }

    fun createRule(rule: SyncRule, onResult: (Result<Long>) -> Unit) {
        viewModelScope.launch {
            val withProfile = rule.copy(profileId = tokenManager.currentProfileId)
            val result = syncRepository.createRule(withProfile)
            onResult(result)
        }
    }

    fun updateRule(rule: SyncRule, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = syncRepository.updateRule(rule)
            // 若编辑的是当前选中规则，更新选中态以即时反映行为变化
            if (result.isSuccess && _uiState.value.selectedRule?.id == rule.id) {
                _uiState.update { it.copy(selectedRule = rule) }
            }
            onResult(result)
        }
    }

    fun deleteRule(rule: SyncRule) {
        viewModelScope.launch {
            syncRepository.deleteRule(rule.id)
        }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }

    private fun buildBadges(diff: DiffReport): Pair<Map<String, SyncBadge>, Map<String, SyncBadge>> {
        val cloud = mutableMapOf<String, SyncBadge>()
        val local = mutableMapOf<String, SyncBadge>()
        // 下载：云端有→将拉到本地。云端栏标“云端新增/覆盖”，本地栏将出现
        diff.toDownload.forEach {
            cloud[it.name] = if (it.reason.contains("覆盖") || it.reason.contains("冲突")) SyncBadge.OVERWRITE else SyncBadge.CLOUD_NEW
        }
        diff.toUpload.forEach {
            local[it.name] = if (it.reason.contains("覆盖") || it.reason.contains("冲突")) SyncBadge.OVERWRITE else SyncBadge.LOCAL_NEW
        }
        diff.toDeleteLocal.forEach { local[it.name] = SyncBadge.DELETE }
        diff.toDeleteRemote.forEach { cloud[it.name] = SyncBadge.DELETE }
        diff.skipped.forEach {
            if (!cloud.containsKey(it.name)) cloud[it.name] = SyncBadge.SKIP
            if (!local.containsKey(it.name)) local[it.name] = SyncBadge.SKIP
        }
        return cloud to local
    }
}
