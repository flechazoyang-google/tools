package com.flechazo.toolbox.feature.calculator

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flechazo.toolbox.core.designsystem.components.KeyValueRow
import com.flechazo.toolbox.core.designsystem.components.ResultCard
import com.flechazo.toolbox.core.designsystem.components.ToolScaffold
import com.flechazo.toolbox.core.designsystem.components.ToolSectionCard
import com.flechazo.toolbox.core.designsystem.theme.ToolShape
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

data class CalculatorUiState(
    val expression: String = "",
    val preview: String = "",
    val error: Boolean = false,
    val history: List<String> = emptyList(),
)

@HiltViewModel
class CalculatorViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(CalculatorUiState())
    val state: StateFlow<CalculatorUiState> = _state

    fun onKey(key: String) {
        val expr = _state.value.expression
        if (key == "=") {
            evaluate()
            return
        }
        val next = when (key) {
            "AC" -> ""
            "DEL" -> expr.dropLast(1)
            else -> expr + key
        }
        updateExpression(next)
    }

    fun clearHistory() {
        _state.value = _state.value.copy(history = emptyList())
    }

    private fun updateExpression(expression: String) {
        val preview = try {
            if (expression.isBlank()) "" else {
                ExpressionEvaluator.format(ExpressionEvaluator.evaluate(expression))
            }
        } catch (_: Exception) {
            ""
        }
        // 任何新输入都清除错误态
        _state.value = _state.value.copy(expression = expression, preview = preview, error = false)
    }

    private fun evaluate() {
        val expr = _state.value.expression
        if (expr.isBlank()) return
        val result = runCatching { ExpressionEvaluator.format(ExpressionEvaluator.evaluate(expr)) }.getOrNull()
        if (result == null) {
            // 保留原表达式，只置错误态；旧实现把表达式本身覆盖成"错误"，后续按键会继续拼接
            _state.value = _state.value.copy(error = true, preview = "")
        } else {
            _state.value = _state.value.copy(
                expression = result,
                preview = "",
                error = false,
                history = (listOf("$expr = $result") + _state.value.history).take(10),
            )
        }
    }
}

private data class CalcKey(val label: String, val weight: Float = 1f)

/** 6 行 × 4 列，含括号键；0 与 = 加宽。 */
private val keys: List<List<CalcKey>> = listOf(
    listOf(CalcKey("AC"), CalcKey("DEL"), CalcKey("("), CalcKey(")")),
    listOf(CalcKey("%"), CalcKey("^"), CalcKey("/"), CalcKey("*")),
    listOf(CalcKey("7"), CalcKey("8"), CalcKey("9"), CalcKey("-")),
    listOf(CalcKey("4"), CalcKey("5"), CalcKey("6"), CalcKey("+")),
    listOf(CalcKey("1"), CalcKey("2"), CalcKey("3"), CalcKey(".")),
    listOf(CalcKey("0", 2f), CalcKey("=", 2f)),
)

@Composable
fun CalculatorScreen(onBack: () -> Unit, viewModel: CalculatorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ToolScaffold(
        title = "计算器",
        onBack = onBack,
        subtitle = "支持 + - × ÷ ^、括号与百分号（如 200×10% = 20）",
        // 键盘必须固定在底部，所以外层不能整体滚动
        scrollable = false,
    ) {
        // 显示区 + 历史：占据键盘以上的剩余空间，内容多时自己滚动
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 表达式 + 实时预览
            ResultCard(
                label = "表达式",
                value = state.expression.ifEmpty { "0" },
                caption = when {
                    state.error -> "表达式无效，请检查后重试"
                    state.preview.isNotEmpty() -> "= ${state.preview}"
                    else -> null
                },
                singleLine = false,
            )

            if (state.history.isNotEmpty()) {
                ToolSectionCard(
                    title = "历史记录",
                    trailing = { TextButton(onClick = viewModel::clearHistory) { Text("清空") } },
                ) {
                    state.history.forEach { line ->
                        KeyValueRow(
                            label = line.substringBefore(" = "),
                            value = line.substringAfter(" = "),
                        )
                    }
                }
            }
        }

        // 按键区：始终贴底
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            keys.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { key ->
                        CalculatorKey(
                            label = key.label,
                            modifier = Modifier.weight(key.weight).height(52.dp),
                            onClick = { viewModel.onKey(key.label) },
                            onLongClick = if (key.label == "DEL") {
                                { viewModel.onKey("AC") }
                            } else null,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CalculatorKey(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    // 四档键色：数字 / 运算符 / AC·DEL / =
    val container = when {
        label == "=" -> MaterialTheme.colorScheme.primary
        label in setOf("+", "-", "*", "/", "^", "%") -> MaterialTheme.colorScheme.secondaryContainer
        label in setOf("AC", "DEL") -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val content = when {
        label == "=" -> MaterialTheme.colorScheme.onPrimary
        label in setOf("AC", "DEL") -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .clip(ToolShape.lg)
            .background(container)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 20.sp,
            color = content,
            textAlign = TextAlign.Center,
        )
    }
}
