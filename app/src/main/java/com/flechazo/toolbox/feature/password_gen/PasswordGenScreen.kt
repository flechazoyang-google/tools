package com.flechazo.toolbox.feature.password_gen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flechazo.toolbox.core.designsystem.components.ResultCard
import com.flechazo.toolbox.core.designsystem.components.ToolScaffold
import com.flechazo.toolbox.core.designsystem.components.ToolSectionCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.SecureRandom
import javax.inject.Inject

data class PasswordGenUiState(
    val length: Int = 16,
    val useUpper: Boolean = true,
    val useLower: Boolean = true,
    val useDigits: Boolean = true,
    val useSymbols: Boolean = true,
    val password: String = "",
)

@HiltViewModel
class PasswordGenViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(PasswordGenUiState())
    val state: StateFlow<PasswordGenUiState> = _state
    private val random = SecureRandom()

    init {
        generate()
    }

    fun setLength(v: Int) { _state.value = _state.value.copy(length = v); generate() }
    fun setUpper(v: Boolean) { _state.value = _state.value.copy(useUpper = v); generate() }
    fun setLower(v: Boolean) { _state.value = _state.value.copy(useLower = v); generate() }
    fun setDigits(v: Boolean) { _state.value = _state.value.copy(useDigits = v); generate() }
    fun setSymbols(v: Boolean) { _state.value = _state.value.copy(useSymbols = v); generate() }

    fun generate() {
        val s = _state.value
        var pool = ""
        if (s.useUpper) pool += "ABCDEFGHJKLMNPQRSTUVWXYZ"
        if (s.useLower) pool += "abcdefghijkmnpqrstuvwxyz"
        if (s.useDigits) pool += "23456789"
        if (s.useSymbols) pool += "!@#\$%^&*-_=+?"
        if (pool.isEmpty()) {
            _state.value = s.copy(password = "请至少选择一种字符集")
            return
        }
        val pw = buildString { repeat(s.length) { append(pool[random.nextInt(pool.length)]) } }
        _state.value = s.copy(password = pw)
    }
}

@Composable
fun PasswordGenScreen(onBack: () -> Unit, viewModel: PasswordGenViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    ToolScaffold(
        title = "密码生成器",
        subtitle = "基于本地 SecureRandom，已剔除易混淆字符",
        onBack = onBack,
    ) {
        // 结果区
        ResultCard(
            label = "生成结果",
            value = state.password,
            caption = "长度 ${state.length} 位 · 点击「重新生成」刷新",
        )

        // 操作区
        ToolSectionCard(title = "操作") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::generate, modifier = Modifier.weight(1f)) { Text("重新生成") }
                OutlinedButton(
                    onClick = { clipboard.setText(AnnotatedString(state.password)) },
                    modifier = Modifier.weight(1f),
                ) { Text("复制") }
            }
        }

        // 配置区
        ToolSectionCard(title = "配置") {
            Text("长度：${state.length} 位", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = state.length.toFloat(),
                onValueChange = { viewModel.setLength(it.toInt()) },
                valueRange = 6f..32f,
                steps = 25,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            CheckRow("大写字母（去易混淆 I O）", state.useUpper, viewModel::setUpper)
            CheckRow("小写字母（去易混淆 l o）", state.useLower, viewModel::setLower)
            CheckRow("数字（去易混淆 0 1）", state.useDigits, viewModel::setDigits)
            CheckRow("特殊符号", state.useSymbols, viewModel::setSymbols)
        }
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Checkbox(checked = checked, onCheckedChange = onChange)
    }
}