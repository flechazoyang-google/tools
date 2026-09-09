package com.flechazo.toolbox.feature.period

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flechazo.toolbox.core.designsystem.components.FeedbackBlock
import com.flechazo.toolbox.core.designsystem.components.FeedbackType
import com.flechazo.toolbox.core.designsystem.components.KeyValueRow
import com.flechazo.toolbox.core.designsystem.components.LabeledDropdown
import com.flechazo.toolbox.core.designsystem.components.ResultCard
import com.flechazo.toolbox.core.designsystem.components.ToolScaffold
import com.flechazo.toolbox.core.designsystem.components.ToolSectionCard
import com.flechazo.toolbox.core.designsystem.components.ToolTextField
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val dayFormat = DateTimeFormatter.ofPattern("M 月 d 日", Locale.CHINA)
private val fullFormat = DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日", Locale.CHINA)
private val weekdayFormat = DateTimeFormatter.ofPattern("M 月 d 日 EEEE", Locale.CHINA)

@Composable
fun PeriodScreen(onBack: () -> Unit, viewModel: PeriodViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showSettings by remember { mutableStateOf(false) }
    var daySheetDate by remember { mutableStateOf<LocalDate?>(null) }
    var showStartPicker by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val json = viewModel.exportJson()
                val ok = runCatching {
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(json.toByteArray(Charsets.UTF_8))
                    } != null
                }.getOrDefault(false)
                snackbarHostState.showSnackbar(if (ok) "已导出经期数据（JSON）" else "导出失败")
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            }.getOrNull()
            if (text.isNullOrBlank()) {
                scope.launch { snackbarHostState.showSnackbar("无法读取所选文件") }
            } else {
                viewModel.importJson(text)
            }
        }
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearMessage()
    }

    LaunchedEffect(state.lastDeleted) {
        val deleted = state.lastDeleted ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "已删除 ${deleted.start.format(dayFormat)} 的经期",
            actionLabel = "撤销",
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete() else viewModel.clearUndo()
    }

    Box(Modifier.fillMaxSize()) {
        ToolScaffold(
            title = "经期记录",
            subtitle = "记录周期并估算下次经期与易孕期（仅供参考，不能用于避孕）",
            onBack = onBack,
            actions = {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = "设置",
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(100.dp))
                        .clickable { showSettings = true }
                        .padding(10.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            },
        ) {
            if (!state.settings.consentAccepted) {
                ConsentCard(onAccept = viewModel::acceptConsent)
            } else {
                StatusCard(state)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = { viewModel.markStart(state.today) },
                        modifier = Modifier.weight(1f),
                    ) { Text("记录今天") }
                    OutlinedButton(
                        onClick = { viewModel.markEnd(state.today) },
                        modifier = Modifier.weight(1f),
                    ) { Text("经期结束") }
                }
                TextButton(onClick = { showStartPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("补记历史经期开始日")
                }

                PeriodMonthCalendar(
                    month = state.month,
                    today = state.today,
                    records = state.records,
                    prediction = state.prediction,
                    settings = state.settings,
                    logs = state.logs,
                    periodLength = expectedPeriodLength(state.prediction),
                    onDateClick = { daySheetDate = it },
                    onPrev = viewModel::previousMonth,
                    onNext = viewModel::nextMonth,
                    onToday = viewModel::goToToday,
                )

                if (state.hasRecords) {
                    SummaryCard(state)
                    CycleTrendCard(state)
                    HistoryCard(state, onDelete = viewModel::deleteRecord)
                } else {
                    FeedbackBlock(
                        text = "还没有记录。点「记录今天」或日历上最近一次经期的开始日即可开始。",
                        type = FeedbackType.EMPTY,
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }

    if (showStartPicker) {
        DatePickerSheet(
            initial = state.today,
            onDismiss = { showStartPicker = false },
            onPick = { viewModel.markStart(it) },
        )
    }

    daySheetDate?.let { date ->
        DaySheet(
            date = date,
            state = state,
            onDismiss = { daySheetDate = null },
            onFlow = { viewModel.setFlow(date, it) },
            onSaveLog = viewModel::saveLog,
            onMarkStart = { viewModel.markStart(date) },
            onMarkEnd = { viewModel.markEnd(date) },
            onDelete = { start -> viewModel.deleteRecord(start); daySheetDate = null },
        )
    }

    if (showSettings) {
        SettingsSheet(
            state = state,
            onDismiss = { showSettings = false },
            onSettings = viewModel::updateSettings,
            onExport = { exportLauncher.launch("period-backup.json") },
            onImport = { importLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream")) },
            onClearAll = viewModel::clearAllData,
        )
    }
}

// ---------------------------------------------------------------------------
// 首启同意
// ---------------------------------------------------------------------------

@Composable
private fun ConsentCard(onAccept: () -> Unit) {
    ToolSectionCard(title = "开始之前") {
        Text(
            "经期数据属于敏感个人信息。本应用不注册账号、不联网、不含统计或广告 SDK，" +
                "所有记录只保存在本机，卸载即删除。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "你可以随时在设置中导出或清空全部数据。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onAccept, modifier = Modifier.fillMaxWidth()) {
            Text("同意并开始记录")
        }
    }
}

// ---------------------------------------------------------------------------
// 状态卡
// ---------------------------------------------------------------------------

private data class StatusModel(
    val label: String,
    val value: String,
    val caption: String,
    val container: Color,
    val content: Color,
)

@Composable
private fun StatusCard(state: PeriodUiState) {
    val model = statusModel(state)
    ResultCard(
        label = model.label,
        value = model.value,
        caption = model.caption,
        singleLine = false,
        containerColor = model.container,
        contentColor = model.content,
    )
}

@Composable
private fun statusModel(state: PeriodUiState): StatusModel {
    val scheme = MaterialTheme.colorScheme
    val prediction = state.prediction
    val today = state.today

    if (!state.settings.predictEnabled) {
        return StatusModel(
            "预测已关闭",
            "仅记录",
            "可在设置中重新开启预测",
            scheme.surfaceContainerHigh,
            scheme.onSurface,
        )
    }

    val periodDay = state.periodDay
    if (periodDay != null) {
        val record = state.records
            .filter { !it.start.isAfter(today) }
            .maxByOrNull { it.start }
        val expectedEnd = record?.let {
            it.end ?: it.start.plusDays((expectedPeriodLength(prediction) - 1).toLong())
        }
        return StatusModel(
            "经期第 $periodDay 天",
            "经期中",
            buildString {
                record?.let { append("${it.start.format(dayFormat)}开始") }
                expectedEnd?.let { append(" · 预计 ${it.format(dayFormat)}结束") }
            },
            scheme.primaryContainer,
            scheme.onPrimaryContainer,
        )
    }

    if (prediction == null) {
        return StatusModel(
            "下次经期预测",
            "再记录一次",
            "连续记录 3 个周期后预测会更准",
            scheme.surfaceContainerHigh,
            scheme.onSurface,
        )
    }

    val basis = basisCaption(prediction)
    if (prediction.missedCycle) {
        return StatusModel(
            "疑似漏记",
            "已超过一个周期",
            "请确认是否漏记，或补充记录",
            scheme.surfaceContainerHigh,
            scheme.onSurface,
        )
    }
    if (prediction.daysLate > 0) {
        return StatusModel(
            "下次经期预测",
            "预计已推迟 ${prediction.daysLate} 天",
            "预测区间 ${prediction.windowStart.format(dayFormat)}–${prediction.windowEnd.format(dayFormat)} · 偶尔波动很常见",
            scheme.tertiaryContainer,
            scheme.onTertiaryContainer,
        )
    }
    if (!today.isBefore(prediction.windowStart) && !today.isAfter(prediction.windowEnd)) {
        return StatusModel(
            "下次经期预测",
            "预计这几天会来",
            "预测区间 ${prediction.windowStart.format(dayFormat)}–${prediction.windowEnd.format(dayFormat)} · $basis",
            scheme.primaryContainer,
            scheme.onPrimaryContainer,
        )
    }

    val daysUntil = ChronoUnit.DAYS.between(today, prediction.nextStart)
    val daysToOvulation = ChronoUnit.DAYS.between(today, prediction.ovulation)
    if (daysToOvulation in 1..3) {
        return StatusModel(
            "排卵估算",
            "距离排卵约 $daysToOvulation 天",
            "估算值，不能用于避孕",
            scheme.secondaryContainer,
            scheme.onSecondaryContainer,
        )
    }

    val emphasized = daysUntil <= 7
    return StatusModel(
        "下次经期预测",
        "还有 $daysUntil 天",
        "${prediction.nextStart.format(dayFormat)}（±${windowDays(prediction)} 天）· $basis",
        if (emphasized) scheme.primaryContainer else scheme.surfaceContainerHigh,
        if (emphasized) scheme.onPrimaryContainer else scheme.onSurface,
    )
}

private fun windowDays(prediction: PeriodPrediction): Long =
    prediction.windowEnd.toEpochDay() - prediction.nextStart.toEpochDay()

private fun basisCaption(prediction: PeriodPrediction): String {
    val stats = prediction.stats
    val basis = if (stats.basedOnCycles == 0) "首次记录" else "基于最近 ${stats.basedOnCycles} 个周期"
    return "$basis · 周期 ${stats.cycleLength} 天"
}

// ---------------------------------------------------------------------------
// 摘要 / 趋势 / 历史
// ---------------------------------------------------------------------------

@Composable
private fun SummaryCard(state: PeriodUiState) {
    val prediction = state.prediction
    val stats = prediction?.stats
    ToolSectionCard(title = "周期摘要") {
        if (state.settings.showCycleDay) {
            state.cycleDay?.let { KeyValueRow(label = "今天", value = "周期第 $it 天") }
        }
        KeyValueRow(
            label = "平均周期",
            value = stats?.let { "${it.cycleLength} 天" } ?: "数据不足",
        )
        KeyValueRow(
            label = "经期长度",
            value = stats?.let {
                "${it.periodLength} 天" + if (it.periodLengthKnown) "" else "（默认）"
            } ?: "—",
        )
        KeyValueRow(
            label = "周期波动",
            value = stats?.let {
                if (it.basedOnCycles < 2) "数据不足" else "${it.variationDays} 天 · ${it.regularity.label}"
            } ?: "数据不足",
        )
        prediction?.let {
            KeyValueRow(label = "估算排卵日", value = it.ovulation.format(dayFormat))
            KeyValueRow(
                label = "易孕期（估算）",
                value = "${it.fertileStart.format(dayFormat)} ~ ${it.fertileEnd.format(dayFormat)}",
            )
        }
        KeyValueRow(label = "已记录", value = "${state.records.size} 次")
    }
}

@Composable
private fun CycleTrendCard(state: PeriodUiState) {
    val starts = state.records.map { it.start }.distinct().sorted()
    val cycles = starts.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b).toInt() }
        .filter { it in MIN_CYCLE_GAP..MAX_CYCLE_GAP }
        .takeLast(6)
    if (cycles.size < 2) return

    val maxCycle = cycles.max()
    ToolSectionCard(title = "周期趋势") {
        Row(
            modifier = Modifier.fillMaxWidth().height(110.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            cycles.forEachIndexed { index, value ->
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Text(
                        value.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((64f * value / maxCycle).dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "第${index + 1}次",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Text(
            "最近 ${cycles.size} 个周期的长度（天）。波动越大，预测区间越宽。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HistoryCard(state: PeriodUiState, onDelete: (LocalDate) -> Unit) {
    ToolSectionCard(title = "历史记录") {
        state.records.take(24).forEach { record ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    val range = record.end?.let { "${record.start.format(dayFormat)} – ${it.format(dayFormat)}" }
                        ?: "${record.start.format(dayFormat)} 起"
                    Text(
                        range,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        buildString {
                            append(record.lengthDays?.let { "$it 天" } ?: "长度未知")
                            if (record.isOngoing) append(" · 进行中")
                            if (record.note.isNotBlank()) append(" · ${record.note}")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { onDelete(record.start) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "删除", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 当日记录弹窗
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DaySheet(
    date: LocalDate,
    state: PeriodUiState,
    onDismiss: () -> Unit,
    onFlow: (FlowLevel?) -> Unit,
    onSaveLog: (DailyLog) -> Unit,
    onMarkStart: () -> Unit,
    onMarkEnd: () -> Unit,
    onDelete: (LocalDate) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val record = state.recordCovering(date)
    val existingLog = state.logOn(date)

    var flow by remember(date) { mutableStateOf(record?.flows?.get(date)) }
    var pain by remember(date) { mutableStateOf(existingLog?.pain) }
    var symptoms by remember(date) { mutableStateOf(existingLog?.symptoms.orEmpty()) }
    var note by remember(date) { mutableStateOf(existingLog?.note.orEmpty()) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                date.format(weekdayFormat),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (record != null) {
                Text("经量", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowLevel.entries.forEach { level ->
                        FilterChip(
                            selected = flow == level,
                            onClick = {
                                flow = if (flow == level) null else level
                                onFlow(flow)
                            },
                            label = { Text(level.label) },
                        )
                    }
                }
            } else {
                OutlinedButton(onClick = onMarkStart, modifier = Modifier.fillMaxWidth()) {
                    Text("把这一天记为经期开始")
                }
            }

            Text("疼痛", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PainLevel.entries.forEach { level ->
                    FilterChip(
                        selected = pain == level,
                        onClick = { pain = if (pain == level) null else level },
                        label = { Text(level.label) },
                    )
                }
            }

            Text("症状", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Symptom.entries.forEach { symptom ->
                    FilterChip(
                        selected = symptom in symptoms,
                        onClick = {
                            symptoms = if (symptom in symptoms) symptoms - symptom else symptoms + symptom
                        },
                        label = { Text(symptom.label) },
                    )
                }
            }

            ToolTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("备注") },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        onSaveLog(DailyLog(date = date, symptoms = symptoms, pain = pain, note = note.trim()))
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("保存") }
                if (record?.isOngoing == true) {
                    OutlinedButton(onClick = { onMarkEnd(); onDismiss() }, modifier = Modifier.weight(1f)) {
                        Text("记录结束")
                    }
                }
            }
            if (record != null) {
                TextButton(
                    onClick = { onDelete(record.start) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("删除这次经期") }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 设置弹窗
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    state: PeriodUiState,
    onDismiss: () -> Unit,
    onSettings: (PeriodSettings) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onClearAll: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val settings = state.settings
    var confirmClear by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("设置", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)

            SwitchRow(
                title = "显示预测",
                subtitle = "关闭后只记录，不显示预测与排卵估算",
                checked = settings.predictEnabled,
                onCheckedChange = { onSettings(settings.copy(predictEnabled = it)) },
            )
            SwitchRow(
                title = "显示易孕期",
                subtitle = "易孕期为日历估算，不能用于避孕",
                checked = settings.showFertileWindow,
                onCheckedChange = { onSettings(settings.copy(showFertileWindow = it)) },
            )
            SwitchRow(
                title = "显示周期天数",
                subtitle = "在摘要中显示「周期第 N 天」",
                checked = settings.showCycleDay,
                onCheckedChange = { onSettings(settings.copy(showCycleDay = it)) },
            )

            LabeledDropdown(
                label = "周期长度",
                options = listOf<Int?>(null) + (21..45).toList(),
                selected = settings.cycleLengthOverride,
                display = { it?.let { value -> "$value 天" } ?: "自动估算" },
                onSelect = { onSettings(settings.copy(cycleLengthOverride = it)) },
                modifier = Modifier.padding(top = 8.dp),
            )
            LabeledDropdown(
                label = "经期长度",
                options = listOf<Int?>(null) + (2..10).toList(),
                selected = settings.periodLengthOverride,
                display = { it?.let { value -> "$value 天" } ?: "自动估算" },
                onSelect = { onSettings(settings.copy(periodLengthOverride = it)) },
            )

            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
                Text("导出经期数据（JSON）")
            }
            OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                Text("导入经期数据")
            }
            if (confirmClear) {
                Text(
                    "此操作不可恢复，是否继续？",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { onClearAll(); confirmClear = false; onDismiss() },
                        modifier = Modifier.weight(1f),
                    ) { Text("确认清空") }
                    OutlinedButton(onClick = { confirmClear = false }, modifier = Modifier.weight(1f)) {
                        Text("取消")
                    }
                }
            } else {
                TextButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("清空全部记录", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// ---------------------------------------------------------------------------
// 日期选择
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerSheet(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit,
) {
    // M3 按 UTC 解释毫秒，必须用 UTC 起算，否则 UTC+8 凌晨会记成前一天。
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    onDismiss()
                },
            ) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    ) {
        DatePicker(state = pickerState, title = { Text("选择经期开始日", modifier = Modifier.padding(16.dp)) })
    }
}
