package com.flechazo.toolbox.core.algorithm

import com.flechazo.toolbox.feature.countdown.LunarCalendar
import com.flechazo.toolbox.feature.countdown.LunarDate
import com.flechazo.toolbox.feature.countdown.LunarResolver
import com.flechazo.toolbox.feature.countdown.TableLunarCalendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 农历换算正确性。
 *
 * 表驱动实现唯一可信的验证方式是**拿外部事实对账**：春节、中秋、端午、七夕这些
 * 日期是有公共记录可查的，一旦数据表抄错一位或闰月插错位置，这些断言立刻失败。
 * 纯自洽（自己算自己）是检不出错的 —— 那正是这类实现最危险的假象。
 */
class CountdownLunarTest {

    private val cal: LunarCalendar = TableLunarCalendar()

    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    // ---- 春节（正月初一）：每年一个，错了整条农历就全错 ----

    @Test
    fun springFestivalDates() {
        val cny = mapOf(
            2020 to d(2020, 1, 25),
            2021 to d(2021, 2, 12),
            2022 to d(2022, 2, 1),
            2023 to d(2023, 1, 22),
            2024 to d(2024, 2, 10),
            2025 to d(2025, 1, 29),
            2026 to d(2026, 2, 17),
            2027 to d(2027, 2, 6),
            2028 to d(2028, 1, 26),
            2029 to d(2029, 2, 13),
            2030 to d(2030, 2, 3),
        )
        cny.forEach { (year, date) ->
            assertEquals("农历$year 年正月初一应为 $date", LunarDate(year, 1, 1, false), cal.solarToLunar(date))
            assertEquals("反向换算 $year", date, cal.lunarToSolar(LunarDate(year, 1, 1, false)))
        }
    }

    /** 流传的 JS 实现有个把 1900 年正月初一算成 1/30 的差一 bug，锚点必须钉死。 */
    @Test
    fun epochAnchor() {
        assertEquals(LunarDate(1900, 1, 1, false), cal.solarToLunar(d(1900, 1, 31)))
        assertEquals(d(1900, 1, 31), cal.lunarToSolar(LunarDate(1900, 1, 1, false)))
        // 锚点之前不再往外推：诚实返回 null，而不是给一个没依据的日期
        assertNull(cal.solarToLunar(d(1900, 1, 30)))
        assertNull(cal.solarToLunar(d(1899, 12, 31)))
    }

    // ---- 节日：月日固定，最容易暴露"月份偏移" ----

    @Test
    fun midAutumnFestival() {
        val dates = listOf(d(2020, 10, 1), d(2021, 9, 21), d(2022, 9, 10), d(2023, 9, 29), d(2024, 9, 17), d(2025, 10, 6))
        dates.forEach { date ->
            val l = cal.solarToLunar(date)
            assertNotNull("中秋 $date 无法换算", l)
            assertEquals("中秋 $date 应是八月十五", LunarDate(l!!.year, 8, 15, false), l)
        }
    }

    @Test
    fun dragonBoatAndQixi() {
        // 端午 = 五月初五
        listOf(d(2023, 6, 22), d(2024, 6, 10), d(2025, 5, 31)).forEach { date ->
            val l = cal.solarToLunar(date)!!
            assertEquals("端午 $date", 5, l.month)
            assertEquals("端午 $date", 5, l.day)
        }
        // 七夕 = 七月初七
        listOf(d(2024, 8, 10), d(2025, 8, 29)).forEach { date ->
            val l = cal.solarToLunar(date)!!
            assertEquals("七夕 $date", LunarDate(l.year, 7, 7, false), l)
        }
    }

    /** 2025 年没有"大年三十"：除夕是腊月廿九。这条能同时验证大小月与年末边界。 */
    @Test
    fun newYearEveOf2025IsTwentyNinth() {
        val l = cal.solarToLunar(d(2025, 1, 28))!!
        assertEquals(LunarDate(2024, 12, 29, false), l)
        assertEquals(29, cal.daysInLunarMonth(2024, 12, false))
    }

    // ---- 闰月 ----

