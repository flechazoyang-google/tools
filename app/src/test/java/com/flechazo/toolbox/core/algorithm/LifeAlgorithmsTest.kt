package com.flechazo.toolbox.core.algorithm

import com.flechazo.toolbox.feature.kinship.reverseRelation
import com.flechazo.toolbox.feature.period.predictPeriod
import com.flechazo.toolbox.feature.pomodoro.remainingSeconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** 亲戚称呼反推、经期预测、番茄钟计时三个纯函数算法的回归测试。 */
class LifeAlgorithmsTest {

    // ---------------- 亲戚称呼反推 ----------------

    @Test
    fun reverseDirectRelations() {
        assertEquals("子", reverseRelation("父", true))
        assertEquals("女", reverseRelation("父", false))
        assertEquals("子", reverseRelation("母", true))
        assertEquals("弟", reverseRelation("兄", true))
        assertEquals("妹", reverseRelation("兄", false))
        assertEquals("兄", reverseRelation("弟", true))
        assertEquals("姐", reverseRelation("弟", false))
        assertEquals("父", reverseRelation("子", true))
        assertEquals("母", reverseRelation("子", false))
        assertEquals("妻", reverseRelation("夫", true))
        assertEquals("夫", reverseRelation("妻", false))
    }

    @Test
    fun reverseGrandparents() {
        assertEquals("子子", reverseRelation("父父", true))
        assertEquals("子女", reverseRelation("父父", false))
        assertEquals("女子", reverseRelation("母父", true))
        assertEquals("女女", reverseRelation("母母", false))
    }

    @Test
    fun reverseUnclesAndAunts() {
        // 伯父 → 侄子/侄女
        assertEquals("弟子", reverseRelation("父兄", true))
        assertEquals("弟女", reverseRelation("父兄", false))
        // 舅舅 → 外甥/外甥女
        assertEquals("妹子", reverseRelation("母兄", true))
        assertEquals("妹女", reverseRelation("母兄", false))
    }

    @Test
    fun reverseCousins() {
        // 堂：双方父母均为男性
        assertEquals("父弟子", reverseRelation("父兄子", true))
        assertEquals("父兄女", reverseRelation("父弟子", false))
        // 表：姑妈方向
        assertEquals("母弟女", reverseRelation("父姐子", false))
        assertEquals("母兄子", reverseRelation("父妹子", true))
        // 表：舅舅方向
        assertEquals("父妹子", reverseRelation("母兄子", true))
        assertEquals("父姐女", reverseRelation("母弟子", false))
        // 表：姨妈方向
        assertEquals("母妹子", reverseRelation("母姐子", true))
        assertEquals("母姐女", reverseRelation("母妹子", false))
    }

    @Test
    fun reverseUnknownChain() {
        assertNull(reverseRelation("父父母父", true))
    }

    // ---------------- 经期预测 ----------------

    private val today: LocalDate = LocalDate.of(2026, 1, 10)

    @Test
    fun emptyHistoryUsesDefaultCycle() {
        val p = predictPeriod(emptyList(), today)
        assertEquals(28, p.averageCycle)
        assertNull(p.nextStart)
        assertNull(p.ovulation)
    }

    @Test
    fun regularCycle() {
        val starts = listOf(today, today.minusDays(28), today.minusDays(56))
        val p = predictPeriod(starts, today)
        assertEquals(28, p.averageCycle)
        assertEquals(today.plusDays(28), p.nextStart)
        assertEquals(today.plusDays(14), p.ovulation)
        assertEquals(today.plusDays(9), p.fertileStart)
        assertEquals(today.plusDays(15), p.fertileEnd)
    }

    @Test
    fun outlierGapIsIgnored() {
        // 14 天间隔不在 15..60 内，被剔除；剩下 28/28
        val starts = listOf(today, today.minusDays(14), today.minusDays(42), today.minusDays(70))
        assertEquals(28, predictPeriod(starts, today).averageCycle)
    }

    @Test
    fun overduePredictionRollsForward() {
        // 最后一次记录在 40 天前，28 天周期已过 → 顺延到下一个周期
        val p = predictPeriod(listOf(today.minusDays(40)), today)
        assertEquals(today.plusDays(16), p.nextStart)
    }

    // ---------------- 番茄钟计时 ----------------

    @Test
    fun remainingSecondsRounding() {
        assertEquals(0, remainingSeconds(1_000, 2_000))
        assertEquals(0, remainingSeconds(0, 0))
        assertEquals(1, remainingSeconds(1_000, 0))
        assertEquals(1, remainingSeconds(1_500, 1_000))
        assertEquals(2, remainingSeconds(1_500, 0))
    }
}
