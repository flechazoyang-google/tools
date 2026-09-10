package com.flechazo.toolbox.feature.countdown

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * 纯 Kotlin 的农历（阴阳历）换算，数据表覆盖农历年 1900–2100。
 *
 * **为什么不用 `android.icu.util.ChineseCalendar`**（原本的首选方案）：
 * 1. ICU 类在 JVM 单元测试里是 stub，一调用就抛异常 —— 农历换算将无法被单测覆盖，
 *    而它恰恰是本工具最容易算错用户生日的部分；
 * 2. 各厂商 ROM 的 ICU 版本不一致，同一日期在小米与原生系统上可能给出不同结果；
 * 3. 一份表 + 一个算法，行为完全确定、可测、跨设备一致，也不增加任何依赖。
 *
 * 数据表来自社区沿用最广的 `lunarInfo`（1900–2100，见 `solarlunar` / `calendar.js` 系），
 * 已用闰月分布逐位比对：2012 闰四、2014 闰九、2017 闰六、2020 闰四、2023 闰二、
 * 2025 闰六、2033 闰十一（"2033 年问题"年份）均与事实一致。
 *
 * 位布局（每个农历年一个整数）：
 * - `bit 0..3`  该年闰月月份，0 = 无闰月
 * - `bit 4..15` 正月到腊月的大小月，bit 15 = 正月 … bit 4 = 腊月；1 = 30 天，0 = 29 天
 * - `bit 16`    闰月是否 30 天（仅当有闰月时有意义）
 *
 * 锚点：**公历 1900-01-31 = 农历 1900 年正月初一**。
 * 注意：流传的那份 JS 实现把锚点算成了 1900-01-30（`lunar2solar` 里的差一 bug），
 * 本实现以 `solar2lunar` 一侧的正确锚点为准 —— 已由 2023/2024/2025/2026 年春节日期校验。
 */
class TableLunarCalendar @Inject constructor() : LunarCalendar {

    override val minYear: Int get() = SUPPORT_MIN
    override val maxYear: Int get() = SUPPORT_MAX

    override fun leapMonthOf(year: Int): Int =
        if (year !in RANGE_MIN..RANGE_MAX) -1 else infoOf(year) and 0xf

    /** 该农历月的天数（29 / 30）；月份不存在（如请求一个该年没有的闰月）返回 0。 */
    override fun daysInLunarMonth(year: Int, month: Int, isLeap: Boolean): Int {
        if (year !in RANGE_MIN..RANGE_MAX || month !in 1..12) return 0
        val info = infoOf(year)
        return if (isLeap) {
            if ((info and 0xf) != month) 0 else if (info and 0x10000 != 0) 30 else 29
        } else {
            if (info and (0x10000 shr month) != 0) 30 else 29
        }
    }

    override fun solarToLunar(date: LocalDate): LunarDate? {
        val epochDay = EPOCH.toEpochDay()
        val target = date.toEpochDay()
        if (target < epochDay) return null

        var offset = ChronoUnit.DAYS.between(EPOCH, date).toInt()
        var year = RANGE_MIN
        while (year <= RANGE_MAX) {
            val yd = yearDays(year)
            if (yd < 0) return null
            if (offset < yd) break
            offset -= yd
            year++
        }
        if (year > RANGE_MAX) return null

        for (segment in segmentsOf(year)) {
            val dim = daysInLunarMonth(year, segment.month, segment.isLeap)
            if (dim == 0) continue
            if (offset < dim) return LunarDate(year, segment.month, offset + 1, segment.isLeap)
            offset -= dim
        }
        return null // 表内数据不足以覆盖该日期（例如 2101 年之后）
    }

    override fun lunarToSolar(date: LunarDate): LocalDate? {
        val y = date.year
        if (y !in RANGE_MIN..RANGE_MAX) return null
        val dim = daysInLunarMonth(y, date.month, date.isLeap)
        if (dim == 0 || date.day !in 1..dim) return null

        var total = 0L
        for (year in RANGE_MIN until y) {
            val yd = yearDays(year)
            if (yd < 0) return null
            total += yd
        }
        for (segment in segmentsOf(y)) {
            if (segment.month == date.month && segment.isLeap == date.isLeap) break
            val sd = daysInLunarMonth(y, segment.month, segment.isLeap)
            if (sd == 0) return null
            total += sd
        }
        return EPOCH.plusDays(total + date.day - 1)
    }

    /** 该农历年的总天数（含闰月）。 */
    private fun yearDays(year: Int): Int {
        if (year !in RANGE_MIN..RANGE_MAX) return -1
        val info = infoOf(year)
        var sum = 348 // 12 个月 × 29 天
        var bit = 0x8000
        while (bit > 0x8) {
            if (info and bit != 0) sum++
            bit = bit shr 1
        }
        if ((info and 0xf) != 0) sum += if (info and 0x10000 != 0) 30 else 29
        return sum
    }

