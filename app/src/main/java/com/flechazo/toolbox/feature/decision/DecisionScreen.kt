package com.flechazo.toolbox.feature.decision

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate as drawRotate
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.flechazo.toolbox.core.designsystem.components.BottomActionBar
import com.flechazo.toolbox.core.designsystem.components.KeyValueRow
import com.flechazo.toolbox.core.designsystem.components.ResultCard
import com.flechazo.toolbox.core.designsystem.components.SegmentedTabs
import com.flechazo.toolbox.core.designsystem.components.ToolTextField
import com.flechazo.toolbox.core.designsystem.components.ToolScaffold
import com.flechazo.toolbox.core.designsystem.components.ToolSectionCard
import com.flechazo.toolbox.core.designsystem.theme.ToolShape
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import javax.inject.Inject

enum class DecisionMode(val label: String) {
    WHEEL("转盘"), DRAW("抽签"), NUMBER("随机数"),
}

data class DecisionUiState(
    val mode: DecisionMode = DecisionMode.WHEEL,
    val options: List<String> = listOf("是", "否", "再想想"),
    val result: String = "",
    val isSpinning: Boolean = false,
    val rotation: Float = 0f,
    val minNumber: String = "1",
    val maxNumber: String = "100",
    val history: List<String> = emptyList(),
)

/** 与转盘动画时长一致；由 ViewModel 计时兜底，避免动画回调丢失导致按钮永久禁用。 */
private const val SPIN_DURATION_MS = 3000L

@HiltViewModel
class DecisionViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(DecisionUiState())
    val state: StateFlow<DecisionUiState> = _state

    private var spinJob: Job? = null

    fun setMode(mode: DecisionMode) {
        spinJob?.cancel()
        _state.value = _state.value.copy(mode = mode, isSpinning = false, result = "")
    }

    fun addOption(option: String) {
        val trimmed = option.trim()
        if (trimmed.isNotEmpty() && !_state.value.options.contains(trimmed)) {
            _state.value = _state.value.copy(options = _state.value.options + trimmed)
        }
    }

    fun removeOption(index: Int) {
        val current = _state.value.options
        if (current.size > 2 && index in current.indices) {
            _state.value = _state.value.copy(
                options = current.filterIndexed { i, _ -> i != index },
                result = "",
            )
        }
    }

    fun setMinNumber(v: String) { _state.value = _state.value.copy(minNumber = v.filter { it.isDigit() }) }
    fun setMaxNumber(v: String) { _state.value = _state.value.copy(maxNumber = v.filter { it.isDigit() }) }

    fun spin() {
        val s = _state.value
        if (s.isSpinning || s.options.size < 2) return
        val target = s.rotation + 720f + Random.nextFloat() * 360f
        _state.value = s.copy(isSpinning = true, rotation = target, result = "")
        spinJob?.cancel()
        spinJob = viewModelScope.launch {
            delay(SPIN_DURATION_MS)
            completeSpin()
        }
    }

    private fun completeSpin() {
        val s = _state.value
        if (!s.isSpinning) return
        val n = s.options.size
        if (n == 0) {
            _state.value = s.copy(isSpinning = false)
            return
        }
        val normalized = (s.rotation % 360f + 360f) % 360f
        val segment = 360f / n
        val index = ((360f - normalized) / segment).toInt().coerceIn(0, n - 1)
        publish(s.options[index])
    }

    /** 抽签：直接随机取一项（带一次短暂延迟给用户"正在抽"的反馈）。 */
    fun draw() {
        val s = _state.value
        if (s.isSpinning || s.options.isEmpty()) return
        _state.value = s.copy(isSpinning = true, result = "")
        spinJob?.cancel()
        spinJob = viewModelScope.launch {
            delay(600)
            val current = _state.value
            publish(current.options.randomOrNull() ?: "")
        }
    }

    fun rollNumber() {
        val s = _state.value
        val min = s.minNumber.toIntOrNull() ?: 1
        val max = s.maxNumber.toIntOrNull() ?: 100
        if (min > max) {
            _state.value = s.copy(result = "", history = listOf("区间无效：最小值大于最大值") + s.history)
            return
        }
        publish(Random.nextInt(min, max + 1).toString())
    }

    fun reset() {
        spinJob?.cancel()
        _state.value = DecisionUiState(
            mode = _state.value.mode,
            minNumber = _state.value.minNumber,
            maxNumber = _state.value.maxNumber,
        )
    }

    private fun publish(value: String) {
        val s = _state.value
        _state.value = s.copy(
            isSpinning = false,
            result = value,
            history = (listOf(value) + s.history).take(10),
        )
    }
}

