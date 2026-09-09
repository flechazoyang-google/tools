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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.flechazo.toolbox.core.data.PeriodRepository
import com.flechazo.toolbox.core.designsystem.components.FeedbackBlock
import com.flechazo.toolbox.core.designsystem.components.FeedbackType
import com.flechazo.toolbox.core.designsystem.components.KeyValueRow
import com.flechazo.toolbox.core.designsystem.components.ResultCard
import com.flechazo.toolbox.core.designsystem.components.ToolScaffold
import com.flechazo.toolbox.core.designsystem.components.ToolSectionCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** 预测结果。 */
data class PeriodPrediction(
    val averageCycle: Int,
    val nextStart: LocalDate?,
    val ovulation: LocalDate?,
    val fertileStart: LocalDate?,
    val fertileEnd: LocalDate?,
)

/**
 * 由历次经期开始日推算下次经期 / 排卵日 / 易孕期。
 *
 * 纯函数，便于单测。周期长度取相邻间隔的中位数（比平均值更抗异常值），
 * 间隔超出 15..60 天视为异常记录直接剔除。
 */
internal fun predictPeriod(starts: List<LocalDate>, today: LocalDate = LocalDate.now()): PeriodPrediction {
    if (starts.isEmpty()) {
        return PeriodPrediction(28, null, null, null, null)
    }
    val sorted = starts.sortedDescending()
    val gaps = sorted.zipWithNext { a, b -> (a.toEpochDay() - b.toEpochDay()).toInt() }
        .filter { it in 15..60 }
    val cycle = if (gaps.isEmpty()) 28 else gaps.sorted()[gaps.size / 2].coerceIn(20, 45)
    val lastStart = sorted.first()
    // 若预测日已过，顺延到下一个周期，避免长期未记录时显示"已推迟 xx 天"
    var next = lastStart.plusDays(cycle.toLong())
    var guard = 0
    while (next.isBefore(today) && guard < 24) {
        next = next.plusDays(cycle.toLong())
        guard++
    }
    val ovulation = next.minusDays(14)
    return PeriodPrediction(
        averageCycle = cycle,
        nextStart = next,
        ovulation = ovulation,
        fertileStart = ovulation.minusDays(5),
        fertileEnd = ovulation.plusDays(1),
    )
}

data class PeriodUiState(
    val starts: List<LocalDate> = emptyList(),
    val prediction: PeriodPrediction = PeriodPrediction(28, null, null, null, null),
    val month: YearMonth = YearMonth.now(),
) {
    val daysUntilNext: Long?
        get() = prediction.nextStart?.let { it.toEpochDay() - LocalDate.now().toEpochDay() }
}

@HiltViewModel
class PeriodViewModel @Inject constructor(
    private val repository: PeriodRepository,
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.now())

    val state: StateFlow<PeriodUiState> = combine(repository.starts, month) { starts, m ->
        PeriodUiState(starts = starts, prediction = predictPeriod(starts), month = m)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeriodUiState())

    fun toggleDate(date: LocalDate) {
        viewModelScope.launch {
            if (state.value.starts.contains(date)) repository.remove(date) else repository.add(date)
        }
    }

    fun previousMonth() { month.value = month.value.minusMonths(1) }
    fun nextMonth() { month.value = month.value.plusMonths(1) }
}

private val displayFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd EEEE", java.util.Locale.CHINA)
private val monthFormat = DateTimeFormatter.ofPattern("yyyy 年 M 月", java.util.Locale.CHINA)

@Composable
fun PeriodScreen(onBack: () -> Unit, viewModel: PeriodViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ToolScaffold(
        title = "经期记录",
        subtitle = "点击日历上的日期记录或取消，自动预测下次与排卵日",
        onBack = onBack,
    ) {
        // 预测摘要
        val prediction = state.prediction
        val next = prediction.nextStart
        if (next == null) {
            FeedbackBlock(text = "记录第一次经期开始日期，开始预测周期", type = FeedbackType.EMPTY)
        } else {
            val days = state.daysUntilNext
            ResultCard(
                label = "下次经期预测",
                value = when {
                    days == null -> ""
                    days == 0L -> "预测今天开始"
                    days > 0L -> "还有 $days 天"
                    else -> "已推迟 ${-days} 天"
                },
                caption = "${next.format(displayFormat)} · 平均周期 ${prediction.averageCycle} 天",
            )
            ToolSectionCard(title = "预测详情") {
                KeyValueRow(label = "下次经期", value = next.format(DateTimeFormatter.ISO_LOCAL_DATE))
                prediction.ovulation?.let {
                    KeyValueRow(label = "排卵日", value = it.format(DateTimeFormatter.ISO_LOCAL_DATE))
                }
                if (prediction.fertileStart != null && prediction.fertileEnd != null) {
                    KeyValueRow(
                        label = "易孕期",
                        value = "${prediction.fertileStart.format(DateTimeFormatter.ISO_LOCAL_DATE)} ~ " +
                            prediction.fertileEnd.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    )
                }
                KeyValueRow(label = "已记录", value = "${state.starts.size} 次")
            }
        }

        // 月历
        MonthCalendar(
            month = state.month,
            recorded = state.starts.toSet(),
            predictedStart = prediction.nextStart,
            onToggle = viewModel::toggleDate,
            onPrev = viewModel::previousMonth,
            onNext = viewModel::nextMonth,
        )

        // 历史
        if (state.starts.isNotEmpty()) {
            ToolSectionCard(title = "历史记录") {
                state.starts.take(12).forEach { date ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            date.format(displayFormat),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { viewModel.toggleDate(date) }) { Text("删除") }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthCalendar(
    month: YearMonth,
    recorded: Set<LocalDate>,
    predictedStart: LocalDate?,
    onToggle: (LocalDate) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    val today = LocalDate.now()
    ToolSectionCard(title = month.format(monthFormat)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onPrev) { Text("上月") }
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
        // 周一为第一列
        val leading = (firstDay.dayOfWeek.value + 6) % 7
        val cells = leading + month.lengthOfMonth()
        val rows = (cells + 6) / 7

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(rows) { row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    repeat(7) { col ->
                        val dayIndex = row * 7 + col - leading
                        if (dayIndex < 0 || dayIndex >= month.lengthOfMonth()) {
                            Box(Modifier.weight(1f).aspectRatio(1f))
                        } else {
                            val date = month.atDay(dayIndex + 1)
                            DayCell(
                                date = date,
                                isToday = date == today,
                                isRecorded = date in recorded,
                                isPredicted = date == predictedStart,
                                onClick = { onToggle(date) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LegendDot(MaterialTheme.colorScheme.primary, "已记录")
            LegendDot(MaterialTheme.colorScheme.outline, "预测")
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isToday: Boolean,
    isRecorded: Boolean,
    isPredicted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = when {
        isRecorded -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = when {
        isRecorded -> MaterialTheme.colorScheme.onPrimary
        isPredicted -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = modifier
            .padding(2.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(container)
            .then(
                if (isPredicted && !isRecorded) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = if (isToday || isRecorded) FontWeight.Bold else FontWeight.Normal,
            ),
            color = content,
        )
    }
}

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.clip(RoundedCornerShape(100)).background(color).padding(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
