package com.flechazo.toolbox.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.flechazo.toolbox.core.data.LegacyImporter
import com.flechazo.toolbox.core.data.SettingsRepository
import com.flechazo.toolbox.core.data.ThemeMode
import com.flechazo.toolbox.core.designsystem.theme.ToolShape
import com.flechazo.toolbox.core.designsystem.theme.horizontalSafePadding
import com.flechazo.toolbox.core.designsystem.theme.statusBarTopInset
import com.flechazo.toolbox.core.update.UpdateRepository
import com.flechazo.toolbox.core.update.UpdateUiState
import com.flechazo.toolbox.feature.countdown.CountdownBackup
import com.flechazo.toolbox.feature.countdown.CountdownRepository
import com.google.gson.Gson
import com.flechazo.toolbox.core.designsystem.components.ToolTextField
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val legacyImporter: LegacyImporter,
    private val countdownRepository: CountdownRepository,
    private val updateRepository: UpdateRepository,
    private val gson: Gson,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(
        settings.themeMode,
        settings.dynamicColor,
    ) { mode, dynamic ->
        SettingsUiState(mode, dynamic)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    /** 与启动自动检查共用同一份状态（UpdateRepository 是单例）。 */
    val updateState: StateFlow<UpdateUiState> = updateRepository.state

    fun checkForUpdate() {
        viewModelScope.launch { updateRepository.check(isManual = true) }
    }

    fun clearUpdateResult() = updateRepository.clearResult()

    fun dismissUpdate() = updateRepository.dismiss()

    private val _importMessage = MutableStateFlow<String?>(null)
    val importMessage: StateFlow<String?> = _importMessage

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }
    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { settings.setDynamicColor(enabled) }

    /** Parse the legacy backup file; returns true if passwords need a master password step. */
    fun importBackup(json: String, onNeedMaster: () -> Unit) {
        viewModelScope.launch {
            val result = runCatching { legacyImporter.import(json) }.fold(
                onSuccess = { it },
                onFailure = { null },
            )
            if (result == null) {
                _importMessage.value = "导入失败：文件不是有效的旧版备份"
            } else {
                val msg = buildString {
                    append("倒数日：导入 ${result.countdownsImported} 条")
                    if (result.lunarImported > 0) append("（含农历 ${result.lunarImported} 条）")
                    if (result.countdownsSkipped > 0) append("，跳过重复 ${result.countdownsSkipped} 条")
                    if (result.invalid > 0) append("， ${result.invalid} 条日期无效已忽略")
                }
                lastSummary = msg
                _importMessage.value = msg
                if (result.passwordsPending > 0) onNeedMaster()
            }
        }
    }

    private var lastSummary: String? = null

    fun importPasswordsWithMaster(master: String, json: String) {
        viewModelScope.launch {
            val n = runCatching { legacyImporter.importPasswords(json, master.toCharArray()) }
                .getOrDefault(-1)
            val tail = when {
                n < 0 -> "密码导入失败：主密码错误或数据损坏"
                n == 0 -> "密码全部已存在，无需导入"
                else -> "密码：成功导入 $n 条"
            }
            _importMessage.value = listOfNotNull(lastSummary, tail).joinToString("；")
        }
    }

    fun reportImportError(msg: String) { _importMessage.value = msg }

    fun clearMessage() { _importMessage.value = null }

    /**
     * 导出倒数日到所选文件。
     *
     * 载荷是旧版 `toolbox_backup.json` 的**超集**（见 [CountdownBackup]）：
     * 既带 v1 的 `targetDate` / `type`，也带 v2 的农历、重复、提醒、颜色字段，
     * 所以旧版 App 能读、我们能原样读回，导出的文件也可以直接再导入回来。
     */
    fun exportBackup(context: android.content.Context, uri: android.net.Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val events = countdownRepository.observeAll().first()
                    val payload = linkedMapOf<String, Any>(
                        "schemaVersion" to CountdownBackup.SCHEMA_VERSION,
                        "countdowns" to events.map { CountdownBackup.toEvent(it) },
                        "passwords" to emptyList<Map<String, String>>(),
                    )
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(gson.toJson(payload).toByteArray(Charsets.UTF_8))
                    } ?: error("无法写入所选文件")
                    events.size
                }
            }
            _importMessage.value = result.fold(
                onSuccess = { "已导出 $it 条倒数日" },
                onFailure = { "导出失败：${it.message ?: "未知错误"}" },
            )
        }
    }
}