    @Test
    fun leapMonthNumbers() {
        val expected = mapOf(
            2012 to 4, 2014 to 9, 2017 to 6, 2020 to 4, 2023 to 2, 2025 to 6, 2033 to 11,
            2024 to 0, 2026 to 0, 2022 to 0, 2021 to 0,
        )
        expected.forEach { (year, leap) -> assertEquals("$year 年闰月", leap, cal.leapMonthOf(year)) }
    }

    /**
     * 三行**被坊间抄本抄错**的数据，用出版的黄历钉住。
     *
     * 这三行的错误特征是"全年月长总和正确、月内分布错位"，所以任何拿表自校验的断言
     * （往返一致、年长区间、闰月数量）都抓不到 —— 只有外部日期事实能抓。
     * 出处：
     *  - 1933 闰五月：多站一致记"1933-07-23 农历六月初一，六月(小)"，即闰五月才是大月。
     *  - 1996：抄本 0x055c0 会让中秋落到 9/26，出版万年历作 1996-09-27。
     *  - 2060：抄本 0x0a2e0 会把三月排成 30 天，出版万年历作"2060-02-02 农历正月(大)初一"
     *    且后续月长与 0x092e0 才自洽。
     */
    @Test
    fun correctedRowsMatchPublishedAlmanacs() {
        assertEquals("1933 年六月初一", d(1933, 7, 23), cal.lunarToSolar(LunarDate(1933, 6, 1, false)))
        assertEquals(29, cal.daysInLunarMonth(1933, 6, false))
        assertEquals(30, cal.daysInLunarMonth(1933, 5, true))

        assertEquals("1996 年中秋", d(1996, 9, 27), cal.lunarToSolar(LunarDate(1996, 8, 15, false)))
        assertEquals(30, cal.daysInLunarMonth(1996, 5, false))
        assertEquals(29, cal.daysInLunarMonth(1996, 6, false))

        assertEquals("2060 年春节", d(2060, 2, 2), cal.lunarToSolar(LunarDate(2060, 1, 1, false)))
        assertEquals(30, cal.daysInLunarMonth(2060, 1, false))
        assertEquals(29, cal.daysInLunarMonth(2060, 3, false))
    }

    /**
     * 闰月的**位置**：必须夹在同名月与下一月之间。
     *
     * 用"公历相邻日"来断言，而不是拿表自校验 —— 后者对任何实现都恒真。
     * 这一条同时守住著名的"2033 年问题"（闰十一月该排在十月之后）。
     */
    @Test
    fun leapMonthSitsBetweenNeighbours() {
        for (year in 1901..2099) {
            val leap = cal.leapMonthOf(year)
            if (leap == 0) continue
            val lastLeapDay = cal.daysInLunarMonth(year, leap, true)
            assertTrue("$year 闰$leap 月天数异常: $lastLeapDay", lastLeapDay in 29..30)
            val first = cal.lunarToSolar(LunarDate(year, leap, 1, true)) ?: error("$year 闰$leap 初一算不出来")
            val last = cal.lunarToSolar(LunarDate(year, leap, lastLeapDay, true)) ?: error("$year 闰$leap 三十算不出来")
            // 前一天 = 同名正常月的最后一天
            assertEquals(
                "$year 闰$leap 月前应紧跟 $leap 月末",
                LunarDate(year, leap, cal.daysInLunarMonth(year, leap, false), false),
                cal.solarToLunar(first.minusDays(1)),
            )
            // 后一天 = 下一个正常月的初一（闰十二月理论上不存在，故只走到十一月）
            val nextMonth = if (leap == 12) 1 else leap + 1
            val nextYear = if (leap == 12) year + 1 else year
            assertEquals(
                "$year 闰$leap 月后应接 $nextMonth 月初一",
                LunarDate(nextYear, nextMonth, 1, false),
                cal.solarToLunar(last.plusDays(1)),
            )
        }
    }

    @Test
    fun noLeapMonthYearRejectsLeapRequest() {
        assertEquals(0, cal.leapMonthOf(2024))
        assertEquals(0, cal.daysInLunarMonth(2024, 6, true))
        assertNull(cal.lunarToSolar(LunarDate(2024, 6, 1, true)))
    }

