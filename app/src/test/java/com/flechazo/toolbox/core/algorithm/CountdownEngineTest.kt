package com.flechazo.toolbox.core.algorithm

import com.flechazo.toolbox.feature.countdown.CountdownEngine
import com.flechazo.toolbox.feature.countdown.CountdownEntity
import com.flechazo.toolbox.feature.countdown.CountdownItem
import com.flechazo.toolbox.feature.countdown.DurationParts
import com.flechazo.toolbox.feature.countdown.DisplayKind
import com.flechazo.toolbox.feature.countdown.EventMode
import com.flechazo.toolbox.feature.countdown.GroupKind
import com.flechazo.toolbox.feature.countdown.LunarCalendar
import com.flechazo.toolbox.feature.countdown.LunarDate
import com.flechazo.toolbox.feature.countdown.RepeatRule
import com.flechazo.toolbox.feature.countdown.TableLunarCalendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 倒数日核心算法。全部走纯函数，不需要设备。
 *
 * 覆盖重构方案 §11 验收清单里"重复规则 / 排序 / 显示文案 / 边界日期"三组断言。
 */
class CountdownEngineTest {

    private val lunar: LunarCalendar = TableLunarCalendar()
    private val today: LocalDate = LocalDate.of(2026, 3, 15)

    private fun entity(
        date: String,
        mode: EventMode = EventMode.COUNTDOWN,
        repeat: RepeatRule = RepeatRule.NONE,
        isLunar: Boolean = false,
        lunarMonth: Int = 0,
        lunarDay: Int = 0,
        lunarLeap: Boolean = false,
        pinned: Boolean = false,
        createdAt: Long = 0L,
        anchorDate: String? = null,
        title: String = "事件",
    ) = CountdownEntity(
        id = 0,
        title = title,
        date = date,
        type = mode.ordinal,
        repeat = repeat.ordinal,
        isLunar = isLunar,
        lunarMonth = lunarMonth,
        lunarDay = lunarDay,
        lunarLeapMonth = lunarLeap,
        pinned = pinned,
        createdAt = createdAt,
        anchorDate = anchorDate,
    )

    private fun next(e: CountdownEntity, at: LocalDate = today) =
        CountdownEngine.nextOccurrence(e, at, lunar)

    private fun item(e: CountdownEntity, at: LocalDate = today) =
        CountdownEngine.buildItem(e, at, lunar)

    // ---- 一次性事件（旧版全部按一次性处理，生日过完就"已过期"） ----

    @Test
    fun oneOffEventInFuture() {
        val e = entity("2026-12-31")
        assertEquals(LocalDate.of(2026, 12, 31), next(e))
        val i = item(e)
        assertEquals(291L, i.daysToNext)
        assertFalse(i.isToday)
        assertEquals(DisplayKind.TO_TARGET, i.display.kind)
    }

    @Test
    fun oneOffEventInPastHasNoNextOccurrence() {
        val e = entity("2020-01-01")
        assertNull("一次性事件过了就是过了", next(e))
        val i = item(e)
        assertEquals(2265L, i.daysSinceLast)
        assertEquals(DisplayKind.PASSED, i.display.kind)
        assertEquals("已过 2265 天", i.display.headline)
    }

    @Test
    fun todayIsTodayForBothDirections() {
        val e = entity(today.toString())
        assertTrue(item(e).isToday)
        assertEquals(DisplayKind.TODAY, item(e).display.kind)
        assertEquals("就是今天", item(e).display.headline)

        val elapsed = entity(today.toString(), mode = EventMode.ELAPSED)
        assertEquals(DisplayKind.TODAY, item(elapsed).display.kind)
    }

    // ---- 每年公历重复：修 P0-2 的主体 ----

    @Test
    fun yearlySolarBirthdayRollsForward() {
        val e = entity("1990-11-02", repeat = RepeatRule.YEARLY_SOLAR)
        assertEquals(LocalDate.of(2026, 11, 2), next(e))
        // 已过完的生日滚到下一年，而不是显示"已过 1600 天"
        val last = CountdownEngine.lastOccurrence(e, today, lunar)
        assertEquals(LocalDate.of(2025, 11, 2), last)
        val i = item(e)
        assertEquals(232L, i.daysToNext)
        assertEquals(DisplayKind.TO_TARGET, i.display.kind)
    }

    @Test
    fun yearlySolarOnTheAnniversaryDayItself() {
        val e = entity("1990-03-15", repeat = RepeatRule.YEARLY_SOLAR)
        assertEquals(today, next(e))
        assertTrue(item(e).isToday)
    }