    private fun infoOf(year: Int): Int = LUNAR_INFO[year - RANGE_MIN]

    /**
     * 一个农历年的月段序列：`[正月, (闰N月)?, 二月, ...]`。
     * 闰月紧跟在同名月之后 —— 农历闰 N 月排在 N 月之后、N+1 月之前。
     */
    private fun segmentsOf(year: Int): List<Segment> {
        val leap = if (year in RANGE_MIN..RANGE_MAX) infoOf(year) and 0xf else 0
        val out = ArrayList<Segment>(13)
        for (m in 1..12) {
            out += Segment(m, false)
            if (m == leap) out += Segment(m, true)
        }
        return out
    }

    companion object {
        /** 1900-01-31 = 农历庚子年正月初一 */
        internal val EPOCH: LocalDate = LocalDate.of(1900, 1, 31)

        internal data class Segment(val month: Int, val isLeap: Boolean)

        private const val RANGE_MIN = 1900
        private const val RANGE_MAX = 2100

        /** 产品口径的支持范围：保守收到 2099，避开 2100 年尾部跨表的数据。 */
        const val SUPPORT_MIN = 1901
        const val SUPPORT_MAX = 2099

        /** 索引 0 = 农历年 1900。每行 10 年，便于人工核对。 */
        internal val LUNAR_INFO = intArrayOf(
            // 1900-1909
            0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2,
            // 1910-1919
            0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540, 0x0d6a0, 0x0ada2, 0x095b0, 0x14977,
            // 1920-1929
            0x04970, 0x0a4b0, 0x0b4b5, 0x06a50, 0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970,
            // 1930-1939
            // 1933 闰五月：坊间抄本多作 0x06e95（闰五月 29 天 / 六月 30 天），
            // 但出版的黄历明确记"1933-07-23 农历六月初一，六月(小)"，即六月只有 29 天、
            // 闰五月才是大月 —— 差一天就会让这一年六月之后的所有日期整体错位。
            0x06566, 0x0d4a0, 0x0ea50, 0x16a95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950,
            // 1940-1949
            0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557,
            // 1950-1959
            0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5b0, 0x14573, 0x052b0, 0x0a9a8, 0x0e950, 0x06aa0,
            // 1960-1969
            0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0,
            // 1970-1979
            0x096d0, 0x04dd5, 0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b6a0, 0x195a6,
            // 1980-1989
            0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46, 0x0ab60, 0x09570,
            // 1990-1999
            // 1996：坊间抄本作 0x055c0，五至八月的月长整体反了（总数相同，但月内日期错位），
            // 出版的万年历作 0x05ac0。
            0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58, 0x05ac0, 0x0ab60, 0x096d5, 0x092e0,
            // 2000-2009
            0x0c960, 0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5,
            // 2010-2019
            0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930,
            // 2020-2029
            0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530,
            // 2030-2039
            0x05aa0, 0x076a3, 0x096d0, 0x04afb, 0x04ad0, 0x0a4d0, 0x1d0b6, 0x0d250, 0x0d520, 0x0dd45,
            // 2040-2049
            0x0b5a0, 0x056d0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0aa50, 0x1b255, 0x06d20, 0x0ada0,
            // 2050-2059
            0x14b63, 0x09370, 0x049f8, 0x04970, 0x064b0, 0x168a6, 0x0ea50, 0x06b20, 0x1a6c4, 0x0aae0,
            // 2060-2069
            // 2060：抄本作 0x0a2e0，三、四月的月长反了；按天文实现与出版的万年历应为 0x092e0。
            0x092e0, 0x0d2e3, 0x0c960, 0x0d557, 0x0d4a0, 0x0da50, 0x05d55, 0x056a0, 0x0a6d0, 0x055d4,
            // 2070-2079
            0x052d0, 0x0a9b8, 0x0a950, 0x0b4a0, 0x0b6a6, 0x0ad50, 0x055a0, 0x0aba4, 0x0a5b0, 0x052b0,
            // 2080-2089
            0x0b273, 0x06930, 0x07337, 0x06aa0, 0x0ad50, 0x14b55, 0x04b60, 0x0a570, 0x054e4, 0x0d160,
            // 2090-2099
            0x0e968, 0x0d520, 0x0daa0, 0x16aa6, 0x056d0, 0x04ae0, 0x0a9d4, 0x0a2d0, 0x0d150, 0x0f252,
            // 2100
            0x0d520,
        )
    }
}