private val WheelColors = listOf(
    0xFFEF5350, 0xFFEC407A, 0xFFAB47BC, 0xFF7E57C2,
    0xFF5C6BC0, 0xFF42A5F5, 0xFF29B6F6, 0xFF26C6DA,
)

@Composable
fun DecisionScreen(onBack: () -> Unit, viewModel: DecisionViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var newOption by remember { mutableStateOf("") }

    val rotationAnim = animateFloatAsState(
        targetValue = state.rotation,
        animationSpec = tween(SPIN_DURATION_MS.toInt(), easing = FastOutSlowInEasing),
        label = "wheel",
    )

    ToolScaffold(
        title = "做个决定",
        onBack = onBack,
        subtitle = "转盘 / 抽签 / 随机数，让命运帮你选",
    ) {
        SegmentedTabs(
            options = DecisionMode.entries,
            selected = state.mode,
            label = { it.label },
            onSelect = viewModel::setMode,
        )

        if (state.mode == DecisionMode.WHEEL) {
            WheelView(state = state, rotation = rotationAnim.value)
        }

        if (state.mode == DecisionMode.NUMBER) {
            ToolSectionCard(title = "随机数区间") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToolTextField(
                        value = state.minNumber,
                        onValueChange = viewModel::setMinNumber,
                        label = { Text("最小值") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    ToolTextField(
                        value = state.maxNumber,
                        onValueChange = viewModel::setMaxNumber,
                        label = { Text("最大值") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        if (state.result.isNotEmpty() && !state.isSpinning) {
            ResultCard(label = "结果", value = state.result)
        }

        if (state.mode != DecisionMode.NUMBER) {
            ToolSectionCard(title = "选项列表（至少 2 项）") {
                state.options.forEachIndexed { index, option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(ToolShape.xs)
                                .background(Color(WheelColors[index % WheelColors.size])),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            option,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        if (state.options.size > 2) {
                            IconButton(
                                onClick = { viewModel.removeOption(index) },
                                modifier = Modifier.size(44.dp),
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "删除 $option")
                            }
                        }
                    }
                }

                ToolTextField(
                    value = newOption,
                    onValueChange = { newOption = it },
                    label = { Text("添加选项") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                viewModel.addOption(newOption)
                                newOption = ""
                            },
                            enabled = newOption.isNotBlank(),
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "添加")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (state.history.isNotEmpty()) {
            ToolSectionCard(title = "最近结果") {
                state.history.forEach { KeyValueRow(label = it, value = "") }
            }
        }

        BottomActionBar(
            primaryLabel = when {
                state.isSpinning -> "转动中…"
                state.mode == DecisionMode.DRAW -> "抽签"
                state.mode == DecisionMode.NUMBER -> "随机取数"
                else -> "开始转盘"
            },
            onPrimary = {
                when (state.mode) {
                    DecisionMode.WHEEL -> viewModel.spin()
                    DecisionMode.DRAW -> viewModel.draw()
                    DecisionMode.NUMBER -> viewModel.rollNumber()
                }
            },
            enabled = !state.isSpinning && (
                state.mode == DecisionMode.NUMBER || state.options.size >= 2
                ),
            secondaryLabel = "重置",
            onSecondary = viewModel::reset,
        )
    }
}

@Composable
private fun WheelView(state: DecisionUiState, rotation: Float) {
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .rotate(rotation),
        ) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension / 2
            val segmentAngle = 360f / state.options.size

            state.options.forEachIndexed { index, option ->
                val startAngle = index * segmentAngle - 90f
                drawArc(
                    color = Color(WheelColors[index % WheelColors.size]),
                    startAngle = startAngle,
                    sweepAngle = segmentAngle,
                    useCenter = true,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                )

                // 沿半径方向绘制选项文字（旧实现只算了坐标，从未真正绘制）
                val layout = measurer.measure(AnnotatedString(option), style = labelStyle)
                drawRotate(
                    degrees = index * segmentAngle + segmentAngle / 2,
                    pivot = center,
                ) {
                    drawText(
                        textLayoutResult = layout,
                        topLeft = Offset(
                            center.x + radius * 0.30f,
                            center.y - layout.size.height / 2f,
                        ),
                    )
                }
            }

            drawCircle(
                color = Color.Black.copy(alpha = 0.15f),
                radius = radius,
                center = center,
                style = Stroke(width = 4f),
            )
        }

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(ToolShape.full)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Text("?", style = MaterialTheme.typography.headlineMedium)
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2
            drawLine(
                color = Color.White,
                start = Offset(centerX, 0f),
                end = Offset(centerX, 24f),
                strokeWidth = 4f,
                cap = StrokeCap.Round,
            )
        }
    }
}
