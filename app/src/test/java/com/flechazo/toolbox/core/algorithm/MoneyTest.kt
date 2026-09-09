package com.flechazo.toolbox.core.algorithm

import com.flechazo.toolbox.feature.money.numberToChinese
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class MoneyTest {

    private fun cn(amount: String): String = numberToChinese(BigDecimal(amount))

    // ---- 零与整数边界 ----

    @Test
    fun zeroAndWholeYuan() {
        assertEquals("零元整", cn("0"))
        assertEquals("壹元整", cn("1"))
        assertEquals("壹拾元整", cn("10"))
        assertEquals("壹佰元整", cn("100"))
        assertEquals("壹仟元整", cn("1000"))
        assertEquals("壹万元整", cn("10000"))
    }

    // ---- 节内零插入 ----

    @Test
    fun sectionZeroInsertion() {
        assertEquals("壹仟零壹元整", cn("1001"))
        assertEquals("壹仟零壹拾元整", cn("1010"))
        assertEquals("壹仟肆佰零玖元整", cn("1409"))
        assertEquals("壹万零壹元整", cn("10001"))
        assertEquals("壹拾万零壹元整", cn("100001"))
        assertEquals("壹亿零壹万元整", cn("100010000"))
        assertEquals("贰仟万零伍元整", cn("20000005"))
        assertEquals("壹亿零壹元整", cn("100000001"))
        assertEquals("壹佰万元整", cn("1000000"))
        assertEquals("壹佰亿元整", cn("10000000000"))
    }

    @Test
    fun largeUnits() {
        assertEquals("壹万亿零壹元整", cn("1000000000001"))
        assertEquals("壹仟万亿元整", cn("1000000000000000"))
    }

    // ---- 分/角：旧实现的截断 bug 回归 ----

    @Test
    fun jiaoAndFenRounding() {
        // 旧实现：(8.10-8)*100.toInt() == 9 → "玖分"
        assertEquals("捌元壹角整", cn("8.10"))
        assertEquals("捌元贰角整", cn("8.20"))
        // 旧实现：(1234.56-1234)*100.toInt() == 55 → "伍角伍分"
        assertEquals("壹仟贰佰叁拾肆元伍角陆分", cn("1234.56"))
        // 旧实现：(0.29)*100.toInt() == 28 → "贰角捌分"
        assertEquals("零元贰角玖分", cn("0.29"))
    }

    @Test
    fun fenZeroRules() {
        // 角位为 0、分位非 0 → 必须补「零」
        assertEquals("捌元零贰分", cn("8.02"))
        assertEquals("零元零壹分", cn("0.01"))
        assertEquals("零元壹角整", cn("0.10"))
        assertEquals("壹万陆仟肆佰零玖元零贰分", cn("16409.02"))
        assertEquals("壹仟肆佰零玖元伍角整", cn("1409.50"))
    }

    @Test
    fun halfUpRounding() {
        assertEquals("壹元零壹分", cn("1.005"))
        assertEquals("壹佰元整", cn("99.995"))
        assertEquals("零元零壹分", cn("0.005"))
    }

    @Test
    fun trailingZerosFromParsing() {
        assertEquals("捌元整", cn("8.00"))
        assertEquals("捌元整", cn("8.0"))
        assertEquals("捌元整", cn("8"))
    }

    // ---- 非法输入 ----

    @Test
    fun outOfRange() {
        assertEquals("负数不支持", cn("-1"))
        assertEquals("金额过大", cn("10000000000000000"))
        assertEquals("金额过大", cn("1E20"))
    }

    // ---- 整数部分函数单测 ----

    @Test
    fun intPartDirect() {
        assertEquals("零", com.flechazo.toolbox.feature.money.intToChinese(java.math.BigInteger.ZERO))
        assertEquals("贰拾叁", com.flechazo.toolbox.feature.money.intToChinese(java.math.BigInteger.valueOf(23)))
        assertEquals(
            "玖仟玖佰玖拾玖",
            com.flechazo.toolbox.feature.money.intToChinese(java.math.BigInteger.valueOf(9999)),
        )
    }
}
