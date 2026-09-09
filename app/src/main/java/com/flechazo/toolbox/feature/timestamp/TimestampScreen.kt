package com.flechazo.toolbox.feature.timestamp

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flechazo.toolbox.core.designsystem.components.KeyValueRow
import com.flechazo.toolbox.core.designsystem.components.ResultCard
import com.flechazo.toolbox.core.designsystem.components.SegmentedTabs
import com.flechazo.toolbox.core.designsystem.components.ToolTextField
import com.flechazo.toolbox.core.designsystem.components.ToolScaffold
import com.flechazo.toolbox.core.designsystem.components.ToolSectionCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

enum class TsMode(val label: String) { TS_TO_DATE("时间戳 → 日期"), DATE_TO_TS("日期 → 时间戳") }
enum class TsUnit(val label: String) { MILLIS("毫秒"), SECONDS("秒") }

data class TimestampUiState(
    val nowMillis: Long = System.currentTimeMillis(),
    val mode: TsMode = TsMode.TS_TO_DATE,
    val unit: TsUnit = TsUnit.MILLIS,
    val input: String = "",
    val resultLocal: String = "",
    val resultUtc: String = "",
    val resultRelative: String = "",
    val resultTs: String = "",
    val error: Boolean = false,
)

@HiltViewModel
class TimestampViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(TimestampUiState())
    val state: StateFlow<TimestampUiState> = _state

    init {
        viewModelScope.launch {
            while (true) {
                _state.value = _state.value.copy(nowMillis = System.currentTimeMillis())
                delay(1000)
            }
        }
    }

    fun setMode(mode: TsMode) {
        _state.value = _state.value.copy(mode = mode, error = false)
        recompute()
    }

    fun setUnit(unit: TsUnit) {
        _state.value = _state.value.copy(unit = unit)
        recompute()
    }

    fun onInput(value: String) {
        _state.value = _state.value.copy(input = value)
        recompute()
    }

    fun fillNow() {
        val s = _state.value
        val now = if (s.mode == TsMode.TS_TO_DATE) {
            if (s.unit == TsUnit.MILLIS) System.currentTimeMillis().toString()
            else (System.currentTimeMillis() / 1000).toString()
        } else {
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US))
        }
        _state.value = s.copy(input = now)
        recompute()
    }

    private fun recompute() {
        val s = _state.value
        when (s.mode) {
            TsMode.TS_TO_DATE -> {
                val raw = s.input.trim().toLongOrNull()
                val millis = raw?.let {
                    when {
                        s.unit == TsUnit.MILLIS -> it
                        else -> it * 1000L
                    }
                }
                if (s.input.isBlank()) {
                    _state.value = s.copy(resultLocal = "", resultUtc = "", resultRelative = "", resultTs = "", error = false)
                    return
                }
                if (millis == null) {
                    _state.value = s.copy(resultLocal = "", resultUtc = "", resultRelative = "", resultTs = "", error = true)
                    return
                }
                val instant = Instant.ofEpochMilli(millis)
                _state.value = s.copy(
                    resultLocal = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)),
                    resultUtc = LocalDateTime.ofInstant(instant, ZoneId.of("UTC"))
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)),
                    resultRelative = relativeTo(instant, Instant.now()),
                    resultTs = raw.toString(),
                    error = false,
                )
            }
            TsMode.DATE_TO_TS -> {
                val parsed = try {
                    LocalDateTime.parse(s.input.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US))
                } catch (_: Exception) {
                    null
                }
                if (s.input.isBlank()) {
                    _state.value = s.copy(resultLocal = "", resultUtc = "", resultRelative = "", resultTs = "", error = false)
                    return
                }
                if (parsed == null) {
                    _state.value = s.copy(resultLocal = "", resultUtc = "", resultRelative = "", resultTs = "", error = true)
                    return
                }
                val millis = parsed.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val ts = if (s.unit == TsUnit.MILLIS) millis.toString() else (millis / 1000).toString()
                _state.value = s.copy(
                    resultTs = ts,
                    resultLocal = s.input.trim(),
                    resultUtc = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.of("UTC"))
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)),
                    resultRelative = relativeTo(Instant.ofEpochMilli(millis), Instant.now()),
                    error = false,
                )
            }
        }
    }
}

/**
 * 相对时间文案。旧实现把前缀/后缀判断写反了：过去的时间显示成"5 分钟后"。
 *
 * @param instant 目标时刻
 * @param now 参照时刻（注入以便单测）
 */
