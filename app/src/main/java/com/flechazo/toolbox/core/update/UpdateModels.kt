package com.flechazo.toolbox.core.update

import com.google.gson.annotations.SerializedName

/**
 * GitHub `releases/latest` 响应中我们关心的字段。
 *
 * 字段名用 [SerializedName] 标注，因此 R8 混淆字段名也不影响 Gson 解析
 * （proguard-rules.pro 已保留带该注解的字段）。
 */
internal data class GitHubRelease(
    @SerializedName("tag_name") val tagName: String = "",
    @SerializedName("body") val body: String = "",
    @SerializedName("html_url") val htmlUrl: String = "",
    @SerializedName("assets") val assets: List<GitHubAsset> = emptyList(),
    @SerializedName("prerelease") val prerelease: Boolean = false,
    @SerializedName("draft") val draft: Boolean = false,
)

internal data class GitHubAsset(
    @SerializedName("name") val name: String = "",
    @SerializedName("browser_download_url") val downloadUrl: String = "",
)

/** 有新版本时展示给用户的信息。 */
data class UpdateInfo(
    /** 规范化后的最新版本号，如 `1.1.2` */
    val latestVersion: String,
    val currentVersion: String,
    val notes: String,
    val releaseUrl: String,
    /** Release 里的 APK 资产直链；没有资产时为 null */
    val apkUrl: String?,
)

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class Available(val info: UpdateInfo) : UpdateUiState
    data class UpToDate(val currentVersion: String) : UpdateUiState
    data class Failed(val message: String) : UpdateUiState
}

/**
 * `latest` 是否比 `current` 新。
 *
 * 按数值逐段比较（`1.1.10` > `1.1.9`，而非字典序），前导 `v`/`V` 忽略，
 * `-beta.1` 之类的后缀不参与比较；任一侧无法解析为数字版本时返回 false，
 * 避免脏 tag 触发误报。
 */
internal fun isNewer(current: String, latest: String): Boolean {
    val c = parseVersion(current) ?: return false
    val l = parseVersion(latest) ?: return false
    val size = maxOf(c.size, l.size)
    for (i in 0 until size) {
        val a = c.getOrElse(i) { 0 }
        val b = l.getOrElse(i) { 0 }
        if (a != b) return b > a
    }
    return false
}

/** 解析 `v1.1.2` / `1.1.2-beta.1` → `[1, 1, 2]`；无法解析返回 null。 */
internal fun parseVersion(raw: String): List<Int>? {
    val core = raw.trim()
        .removePrefix("v")
        .removePrefix("V")
        .substringBefore('-')
        .substringBefore('+')
    if (core.isEmpty()) return null
    val parts = core.split('.')
    val numbers = ArrayList<Int>(parts.size)
    for (part in parts) {
        val n = part.toIntOrNull() ?: return null
        numbers.add(n)
    }
    return numbers.ifEmpty { null }
}
