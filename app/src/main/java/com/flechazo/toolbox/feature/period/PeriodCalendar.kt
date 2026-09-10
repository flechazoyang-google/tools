package com.flechazo.toolbox.feature.period

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flechazo.toolbox.core.designsystem.components.ToolSectionCard
import com.flechazo.toolbox.core.designsystem.theme.ToolShape
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val monthFormat = DateTimeFormatter.ofPattern("yyyy 年 M 月", Locale.CHINA)

/** 含首尾的日期序列；`ClosedRange<LocalDate>` 本身不可迭代，必须显式展开。 */
private fun dateRange(from: LocalDate, to: LocalDate): List<LocalDate> =
    if (to.isBefore(from)) {
        emptyList()
    } else {
        generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()
    }

/**
 * 月历。状态优先级：已记录 > 预测经期 > 预测区间边界 > 易孕期 > 普通日。
 *
 * 「今天」用 2dp 外描边区分，与预测区间边界（1dp）刻意不同——同一种描边不表达两种语义。
 */
@Composable
internal fun PeriodMonthCalendar(
    month: YearMonth,
    today: LocalDate,
    records: List<PeriodRecord>,
    prediction: PeriodPrediction?,
    settings: PeriodSettings,
    logs: List<DailyLog>,
    periodLength: Int,
    onDateClick: (LocalDate) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val recordedDays = buildSet {
        records.forEach { record ->
            val last = record.end ?: minOf(today, record.start.plusDays((periodLength - 1).toLong()))
            addAll(dateRange(record.start, last))
        }
    }
    val predictedDays = prediction?.periodDays?.toSet().orEmpty()
    val windowEdges = setOfNotNull(prediction?.windowStart, prediction?.windowEnd)
    val fertileDays = if (settings.showFertileWindow) prediction?.fertileDays?.toSet().orEmpty() else emptySet()
    val ovulation = prediction?.ovulation
    val loggedDays = logs.map { it.date }.toSet()

    ToolSectionCard(title = month.format(monthFormat), modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onPrev) { Text("上月") }
            TextButton(onClick = onToday) { Text("今天") }
            TextButton(onClick = onNext) { Text("下月") }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        val firstDay = month.atDay(1)
        val leading = (firstDay.dayOfWeek.value + 6) % 7
        val rows = (leading + month.lengthOfMonth() + 6) / 7

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(rows) { row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    repeat(7) { col ->
                        val dayIndex = row * 7 + col - leading
                        if (dayIndex < 0 || dayIndex >= month.lengthOfMonth()) {
                            Box(Modifier.weight(1f).aspectRatio(1f))
                        } else {
                            val date = month.atDay(dayIndex + 1)
                            CalendarDay(
                                date = date,
                                isToday = date == today,
                                isRecorded = date in recordedDays,
                                isPredicted = date in predictedDays,
                                isWindowEdge = date in windowEdges,
                                isFertile = date in fertileDays,
                                isOvulation = date == ovulation,
                                hasLog = date in loggedDays,
                                onClick = { onDateClick(date) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LegendItem(MaterialTheme.colorScheme.primary, "已记录")
                LegendItem(MaterialTheme.colorScheme.primaryContainer, "预测经期")
                LegendItem(MaterialTheme.colorScheme.outline, "预测区间")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LegendItem(MaterialTheme.colorScheme.secondaryContainer, "易孕期")
                LegendItem(MaterialTheme.colorScheme.tertiary, "估算排卵日")
                LegendItem(MaterialTheme.colorScheme.onSurfaceVariant, "有每日记录")
            }
        }
    }
}

@Composable
private fun CalendarDay(
    date: LocalDate,
    isToday: Boolean,
    isRecorded: Boolean,
    isPredicted: Boolean,
    isWindowEdge: Boolean,
    isFertile: Boolean,
    isOvulation: Boolean,
    hasLog: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = when {
        isRecorded -> MaterialTheme.colorScheme.primary
        isPredicted -> MaterialTheme.colorScheme.primaryContainer
        isFertile -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = when {
        isRecorded -> MaterialTheme.colorScheme.onPrimary
        isPredicted -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val borderColor: Color? = when {
        isToday -> MaterialTheme.colorScheme.primary
        isOvulation -> MaterialTheme.colorScheme.tertiary
        isWindowEdge -> MaterialTheme.colorScheme.primary
        else -> null
    }
    val borderWidth: Dp = when {
        isToday -> 2.dp
        isOvulation || isWindowEdge -> 1.dp
        else -> 0.dp
    }

    val description = buildString {
        append("${date.monthValue}月${date.dayOfMonth}日")
        if (isToday) append("，今天")
        if (isRecorded) append("，已记录经期")
        if (isPredicted) append("，预测经期")
        if (isOvulation) append("，估算排卵日")
        if (isFertile) append("，易孕期")
        if (hasLog) append("，有每日记录")
    }

    Box(
        modifier = modifier
            .padding(2.dp)
            .aspectRatio(1f)
            .clip(ToolShape.md)
            .background(container)
            .then(
                if (borderColor != null && borderWidth > 0.dp) {
                    Modifier.border(borderWidth, borderColor, ToolShape.md)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = if (isToday || isRecorded) FontWeight.Bold else FontWeight.Normal,
            ),
            color = content,
        )
        if (hasLog) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = (-3).dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(if (isRecorded) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(10.dp).clip(ToolShape.xs).background(color))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
