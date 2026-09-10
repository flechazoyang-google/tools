package com.flechazo.toolbox.feature.text_diff

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.flechazo.toolbox.core.designsystem.components.FeedbackBlock
import com.flechazo.toolbox.core.designsystem.components.FeedbackType
import com.flechazo.toolbox.core.designsystem.components.KeyValueRow
import com.flechazo.toolbox.core.designsystem.components.ToolTextField
import com.flechazo.toolbox.core.designsystem.components.ToolScaffold
import com.flechazo.toolbox.core.designsystem.components.ToolSectionCard
import com.flechazo.toolbox.core.designsystem.theme.ToolShape
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class DiffLine(val marker: Char, val text: String) // '=' common, '+' only in B, '-' only in A

data class TextDiffUiState(
    val textA: String = "",
    val textB: String = "",
    val diff: List<DiffLine> = emptyList(),
    val computed: Boolean = false,
    val computing: Boolean = false,
    val error: String? = null,
)

/**
 * LCS 逐行 diff。纯函数，便于单测。
 *
 * 行数超过 [maxLines] 时抛错：O(n*m) 的 DP 表在 1 万行时约需 400 MB。
 */
internal fun computeDiff(textA: String, textB: String, maxLines: Int = 2_000): List<DiffLine> {
    val a = textA.lines()
    val b = textB.lines()
    if (a.size > maxLines || b.size > maxLines) {
        throw IllegalArgumentException("行数过多（上限 $maxLines 行），请分段对比")
    }
    val n = a.size
    val m = b.size
    val lcs = Array(n + 1) { IntArray(m + 1) }
    for (i in n - 1 downTo 0) {
        for (j in m - 1 downTo 0) {
            lcs[i][j] = if (a[i] == b[j]) lcs[i + 1][j + 1] + 1
            else maxOf(lcs[i + 1][j], lcs[i][j + 1])
        }
    }
    val result = mutableListOf<DiffLine>()
    var i = 0
    var j = 0
    while (i < n && j < m) {
        when {
            a[i] == b[j] -> { result.add(DiffLine('=', a[i])); i++; j++ }
            lcs[i + 1][j] >= lcs[i][j + 1] -> { result.add(DiffLine('-', a[i])); i++ }
            else -> { result.add(DiffLine('+', b[j])); j++ }
        }
    }
    while (i < n) { result.add(DiffLine('-', a[i])); i++ }
    while (j < m) { result.add(DiffLine('+', b[j])); j++ }
    return result
}

@HiltViewModel
class TextDiffViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(TextDiffUiState())
    val state: StateFlow<TextDiffUiState> = _state

    private var debounceJob: Job? = null

    fun onAChange(v: String) {
        _state.value = _state.value.copy(textA = v)
        scheduleCompute()
    }

    fun onBChange(v: String) {
        _state.value = _state.value.copy(textB = v)
        scheduleCompute()
    }

    /** 输入停止 500ms 后自动对比。 */
    private fun scheduleCompute() {
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(500)
            compute()
        }
    }

    fun compute() {
        debounceJob?.cancel()
        val a = _state.value.textA
        val b = _state.value.textB
        viewModelScope.launch {
            _state.value = _state.value.copy(computing = true, error = null)
            // DP 表可能很大，必须离开主线程，避免 ANR
            val result = withContext(Dispatchers.Default) { runCatching { computeDiff(a, b) } }
            _state.value = _state.value.copy(
                diff = result.getOrDefault(emptyList()),
                computed = true,
                computing = false,
                error = result.exceptionOrNull()?.message,
            )
        }
    }
}

@Composable
fun TextDiffScreen(onBack: () -> Unit, viewModel: TextDiffViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ToolScaffold(
        title = "文本差异对比",
        subtitle = "基于 LCS 算法的逐行对比，新增绿色 / 删除红色",
        onBack = onBack,
    ) {
        ToolSectionCard(title = "输入") {
            ToolTextField(
                value = state.textA,
                onValueChange = viewModel::onAChange,
                label = { Text("文本 A（原稿）") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            ToolTextField(
                value = state.textB,
                onValueChange = viewModel::onBChange,
                label = { Text("文本 B（修改稿）") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = viewModel::compute,
                enabled = (state.textA.isNotBlank() || state.textB.isNotBlank()) && !state.computing,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) { Text(if (state.computing) "对比中…" else "对比") }
        }

        state.error?.let { FeedbackBlock(text = it, type = FeedbackType.ERROR) }

        if (state.computed && state.error == null) {
            val added = state.diff.count { it.marker == '+' }
            val removed = state.diff.count { it.marker == '-' }
            val common = state.diff.count { it.marker == '=' }

            ToolSectionCard(title = "结果") {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    KeyValueRow(label = "总行数", value = "${state.diff.size}", modifier = Modifier.weight(1f))
                    KeyValueRow(label = "新增", value = "$added", modifier = Modifier.weight(1f))
                    KeyValueRow(label = "删除", value = "$removed", modifier = Modifier.weight(1f))
                    KeyValueRow(label = "相同", value = "$common", modifier = Modifier.weight(1f))
                }
            }

            if (state.diff.isEmpty()) {
                FeedbackBlock(text = "两段文本完全一致", type = FeedbackType.EMPTY)
            } else {
                ToolSectionCard(title = "逐行对比") {
                    // 不要在这里再套 verticalScroll：ToolScaffold 已经提供外层滚动，
                    // 嵌套滚动会导致约束异常
                    Column(Modifier.fillMaxWidth()) {
                        state.diff.forEach { line ->
                            val bg = when (line.marker) {
                                '+' -> MaterialTheme.colorScheme.primaryContainer
                                '-' -> MaterialTheme.colorScheme.errorContainer
                                else -> Color.Transparent
                            }
                            val fg = when (line.marker) {
                                '+' -> MaterialTheme.colorScheme.onPrimaryContainer
                                '-' -> MaterialTheme.colorScheme.onErrorContainer
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                            Text(
                                text = when (line.marker) {
                                    '+' -> "+ ${line.text}"
                                    '-' -> "- ${line.text}"
                                    else -> "  ${line.text}"
                                },
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                color = fg,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(ToolShape.xs)
                                    .background(bg)
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}