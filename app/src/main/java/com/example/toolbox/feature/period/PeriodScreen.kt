package com.example.toolbox.feature.period

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.toolbox.core.components.TopBar
import com.example.toolbox.data.local.datastore.PeriodRecord
import java.util.Calendar
import java.util.Locale

private val PeriodColor = Color(0xFFE91E63)
private val PeriodColorLight = Color(0xFFFCE4EC)
private val PredictedDotColor = Color(0xFFF8BBD0)

@Composable
fun PeriodScreen(
    viewModel: PeriodViewModel = hiltViewModel(),
) {
    val periods by viewModel.periods.collectAsState()
    var calendarYear by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var calendarMonth by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.MONTH)) }
    val todayMs = remember { PeriodViewModel.startOfDay(System.currentTimeMillis()) }
    val calendar = remember { Calendar.getInstance(Locale.CHINA) }

    val stats = remember(periods) { computeStats(periods, todayMs) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(title = "经期记录")

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Status card
            StatusCard(stats)

            // Calendar header
            CalendarHeader(
                year = calendarYear,
                month = calendarMonth,
                onPrev = {
                    if (calendarMonth == 0) {
                        calendarYear--
                        calendarMonth = 11
                    } else {
                        calendarMonth--
                    }
                },
                onNext = {
                    if (calendarMonth == 11) {
                        calendarYear++
                        calendarMonth = 0
                    } else {
                        calendarMonth++
                    }
                },
            )

            // Calendar grid
            CalendarGrid(
                year = calendarYear,
                month = calendarMonth,
                todayMs = todayMs,
                periods = periods,
                predictedStart = stats.nextPredictedStart,
                predictedEnd = stats.nextPredictedEnd,
                onDayClick = { viewModel.toggleDate(it) },
            )

            // Legend
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LegendItem(color = PeriodColor, label = "经期")
                LegendItem(color = PredictedDotColor, label = "预测")
                Text(
                    "●",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "今日",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Info card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "使用说明",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "• 点击日历上的日期标记/取消经期\n" +
                        "• 系统会根据记录自动预测下次经期\n" +
                        "• 预测基于最近3次周期的平均值",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StatusCard(stats: PeriodStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = PeriodColorLight,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatusItem("周期", "${stats.cycleLengthAvg} 天")
            StatusItem("经期", "${stats.periodLengthAvg} 天")
            if (stats.daysUntilNext != null) {
                StatusItem(
                    if (stats.daysUntilNext <= 0) "已推迟" else "距下次",
                    if (stats.daysUntilNext <= 0) "${-stats.daysUntilNext} 天" else "${stats.daysUntilNext} 天",
                )
            } else {
                StatusItem("距下次", "—")
            }
        }
    }
}

@Composable
private fun StatusItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = PeriodColor,
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = PeriodColor.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun CalendarHeader(
    year: Int,
    month: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    val monthNames = arrayOf(
        "一月", "二月", "三月", "四月", "五月", "六月",
        "七月", "八月", "九月", "十月", "十一月", "十二月",
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "上月")
        }
        Text(
            "${year}年 ${monthNames[month]}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "下月")
        }
    }
}

@Composable
private fun CalendarGrid(
    year: Int,
    month: Int,
    todayMs: Long,
    periods: List<PeriodRecord>,
    predictedStart: Long?,
    predictedEnd: Long?,
    onDayClick: (Long) -> Unit,
) {
    val cal = remember { Calendar.getInstance(Locale.CHINA) }
    cal.set(year, month, 1)
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDayOfWeek = (cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY + 7) % 7 // 0=Sun
    val today = PeriodViewModel.startOfDay(todayMs)

    // Day-of-week header
    val dayNames = listOf("日", "一", "二", "三", "四", "五", "六")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Weekday header row
            Row(modifier = Modifier.fillMaxWidth()) {
                dayNames.forEach { name ->
                    Text(
                        text = name,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Day cells
            val totalCells = firstDayOfWeek + daysInMonth
            val rows = (totalCells + 6) / 7

            for (row in 0 until rows) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (col in 0..6) {
                        val cellIndex = row * 7 + col
                        val day = cellIndex - firstDayOfWeek + 1

                        if (day in 1..daysInMonth) {
                            cal.set(year, month, day)
                            val dayMs = PeriodViewModel.startOfDay(cal.timeInMillis)
                            val isToday = dayMs == today
                            val isPeriod = periods.any { dayMs in it.startDateMillis..it.endDateMillis }
                            val isPredicted = predictedStart != null && predictedEnd != null &&
                                dayMs in predictedStart..predictedEnd &&
                                !isPeriod

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .padding(2.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .then(
                                            if (isPeriod) Modifier
                                                .clip(CircleShape)
                                                .background(PeriodColor)
                                            else Modifier
                                        )
                                        .then(
                                            if (isToday && !isPeriod) Modifier
                                                .clip(CircleShape)
                                                .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                            else Modifier
                                        )
                                        .clickable { onDayClick(dayMs) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "$day",
                                        fontSize = 13.sp,
                                        fontWeight = if (isPeriod || isToday) FontWeight.Bold else FontWeight.Normal,
                                        color = when {
                                            isPeriod -> Color.White
                                            isPredicted -> PredictedDotColor.copy(alpha = 0.8f)
                                            else -> MaterialTheme.colorScheme.onSurface
                                        },
                                        textAlign = TextAlign.Center,
                                    )
                                    // Predicted dot indicator
                                    if (isPredicted && !isPeriod) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .padding(bottom = 1.dp)
                                                .size(4.dp)
                                                .clip(CircleShape)
                                                .background(PredictedDotColor),
                                        )
                                    }
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(modifier = Modifier.size(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