    // ---- 往返一致性 ----

    @Test
    fun roundTripWholeSupportedRange() {
        var cursor = d(1901, 1, 1)
        val end = d(2099, 12, 31)
        var checked = 0
        var leapDays = 0
        var leapYears = 0
        while (!cursor.isAfter(end)) {
            val l = cal.solarToLunar(cursor) ?: error("$cursor 换算失败")
            assertEquals("$cursor 往返不一致", cursor, cal.lunarToSolar(l))
            if (l.isLeap) leapDays++
            checked++
            cursor = cursor.plusDays(1)
        }
        assertTrue("抽样太少", checked > 70_000)
        // 19 年 7 闰是阴阳历的基本周期：199 年里应有约 73 个闰月、每个 29~30 天。
        // 这条断言的意义不在精确值，而在"闰月数量级错了"必定暴露表位抄错。
        for (year in 1901..2099) if (cal.leapMonthOf(year) != 0) leapYears++
        assertTrue(
            "闰月年数偏离 19 年 7 闰太远: $leapYears",
            kotlin.math.abs(199 * 7 / 19 - leapYears) <= 2,
        )
        assertTrue("闰月总天数异常: $leapDays", leapDays in 1_900..2_400)
    }

    @Test
    fun consecutiveNewYearsMatchYearLength() {
        for (year in 1901..2098) {
            val a = cal.lunarToSolar(LunarDate(year, 1, 1, false))!!
            val b = cal.lunarToSolar(LunarDate(year + 1, 1, 1, false))!!
            val len = java.time.temporal.ChronoUnit.DAYS.between(a, b)
            assertTrue("$year 年长度异常: $len", len in 353..385)
        }
    }

    @Test
    fun outOfRangeReturnsNullInsteadOfCrashing() {
        assertNull(cal.solarToLunar(d(1899, 6, 1)))
        assertNull(cal.solarToLunar(d(2105, 1, 1)))
        assertNull(cal.lunarToSolar(LunarDate(1899, 1, 1, false)))
        assertNull(cal.lunarToSolar(LunarDate(2200, 1, 1, false)))
        assertNull(cal.lunarToSolar(LunarDate(2024, 13, 1, false)))
        assertNull(cal.lunarToSolar(LunarDate(2024, 1, 31, false)))
        assertEquals(-1, cal.leapMonthOf(1899))
    }

    // ---- LunarResolver：非法组合的回退 ----

    @Test
    fun resolverFallsBackWhenLeapMonthMissing() {
        // 2024 无闰六月 → 退到正六月
        val r = LunarResolver.resolve(cal, LunarDate(2024, 6, 1, true))
        assertNotNull(r)
        assertTrue("应标记为回退", r!!.fallbackUsed)
        assertEquals("requested 保留用户原始输入", LunarDate(2024, 6, 1, true), r.requested)
        assertEquals("actual 应落到正六月", LunarDate(2024, 6, 1, false), r.actual)
    }

    @Test
    fun resolverClampsDayToMonthLength() {
        // 正月只有 29 天时，设定"三十"必须落到当月最后一天而不是跳到下月
        val year = (2020..2035).first { cal.daysInLunarMonth(it, 1, false) == 29 }
        val r = LunarResolver.resolve(cal, LunarDate(year, 1, 30, false))
        assertNotNull(r)
        assertEquals("$year 正月三十应回退到廿九", 29, r!!.actual.day)
        assertTrue(r.fallbackUsed)
        assertEquals(r.actual, cal.solarToLunar(r.solar))
    }

    @Test
    fun resolverKeepsValidDateUnchanged() {
        // 农历 2025 年正月初一 = 公历 2025-01-29（春节）
        val r = LunarResolver.resolve(cal, LunarDate(2025, 1, 1, false))
        assertNotNull(r)
        assertTrue("合法日期不应标记回退", !r!!.fallbackUsed)
        assertEquals(d(2025, 1, 29), r.solar)
    }
}
