package com.flechazo.toolbox.feature.base64

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flechazo.toolbox.core.designsystem.components.BottomActionBar
import com.flechazo.toolbox.core.designsystem.components.FeedbackBlock
import com.flechazo.toolbox.core.designsystem.components.FeedbackType
import com.flechazo.toolbox.core.designsystem.components.ResultCard
import com.flechazo.toolbox.core.designsystem.components.SegmentedTabs
import com.flechazo.toolbox.core.designsystem.components.ToolTextField
import com.flechazo.toolbox.core.designsystem.components.ToolScaffold
import com.flechazo.toolbox.core.designsystem.components.ToolSectionCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Base64
import javax.inject.Inject

data class Base64UiState(
    val mode: Mode = Mode.ENCODE,
    val urlSafe: Boolean = false,
    val input: String = "",
    val output: String = "",
    val failed: Boolean = false,
) {
    enum class Mode(val label: String) { ENCODE("编码"), DECODE("解码") }
}

@HiltViewModel
class Base64ViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(Base64UiState())
    val state: StateFlow<Base64UiState> = _state

    fun setMode(mode: Base64UiState.Mode) {
        val s = _state.value
        if (mode == s.mode) return
        // 切换模式时把上一次的结果带到输入框，方便编解码往返；没有结果则保留原输入
        val nextInput = s.output.ifEmpty { s.input }
        _state.value = recompute(s.copy(mode = mode, input = nextInput, output = ""))
    }

    fun setUrlSafe(enabled: Boolean) {
        val s = _state.value.copy(urlSafe = enabled)
        _state.value = recompute(s)
    }

    fun setInput(value: String) {
        _state.value = recompute(_state.value.copy(input = value))
    }

    fun clear() {
        _state.value = Base64UiState(mode = _state.value.mode, urlSafe = _state.value.urlSafe)
    }

    private fun recompute(s: Base64UiState): Base64UiState {
        if (s.input.isEmpty()) return s.copy(output = "", failed = false)
        return try {
            val output = when (s.mode) {
                Base64UiState.Mode.ENCODE ->
                    if (s.urlSafe) {
                        Base64.getUrlEncoder().encodeToString(s.input.toByteArray(Charsets.UTF_8))
                    } else {
                        Base64.getEncoder().encodeToString(s.input.toByteArray(Charsets.UTF_8))
                    }
                Base64UiState.Mode.DECODE -> {
                    val decoder = if (s.urlSafe) Base64.getUrlDecoder() else Base64.getDecoder()
                    String(decoder.decode(s.input.trim()), Charsets.UTF_8)
                }
            }
            s.copy(output = output, failed = false)
        } catch (_: Exception) {
            s.copy(output = "", failed = true)
        }
    }
}

@Composable
fun Base64Screen(onBack: () -> Unit, viewModel: Base64ViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    ToolScaffold(
        title = "Base64 编解码",
        onBack = onBack,
        subtitle = "输入即时转换，切换模式自动交换输入与输出",
    ) {

        SegmentedTabs(
            options = Base64UiState.Mode.entries.toList(),
            selected = state.mode,
            label = { it.label },
            onSelect = viewModel::setMode,
        )

        // 输入
        ToolSectionCard(
            title = "输入",
            trailing = {
                TextButton(onClick = viewModel::clear) { Text("清空") }
            },
        ) {
            ToolTextField(
                value = state.input,
                onValueChange = viewModel::setInput,
                label = { Text(if (state.mode == Base64UiState.Mode.ENCODE) "原文" else "Base64 字符串") },
                minLines = 4,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth(),
            )
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(
                    "${state.input.length} 字符 · ${state.input.toByteArray(Charsets.UTF_8).size} 字节",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                androidx.compose.material3.Switch(
                    checked = state.urlSafe,
                    onCheckedChange = viewModel::setUrlSafe,
                )
                Text(
                    "URL 安全",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // 结果
        if (state.failed) {
            FeedbackBlock(
                text = if (state.mode == Base64UiState.Mode.ENCODE) "无法编码当前内容" else "不是有效的 Base64 字符串",
                type = FeedbackType.ERROR,
            )
        } else if (state.output.isNotEmpty()) {
            ResultCard(
                label = if (state.mode == Base64UiState.Mode.ENCODE) "Base64 编码结果" else "解码结果",
                value = state.output,
                caption = "${state.output.length} 字符 · ${state.output.toByteArray(Charsets.UTF_8).size} 字节 · 点击复制",
                onClick = { clipboard.setText(AnnotatedString(state.output)) },
                // 多行结果必须完整可见，否则长文本会被省略号截断
                singleLine = false,
            )
            BottomActionBar(
                primaryLabel = "复制结果",
                onPrimary = { clipboard.setText(AnnotatedString(state.output)) },
                secondaryLabel = "清空",
                onSecondary = viewModel::clear,
            )
        }
    }
}
