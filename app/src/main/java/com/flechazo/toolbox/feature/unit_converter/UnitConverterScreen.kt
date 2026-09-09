package com.flechazo.toolbox.feature.unit_converter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flechazo.toolbox.core.designsystem.components.LabeledDropdown
import com.flechazo.toolbox.core.designsystem.components.ResultCard
import com.flechazo.toolbox.core.designsystem.components.SegmentedTabs
import com.flechazo.toolbox.core.designsystem.components.ToolTextField
import com.flechazo.toolbox.core.designsystem.components.ToolScaffold
import com.flechazo.toolbox.core.designsystem.components.ToolSectionCard
import com.flechazo.toolbox.core.designsystem.components.KeyValueRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import javax.inject.Inject

data class UnitInfo(val name: String, val symbol: String, val factorToBase: Double)

enum class UnitCategory(val label: String, val baseSymbol: String, val units: List<UnitInfo>) {
    LENGTH("长度", "m", listOf(
        UnitInfo("毫米", "mm", 0.001), UnitInfo("厘米", "cm", 0.01),
        UnitInfo("米", "m", 1.0), UnitInfo("千米", "km", 1000.0),
        UnitInfo("英寸", "in", 0.0254), UnitInfo("英尺", "ft", 0.3048),
        UnitInfo("英里", "mi", 1609.344), UnitInfo("海里", "nmi", 1852.0),
    )),
    WEIGHT("重量", "kg", listOf(
        UnitInfo("毫克", "mg", 0.000001), UnitInfo("克", "g", 0.001),
        UnitInfo("千克", "kg", 1.0), UnitInfo("吨", "t", 1000.0),
        UnitInfo("磅", "lb", 0.45359237), UnitInfo("盎司", "oz", 0.028349523125),
        UnitInfo("斤", "jin", 0.5),
    )),
    TEMPERATURE("温度", "°C", listOf(
        UnitInfo("摄氏度", "°C", 1.0), UnitInfo("华氏度", "°F", 1.0),
        UnitInfo("开尔文", "K", 1.0),
    )),
    AREA("面积", "m²", listOf(
        UnitInfo("平方米", "m²", 1.0), UnitInfo("平方千米", "km²", 1000000.0),
        UnitInfo("公顷", "ha", 10000.0), UnitInfo("亩", "mu", 666.6667),
        UnitInfo("平方英尺", "ft²", 0.09290304),
    )),
    VOLUME("体积", "L", listOf(
        UnitInfo("毫升", "mL", 0.001), UnitInfo("升", "L", 1.0),
        UnitInfo("立方米", "m³", 1000.0), UnitInfo("加仑(美)", "gal", 3.785411784),
    )),
    SPEED("速度", "m/s", listOf(
        UnitInfo("米/秒", "m/s", 1.0), UnitInfo("千米/时", "km/h", 1.0 / 3.6),
        UnitInfo("英里/时", "mph", 0.44704), UnitInfo("节", "kn", 0.5144444),
    )),
    DATA("数据", "B", listOf(
        UnitInfo("比特", "bit", 0.125), UnitInfo("字节", "B", 1.0),
        UnitInfo("KB", "KB", 1024.0), UnitInfo("MB", "MB", 1048576.0),
        UnitInfo("GB", "GB", 1073741824.0), UnitInfo("TB", "TB", 1099511627776.0),
    )),
    ;

    fun convert(value: Double, from: UnitInfo, to: UnitInfo): Double = when (this) {
        TEMPERATURE -> when {
            from.symbol == to.symbol -> value
            from.symbol == "°C" && to.symbol == "°F" -> value * 9 / 5 + 32
            from.symbol == "°C" && to.symbol == "K" -> value + 273.15
            from.symbol == "°F" && to.symbol == "°C" -> (value - 32) * 5 / 9
            from.symbol == "°F" && to.symbol == "K" -> (value - 32) * 5 / 9 + 273.15
            from.symbol == "K" && to.symbol == "°C" -> value - 273.15
            from.symbol == "K" && to.symbol == "°F" -> (value - 273.15) * 9 / 5 + 32
            // 未覆盖的组合一律返回 NaN，绝不静默给出错误数值
            else -> Double.NaN
        }
        else -> value * from.factorToBase / to.factorToBase
    }
}

data class UnitConverterUiState(
    val category: UnitCategory = UnitCategory.LENGTH,
    val input: String = "1",
    val fromIndex: Int = 2,
    val toIndex: Int = 0,
    val results: List<Double> = emptyList(), // input converted to every unit
)