    /** 2/29 出生的人在平年该哪天过：业界通行做法是 2/28，且必须可预测。 */
    @Test
    fun leapDayBirthdayClampsToFeb28InCommonYears() {
        val e = entity("2000-02-29", repeat = RepeatRule.YEARLY_SOLAR)
        assertEquals(LocalDate.of(2027, 2, 28), next(e, LocalDate.of(2027, 1, 1)))
        assertEquals(LocalDate.of(2028, 2, 29), next(e, LocalDate.of(2028, 1, 1)))
    }

    // ---- 每月 / 每周重复 ----

    @Test
    fun monthlyOn31stClampsShortMonths() {
        val e = entity("2026-01-31", repeat = RepeatRule.MONTHLY)
        assertEquals(LocalDate.of(2026, 3, 31), next(e, LocalDate.of(2026, 3, 2)))
        assertEquals("二月没有 31 号", LocalDate.of(2027, 2, 28), next(e, LocalDate.of(2027, 2, 1)))
        assertEquals(LocalDate.of(2028, 2, 29), next(e, LocalDate.of(2028, 2, 1)))
        assertEquals(LocalDate.of(2026, 4, 30), next(e, LocalDate.of(2026, 4, 1)))
    }

    @Test
    fun weeklyKeepsWeekdayAndNeverGoesBackwards() {
        val e = entity("2026-01-01", repeat = RepeatRule.WEEKLY) // 2026-01-01 是周四
        // 正好落在重复日当天时不要跳到下周
        val thursday = LocalDate.of(2026, 3, 12)
        assertEquals(java.time.DayOfWeek.THURSDAY, thursday.dayOfWeek)
        assertEquals(thursday, next(e, thursday))
        val n = next(e, today) // today 是周日
        assertEquals(java.time.DayOfWeek.THURSDAY, n!!.dayOfWeek)
        assertTrue("必须 >= today", !n.isBefore(today))
        assertEquals(LocalDate.of(2026, 3, 19), next(e, LocalDate.of(2026, 3, 16)))
    }

    // ---- 农历每年重复 ----

    @Test
    fun yearlyLunarUsesKnownSpringFestival() {
        // 正月初一：2026-02-17 / 2027-02-06（外部可查证的事实）
        val e = entity("2025-01-29", repeat = RepeatRule.YEARLY_LUNAR, isLunar = true, lunarMonth = 1, lunarDay = 1)
        assertEquals(LocalDate.of(2026, 2, 17), next(e, LocalDate.of(2025, 6, 1)))
        assertEquals(LocalDate.of(2027, 2, 6), next(e))
        assertEquals(LocalDate.of(2026, 2, 17), CountdownEngine.lastOccurrence(e, today, lunar))
    }

    @Test
    fun lunarEventKeepsRollingFarIntoTheFuture() {
        // base 固定在 2025，但事件必须永远滚得下去。早先的实现从 **base 的农历年**起扫 6 年，
        // 于是 2031 年之后再也够不到未来 —— 农历生日会静默停摆成"已过 N 天"。
        val e = entity(
            "2025-01-29",
            repeat = RepeatRule.YEARLY_LUNAR,
            isLunar = true,
            lunarMonth = 1,
            lunarDay = 1,
        )
        val far = LocalDate.of(2040, 6, 1)
        val n = next(e, far)
        assertNotNull("2040 年仍要能算出下一次", n)
        assertTrue(n!!.isAfter(far))
        // 6 月已过该年春节，所以下一次是**下一个农历年**的正月初一
        assertEquals(LunarDate(2041, 1, 1, false), lunar.solarToLunar(n))
        val last = CountdownEngine.lastOccurrence(e, far, lunar)
        assertNotNull("上一次也要找得回来", last)
        assertEquals(LunarDate(2040, 1, 1, false), lunar.solarToLunar(last ?: return))
    }

    @Test
    fun yearlyLunarMidAutumn() {
        val e = entity("2025-10-06", repeat = RepeatRule.YEARLY_LUNAR, isLunar = true, lunarMonth = 8, lunarDay = 15)
        assertEquals(LocalDate.of(2026, 9, 25), next(e))
        assertEquals("八月十五", CountdownEngine.formatLunarDay(lunar.solarToLunar(next(e)!!)!!))
    }

