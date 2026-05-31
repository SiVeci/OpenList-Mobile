package com.openlistmobile.app.utils

import java.net.URLEncoder

/**
 * 统一的 AList 文件直链构造器，下载入队与首页预览复用同一实现，避免 URL 编码不一致。
 * 形如：$baseUrl/d{逐段编码的 path}{?sign=...}
 */
object RemoteLinkBuilder {
    fun build(baseUrl: String, fullPath: String, sign: String?): String {
        val normalizedBase = baseUrl.trimEnd('/')
        val encodedPath = fullPath.split("/").joinToString("/") { seg ->
            if (seg.isEmpty()) "" else URLEncoder.encode(seg, "UTF-8").replace("+", "%20")
        }
        val signQuery = if (!sign.isNullOrBlank()) "?sign=$sign" else ""
        return "$normalizedBase/d$encodedPath$signQuery"
    }
}
