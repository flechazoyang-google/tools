package com.example.toolbox.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toolbox.BuildConfig
import com.example.toolbox.core.theme.ThemeMode
import com.example.toolbox.data.local.datastore.SettingsDataStore
import com.example.toolbox.data.repository.CountdownRepository
import com.example.toolbox.data.repository.PasswordRepository
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

private const val VERSION_URL = "https://raw.githubusercontent.com/flechazoyang-google/tools/main/version.json"

private data class VersionInfo(
    val versionCode: Int = 0,
    val versionName: String = "",
    val downloadUrl: String = "",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsDataStore,
    private val passwordRepo: PasswordRepository,
    private val countdownRepo: CountdownRepository,
    @ApplicationContext private val context: Context,
    private val gson: Gson,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settings.themeMode.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ThemeMode.SYSTEM,
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    /** Export passwords + countdowns to <external-files>/toolbox_backup.json. Returns the path or null. */
    suspend fun exportData(): String? = runCatching {
        val passwords = passwordRepo.exportAll()
        val countdowns = countdownRepo.observeAll().first().map { CountdownExport.fromEntity(it) }
        val payload = BackupPayload(passwords, countdowns)
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(dir, "toolbox_backup.json")
        file.writeText(gson.toJson(payload))
        file.absolutePath
    }.getOrNull()

    /** Import from the backup file. Merges into existing data. */
    suspend fun importData(): Boolean = runCatching {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(dir, "toolbox_backup.json")
        if (!file.exists()) return false
        val payload = gson.fromJson(file.readText(), BackupPayload::class.java) ?: return false
        passwordRepo.importAll(payload.passwords)
        payload.countdowns.forEach { countdownRepo.add(it.toEntity()) }
        true
    }.getOrNull() ?: false

    /** Check for updates. Downloads and installs if a newer version is found. */
    suspend fun checkUpdate(): String = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    chain.proceed(chain.request().newBuilder()
                        .header("User-Agent", "Toolbox/${BuildConfig.VERSION_NAME}")
                        .build())
                }
                .build()
            val request = Request.Builder().url(VERSION_URL).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext "检查失败：无响应"
            val remote = gson.fromJson(body, VersionInfo::class.java)

            if (remote.versionCode > BuildConfig.VERSION_CODE) {
                val apkFile = downloadApk(client, remote.downloadUrl)
                if (apkFile != null) {
                    installApk(apkFile)
                    "正在安装 ${remote.versionName}…"
                } else {
                    "下载失败，请稍后重试"
                }
            } else {
                "已是最新版本 ${BuildConfig.VERSION_NAME}"
            }
        } catch (e: Exception) {
            "检查失败：${e.localizedMessage ?: "网络错误"}"
        }
    }

    private fun downloadApk(client: OkHttpClient, url: String): File? {
        return try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body ?: return null
            val apkFile = File(context.cacheDir, "update.apk")
            FileOutputStream(apkFile).use { output ->
                body.byteStream().use { input -> input.copyTo(output) }
            }
            apkFile
        } catch (_: Exception) {
            null
        }
    }

    private fun installApk(apkFile: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
