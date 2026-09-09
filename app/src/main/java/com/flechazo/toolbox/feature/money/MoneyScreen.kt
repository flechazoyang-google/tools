package com.flechazo.toolbox.feature.money

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flechazo.toolbox.core.designsystem.components.ResultCard
import com.flechazo.toolbox.core.designsystem.components.ToolTextField
import com.flechazo.toolbox.core.designsystem.components.ToolScaffold
import com.flechazo.toolbox.core.designsystem.components.ToolSectionCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import javax.inject.Inject

data class MoneyUiState(
    val input: String = "",
    val result: String = "",
    val error: Boolean = false,
)

@HiltViewModel
class MoneyViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(MoneyUiState())
    val state: StateFlow<MoneyUiState> = _state

    fun onInput(value: String) {
        // 只保留数字和至多一个小数点
        val sb = StringBuilder()
        var dotSeen = false
        value.forEach { c ->
            when {
                c.isDigit() -> sb.append(c)
                c == '.' && !dotSeen -> { sb.append(c); dotSeen = true }
            }
        }
        val filtered = sb.toString()
        val parsed = filtered.toBigDecimalOrNull()
        val result = parsed?.let { numberToChinese(it) }.orEmpty()
        _state.value = MoneyUiState(
            input = filtered,
            result = result,
            error = filtered.isNotEmpty() && result.isEmpty(),
        )
    }

    fun clear() {
        _state.value = MoneyUiState()
    }
}

private val CN_NUMBERS = arrayOf("零", "壹", "贰", "叁", "肆", "伍", "陆", "柒", "捌", "玖")
private val CN_UNITS = arrayOf("", "拾", "佰", "仟")
private val CN_BIG_UNITS = arrayOf("", "万", "亿", "万亿")

/** 上限：4 个万进节 = 9999 万亿 = 9.999e15，故 1e16 以上直接拒绝。 */
private val MAX_AMOUNT: BigDecimal = BigDecimal.valueOf(1e16)

/**
 * 数字金额转中文大写。
 *
 * 全部使用 [BigDecimal] 计算，避免 double 截断导致的分/角错误
 * （旧实现 `((amount - yuan) * 100).toInt()` 会把 8.10 算成 9 分、1234.56 算成 55 分）。
 */
internal fun numberToChinese(amount: BigDecimal): String {
    if (amount.signum() < 0) return "负数不支持"
    if (amount >= MAX_AMOUNT) return "金额过大"

    // 四舍五入取到「分」
    val cents: BigInteger = amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toBigInteger()
    val hundred = BigInteger.valueOf(100)
    val yuan = cents.divide(hundred)
    val fen = cents.mod(hundred).toInt()

    if (yuan.signum() == 0 && fen == 0) return "零元整"

    val sb = StringBuilder()
    if (yuan.signum() > 0) {
        sb.append(intToChinese(yuan)).append("元")
    } else {
        sb.append("零元")
    }

    if (fen == 0) return sb.append("整").toString()

    val jiao = fen / 10
    val remainder = fen % 10
    if (jiao > 0) sb.append(CN_NUMBERS[jiao]).append("角")
    if (remainder > 0) {
        // 角位为 0 而分位非 0 时必须补「零」，如 16409.02 → ……元零贰分
        if (jiao == 0) sb.append(CN_NUMBERS[0])
        sb.append(CN_NUMBERS[remainder]).append("分")
    } else {
        // 分位为 0 而角位非 0，如 8.20 → 捌元贰角整
        sb.append("整")
    }
    return sb.toString()
}

/** 整数部分转中文，按 4 位一节处理，节间按需补「零」。 */
internal fun intToChinese(value: BigInteger): String {
    if (value.signum() == 0) return "零"
    val tenThousand = BigInteger.valueOf(10000)
    val groups = mutableListOf<Int>()
    var v = value
    while (v.signum() > 0) {
        groups.add(v.mod(tenThousand).toInt())
        v = v.divide(tenThousand)
    }
    if (groups.size > CN_BIG_UNITS.size) return "金额过大"

    val sb = StringBuilder()
    for (i in groups.indices.reversed()) {
        val group = groups[i]
        if (group == 0) {
            // 高位节为 0 且其下还有非零节时补一个「零」
            val lowerNonZero = (0 until i).any { groups[it] != 0 }
            if (sb.isNotEmpty() && lowerNonZero && !sb.endsWith("零")) sb.append("零")
            continue
        }
        // 本节不足 4 位且前面已有内容时补「零」，如 10001 → 壹万零壹
        if (sb.isNotEmpty() && group < 1000 && !sb.endsWith("零")) sb.append("零")
        sb.append(sectionToChinese(group)).append(CN_BIG_UNITS[i])
    }
    return sb.toString()
}

/** 0 < num < 10000 的一节转中文，内部按需补「零」。 */
private fun sectionToChinese(num: Int): String {
    val sb = StringBuilder()
    var v = num
    var unit = 0
    var zeroPending = false
    while (v > 0) {
        val digit = v % 10
        if (digit == 0) {
            if (sb.isNotEmpty()) zeroPending = true
        } else {
            if (zeroPending) {
                sb.insert(0, CN_NUMBERS[0])
                zeroPending = false
            }
            sb.insert(0, CN_UNITS[unit])
            sb.insert(0, CN_NUMBERS[digit])
        }
        v /= 10
        unit++
    }
    return sb.toString()
}

@Composable
fun MoneyScreen(onBack: () -> Unit, viewModel: MoneyViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    ToolScaffold(
        title = "数字金额转大写",
        onBack = onBack,
        subtitle = "报销发票专用，支持到分",
    ) {
        ToolSectionCard(
            title = "输入金额",
            trailing = { TextButton(onClick = viewModel::clear) { Text("清空") } },
        ) {
            ToolTextField(
                value = state.input,
                onValueChange = viewModel::onInput,
                label = { Text("金额（元）") },
                singleLine = true,
                isError = state.error,
                supportingText = if (state.error) {
                    { Text("请输入有效金额") }
                } else null,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (state.result.isNotEmpty()) {
            ResultCard(
                label = "中文大写",
                value = state.result,
                caption = "点击复制",
                onClick = { clipboard.setText(AnnotatedString(state.result)) },
                // 大写金额可能很长（十几位数字 + 单位），必须允许换行
                singleLine = false,
            )
        }
    }
}
