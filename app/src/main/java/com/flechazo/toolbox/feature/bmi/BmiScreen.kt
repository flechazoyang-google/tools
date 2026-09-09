package com.flechazo.toolbox.feature.bmi

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flechazo.toolbox.core.designsystem.components.KeyValueRow
import com.flechazo.toolbox.core.designsystem.components.ResultCard
import com.flechazo.toolbox.core.designsystem.components.ToolTextField
import com.flechazo.toolbox.core.designsystem.components.ToolScaffold
import com.flechazo.toolbox.core.designsystem.components.ToolSectionCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import javax.inject.Inject

data class BmiUiState(
    val height: String = "170",
    val weight: String = "60",
    val bmi: Double? = null,
) {
    val category: String
        get() = when {
            bmi == null -> ""
            bmi!! < 18.5 -> "偏瘦"
            bmi!! < 24.0 -> "正常"
            bmi!! < 28.0 -> "偏胖"
            else -> "肥胖"
        }

    /** 0..3 category index for the color band. */
    val categoryIndex: Int
        get() = when {
            bmi == null -> 1
            bmi!! < 18.5 -> 0
            bmi!! < 24.0 -> 1
            bmi!! < 28.0 -> 2
            else -> 3
        }

    val advice: String
        get() {
            if (bmi == null) return ""
            val h = (height.toDoubleOrNull() ?: 170.0) / 100
            val normalMin = 18.5 * h * h
            val normalMax = 24.0 * h * h
            return String.format(Locale.ROOT, "健康体重范围：%.1f ~ %.1f kg", normalMin, normalMax)
        }
}

@HiltViewModel
class BmiViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(BmiUiState(bmi = compute("170", "60")))
    val state: StateFlow<BmiUiState> = _state

    fun onHeightChange(v: String) {
        _state.value = _state.value.copy(height = v, bmi = compute(v, _state.value.weight))
    }

    fun onWeightChange(v: String) {
        _state.value = _state.value.copy(weight = v, bmi = compute(_state.value.height, v))
    }

    private fun compute(h: String, w: String): Double? {
        val hv = h.toDoubleOrNull() ?: return null
        val wv = w.toDoubleOrNull() ?: return null
        if (hv <= 0 || hv > 250 || wv <= 0 || wv > 300) return null
        val meter = hv / 100
        return wv / (meter * meter)
    }
}

/** Four-segment category color band with a pointer at current BMI position. */
@Composable
private fun BmiBand(bmi: Double?) {
    // 全部取自主题，深色模式与动态取色下都保持可读
    val colors = listOf(
        MaterialTheme.colorScheme.tertiary,  // 偏瘦
        MaterialTheme.colorScheme.primary,   // 正常
        MaterialTheme.colorScheme.secondary, // 偏胖
        MaterialTheme.colorScheme.error,     // 肥胖
    )
    val labels = listOf("偏瘦", "正常", "偏胖", "肥胖")
    val index = when {
        bmi == null -> 1
        bmi < 18.5 -> 0
        bmi < 24.0 -> 1
        bmi < 28.0 -> 2
        else -> 3
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().height(8.dp)) {
            colors.forEachIndexed { i, c ->
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 1.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                        .background(colors[i].copy(alpha = if (i == index) 1f else 0.35f)),
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (i == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (i == index) FontWeight.Bold else null,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun BmiScreen(onBack: () -> Unit, viewModel: BmiViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ToolScaffold(
        title = "BMI 计算器",
        onBack = onBack,
        subtitle = "根据身高体重评估健康状态（中国成人标准）",
    ) {

        // 测量输入：数值 + 滑杆联动
        ToolSectionCard(title = "身高体重") {
            MeasureInput(
                label = "身高",
                value = state.height,
                unit = "cm",
                range = 100f..220f,
                onValueChange = viewModel::onHeightChange,
            )
            MeasureInput(
                label = "体重",
                value = state.weight,
                unit = "kg",
                range = 25f..150f,
                onValueChange = viewModel::onWeightChange,
            )
        }

        // 结果
        val bmi = state.bmi
        if (bmi != null) {
            ResultCard(
                label = "BMI 指数",
                value = String.format(Locale.ROOT, "%.1f", bmi),
                unit = state.category,
                caption = state.advice,
            )
            BmiBand(bmi)
        } else {
            Text(
                "请输入有效的身高体重",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // 参考标准
        ToolSectionCard(title = "中国成人 BMI 标准") {
            KeyValueRow(label = "偏瘦", value = "< 18.5")
            KeyValueRow(label = "正常", value = "18.5 ~ 24.0", emphasized = true)
            KeyValueRow(label = "偏胖（超重）", value = "24.0 ~ 28.0")
            KeyValueRow(label = "肥胖", value = "≥ 28.0")
        }
    }
}

/** Label + numeric text field + slider, both ways bound. */
@Composable
private fun MeasureInput(
    label: String,
    value: String,
    unit: String,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            ToolTextField(
                value = value,
                onValueChange = { input ->
                    val filtered = input.filter { it.isDigit() || it == '.' }.take(6)
                    onValueChange(filtered)
                },
                singleLine = true,
                suffix = { Text(unit, style = MaterialTheme.typography.labelSmall) },
                textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = androidx.compose.ui.text.style.TextAlign.End),
                modifier = Modifier
                    .width(140.dp)
                    .height(56.dp),
            )
        }
        val sliderValue = value.toFloatOrNull()?.coerceIn(range.start, range.endInclusive) ?: range.start
        Slider(
            value = sliderValue,
            onValueChange = {
                // 必须用 Locale.ROOT：逗号小数点区域会把 "65,3" 写进输入框，
                // 随后 toDoubleOrNull() 返回 null，BMI 直接消失。
                onValueChange(if (it == it.toInt().toFloat()) it.toInt().toString() else String.format(Locale.ROOT, "%.1f", it))
            },
            valueRange = range,
        )
    }
}
