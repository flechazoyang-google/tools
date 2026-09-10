package com.flechazo.toolbox.core.algorithm

import com.flechazo.toolbox.feature.countdown.CountdownEngine
import com.flechazo.toolbox.feature.countdown.CountdownEntity
import com.flechazo.toolbox.feature.countdown.CountdownReminders
import com.flechazo.toolbox.feature.countdown.EventMode
import com.flechazo.toolbox.feature.countdown.LunarCalendar
import com.flechazo.toolbox.feature.countdown.RemindDays
import com.flechazo.toolbox.feature.countdown.RepeatRule
import com.flechazo.toolbox.feature.countdown.TableLunarCalendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 提醒触发点计算（P0-3 的可测部分）。
 *
 * 真正落 `AlarmManager` 的那层需要设备，但"算出哪些时刻该响、错过哪些该补"是纯算术 ——
 * 把它抽出来测，就等于测掉了最容易出事故的那半。
 */
class CountdownReminderTest {

    private val lunar: LunarCalendar = TableLunarCalendar()
    private val today: LocalDate = LocalDate.of(2026, 3, 15)

    private fun entity(
        date: String,
        days: List<Int>,
        enabled: Boolean = true,
        hour: Int = 9,
        repeat: RepeatRule = RepeatRule.NONE,
    ) = CountdownEntity(
        title = "提醒测试",
        date = date,
        type = EventMode.COUNTDOWN.ordinal,
        repeat = repeat.ordinal,
        remindEnabled = enabled,
        remindDaysBefore = RemindDays.encode(days),
        remindHour = hour,
    )

    private fun upcoming(e: CountdownEntity, at: LocalDateTime = today.atTime(8, 0)) =
        CountdownReminders.upcoming(e, today, at, lunar)

    @Test
    fun disabledEventProducesNothing() {
        assertTrue(upcoming(entity("2026-12-31", listOf(0), enabled = false)).isEmpty())
    }

    @Test
    fun emptyDaysMeansNoReminder() {
        assertTrue(upcoming(entity("2026-12-31", emptyList())).isEmpty())
    }

    @Test
    fun sameDayReminderFiresAtConfiguredHour() {
        val t = upcoming(entity("2026-03-15", listOf(0)))
        assertEquals(1, t.size)
        assertEquals(LocalDateTime.of(2026, 3, 15, 9, 0), t[0].at)
        assertEquals(today, t[0].occurrence)
        assertEquals(0, t[0].daysBefore)
    }
    @Test
    fun alreadyElapsedSlotsAreSkippedNotQueued() {
        // 当天 + 提前 1、3 天，而现在是发生日早上 8 点：只有"当天 09:00"还没过
        val t = upcoming(entity("2026-03-15", listOf(0, 1, 3)))
        assertEquals(listOf(0), t.map { it.daysBefore })
    }

    @Test
    /** 已过期的一次性事件不再排任何闹钟（旧版会留下永远不响也永远不清的空提醒）。 */
    fun pastOneOffProducesNothing() {
        assertTrue(upcoming(entity("2020-01-01", listOf(0, 3))).isEmpty())
    }

    @Test
    fun multiTierOffsetsForFutureOccurrence() {
        val e = entity("1990-11-02", listOf(0, 3, 30), repeat = RepeatRule.YEARLY_SOLAR)
        val t = upcoming(e)
        // 只排下一年的 3 档，再下一年的第 1 档填满 6 个上限
        assertEquals(6, t.size)
        assertEquals(
            listOf(
                LocalDateTime.of(2026, 10, 3, 9, 0),
                LocalDateTime.of(2026, 10, 30, 9, 0),
                LocalDateTime.of(2026, 11, 2, 9, 0),
                LocalDateTime.of(2027, 10, 3, 9, 0),
                LocalDateTime.of(2027, 10, 30, 9, 0),
                LocalDateTime.of(2027, 11, 2, 9, 0),
            ),
            t.map { it.at },
        )
        assertTrue("必须严格升序", t.zipWithNext().all { (a, b) -> !a.at.isAfter(b.at) })
    }

    @Test
    fun triggerCountIsAlwaysBounded() {
        // 每周重复 × 5 档：理论上一轮就 5 个，上限必须生效，否则闹钟会被排爆
        val e = entity("2026-01-01", RemindDays.OPTIONS, repeat = RepeatRule.WEEKLY)
        val t = upcoming(e, today.atTime(0, 0))
        assertEquals(RemindDays.MAX_TRIGGERS, t.size)
    }

    @Test
    fun lunarOccurrenceDrivesReminder() {
        val e = CountdownEntity(
            title = "农历生日",
            date = "2025-01-29",
            repeat = RepeatRule.YEARLY_LUNAR.ordinal,
            isLunar = true,
            lunarMonth = 1,
            lunarDay = 1,
            remindDaysBefore = RemindDays.encode(listOf(0)),
            remindHour = 9,
        )
        // 今天已是 2026-03-15，2026 年春节（2/17）过了 → 从 2027 年起排。
        // 前四年都是可外部查证的春节日期，同时验证了农历推进与提醒排期两条链路。
        val t = upcoming(e)
        assertEquals(
            listOf(
                LocalDate.of(2027, 2, 6),
                LocalDate.of(2028, 1, 26),
                LocalDate.of(2029, 2, 13),
                LocalDate.of(2030, 2, 3),
            ),
            t.take(4).map { it.occurrence },
        )
        assertEquals("待触发点必须被夹在上限内", RemindDays.MAX_TRIGGERS, t.size)
        assertEquals(LocalDateTime.of(2027, 2, 6, 9, 0), t.first().at)
    }

