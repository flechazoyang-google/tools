package com.flechazo.toolbox.feature.countdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.flechazo.toolbox.core.designsystem.components.LabeledDropdown
import com.flechazo.toolbox.core.designsystem.components.SegmentedTabs
import com.flechazo.toolbox.core.designsystem.components.ToolTextField
import com.flechazo.toolbox.core.designsystem.theme.ToolShape
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val LUNAR_MONTHS = listOf(
    "正月", "二月", "三月", "四月", "五月", "六月",
    "七月", "八月", "九月", "十月", "冬月", "腊月",
)

private val LUNAR_DAYS = listOf(
    "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
    "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
    "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十",
)

/**
 * 新建 / 编辑事件表单。
 *
 * 用 `ModalBottomSheet` 而不是原来的 `AlertDialog`：字段从 3 个涨到 9 个，小屏对话框
 * 装不下；而且**新建与编辑必须共用同一份表单** —— 旧版根本没有编辑路径（P0-1）。
 *
 * 刻意不做的事：农历非法组合（如"闰五月初五"但该年无闰五月）不阻断保存，
 * 由 [LunarResolver] 回退并在预览行如实标注"按五月初五过"。让用户看得见结果，
 * 比弹一个"输入有误"的框更诚实。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CountdownEditor(
    initial: CountdownDraft,
    isEditing: Boolean,
    lunar: LunarCalendar,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSave: (CountdownDraft) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var draft by remember { mutableStateOf(initial) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    // 次要字段默认折叠：9 个字段在小屏上一屏装不下，但提醒/颜色恰恰是"用得少、设一次就好"的配置
    var moreExpanded by remember { mutableStateOf(initial.hasSecondarySettings()) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val resolvedEntity = remember(draft) { draft.toEntity(lunar, today) }
    val resolvedSolar = resolvedEntity?.let { CountdownEngine.parseDate(it.date) }

    /** "农历四月廿一 → 2026-06-02"；若发生回退，如实说明按哪天过。 */
    val lunarPreview: String? = remember(draft, resolvedSolar) {
        if (!draft.isLunar || resolvedSolar == null) return@remember null
        val actual = lunar.solarToLunar(resolvedSolar)
        val fellBack = actual != null &&
            (actual.month != draft.lunarMonth || actual.day != draft.lunarDay || actual.isLeap != draft.lunarLeap)
        if (fellBack && actual != null) {
            "对应 $resolvedSolar · 实际按${CountdownEngine.formatLunarDay(actual)}"
        } else {
            "对应 $resolvedSolar"
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(if (isEditing) "编辑事件" else "添加事件", style = MaterialTheme.typography.titleLarge)

            ToolTextField(
                value = draft.title,
                onValueChange = { draft = draft.copy(title = it) },
                label = { Text("名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // 不写"倒数日 / 纪念日"：用户理解不了这两个词的区别（P0-4 的根因），
            // 而是直接问"这个日子是还没到、还是已经过去"。
            FieldLabel("这个日子是")
            SegmentedTabs(
                options = listOf(EventMode.COUNTDOWN, EventMode.ELAPSED),
                selected = draft.mode,
                label = { it.label },
                onSelect = { draft = draft.copy(mode = it) },
            )

            FieldLabel("日期")
            if (draft.isLunar) {
                LabeledDropdown(
                    label = "农历月份",
                    options = (1..12).toList(),
                    selected = draft.lunarMonth.coerceIn(1, 12),
                    display = { LUNAR_MONTHS[it - 1] },
                    onSelect = { draft = draft.copy(lunarMonth = it) },
                )
                LabeledDropdown(
                    label = "农历日期",
                    options = (1..30).toList(),
                    selected = draft.lunarDay.coerceIn(1, 30),
                    display = { LUNAR_DAYS[it - 1] },
                    onSelect = { draft = draft.copy(lunarDay = it) },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = draft.lunarLeap,
                        onCheckedChange = { draft = draft.copy(lunarLeap = it) },
                    )
                    Text("闰月", style = MaterialTheme.typography.bodyMedium)
                }
                lunarPreview?.let { Hint(it) }
            } else {
                PickupField(
                    text = "${draft.solarDate} ${draft.solarDate.dayOfWeekName()}",
                    icon = Icons.Filled.CalendarMonth,
                    onClick = { showDatePicker = true },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = draft.isLunar, onCheckedChange = { draft = draft.copy(isLunar = it) })
                Spacer(Modifier.size(10.dp))
                Text("按农历", style = MaterialTheme.typography.bodyMedium)
            }

            FieldLabel("重复")
            SegmentedTabs(
                options = listOf(RepeatRule.NONE, RepeatRule.WEEKLY, RepeatRule.MONTHLY, RepeatRule.YEARLY_SOLAR),
                selected = if (draft.effectiveRepeat == RepeatRule.YEARLY_LUNAR) {
                    RepeatRule.YEARLY_SOLAR
                } else {
                    draft.repeat
                },
                label = { rule ->
                    when {
                        rule == RepeatRule.NONE -> "不重复"
                        rule == RepeatRule.YEARLY_SOLAR && draft.isLunar -> "农历每年"
                        else -> rule.shortLabel.ifBlank { rule.label }
                    }
                },
                onSelect = { draft = draft.copy(repeat = it) },
            )
            val anchorDay = if (draft.isLunar) draft.lunarDay else draft.solarDate.dayOfMonth
            if (draft.repeat == RepeatRule.MONTHLY && anchorDay > 28) Hint("小月没有这天，按该月最后一天过")
            if (draft.repeat == RepeatRule.YEARLY_SOLAR && !draft.isLunar &&
                draft.solarDate.monthValue == 2 && draft.solarDate.dayOfMonth == 29
            ) {
                Hint("平年按 2 月 28 日过")
            }

            TextButton(onClick = { moreExpanded = !moreExpanded }) {
                Text(if (moreExpanded) "收起提醒与外观" else "提醒、颜色与备注")
            }

            if (moreExpanded) {
                FieldLabel("提醒")
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FilterChip(
                        selected = draft.remindDays.isEmpty(),
                        onClick = { draft = draft.copy(remindDays = emptyList()) },
                        label = { Text("关闭") },
                    )
                    RemindDays.OPTIONS.forEach { d ->
                        FilterChip(
                            selected = d in draft.remindDays,
                            onClick = {
                                draft = draft.copy(
                                    remindDays = if (d in draft.remindDays) {
                                        draft.remindDays - d
                                    } else {
                                        (draft.remindDays + d).sorted()
                                    },
                                )
                            },
                            label = { Text(if (d == 0) "当天" else "提前${chipUnit(d)}") },
                        )
                    }
                }
                if (draft.remindDays.isNotEmpty()) {
                    PickupField(
                        text = "%02d:00 提醒".format(draft.remindHour),
                        icon = Icons.Filled.Schedule,
                        onClick = { showTimePicker = true },
                    )
                }

                FieldLabel("颜色")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // 第一个"跟随主题"选项：动态取色下让卡片仍能与全应用一致
                    Swatch(key = "", selected = draft.colorKey, onSelect = { draft = draft.copy(colorKey = "") })
                    CountdownPalette.ORDERED_KEYS.forEach { key ->
                        Swatch(key = key, selected = draft.colorKey, onSelect = { draft = draft.copy(colorKey = key) })
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = draft.pinned, onCheckedChange = { draft = draft.copy(pinned = it) })
                    Spacer(Modifier.size(10.dp))
                    Text("置顶", style = MaterialTheme.typography.bodyMedium)
                }

                ToolTextField(
                    value = draft.note,
                    onValueChange = { if (it.length <= 200) draft = draft.copy(note = it) },
                    label = { Text("备注") },
                    minLines = 2,
                    supportingText = { Text("${draft.note.length}/200") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
                TextButton(
                    onClick = { onSave(draft) },
                    enabled = draft.titleValid,
                    modifier = Modifier.weight(1f),
                ) { Text("保存") }
            }
            if (isEditing && onDelete != null) {
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Text("删除这个事件", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showDatePicker) {
        SolarDatePicker(
            initial = draft.solarDate,
            onDismiss = { showDatePicker = false },
            onPick = {
                draft = draft.copy(solarDate = it)
                showDatePicker = false
            },
        )
    }

    if (showTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = draft.remindHour.coerceIn(0, 23),
            initialMinute = 0,
            is24Hour = true,
        )
        // 用 AlertDialog 而不是 M3 的 TimePickerDialog：后者在本 BOM 版本里签名不稳定，
        // 而这里只需要"选个整点"，不必引入对话框封装。
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    draft = draft.copy(remindHour = timeState.hour.coerceIn(0, 23))
                    showTimePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("取消") } },
            text = { TimePicker(state = timeState) },
        )
    }
}

