package com.flechazo.toolbox.feature.pomodoro

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.flechazo.toolbox.core.data.PomodoroStats
import com.flechazo.toolbox.core.data.PomodoroStatsRepository
import com.flechazo.toolbox.core.designsystem.components.KeyValueRow
import com.flechazo.toolbox.core.designsystem.components.SegmentedTabs
import com.flechazo.toolbox.core.designsystem.components.ToolScaffold
import com.flechazo.toolbox.core.designsystem.components.ToolSectionCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PomodoroPhase(val label: String, val minutes: Int) {
    WORK("专注", 25),
    SHORT_BREAK("短休息", 5),
    LONG_BREAK("长休息", 15),
}

data class PomodoroUiState(
    val phase: PomodoroPhase = PomodoroPhase.WORK,
    val remainingSec: Int = PomodoroPhase.WORK.minutes * 60,
    val running: Boolean = false,
    val stats: PomodoroStats = PomodoroStats(),
) {
    val totalSec: Int get() = phase.minutes * 60
}

/**
 * 由截止时间推算剩余秒数（向上取整）。
 *
 * 旧实现用 `delay(1000)` 递减计数，任何调度延迟都会永久丢失时间；
 * 改为锚定 [SystemClock.elapsedRealtime] 后，剩余时间永远与真实经过时间一致。
 */
internal fun remainingSeconds(deadlineElapsedMs: Long, nowElapsedMs: Long): Int {
    val delta = deadlineElapsedMs - nowElapsedMs
    if (delta <= 0L) return 0
    return ((delta + 999L) / 1000L).toInt()
}

@HiltViewModel
class PomodoroViewModel @Inject constructor(
    private val statsRepository: PomodoroStatsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PomodoroUiState())
    val state: StateFlow<PomodoroUiState> = _state

    private var ticker: Job? = null
    private var deadlineElapsedMs: Long = 0L

    init {
        viewModelScope.launch {
            statsRepository.stats.collect { stats ->
                _state.value = _state.value.copy(stats = stats)
            }
        }
    }

    fun toggle() {
        if (_state.value.running) pause() else start()
    }

    fun start() {
        if (_state.value.running) return
        deadlineElapsedMs = SystemClock.elapsedRealtime() + _state.value.remainingSec * 1000L
        _state.value = _state.value.copy(running = true)
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (isActive) {
                val remaining = remainingSeconds(deadlineElapsedMs, SystemClock.elapsedRealtime())
                if (remaining <= 0) {
                    advancePhase()
                    if (!_state.value.running) break
                    deadlineElapsedMs = SystemClock.elapsedRealtime() + _state.value.remainingSec * 1000L
                } else {
                    if (remaining != _state.value.remainingSec) {
                        _state.value = _state.value.copy(remainingSec = remaining)
                    }
                    delay(200)
                }
            }
        }
    }

    fun pause() {
        ticker?.cancel()
        ticker = null
        // 暂停时按真实剩余时间落盘，恢复后从同一位置继续
        val remaining = remainingSeconds(deadlineElapsedMs, SystemClock.elapsedRealtime())
        _state.value = _state.value.copy(
            running = false,
            remainingSec = if (remaining > 0) remaining else _state.value.remainingSec,
        )
    }

    fun reset() {
        ticker?.cancel()
        ticker = null
        _state.value = _state.value.copy(
            remainingSec = _state.value.phase.minutes * 60,
            running = false,
        )
    }

    /** 手动切换阶段（会停止计时）。 */
    fun setPhase(phase: PomodoroPhase) {
        ticker?.cancel()
        ticker = null
        _state.value = _state.value.copy(
            phase = phase,
            remainingSec = phase.minutes * 60,
            running = false,
        )
    }

    private fun advancePhase() {
        val s = _state.value
        val finishedWork = s.phase == PomodoroPhase.WORK
        val nextPhase = when (s.phase) {
            PomodoroPhase.WORK -> {
                val completed = s.stats.cycles + 1
                if (completed % 4 == 0) PomodoroPhase.LONG_BREAK else PomodoroPhase.SHORT_BREAK
            }
            PomodoroPhase.SHORT_BREAK, PomodoroPhase.LONG_BREAK -> PomodoroPhase.WORK
        }
        // 阶段自动衔接，不再停在边界上
        _state.value = s.copy(
            phase = nextPhase,
            remainingSec = nextPhase.minutes * 60,
            running = true,
        )
        if (finishedWork) {
            viewModelScope.launch { statsRepository.recordCompletedWork(PomodoroPhase.WORK.minutes) }
        }
    }

    override fun onCleared() {
        ticker?.cancel()
        super.onCleared()
    }
}

