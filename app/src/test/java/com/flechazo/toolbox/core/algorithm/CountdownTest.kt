package com.flechazo.toolbox.core.algorithm

import com.flechazo.toolbox.feature.countdown.CountdownEngine
import com.flechazo.toolbox.feature.countdown.CountdownEntity
import com.flechazo.toolbox.feature.countdown.EventMode
import com.flechazo.toolbox.feature.countdown.LunarCalendar
import com.flechazo.toolbox.feature.countdown.TableLunarCalendar
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * 旧 `countdownLabel(target, type, today)` 的三条回归守卫。
 *
 * 那个自由函数已被 [CountdownEngine] 的派生模型取代（它拿不到"重复规则"这个维度），
 * 但它守住的三个真实 bug 必须继续有测试盯着：
 * 1. 明天显示"还剩 2 天"（旧实现 days + 1）
 * 2. 过去的纪念日显示"已 -1699 天"（旧实现先判 type 再判正负）
 * 3. 脏日期直接把页面搞崩
 *
 * 现在断言的是 `display.headline` —— 也就是卡片真正渲染的那句文案，
 * 比测一个仅供测试使用的中间函数更贴近"用户看得见什么"。
 */
class CountdownTest {

    private val lunar: LunarCalendar = TableLunarCalendar()
    private val today: LocalDate = LocalDate.of(2026, 1, 10)

    private fun headline(date: String, mode: EventMode): String {
        val e = CountdownEntity(title = "x", date = date, type = mode.ordinal)
        return CountdownEngine.displayOf(CountdownEngine.buildItem(e, today, lunar)).headline
    }

    @Test
    fun countdownType() {
        assertEquals("还有 1 天", headline(today.plusDays(1).toString(), EventMode.COUNTDOWN))
        assertEquals("还有 10 天", headline(today.plusDays(10).toString(), EventMode.COUNTDOWN))
        assertEquals("就是今天", headline(today.toString(), EventMode.COUNTDOWN))
        assertEquals("已过 1 天", headline(today.minusDays(1).toString(), EventMode.COUNTDOWN))
        assertEquals("已过 100 天", headline(today.minusDays(100).toString(), EventMode.COUNTDOWN))
    }

    @Test
    fun anniversaryTypeNeverNegative() {
        assertEquals("已 1 天", headline(today.minusDays(1).toString(), EventMode.ELAPSED))
        assertEquals("已 1699 天", headline(today.minusDays(1699).toString(), EventMode.ELAPSED))
        assertEquals("就是今天", headline(today.toString(), EventMode.ELAPSED))
        assertEquals("还有 3 天", headline(today.plusDays(3).toString(), EventMode.ELAPSED))
    }

    @Test
    fun invalidDate() {
        assertEquals("日期无效", headline("garbage", EventMode.COUNTDOWN))
        assertEquals("日期无效", headline("", EventMode.ELAPSED))
    }
}
