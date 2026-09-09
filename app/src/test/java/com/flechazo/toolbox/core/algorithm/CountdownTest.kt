package com.flechazo.toolbox.core.algorithm

import com.flechazo.toolbox.feature.countdown.countdownLabel
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CountdownTest {

    private val today: LocalDate = LocalDate.of(2026, 1, 10)

    @Test
    fun countdownType() {
        // 旧实现写成 days + 1，明天会显示"还剩 2 天"
        assertEquals("还剩 1 天", countdownLabel(today.plusDays(1), 0, today))
        assertEquals("还剩 10 天", countdownLabel(today.plusDays(10), 0, today))
        assertEquals("就是今天", countdownLabel(today, 0, today))
        assertEquals("已过 1 天", countdownLabel(today.minusDays(1), 0, today))
        assertEquals("已过 100 天", countdownLabel(today.minusDays(100), 0, today))
    }

    @Test
    fun anniversaryTypeNeverNegative() {
        // 旧实现先判断 type==1，导致过去的纪念日显示"已 -1699 天"
        assertEquals("已 1 天", countdownLabel(today.minusDays(1), 1, today))
        assertEquals("已 1699 天", countdownLabel(today.minusDays(1699), 1, today))
        assertEquals("就是今天", countdownLabel(today, 1, today))
        assertEquals("还有 3 天", countdownLabel(today.plusDays(3), 1, today))
    }

    @Test
    fun invalidDate() {
        assertEquals("日期无效", countdownLabel(null, 0, today))
    }
}
