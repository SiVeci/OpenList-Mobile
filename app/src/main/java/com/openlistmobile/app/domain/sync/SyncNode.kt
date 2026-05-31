package com.openlistmobile.app.domain.sync

/**
 * 递归扫描后的扁平文件节点（仅文件，目录已在扫描阶段展开）。
 * relativePath 相对规则根目录，用 '/' 连接，如 "sub/deep/b.txt"。
 */
data class RemoteNode(
    val relativePath: String,
    val name: String,
    val size: Long,
    val modified: String,
    val sign: String
)

data class LocalNode(
    val relativePath: String,
    val name: String,
    val uri: String,
    val size: Long,
    val lastModified: Long
)