    /**
     * 闰月生日：闰二月只在部分年份出现，缺闰二月的年份必须回退到正二月，
     * 并且**在界面上标出来**（否则用户会以为算错了）。
     */
    @Test
    fun leapMonthBirthdayFallsBackAndFlagsIt() {
        val leap2023 = LunarDate(2023, 2, 2, true)
        val base = lunar.lunarToSolar(leap2023)!!
        val e = entity(
            date = base.toString(),
            repeat = RepeatRule.YEARLY_LUNAR,
            isLunar = true,
            lunarMonth = 2,
            lunarDay = 2,
            lunarLeap = true,
        )

        // 2023 有闰二月 → 命中的那天换算回来确实是闰二月初二
        val in2023 = next(e, base)!!
        assertEquals(leap2023, lunar.solarToLunar(in2023))
        assertFalse("闰月存在时不该标记回退", item(e, base).lunarFallback)

        // 2024 无闰二月 → 回退到正二月初二，并标记
        val in2024 = next(e, LocalDate.of(2024, 1, 1))!!
        val back = lunar.solarToLunar(in2024)!!
        assertEquals(LunarDate(2024, 2, 2, false), back)
        assertTrue("回退必须被标记", item(e, LocalDate.of(2024, 1, 1)).lunarFallback)
    }

    @Test
    fun lunarDateOutsideTableRangeIsHandled() {
        // 1900 年在产品支持范围（1901–2099）之外：必须返回 null，而不是抛异常或跑飞循环
        val e = entity("1900-01-31", repeat = RepeatRule.YEARLY_LUNAR, isLunar = true, lunarMonth = 1, lunarDay = 1)
        assertNull(next(e, LocalDate.of(1900, 2, 1)))
    }

    // ---- 已过期的重复事件：lastOccurrence 语义 ----

    @Test
    fun elapsedRecurringCountsFromLastOccurrence() {
        val e = entity("2020-03-20", mode = EventMode.ELAPSED, repeat = RepeatRule.YEARLY_SOLAR)
        val i = item(e)
        assertEquals(LocalDate.of(2026, 3, 20).minusYears(1), i.lastDate)
        assertEquals(DisplayKind.TO_TARGET, i.display.kind)
        assertNotNull(i.display.breakdown)
    }

    // ---- 进度条（anchorDate → next 的完成度） ----

    @Test
    fun progressNeedsAnchorAndTarget() {
        val withAnchor = entity("2026-12-31", anchorDate = "2026-01-01")
        val p = item(withAnchor).progress
        assertNotNull(p)
        assertTrue("进度必须落在 0..1", p!! in 0f..1f)
        assertEquals(0.2f, p, 0.05f)

        assertNull("没有锚点就没有进度", item(entity("2026-12-31")).progress)
        assertNull("锚点在目标之后不合法", item(entity("2026-03-20", anchorDate = "2026-12-31")).progress)
    }

    // ---- 排序（修 P0-5） ----

    private fun sampleItems(): List<CountdownItem> {
        val soon = entity("2026-03-16", title = "明天", createdAt = 100L)
        val far = entity("2027-12-31", title = "很久以后", createdAt = 300L)
        val past = entity("2020-01-01", title = "早就过了", createdAt = 200L)
        val pinnedFar = entity("2027-06-01", title = "置顶但很远", pinned = true, createdAt = 1L)
        return listOf(soon, far, past, pinnedFar).map { item(it) }
    }

    @Test
    fun nearestSortIgnoresCreatedAt() {
        val sorted = CountdownEngine.sorted(sampleItems(), CountdownEngine.SortMode.NEAREST)
        assertEquals(listOf("置顶但很远", "明天", "很久以后", "早就过了"), sorted.map { it.event.title })
    }

    @Test
    fun createdSortUsesTimestamp() {
        val sorted = CountdownEngine.sorted(sampleItems(), CountdownEngine.SortMode.CREATED)
        assertEquals(listOf("置顶但很远", "很久以后", "早就过了", "明天"), sorted.map { it.event.title })
    }

    @Test
    fun titleSortIsCaseInsensitiveAndPinnedFirst() {
        val a = entity("2026-05-01", title = "apple").let { item(it) }
        val b = entity("2026-06-01", title = "Banana").let { item(it) }
        val c = entity("2026-07-01", title = "cherry", pinned = true).let { item(it) }
        val sorted = CountdownEngine.sorted(listOf(b, a, c), CountdownEngine.SortMode.TITLE)
        assertEquals(listOf("cherry", "apple", "Banana"), sorted.map { it.event.title })
    }

    @Test
    fun pinnedAlwaysWinsInEveryMode() {
        CountdownEngine.SortMode.entries.forEach { mode ->
            val sorted = CountdownEngine.sorted(sampleItems(), mode)
            assertTrue("$mode 下置顶项应在最前", sorted.first().event.pinned)
        }
    }

    @Test
    fun expiredSinksToBottomInNearestSort() {
        val sorted = CountdownEngine.sorted(sampleItems(), CountdownEngine.SortMode.NEAREST)
        val unpinned = sorted.filterNot { it.event.pinned }
        assertEquals("早就过了", unpinned.last().event.title)
    }

    // ---- 分组 ----

