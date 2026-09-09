package com.flechazo.toolbox.feature.calculator

import java.util.Locale
import java.util.Stack
import kotlin.math.pow

/**
 * Pure-Kotlin expression evaluator supporting + - * / % ^ and parentheses.
 * Implemented with the shunting-yard algorithm (no eval, no WebView).
 */
object ExpressionEvaluator {

    private val precedence = mapOf(
        "+" to 1, "-" to 1,
        "*" to 2, "/" to 2, "%" to 2,
        "^" to 3,
    )

    fun evaluate(expression: String): Double {
        val tokens = tokenize(expression)
        if (tokens.isEmpty()) throw IllegalArgumentException("空表达式")
        val output = Stack<Double>()
        val operators = Stack<String>()
        // "操作数" 标记：数值、")"、以及 % 之后都视为操作数
        val operand = "0"
        var previous: String? = null

        for (token in tokens) {
            when {
                token.toDoubleOrNull() != null -> {
                    output.push(token.toDouble())
                    previous = operand
                }
                token == "(" -> {
                    operators.push(token)
                    previous = token
                }
                token == ")" -> {
                    while (operators.isNotEmpty() && operators.peek() != "(") {
                        applyOperator(output, operators.pop())
                    }
                    if (operators.isEmpty()) throw IllegalArgumentException("括号不匹配")
                    operators.pop()
                    previous = operand
                }
                token == "%" -> {
                    // 后缀百分号：200*10% = 20；必须紧跟在数值或右括号之后
                    val prevIsOperand = previous != null && previous != "(" && previous !in precedence
                    if (!prevIsOperand) throw IllegalArgumentException("% 必须跟在数值或括号之后")
                    if (output.isEmpty()) throw IllegalArgumentException("表达式不完整")
                    output.push(output.pop() / 100.0)
                    previous = operand
                }
                token in precedence -> {
                    // unary minus / plus: appears at start, after "(" or after another operator
                    val isUnary = (token == "-" || token == "+") &&
                        (previous == null || previous == "(" || previous in precedence)
                    if (isUnary && token == "+") {
                        // unary plus: no-op, keep previous so "-+" chains still work
                    } else if (isUnary) {
                        output.push(0.0)
                        operators.push("-")
                    } else {
                        while (
                            operators.isNotEmpty() && operators.peek() != "(" &&
                            (precedence[operators.peek()] ?: 0) >= (precedence[token] ?: 0) &&
                            token != "^" // right-assoc
                        ) {
                            applyOperator(output, operators.pop())
                        }
                        operators.push(token)
                    }
                    previous = token
                }
                else -> throw IllegalArgumentException("非法字符: $token")
            }
        }

        while (operators.isNotEmpty()) {
            val op = operators.pop()
            if (op == "(") throw IllegalArgumentException("括号不匹配")
            applyOperator(output, op)
        }
        if (output.size != 1) throw IllegalArgumentException("表达式不完整")
        return output.pop()
    }

    private fun applyOperator(output: Stack<Double>, op: String) {
        if (output.size < 2) throw IllegalArgumentException("表达式不完整")
        val b = output.pop()
        val a = output.pop()
        output.push(
            when (op) {
                "+" -> a + b
                "-" -> a - b
                "*" -> a * b
                "/" -> a / b
                "%" -> a % b
                "^" -> a.pow(b)
                else -> throw IllegalArgumentException("未知运算符: $op")
            },
        )
    }

    private fun tokenize(expression: String): List<String> {
        val tokens = mutableListOf<String>()
        var number = StringBuilder()
        for (ch in expression) {
            when {
                ch.isDigit() || ch == '.' -> number.append(ch)
                // 只跳过空格。逗号不再被静默丢弃——否则 "1,5*2" 会被当成 15*2=30
                ch == ' ' -> { /* skip */ }
                else -> {
                    if (number.isNotEmpty()) {
                        tokens.add(number.toString())
                        number = StringBuilder()
                    }
                    tokens.add(ch.toString())
                }
            }
        }
        if (number.isNotEmpty()) tokens.add(number.toString())
        return tokens
    }

    /**
     * Format a result for display: integers without decimal point, otherwise up to
     * 8 decimal places. Always uses [Locale.ROOT] so the output can be fed back into
     * [evaluate] (a comma decimal separator would silently corrupt the expression).
     */
    fun format(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return "错误"
        if (value == value.toLong().toDouble() && kotlin.math.abs(value) < 1e15) {
            return value.toLong().toString()
        }
        // 极小值避免被 %.8f 抹成 "0"
        if (value != 0.0 && kotlin.math.abs(value) < 1e-8) {
            return String.format(Locale.ROOT, "%.6e", value)
        }
        return String.format(Locale.ROOT, "%.8f", value).trimEnd('0').trimEnd('.')
    }
}
