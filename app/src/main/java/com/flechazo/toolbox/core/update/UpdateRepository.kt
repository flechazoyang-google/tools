package com.flechazo.toolbox.core.update

import com.flechazo.toolbox.BuildConfig
import com.flechazo.toolbox.core.data.SettingsRepository
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 「检查更新」的唯一数据源：GitHub Releases。
 *
 * 单例，因此「我的」页与启动自动检查共享同一份状态。
 * 仅依赖 GitHub，不涉及任何 CDN / gitee。
 */
@Singleton
class UpdateRepository @Inject constructor(
    private val settings: SettingsRepository,
    private val gson: Gson,
) {

    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /** 启动时调用：距上次检查不足 24 小时则跳过。 */
    suspend fun autoCheck() {
        val last = settings.lastUpdateCheckMs.first()
        if (System.currentTimeMillis() - last < CHECK_INTERVAL_MS) return
        check(isManual = false)
    }

    /** 手动调用：忽略限频，失败时给出可读提示。 */
    suspend fun check(isManual: Boolean) {
        if (_state.value is UpdateUiState.Checking) return
        _state.value = UpdateUiState.Checking
        val result = runCatching { fetchLatest() }
        settings.setLastUpdateCheckMs(System.currentTimeMillis())
        _state.value = result.fold(
            onSuccess = { info ->
                if (info == null) UpdateUiState.UpToDate(BuildConfig.VERSION_NAME)
                else UpdateUiState.Available(info)
            },
            onFailure = { error ->
                if (isManual) UpdateUiState.Failed(friendlyMessage(error)) else UpdateUiState.Idle
            },
        )
    }

    /** 关闭弹窗后回到 Idle，避免每次重组都重新弹出。 */
    fun dismiss() {
        if (_state.value is UpdateUiState.Available) _state.value = UpdateUiState.Idle
    }

    /** 手动检查后的结果提示消费掉。 */
    fun clearResult() {
        when (_state.value) {
            is UpdateUiState.UpToDate, is UpdateUiState.Failed -> _state.value = UpdateUiState.Idle
            else -> Unit
        }
    }

    /** 返回 null 表示「已是最新」或「仓库暂无 Release」。 */
    private suspend fun fetchLatest(): UpdateInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
            // 缺少 User-Agent 会被 GitHub 直接 403
            .header("User-Agent", "Toolbox-Android")
            .header("Accept", "application/vnd.github+json")
            .build()

        client.newCall(request).execute().use { response ->
            // 还没有发布任何 Release：视为「暂无更新」，不是错误
            if (response.code == 404) return@withContext null
            if (!response.isSuccessful) error("HTTP ${response.code}")

            val payload = response.body?.string().orEmpty()
            if (payload.isBlank()) error("空响应")
            val release = gson.fromJson(payload, GitHubRelease::class.java)
                ?: error("解析失败")

            val current = BuildConfig.VERSION_NAME
            if (!isNewer(current, release.tagName)) return@withContext null

            val apk = release.assets.firstOrNull {
                it.name.endsWith(".apk", ignoreCase = true) && it.downloadUrl.isNotBlank()
            }
            UpdateInfo(
                latestVersion = release.tagName.trim().removePrefix("v").removePrefix("V"),
                currentVersion = current,
                notes = release.body.trim(),
                releaseUrl = release.htmlUrl,
                apkUrl = apk?.downloadUrl,
            )
        }
    }

    private fun friendlyMessage(error: Throwable): String = when {
        error is UnknownHostException || error is SocketTimeoutException ->
            "网络不可用，请检查网络后重试"
        error.message?.contains("403") == true || error.message?.contains("429") == true ->
            "请求过于频繁，请稍后再试"
        else -> "检查更新失败，请稍后重试"
    }

    private companion object {
        /** 启动自动检查的最小间隔：24 小时。 */
        const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000
    }
}