@Composable
fun SettingsScreen(
    bottomBarPadding: Dp = 0.dp,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val importMessage by viewModel.importMessage.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    var showMasterDialog by remember { mutableStateOf(false) }
    var pendingJson by remember { mutableStateOf<String?>(null) }

    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri?.let { viewModel.exportBackup(context, it) }
    }

    val pickBackup = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val json = kotlin.runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull()
        if (json.isNullOrBlank()) {
            viewModel.reportImportError("无法读取所选文件")
        } else {
            viewModel.importBackup(json) {
                pendingJson = json
                showMasterDialog = true
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // 左右让开刘海；纵向留白写进滚动内容里，内容才能从状态栏/玻璃栏下滚过
            .horizontalSafePadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            "我的",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(
                start = 16.dp,
                top = statusBarTopInset() + 20.dp,
                bottom = 8.dp,
            ),
        )

        SectionCard {
            SettingRow(
                title = "跟随系统",
                selected = state.themeMode == ThemeMode.SYSTEM,
                onClick = { viewModel.setThemeMode(ThemeMode.SYSTEM) },
            )
            SettingRow(
                title = "浅色模式",
                selected = state.themeMode == ThemeMode.LIGHT,
                onClick = { viewModel.setThemeMode(ThemeMode.LIGHT) },
            )
            SettingRow(
                title = "深色模式",
                selected = state.themeMode == ThemeMode.DARK,
                onClick = { viewModel.setThemeMode(ThemeMode.DARK) },
            )
        }

        SectionCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.setDynamicColor(!state.dynamicColor) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("动态取色", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "跟随系统壁纸配色（Android 12+）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(checked = state.dynamicColor, onCheckedChange = { viewModel.setDynamicColor(it) })
            }
        }

        SectionCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { pickBackup.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("导入旧版数据", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "选择旧版 Toolbox 导出的 toolbox_backup.json",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        importMessage?.let { msg ->
            Text(
                msg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }

        SectionCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { exportLauncher.launch("toolbox_backup.json") }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("导出数据", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "导出倒数日为 JSON，可直接再次导入",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        SectionCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = updateState !is UpdateUiState.Checking) {
                        viewModel.clearUpdateResult()
                        viewModel.checkForUpdate()
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("检查更新", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        when (val s = updateState) {
                            is UpdateUiState.Checking -> "正在检查…"
                            is UpdateUiState.UpToDate -> "已是最新版本 v${s.currentVersion}"
                            is UpdateUiState.Failed -> s.message
                            is UpdateUiState.Available -> "发现新版本 v${s.info.latestVersion}"
                            else -> "当前版本 v${com.flechazo.toolbox.BuildConfig.VERSION_NAME}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (updateState is UpdateUiState.Failed) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                if (updateState is UpdateUiState.Checking) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp).size(20.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
        }

        SectionCard {
            Column(Modifier.padding(16.dp)) {
                Text("关于", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "工具箱 v${com.flechazo.toolbox.BuildConfig.VERSION_NAME} · 彻底重写版\n" +
                        "本地优先：除汇率外全部离线可用，无广告、无追踪。\n" +
                        "密码箱数据使用 PBKDF2 + AES-256-GCM 加密，主密码不上传、不保存。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        // 底部让开玻璃底栏 + 系统导航条，否则「关于」卡会被压在栏下
        Spacer(Modifier.height(bottomBarPadding + 24.dp))
    }

    if (showMasterDialog) {
        var master by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showMasterDialog = false },
            title = { Text("导入密码数据") },
            text = {
                Column {
                    Text(
                        "备份文件包含密码条目。输入密码箱主密码：已有密码箱将合并导入，尚未创建则用此密码创建。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    ToolTextField(
                        value = master,
                        onValueChange = { master = it },
                        label = { Text("主密码（至少 6 位）") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        viewModel.importPasswordsWithMaster(master, pendingJson.orEmpty())
                        showMasterDialog = false
                    },
                    enabled = master.length >= 6,
                ) { Text("导入") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showMasterDialog = false }) { Text("跳过") }
            },
        )
    }
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    androidx.compose.material3.Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = ToolShape.lg,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column { content() }
    }
}

@Composable
private fun SettingRow(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Text("✓", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}