/** 编辑已有事件时展开次要区，让用户看到"这条已经配了什么"；新建时保持精简。 */
private fun CountdownDraft.hasSecondarySettings(): Boolean =
    remindDays != listOf(0) || colorKey.isNotEmpty() || pinned || note.isNotBlank() || anchorDate != null

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun chipUnit(d: Int): String = when {
    d >= 30 -> "${d / 30}个月"
    d >= 7 -> "${d / 7}周"
    else -> "${d}天"
}

private fun LocalDate.dayOfWeekName(): String =
    DateTimeFormatter.ofPattern("EEEE", Locale.CHINA).format(this)

/** 只读回显行：整行可点。用 readOnly 输入框会吞掉点击，外层 clickable 不生效。 */
@Composable
private fun PickupField(text: String, icon: ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ToolShape.lg)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun Swatch(key: String, selected: String, onSelect: () -> Unit) {
    val dark = isSystemInDarkTheme()
    val color = if (key.isEmpty()) MaterialTheme.colorScheme.primary else CountdownPalette.raw(key, dark)?.container
        ?: MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(color)
            .then(
                if (selected == key) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onSelect)
            .semantics { contentDescription = if (key.isEmpty()) "跟随主题色" else "颜色 $key" },
        contentAlignment = Alignment.Center,
    ) {
        if (key.isEmpty()) {
            Text("主", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

/**
 * M3 日期选择器。
 *
 * `DatePicker` 以 **UTC** 解释毫秒值，初值与回读都必须走 UTC —— 否则东八区选 5/18
 * 会回读成 5/17。这个坑原实现已踩过并留了注释，搬到新文件时把结论一起带过来。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SolarDatePicker(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = state.selectedDateMillis
                if (millis != null) {
                    onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                } else {
                    onDismiss()
                }
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    ) { DatePicker(state = state) }
}