internal fun relativeTo(instant: Instant, now: Instant): String {
    val dur = Duration.between(instant, now)
    // dur > 0 表示 instant 在过去（now - instant 为正）
    val isPast = !dur.isNegative
    val abs = dur.abs()
    return when {
        abs.toMinutes() < 1 -> "刚刚"
        abs.toHours() < 1 -> if (isPast) "${abs.toMinutes()} 分钟前" else "${abs.toMinutes()} 分钟后"
        abs.toDays() < 1 -> if (isPast) "${abs.toHours()} 小时前" else "${abs.toHours()} 小时后"
        abs.toDays() < 30 -> if (isPast) "${abs.toDays()} 天前" else "${abs.toDays()} 天后"
        else -> if (isPast) "${abs.toDays() / 30} 个月前" else "${abs.toDays() / 30} 个月后"
    }
}

@Composable
fun TimestampScreen(onBack: () -> Unit, viewModel: TimestampViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    ToolScaffold(
        title = "时间戳转换",
        onBack = onBack,
        subtitle = "Unix 时间戳与日期时间互转，支持秒/毫秒",
    ) {

        SegmentedTabs(
            options = TsMode.entries.toList(),
            selected = state.mode,
            label = { it.label },
            onSelect = viewModel::setMode,
        )

        // 当前时间 + 输入
        ToolSectionCard(title = "当前时间（每秒刷新）") {
            KeyValueRow(
                label = "毫秒时间戳",
                value = state.nowMillis.toString(),
                onClick = { clipboard.setText(AnnotatedString(state.nowMillis.toString())) },
            )
            KeyValueRow(
                label = "秒时间戳",
                value = (state.nowMillis / 1000).toString(),
                onClick = { clipboard.setText(AnnotatedString((state.nowMillis / 1000).toString())) },
            )
            KeyValueRow(
                label = "本地时间",
                value = LocalDateTime.ofInstant(Instant.ofEpochMilli(state.nowMillis), ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)),
            )
        }

        ToolSectionCard(title = "输入") {
            SegmentedTabs(
                options = TsUnit.entries.toList(),
                selected = state.unit,
                label = { it.label },
                onSelect = viewModel::setUnit,
            )
            ToolTextField(
                value = state.input,
                onValueChange = viewModel::onInput,
                label = {
                    Text(
                        when (state.mode) {
                            TsMode.TS_TO_DATE -> if (state.unit == TsUnit.MILLIS) "时间戳（毫秒）" else "时间戳（秒）"
                            TsMode.DATE_TO_TS -> "日期时间 yyyy-MM-dd HH:mm:ss"
                        },
                    )
                },
                isError = state.error,
                singleLine = true,
                trailingIcon = {
                    TextButton(onClick = viewModel::fillNow) { Text("现在") }
                },
                supportingText = if (state.error) {
                    { Text(if (state.mode == TsMode.TS_TO_DATE) "请输入数字时间戳" else "格式：2026-01-01 12:00:00") }
                } else null,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // 结果
        if (state.resultTs.isNotEmpty() && state.mode == TsMode.DATE_TO_TS) {
            ResultCard(
                label = "时间戳",
                value = state.resultTs,
                unit = if (state.unit == TsUnit.MILLIS) "ms" else "s",
                onClick = { clipboard.setText(AnnotatedString(state.resultTs)) },
            )
        }
        if (state.resultLocal.isNotEmpty()) {
            ResultCard(
                label = "日期时间",
                value = state.resultLocal,
                onClick = { clipboard.setText(AnnotatedString(state.resultLocal)) },
            )
            ToolSectionCard(title = "其他格式（点击复制）") {
                KeyValueRow(
                    label = "UTC 时间",
                    value = state.resultUtc,
                    onClick = { clipboard.setText(AnnotatedString(state.resultUtc)) },
                )
                KeyValueRow(
                    label = "相对时间",
                    value = state.resultRelative,
                    onClick = { clipboard.setText(AnnotatedString(state.resultRelative)) },
                )
                if (state.mode == TsMode.TS_TO_DATE) {
                    KeyValueRow(
                        label = "时间戳（${if (state.unit == TsUnit.MILLIS) "毫秒" else "秒"}）",
                        value = state.resultTs,
                        onClick = { clipboard.setText(AnnotatedString(state.resultTs)) },
                    )
                }
            }
        }
    }
}
