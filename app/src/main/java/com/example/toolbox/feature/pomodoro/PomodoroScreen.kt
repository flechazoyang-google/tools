package com.example.toolbox.feature.pomodoro

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toolbox.core.components.TopBar
import kotlinx.coroutines.delay

private const val WORK_MINUTES = 25
private const val SHORT_BREAK_MINUTES = 5
private const val LONG_BREAK_MINUTES = 15
private const val POMOS_BEFORE_LONG_BREAK = 4

@Composable
fun PomodoroScreen() {
    var phase by remember { mutableStateOf(Phase.WORK) }
    var secondsRemaining by remember { mutableIntStateOf(WORK_MINUTES * 60) }
    var isRunning by remember { mutableStateOf(false) }
    var completedPomos by remember { mutableIntStateOf(0) }
    var currentSessionPomos by remember { mutableIntStateOf(0) }

    // Timer — runs every second while isRunning is true
    LaunchedEffect(isRunning, secondsRemaining) {
        if (!isRunning || secondsRemaining <= 0) return@LaunchedEffect
        delay(1000)
        secondsRemaining--
        if (secondsRemaining == 0) {
            // Time's up — auto switch phase
            isRunning = false
            when (phase) {
                Phase.WORK -> {
                    completedPomos++
                    currentSessionPomos++
                    phase = if (currentSessionPomos % POMOS_BEFORE_LONG_BREAK == 0) {
                        Phase.LONG_BREAK
                    } else {
                        Phase.SHORT_BREAK
                    }
                    secondsRemaining = (if (currentSessionPomos % POMOS_BEFORE_LONG_BREAK == 0) LONG_BREAK_MINUTES else SHORT_BREAK_MINUTES) * 60
                }
                Phase.SHORT_BREAK, Phase.LONG_BREAK -> {
                    phase = Phase.WORK
                    secondsRemaining = WORK_MINUTES * 60
                }
            }
        }
    }

    val totalSeconds = when (phase) {
        Phase.WORK -> WORK_MINUTES * 60
        Phase.SHORT_BREAK -> SHORT_BREAK_MINUTES * 60
        Phase.LONG_BREAK -> LONG_BREAK_MINUTES * 60
    }
    val progress = if (totalSeconds > 0) 1f - secondsRemaining.toFloat() / totalSeconds else 0f

    val minutes = secondsRemaining / 60
    val secs = secondsRemaining % 60
    val timeDisplay = "%02d:%02d".format(minutes, secs)

    val phaseColor = when (phase) {
        Phase.WORK -> Color(0xFFEF5350)
        Phase.SHORT_BREAK -> Color(0xFF66BB6A)
        Phase.LONG_BREAK -> Color(0xFF42A5F5)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(title = "番茄钟")

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Phase label + timer circle
            Box(
                modifier = Modifier.size(240.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Background circle
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .clip(CircleShape)
                        .background(phaseColor.copy(alpha = 0.08f)),
                )

                // Progress ring
                androidx.compose.foundation.Canvas(modifier = Modifier.size(220.dp)) {
                    val stroke = 6.dp.toPx()
                    val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
                    val topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2)

                    // Background arc
                    drawArc(
                        color = phaseColor.copy(alpha = 0.15f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = stroke,
                            cap = StrokeCap.Round,
                        ),
                    )

                    // Progress arc
                    drawArc(
                        color = phaseColor,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = stroke,
                            cap = StrokeCap.Round,
                        ),
                    )
                }

                // Center content
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        timeDisplay,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = phaseColor,
                    )
                    Text(
                        phase.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Control buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Reset button
                IconButton(
                    onClick = {
                        isRunning = false
                        secondsRemaining = when (phase) {
                            Phase.WORK -> WORK_MINUTES * 60
                            Phase.SHORT_BREAK -> SHORT_BREAK_MINUTES * 60
                            Phase.LONG_BREAK -> LONG_BREAK_MINUTES * 60
                        }
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "重置",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }

                // Play/Pause button
                Button(
                    onClick = { isRunning = !isRunning },
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = phaseColor),
                ) {
                    Icon(
                        if (isRunning) Icons.Filled.Pause
                        else Icons.Filled.PlayArrow,
                        contentDescription = if (isRunning) "暂停" else "开始",
                        modifier = Modifier.size(28.dp),
                    )
                }

                // Skip button
                IconButton(
                    onClick = {
                        isRunning = false
                        secondsRemaining = 0
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = "跳过",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = phaseColor,
                trackColor = phaseColor.copy(alpha = 0.15f),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Stats card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "今日统计",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        StatItem("🍅", "已完成", "$completedPomos 个")
                        StatItem("⏱", "当前阶段", phase.label)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Mode quick switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Phase.entries.forEach { p ->
                    OutlinedButton(
                        onClick = {
                            if (phase != p) {
                                isRunning = false
                                phase = p
                                secondsRemaining = when (p) {
                                    Phase.WORK -> WORK_MINUTES * 60
                                    Phase.SHORT_BREAK -> SHORT_BREAK_MINUTES * 60
                                    Phase.LONG_BREAK -> LONG_BREAK_MINUTES * 60
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = if (phase == p) ButtonDefaults.buttonColors(
                            containerColor = phaseColor.copy(alpha = 0.15f),
                        ) else ButtonDefaults.outlinedButtonColors(),
                    ) {
                        Text(p.shortLabel, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private enum class Phase(val label: String, val shortLabel: String) {
    WORK("专注时间", "专注"),
    SHORT_BREAK("短休息", "短休"),
    LONG_BREAK("长休息", "长休"),
}

@Composable
private fun StatItem(emoji: String, label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 20.sp)
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 2.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
