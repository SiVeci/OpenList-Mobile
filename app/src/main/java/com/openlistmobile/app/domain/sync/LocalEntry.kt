package com.openlistmobile.app.domain.sync

/** 本地 SAF 文件树中的一个条目（单层）。uri 为 content:// 字符串。 */
data class LocalEntry(
    val name: String,
    val uri: String,
    val size: Long,
    val lastModified: Long,
    val isDir: Boolean
)
