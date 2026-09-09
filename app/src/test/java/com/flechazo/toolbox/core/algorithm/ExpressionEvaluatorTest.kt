package com.flechazo.toolbox.core.algorithm

import com.flechazo.toolbox.feature.calculator.ExpressionEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ExpressionEvaluatorTest {

    private fun eval(expr: String): Double = ExpressionEvaluator.evaluate(expr)

    @Test
    fun basicArithmetic() {
        assertEquals(4.0, eval("2+2"), 1e-9)
        assertEquals(6.0, eval("2*3"), 1e-9)
        assertEquals(2.5, eval("5/2"), 1e-9)
        assertEquals(1.0, eval("3-2"), 1e-9)
    }

    @Test
    fun precedence() {
        assertEquals(14.0, eval("2+3*4"), 1e-9)
        assertEquals(20.0, eval("(2+3)*4"), 1e-9)
        assertEquals(2.0, eval("6/3/1"), 1e-9) // left-assoc
        assertEquals(1.0, eval("10-3-2-4"), 1e-9)
    }

    @Test
    fun powerIsRightAssociative() {
        assertEquals(8.0, eval("2^3"), 1e-9)
        assertEquals(512.0, eval("2^3^2"), 1e-9) // 2^(3^2) 右结合
        assertEquals(64.0, eval("(2^3)^2"), 1e-9) // 括号强制左结合：8^2
    }

    @Test
    fun unaryMinus() {
        assertEquals(-5.0, eval("-5"), 1e-9)
        assertEquals(-5.0, eval("-2-3"), 1e-9)
        assertEquals(3.0, eval("-2*-2-1"), 1e-9) // 4-1
        assertEquals(8.0, eval("2--6"), 1e-9) // 2-(-6)
    }

    @Test
    fun percentIsPostfixDivideByHundred() {
        assertEquals(20.0, eval("200*10%"), 1e-9)
        assertEquals(0.05, eval("(2+3)%"), 1e-9)
        assertEquals(100.0, eval("50%*200"), 1e-9)
        assertEquals(0.5, eval("50%"), 1e-9)
    }

    @Test
    fun percentRequiresLeadingOperand() {
        assertThrows(Exception::class.java) { eval("%5") }
        assertThrows(Exception::class.java) { eval("1%2") }
    }

    @Test
    fun parenthesesFromKeypad() {
        assertEquals(10.0, eval("(1+2)*3+1"), 1e-9)
        assertEquals(9.0, eval("(1+2)*(4-1)"), 1e-9)
    }

    @Test
    fun formatIsMachineReadable() {
        // 必须是 '.' 小数点且可回读，否则逗号小数点区域会污染表达式
        assertEquals("1.5", ExpressionEvaluator.format(1.5))
        assertEquals("0.001", ExpressionEvaluator.format(0.001))
        assertEquals("1.000000e-09", ExpressionEvaluator.format(1e-9))
    }

    @Test
    fun commaIsRejectedNotSilentlyDropped() {
        // 旧实现跳过 ','，会把 "1,5*2" 算成 15*2=30
        assertThrows(Exception::class.java) { eval("1,5*2") }
    }

    @Test
    fun nestedParens() {
        assertEquals(3.0, eval("((1+2))"), 1e-9)
        assertEquals(10.0, eval("2*(1+(2*2))"), 1e-9)
    }

    @Test
    fun formatIntegers() {
        assertEquals("42", ExpressionEvaluator.format(42.0))
        assertEquals("3.5", ExpressionEvaluator.format(3.5))
        assertEquals("错误", ExpressionEvaluator.format(Double.NaN))
        assertEquals("错误", ExpressionEvaluator.format(Double.POSITIVE_INFINITY))
    }

    @Test
    fun divisionByZeroThrowsOrInfinite() {
        // 实现为 IEEE 除法则结果为 Infinity（format 显示“错误”），只要不崩溃即可
        val result = eval("1/0")
        assert(result.isInfinite() || result.isNaN())
    }

    @Test
    fun garbageThrows() {
        assertThrows(Exception::class.java) { eval("(1+2") }
        assertThrows(Exception::class.java) { eval("1++") }
        assertThrows(Exception::class.java) { eval("") }
    }
}
