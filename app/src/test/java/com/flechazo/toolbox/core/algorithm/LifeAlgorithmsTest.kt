package com.flechazo.toolbox.core.algorithm

import com.flechazo.toolbox.feature.kinship.reverseRelation
import com.flechazo.toolbox.feature.pomodoro.remainingSeconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
    // 已迁移到 feature/period/PeriodPredictorTest.kt（算法与模型重写，签名变更）。

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