@HiltViewModel
class UnitConverterViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(
        UnitConverterUiState(results = convertAll(UnitConverterUiState())),
    )
    val state: StateFlow<UnitConverterUiState> = _state

    fun selectCategory(category: UnitCategory) {
        val s = _state.value.copy(
            category = category,
            fromIndex = category.units.indexOfFirst { it.factorToBase == 1.0 }.takeIf { it >= 0 } ?: 0,
            toIndex = 0,
        )
        _state.value = s.copy(results = convertAll(s))
    }

    fun setInput(value: String) {
        val s = _state.value.copy(input = value)
        _state.value = s.copy(results = convertAll(s))
    }

    fun setFromIndex(index: Int) {
        val s = _state.value.copy(fromIndex = index)
        _state.value = s.copy(results = convertAll(s))
    }

    fun setToIndex(index: Int) {
        val s = _state.value.copy(toIndex = index)
        _state.value = s.copy(results = convertAll(s))
    }

    private fun convertAll(s: UnitConverterUiState): List<Double> {
        val value = s.input.toDoubleOrNull() ?: return List(s.category.units.size) { Double.NaN }
        val from = s.category.units[s.fromIndex]
        return s.category.units.map { s.category.convert(value, from, it) }
    }
}

internal fun convertAll(s: UnitConverterUiState): List<Double> {
    val value = s.input.toDoubleOrNull() ?: return List(s.category.units.size) { Double.NaN }
    val from = s.category.units[s.fromIndex]
    return s.category.units.map { s.category.convert(value, from, it) }
}

@Composable
fun UnitConverterScreen(onBack: () -> Unit, viewModel: UnitConverterViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val category = state.category

    ToolScaffold(title = "单位换算", onBack = onBack, subtitle = "长度、重量、温度等 8 大类单位互转") {

        // 分类：两行分段切换，避免单行 8 段过挤
        val cats = UnitCategory.entries
        SegmentedTabs(
            options = cats.take(4),
            selected = category,
            label = { it.label },
            onSelect = viewModel::selectCategory,
        )
        if (cats.size > 4) {
            SegmentedTabs(
                options = cats.drop(4),
                selected = category,
                label = { it.label },
                onSelect = viewModel::selectCategory,
            )
        }

        // 输入与单位选择
        ToolSectionCard {
            ToolTextField(
                value = state.input,
                onValueChange = viewModel::setInput,
                label = { Text("数值") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                LabeledDropdown(
                    label = "从",
                    options = category.units,
                    selected = category.units[state.fromIndex],
                    display = { "${it.name} (${it.symbol})" },
                    onSelect = { viewModel.setFromIndex(category.units.indexOf(it)) },
                    modifier = Modifier.weight(1f),
                )
                FilledIconButton(
                    onClick = {
                        viewModel.setFromIndex(state.toIndex)
                        viewModel.setToIndex(state.fromIndex)
                    },
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                ) {
                    Icon(Icons.Filled.SwapHoriz, contentDescription = "交换单位")
                }
                LabeledDropdown(
                    label = "到",
                    options = category.units,
                    selected = category.units[state.toIndex],
                    display = { "${it.name} (${it.symbol})" },
                    onSelect = { viewModel.setToIndex(category.units.indexOf(it)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // 结果
        val to = category.units[state.toIndex]
        val direct = state.results.getOrNull(state.toIndex)
        ResultCard(
            label = "换算结果",
            value = if (direct != null && !direct.isNaN()) formatNumber(direct) else "-",
            unit = to.symbol,
            caption = if (direct != null && !direct.isNaN()) "${state.input} ${category.units[state.fromIndex].symbol} = ${formatNumber(direct)} ${to.symbol}" else "请输入有效数值",
        )

        // 全部单位参考列表（点击切换目标单位）
        ToolSectionCard(title = "换算到全部单位") {
            category.units.forEachIndexed { index, unit ->
                val v = state.results.getOrNull(index) ?: Double.NaN
                KeyValueRow(
                    label = "${unit.name} (${unit.symbol})",
                    value = formatNumber(v),
                    emphasized = index == state.toIndex,
                    onClick = { viewModel.setToIndex(index) },
                )
            }
        }
    }
}

private fun formatNumber(v: Double): String {
    if (v.isNaN() || v.isInfinite()) return "-"
    return if (v == v.toLong().toDouble() && kotlin.math.abs(v) < 1e15) {
        v.toLong().toString()
    } else {
        String.format(Locale.ROOT, "%.6f", v).trimEnd('0').trimEnd('.')
    }
}