    // ---- 补发 ----

    @Test
    fun missedWithinWindowIsBackfilled() {
        val e = entity("2026-03-15", listOf(0))
        // 今天发生、09:00 该响，但进程被 ROM 杀了：20:30 时距错过 11.5 小时，仍在 12 小时窗口内
        val m = CountdownReminders.missed(e, today, today.atTime(20, 30), lunar, windowHours = 12)
        assertEquals("早上 9 点的提醒，晚上八点半仍应补发", listOf(0), m.map { it.daysBefore })
    }

    @Test
    fun missedBeyondWindowIsDropped() {
        val e = entity("2026-03-15", listOf(0))
        val m = CountdownReminders.missed(e, today, today.atTime(22, 0), lunar, windowHours = 12)
        assertTrue("过窗太久就不补，避免半夜弹一条三天前的噪音", m.isEmpty())
    }

    @Test
    fun missedDoesNotOverlapUpcoming() {
        val e = entity("1990-11-02", listOf(0, 3), repeat = RepeatRule.YEARLY_SOLAR)
        val now = today.atTime(10, 0)
        val up = CountdownReminders.upcoming(e, today, now, lunar).map { it.at }.toSet()
        val miss = CountdownReminders.missed(e, today, now, lunar).map { it.at }.toSet()
        assertTrue("同一时刻不能既算待触发又算待补发", up.intersect(miss).isEmpty())
    }

    @Test
    fun remindHourOutOfRangeIsCoercedNotCrashing() {
        // remindHour 可能被手改坏的备份喂进 41：夹到 23，而不是 atTime() 抛异常
        val t = upcoming(entity("2026-03-15", listOf(0), hour = 41))
        assertEquals(23, t.single().at.hour)
    }

    // ---- requestCode ----

    @Test
    fun requestCodeSeparatesEventAndTier() {
        assertEquals(0 * 512 + 0, CountdownReminders.requestCode(0, 0))
        assertEquals(7 * 512 + 3, CountdownReminders.requestCode(7, 3))
        assertTrue(
            "不同事件的不同档位必须互不相同",
            CountdownReminders.requestCode(7, 3) != CountdownReminders.requestCode(8, 3),
        )
        assertTrue(CountdownReminders.requestCode(7, 3) != CountdownReminders.requestCode(7, 4))
    }

    @Test
    fun requestCodeSaturatesInsteadOfWrapping() {
        // 溢出会绕开上限保护、变成负数，进而让两个事件抢同一个 PendingIntent 互相覆盖
        val huge = Long.MAX_VALUE / 4
        assertTrue("极端 id 也必须落在合法区间", CountdownReminders.requestCode(huge, 0) >= 0)
        assertEquals(CountdownReminders.requestCode(huge, 0), CountdownReminders.requestCode(huge, 0))
        // 档位越界被夹到 0..511，绝不与相邻事件的编码串位
        assertEquals(CountdownReminders.requestCode(1, 511), CountdownReminders.requestCode(1, 5_000))
        assertEquals(CountdownReminders.requestCode(1, 511), CountdownReminders.requestCode(1, 512))
        assertEquals(CountdownReminders.requestCode(1, 0), CountdownReminders.requestCode(1, -3))
    }

    @Test
    fun requestCodeIsInjectiveOverRealIdRange() {
        // 真实 id 是 AUTOINCREMENT，取一个足够宽的区间：任意 (id, 档位) 组合都不得碰撞
        val seen = HashSet<Int>(60_000)
        for (id in 1L..10_000L) {
            for (tier in intArrayOf(0, 1, 3, 7, 30, 511)) {
                assertTrue("碰撞于 ($id, $tier)", seen.add(CountdownReminders.requestCode(id, tier)))
            }
        }
        assertEquals(60_000, seen.size)
    }

    // ---- CSV 编解码（v2 新增列的物理存储形态） ----

    @Test
    fun csvRoundTripNormalises() {
        assertEquals("0,3,30", RemindDays.encode(listOf(30, 0, 3, 0)))
        assertEquals(listOf(0, 3, 30), RemindDays.decode("0,3,30"))
        assertEquals(listOf(0, 3, 30), RemindDays.decode(" 3 , 0 , 30 "))
    }

    @Test
    fun dirtyCsvNeverBreaksTheList() {
        assertEquals(emptyList<Int>(), RemindDays.decode(null))
        assertEquals(emptyList<Int>(), RemindDays.decode(""))
        assertEquals(emptyList<Int>(), RemindDays.decode("abc"))
        assertEquals(listOf(3), RemindDays.decode("abc,3,-1,9999"))
        assertEquals("", RemindDays.encode(listOf(-5, 400)))
    }

    @Test
    fun reminderLabelsAreHumanReadable() {
        assertEquals("不提醒", RemindDays.label(emptyList()))
        assertEquals("当天提醒", RemindDays.label(listOf(0)))
        assertEquals("当天、提前 3 天、提前 7 天", RemindDays.label(listOf(0, 3, 7)))
    }

    @Test
    fun engineSeesDecodedRemainderConsistently() {
        val e = entity("2026-12-31", listOf(1, 3))
        assertEquals(listOf(1, 3), RemindDays.decode(e.remindDaysBefore))
        assertEquals(291L, CountdownEngine.buildItem(e, today, lunar).daysToNext)
    }
}
