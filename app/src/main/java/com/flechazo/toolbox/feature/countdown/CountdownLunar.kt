package com.flechazo.toolbox.feature.countdown

import java.time.LocalDate

/** 农历日期。[month] 1..12，[day] 1..30，[isLeap] 表示是否为该年的闰月。 */
data class LunarDate(
    val year: Int,
    val month: Int,
    val day: Int,
    val isLeap: Boolean = false,
)

/** 换算原语 + 合法性查询。实现见 [TableLunarCalendar]。 */
interface LunarCalendar {

    /** 支持的最小 / 最大农历年。越界一律返回 null 而不是抛异常。 */
    val minYear: Int
    val maxYear: Int

    /** 公历 → 农历。[date] 越界时返回 null。 */
    fun solarToLunar(date: LocalDate): LunarDate?

    /**
     * 农历 → 公历。**假定入参合法**（该月存在且 [LunarDate.day] 不超过
     * [daysInLunarMonth]）；不合法输入的语义未定义，请一律先过 [LunarResolver.resolve]。
     */
    fun lunarToSolar(date: LunarDate): LocalDate?

    /** 该农历月的天数（29 / 30）；月份或年份不存在时返回 0。 */
    fun daysInLunarMonth(year: Int, month: Int, isLeap: Boolean): Int

    /** 该年的闰月月份（1..12），无闰月返回 0。年份越界返回 -1。 */
    fun leapMonthOf(year: Int): Int

    fun isValidYear(year: Int): Boolean = year in minYear..maxYear
}

/**
 * 农历换算的**合法性判定与回退策略**。纯函数，不碰任何平台 API，因此可完整单测。
 *
 * 两条民间惯例（必须写死并测，否则用户生日会算错）：
 * - 请求"闰五月初五"而该年无闰五月 → 回退到同年**正五月**初五
 * - 请求正月的某日而该月只有 29 天（请求三十）→ 取**该月最后一天**
 */
object LunarResolver {

    data class Resolved(
        val solar: LocalDate,
        val requested: LunarDate,
        /** 回退后的实际生效日期（可能与 requested 不同：闰月缺失或日期被截断） */
        val actual: LunarDate,
    ) {
        val fallbackUsed: Boolean get() = actual != requested
    }

    /** 解析出该农历日期在 [LunarDate.year] 年对应的公历日；无法解析返回 null。 */
    fun resolve(cal: LunarCalendar, requested: LunarDate): Resolved? {
        if (!cal.isValidYear(requested.year)) return null
        if (requested.month !in 1..12 || requested.day !in 1..30) return null

        // 闰月不存在 → 降级到同月正月
        val leapOf = cal.leapMonthOf(requested.year)
        val isLeap = requested.isLeap && leapOf == requested.month
        val effective = requested.copy(isLeap = isLeap)

        val dim = cal.daysInLunarMonth(effective.year, effective.month, effective.isLeap)
        if (dim == 0) return null // 该农历月不存在

        val clampedDay = minOf(effective.day, dim)
        val solar = cal.lunarToSolar(effective.copy(day = clampedDay)) ?: return null
        return Resolved(
            solar = solar,
            requested = requested,
            actual = effective.copy(day = clampedDay),
        )
    }
}
