package com.flechazo.toolbox.feature.device

import android.app.ActivityManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.flechazo.toolbox.core.designsystem.components.FeedbackBlock
import com.flechazo.toolbox.core.designsystem.components.FeedbackType
import com.flechazo.toolbox.core.designsystem.components.KeyValueRow
import com.flechazo.toolbox.core.designsystem.components.ToolScaffold
import com.flechazo.toolbox.core.designsystem.components.ToolSectionCard
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject

data class DeviceUiState(
    val info: DeviceInfo? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

data class DeviceInfo(
    val device: String,
    val model: String,
    val manufacturer: String,
    val androidVersion: String,
    val sdkVersion: Int,
    val cpuModel: String,
    val cpuCores: Int,
    val cpuMaxFreqMhz: Int?,
    val abi: String,
    val screenWidth: Int,
    val screenHeight: Int,
    val density: Float,
    val densityDpi: Int,
    val totalRam: Long,
    val availableRam: Long,
    val totalStorage: Long,
    val availableStorage: Long,
    val batteryLevel: Int?,
    val isCharging: Boolean?,
    val sensors: List<String>,
)

@HiltViewModel
class DeviceViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(DeviceUiState())
    val state: StateFlow<DeviceUiState> = _state

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val info = withContext(Dispatchers.IO) {
                runCatching { loadDeviceInfo() }.getOrNull()
            }
            _state.value = DeviceUiState(
                info = info,
                loading = false,
                error = if (info == null) "读取设备信息失败" else null,
            )
        }
    }

    private fun loadDeviceInfo(): DeviceInfo {
        val metrics = context.resources.displayMetrics

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val statFs = StatFs(Environment.getDataDirectory().path)
        val totalStorage = statFs.blockSizeLong * statFs.blockCountLong
        val availableStorage = statFs.blockSizeLong * statFs.availableBlocksLong

        val batteryIntent = context.registerReceiver(
            null,
            android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED),
        )
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) level * 100 / scale else null
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensors = sensorManager?.getSensorList(Sensor.TYPE_ALL)
            ?.map { it.name }
            ?.distinct()
            ?.sorted()
            .orEmpty()

        return DeviceInfo(
            device = Build.DEVICE,
            model = Build.MODEL,
            manufacturer = Build.MANUFACTURER,
            androidVersion = Build.VERSION.RELEASE,
            sdkVersion = Build.VERSION.SDK_INT,
            cpuModel = Build.HARDWARE.ifBlank { Build.BOARD },
            cpuCores = Runtime.getRuntime().availableProcessors(),
            cpuMaxFreqMhz = readCpuMaxFreqMhz(),
            abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels,
            density = metrics.density,
            densityDpi = metrics.densityDpi,
            totalRam = memoryInfo.totalMem,
            availableRam = memoryInfo.availMem,
            totalStorage = totalStorage,
            availableStorage = availableStorage,
            batteryLevel = batteryPct,
            isCharging = if (batteryPct != null) isCharging else null,
            sensors = sensors,
        )
    }

    /** /sys 读取在部分机型上不可用，失败时返回 null。 */
    private fun readCpuMaxFreqMhz(): Int? = runCatching {
        val khz = File("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq")
            .takeIf { it.canRead() }
            ?.readText()
            ?.trim()
            ?.toLongOrNull()
            ?: return@runCatching null
        (khz / 1000).toInt()
    }.getOrNull()
}

@Composable
fun DeviceScreen(onBack: () -> Unit, viewModel: DeviceViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ToolScaffold(
        title = "设备信息",
        onBack = onBack,
        subtitle = "CPU、内存、屏幕、电池、传感器一览",
        actions = {
            TextButton(onClick = viewModel::refresh) { Text("刷新") }
        },
    ) {
        if (state.loading) {
            FeedbackBlock(text = "读取中…", type = FeedbackType.LOADING)
            return@ToolScaffold
        }
        state.error?.let {
            FeedbackBlock(text = it, type = FeedbackType.ERROR, onAction = viewModel::refresh, actionLabel = "重试")
            return@ToolScaffold
        }

        state.info?.let { info ->
            ToolSectionCard(title = "设备") {
                KeyValueRow(label = "设备型号", value = info.model)
                KeyValueRow(label = "制造商", value = info.manufacturer)
                KeyValueRow(label = "设备代号", value = info.device)
            }

            ToolSectionCard(title = "系统") {
                KeyValueRow(label = "Android 版本", value = info.androidVersion)
                KeyValueRow(label = "SDK 版本", value = "API ${info.sdkVersion}")
            }

            ToolSectionCard(title = "CPU") {
                KeyValueRow(label = "平台", value = info.cpuModel)
                KeyValueRow(label = "核心数", value = "${info.cpuCores} 核")
                KeyValueRow(label = "最高频率", value = info.cpuMaxFreqMhz?.let { "$it MHz" } ?: "不可读")
                KeyValueRow(label = "ABI", value = info.abi)
            }

            ToolSectionCard(title = "屏幕") {
                KeyValueRow(label = "分辨率", value = "${info.screenWidth} × ${info.screenHeight}")
                KeyValueRow(label = "密度", value = "${info.density}x (${info.densityDpi}dpi)")
            }

            ToolSectionCard(title = "内存") {
                KeyValueRow(label = "总内存", value = formatBytes(info.totalRam))
                KeyValueRow(label = "可用内存", value = formatBytes(info.availableRam))
                KeyValueRow(label = "使用率", value = usagePercent(info.totalRam, info.availableRam))
            }

            ToolSectionCard(title = "存储") {
                KeyValueRow(label = "总存储", value = formatBytes(info.totalStorage))
                KeyValueRow(label = "可用存储", value = formatBytes(info.availableStorage))
                KeyValueRow(label = "使用率", value = usagePercent(info.totalStorage, info.availableStorage))
            }

            ToolSectionCard(title = "电池") {
                KeyValueRow(label = "电量", value = info.batteryLevel?.let { "$it%" } ?: "未知")
                KeyValueRow(
                    label = "充电状态",
                    value = info.isCharging?.let { if (it) "充电中" else "未充电" } ?: "未知",
                )
            }

            ToolSectionCard(title = "传感器（${info.sensors.size}）") {
                if (info.sensors.isEmpty()) {
                    Text("未检测到传感器")
                } else {
                    info.sensors.forEach { name -> KeyValueRow(label = name, value = "") }
                }
            }
        }
    }
}

private fun usagePercent(total: Long, available: Long): String =
    if (total <= 0) "未知" else "${(total - available) * 100 / total}%"

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824 -> String.format(Locale.ROOT, "%.2f GB", bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> String.format(Locale.ROOT, "%.2f MB", bytes / 1_048_576.0)
        bytes >= 1_024 -> String.format(Locale.ROOT, "%.2f KB", bytes / 1_024.0)
        else -> "$bytes B"
    }
}
