package com.openlistmobile.app.domain.sync

/**
 * 单个同步动作项。承载入队 TransferTask 所需的全部信息：
 * - 下载：需要 remoteFullPath + remoteSign（拼直链）、size。
 * - 上传：需要 localUri（content:// 源）、name、size。
 * - 删除：本地用 localUri，云端用 remoteFullPath。
 */
data class SyncItem(
    val name: String,
    val size: Long,
    val relativePath: String = "",
    val remoteFullPath: String? = null,
    val remoteSign: String? = null,
    val localUri: String? = null,
    val remoteModified: String? = null,
    val localModified: Long? = null,
    val reason: String = ""
)

/** 内存差异比对结果。删除类本期仅用于展示，不执行。 */
data class DiffReport(
    val toDownload: List<SyncItem> = emptyList(),
    val toUpload: List<SyncItem> = emptyList(),
    val toDeleteLocal: List<SyncItem> = emptyList(),
    val toDeleteRemote: List<SyncItem> = emptyList(),
    val skipped: List<SyncItem> = emptyList()
) {
    val transferCount: Int get() = toDownload.size + toUpload.size
    val totalActionCount: Int get() = transferCount + toDeleteLocal.size + toDeleteRemote.size

    /** 是否有可入队的传输任务（删除本期不入队，故只看下载/上传）。 */
    val hasTransfers: Boolean get() = transferCount > 0
}