    @Test
    fun groupingBoundaries() {
        fun kindOf(date: LocalDate) = CountdownEngine.groupOf(item(entity(date.toString())))
        assertEquals(GroupKind.TODAY, kindOf(today))
        assertEquals(GroupKind.WITHIN_WEEK, kindOf(today.plusDays(7)))
        assertEquals(GroupKind.WITHIN_MONTH, kindOf(today.plusDays(8)))
        assertEquals(GroupKind.WITHIN_MONTH, kindOf(today.plusDays(30)))
        assertEquals(GroupKind.LATER, kindOf(today.plusDays(31)))
        assertEquals(GroupKind.PASSED, kindOf(today.minusDays(5)))
    }

    // ---- 文案与格式化 ----

    @Test
    fun formatDaysGroupsThousands() {
        assertEquals("12", CountdownEngine.formatDays(12))
        assertEquals("999", CountdownEngine.formatDays(999))
        assertEquals("1 000", CountdownEngine.formatDays(1_000))
        assertEquals("12 345 678", CountdownEngine.formatDays(12_345_678))
    }

    @Test
    fun elapsedModeNeverShowsNegativeDays() {
        // 旧版纪念日会把过去的日子显示成"已 -1699 天"
        val i = item(entity("2020-01-01", mode = EventMode.ELAPSED))
        assertTrue(i.display.headline, !i.display.headline.contains("-"))
        assertEquals(DisplayKind.SINCE, i.display.kind)
        assertTrue(i.daysSinceLast > 0)
    }

    @Test
    fun tomorrowIsOneDayNotTwo() {
        // 旧实现写成 days + 1
        val i = item(entity("2026-03-16"))
        assertEquals(1L, i.daysToNext)
        assertEquals("还有 1 天", i.display.headline)
    }

    @Test
    fun decomposeOnlyForLongSpans() {
        assertNull(CountdownEngine.decompose(today, today.plusDays(99)))
        val from = today.minusDays(1_200)
        val p = CountdownEngine.decompose(from, today)!!
        assertEquals("1200 天约合 3 年 3 个月", java.time.Period.between(from, today).toString(), "P3Y3M14D")
        assertEquals(1_200L, p.totalDays)
        assertEquals(p.copy(years = 3, months = 3, days = 14, totalDays = 1_200L).label, p.label)
        assertNull("起点在终点之后不分解", CountdownEngine.decompose(today, today.minusDays(400)))
    }

    @Test
    fun durationPartsLabelOmitsZeroComponents() {
        assertEquals("3 年 3 个月 14 天", DurationParts(3, 3, 14, 1_200).label)
        assertEquals("2 年", DurationParts(2, 0, 0, 730).label)
        assertEquals("5 个月", DurationParts(0, 5, 0, 152).label)
        assertEquals("0 天", DurationParts(0, 0, 0, 0).label)
        assertEquals("1 年 10 天", DurationParts(1, 0, 10, 375).label)
    }

    @Test
    fun lunarDayNaming() {
        fun name(m: Int, d: Int) = CountdownEngine.formatLunarDay(LunarDate(2024, m, d, false))
        assertEquals("正月初一", name(1, 1))
        assertEquals("正月初十", name(1, 10))
        assertEquals("二月二十", name(2, 20))
        assertEquals("三月三十", name(3, 30))
        assertEquals("冬月廿一", name(11, 21))
        assertEquals("腊月十一", name(12, 11))
        assertEquals("闰四月初五", LunarDate(2020, 4, 5, true).let { CountdownEngine.formatLunarDay(it) })
    }

    @Test
    fun invalidDateDegradesInsteadOfCrashing() {
        val e = entity("不是日期")
        val i = item(e)
        assertNull(i.base)
        assertEquals(DisplayKind.INVALID, i.display.kind)
        assertEquals("日期无效", i.display.headline)
        assertNull(CountdownEngine.parseDate(""))
        assertNull(CountdownEngine.parseDate(null))
        assertEquals(today, CountdownEngine.parseDate("  $today  "))
    }

    @Test
    fun lunarSubtitleShownForSolarEventsToo() {
        val i = item(entity("2026-03-15"))
        assertNotNull("公历事件也给出农历副行", i.lunarLabel)
        assertFalse(i.lunarFallback)
    }

    @Test
    fun displayAndLunarLabelAreIndependent() {
        // headline 只讲"还有几天"，农历另走副行 —— 混在一句里卡片文案会变长且难以对齐。
        // 2027-02-06 是农历正月初一（外部可查，且在 CountdownLunarTest 里已单独钉过）
        val i = item(entity("2027-02-06"))
        assertEquals("还有 328 天", i.display.headline)
        assertEquals("正月初一", i.lunarLabel)
    }
}
