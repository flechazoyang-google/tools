package com.example.toolbox.feature.calculator

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.toolbox.core.components.TopBar
import com.example.toolbox.core.util.triggerVibration

@Composable
fun CalculatorScreen() {
    var expression by remember { mutableStateOf("") }
    val result by remember(expression) {
        derivedStateOf { CalculatorEngine.evaluate(expression) }
    }
    val resultText = if (expression.isNotBlank() && result != null) formatNumber(result!!) else ""

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(title = "计算器")

        // Display
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.42f)
                .padding(24.dp),
            contentAlignment = Alignment.BottomEnd,
        ) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    expression.ifBlank { "0" },
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    resultText,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Keypad
        val keys = listOf(
            listOf("C" to KeyType.Clear, "⌫" to KeyType.Back, "(" to KeyType.Paren, ")" to KeyType.Paren),
            listOf("7" to KeyType.Digit, "8" to KeyType.Digit, "9" to KeyType.Digit, "÷" to KeyType.Op),
            listOf("4" to KeyType.Digit, "5" to KeyType.Digit, "6" to KeyType.Digit, "×" to KeyType.Op),
            listOf("1" to KeyType.Digit, "2" to KeyType.Digit, "3" to KeyType.Digit, "−" to KeyType.Op),
            listOf("0" to KeyType.Digit, "." to KeyType.Dot, "=" to KeyType.Eq, "+" to KeyType.Op),
        )

        Column(modifier = Modifier.fillMaxWidth().weight(0.58f)) {
            keys.forEach { row ->
                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    row.forEach { (symbol, type) ->
                        CalcButton(
                            symbol = symbol,
                            type = type,
                            modifier = Modifier.weight(1f).padding(6.dp),
                            onClick = {
                                expression = applyKey(expression, type, symbol)
                            },
                        )
                    }
                }
            }
        }
    }
}

private enum class KeyType { Digit, Dot, Op, Paren, Clear, Back, Eq }

private fun applyKey(current: String, type: KeyType, symbol: String): String {
    return when (type) {
        KeyType.Clear -> ""
        KeyType.Back -> if (current.isNotEmpty()) current.dropLast(1) else current
        KeyType.Eq -> {
            val r = CalculatorEngine.evaluate(current)
            if (r != null) formatNumber(r) else current
        }
        KeyType.Op -> {
            if (current.isEmpty()) return current
            val last = current.last()
            if (last == '+' || last == '−' || last == '×' || last == '÷' || last == '(') return current
            current + symbol
        }
        KeyType.Paren -> current + symbol
        KeyType.Dot -> {
            // avoid two dots in current number
            val segment = current.takeLastWhile { it.isDigit() || it == '.' }
            if (segment.contains('.')) current else current + symbol
        }
        KeyType.Digit -> current + symbol
    }
}

private fun formatNumber(value: Double): String {
    if (value.isNaN() || value.isInfinite()) return value.toString()
    return if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        "%.6f".format(value).trimEnd('0').trimEnd('.')
    }
}

@Composable
private fun CalcButton(
    symbol: String,
    type: KeyType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "scale")

    val (container, content) = when (type) {
        KeyType.Eq, KeyType.Op -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        KeyType.Clear, KeyType.Back -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurface
    }

    Button(
        onClick = { triggerVibration(context, 10); onClick() },
        modifier = modifier.scale(scale),
        interactionSource = interaction,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(symbol, style = MaterialTheme.typography.titleLarge)
    }
}

/** Recursive-descent arithmetic evaluator. Supports + - * /, parentheses and unary minus. */
private object CalculatorEngine {
    private lateinit var tokens: List<String>
    private var pos = 0

    fun evaluate(input: String): Double? {
        tokens = tokenize(input) ?: return null
        if (tokens.isEmpty()) return null
        pos = 0

        return try {
            val r = parseExpr()
            if (pos != tokens.size) null else r
        } catch (e: Exception) {
            null
        }
    }

    private fun parseExpr(): Double {
        var value = parseTerm()
        while (pos < tokens.size && (tokens[pos] == "+" || tokens[pos] == "-")) {
            val op = tokens[pos++]
            val rhs = parseTerm()
            value = if (op == "+") value + rhs else value - rhs
        }
        return value
    }

    private fun parseTerm(): Double {
        var value = parseFactor()
        while (pos < tokens.size && (tokens[pos] == "*" || tokens[pos] == "/")) {
            val op = tokens[pos++]
            val rhs = parseFactor()
            value = if (op == "*") value * rhs else value / rhs
        }
        return value
    }

    private fun parseFactor(): Double {
        if (pos < tokens.size && tokens[pos] == "-") { pos++; return -parseFactor() }
        if (pos < tokens.size && tokens[pos] == "+") { pos++; return parseFactor() }
        if (pos < tokens.size && tokens[pos] == "(") {
            pos++
            val v = parseExpr()
            if (pos < tokens.size && tokens[pos] == ")") pos++
            return v
        }
        val num = tokens[pos++].toDoubleOrNull() ?: throw IllegalArgumentException()
        return num
    }

    private fun tokenize(s: String): List<String>? {
        val cleaned = s.replace('×', '*').replace('÷', '/').replace(" ", "")
        if (cleaned.isEmpty()) return null
        val tokens = mutableListOf<String>()
        var i = 0
        while (i < cleaned.length) {
            val c = cleaned[i]
            when {
                c.isDigit() || c == '.' -> {
                    val start = i
                    while (i < cleaned.length && (cleaned[i].isDigit() || cleaned[i] == '.')) i++
                    tokens.add(cleaned.substring(start, i))
                }
                "+-*/()".contains(c) -> {
                    tokens.add(c.toString())
                    i++
                }
                else -> return null
            }
        }
        return tokens
    }
}