@Composable
fun PomodoroScreen(onBack: () -> Unit, viewModel: PomodoroViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val totalSec = state.totalSec
    val progress by animateFloatAsState(
        targetValue = 1f - state.remainingSec.toFloat() / totalSec,
        animationSpec = tween(600),
        label = "progress",
    )
    val ringColor = when (state.phase) {
        PomodoroPhase.WORK -> MaterialTheme.colorScheme.primary
        PomodoroPhase.SHORT_BREAK -> MaterialTheme.colorScheme.tertiary
        PomodoroPhase.LONG_BREAK -> MaterialTheme.colorScheme.secondary
    }

    ToolScaffold(
        title = "番茄钟",
        subtitle = "25 分钟专注 + 5 分钟短休，每 4 个循环进入长休息",
        onBack = onBack,
    ) {
        ToolSectionCard(title = "${state.phase.label} · 今日已完成 ${state.stats.cycles} 个") {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                RingProgress(progress = progress, ringColor = ringColor, phaseLabel = state.phase.label) {
                    Text(
                        "%02d:%02d".format(state.remainingSec / 60, state.remainingSec % 60),
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 48.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            // 今日完成轮数指示点（每 4 个一组）
            val filledInSet = if (state.stats.cycles > 0 && state.stats.cycles % 4 == 0) {
                4
            } else {
                state.stats.cycles % 4
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(4) { index ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(10.dp)
                            .clip(RoundedCornerShape(100))
                            .background(
                                if (index < filledInSet) {
                                    ringColor
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                },
                            ),
                    )
                }
            }
        }

        ToolSectionCard(title = "阶段") {
            SegmentedTabs(
                options = PomodoroPhase.entries,
                selected = state.phase,
                label = { it.label },
                onSelect = viewModel::setPhase,
            )
        }

        ToolSectionCard(title = "今日统计") {
            KeyValueRow(label = "完成专注", value = "${state.stats.cycles} 个")
            KeyValueRow(label = "专注时长", value = "${state.stats.focusMinutes} 分钟")
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = viewModel::toggle,
                modifier = Modifier.weight(1f).height(52.dp),
            ) {
                Text(
                    when {
                        state.running -> "暂停"
                        state.remainingSec == totalSec -> "开始"
                        else -> "继续"
                    },
                )
            }
            OutlinedButton(
                onClick = viewModel::reset,
                modifier = Modifier.height(52.dp),
            ) { Text("重置") }
        }
    }
}

@Composable
private fun RingProgress(
    progress: Float,
    ringColor: Color,
    phaseLabel: String,
    content: @Composable () -> Unit,
) {
    Box(contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(240.dp)) {
            val stroke = Stroke(width = 20f, cap = StrokeCap.Round)
            val inset = stroke.width / 2 + 4f
            drawArc(
                color = ringColor.copy(alpha = 0.15f),
                startAngle = -90f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - inset * 2, size.height - inset * 2),
                style = stroke,
            )
            drawArc(
                color = ringColor,
                startAngle = -90f, sweepAngle = 360f * progress.coerceIn(0f, 1f), useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - inset * 2, size.height - inset * 2),
                style = stroke,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(phaseLabel, style = MaterialTheme.typography.labelMedium, color = ringColor)
            content()
        }
    }
}
