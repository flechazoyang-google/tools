package com.flechazo.toolbox.core.algorithm

import com.flechazo.toolbox.feature.countdown.CountdownBackup
import com.flechazo.toolbox.feature.countdown.CountdownEngine
import com.flechazo.toolbox.feature.countdown.CountdownEntity
import com.flechazo.toolbox.feature.countdown.EventMode
import com.flechazo.toolbox.feature.countdown.RemindDays
import com.flechazo.toolbox.feature.countdown.RepeatRule
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * 导入 / 导出兼容性。
 *
 * 这条链路只在"用户换机"那天才被用到，出问题就是丢数据，且不会有人来报 bug ——
 * 用户只会以为是自己弄没了。所以 v1 老文件必须继续能吃，v2 导出必须能原样吃回。
 * 测试走真实的 Gson 序列化，覆盖的是磁盘上的字节，不是内存里的对象。
 */
class CountdownBackupTest {

    private val gson = Gson()

    private fun decode(json: String): List<CountdownEntity> {
        val doc = gson.fromJson(json, CountdownBackup.Document::class.java)
        return doc.countdowns.mapNotNull { CountdownBackup.fromEvent(it) }
    }

    // ---- v1 老文件 ----

    @Test
    fun legacyFileStillImportsWithV1Defaults() {
        // 旧版 Toolbox 的真实导出形态：没有 schemaVersion，日期是毫秒，type 是字符串
        val legacy = """
            {"countdowns":[
              {"title":"毕业","targetDate":${millis(2019, 6, 30)},"type":"countdown"},
              {"title":"在一起","targetDate":${millis(2021, 5, 20)},"type":"anniversary"}
            ]}
        """.trimIndent()
        val e = decode(legacy)
        assertEquals(2, e.size)
        assertEquals("毕业", e[0].title)
        assertEquals("2019-06-30", e[0].date)
        assertEquals(EventMode.COUNTDOWN, e[0].mode)

        // v1 的 anniversary 语义 = 从那天起累计 → 映射到 ELAPSED
        assertEquals(EventMode.ELAPSED, e[1].mode)

        // 关键：v1 没有提醒字段，默认必须等价于 v1 的真实行为（开提醒 + 当天），
        // 否则升级后老用户的提醒会静默停掉
        assertTrue(e[0].remindEnabled)
        assertEquals(listOf(0), RemindDays.decode(e[0].remindDaysBefore))
        assertEquals(9, e[0].remindHour)
        assertEquals(RepeatRule.NONE, e[0].repeatRule)
        assertEquals("", e[0].note)
        assertEquals("", e[0].colorKey)
        assertNull(e[0].anchorDate)
    }

    @Test
    fun v1LunarFieldsAreRescuedInsteadOfDropped() {
        // 旧版备份本来就带农历字段，之前被整条跳过（用户数据静默消失）
        val legacy = """
            {"countdowns":[{"title":"奶奶生日","targetDate":${millis(1960, 10, 17)},
              "type":"countdown","isLunar":true,"lunarMonth":9,"lunarDay":8,"isPinned":true}]}
        """.trimIndent()
        val e = decode(legacy).single()
        assertTrue(e.isLunar)
        assertEquals(9, e.lunarMonth)
        assertEquals(8, e.lunarDay)
        assertTrue(e.pinned)
    }

    @Test
    fun unknownTypeStringDegradesToCountdown() {
        val e = decode("""{"countdowns":[{"title":"x","targetDate":${millis(2030, 1, 1)},"type":"???"}]}""")
            .single()
        assertEquals(EventMode.COUNTDOWN, e.mode)
    }

    // ---- v2 往返 ----

    @Test
    fun fullRoundTripPreservesEveryField() {
        val original = CountdownEntity(
            id = 9,
            title = "结婚纪念日",
            date = "2020-10-01",
            type = EventMode.ELAPSED.ordinal,
            createdAt = 1_700_000_000_000,
            note = "酒店订在江边那家",
            repeat = RepeatRule.MONTHLY.ordinal,
            isLunar = true,
            lunarMonth = 8,
            lunarDay = 15,
            lunarLeapMonth = true,
            pinned = true,
            colorKey = "coral",
            remindEnabled = false,
            remindDaysBefore = RemindDays.encode(listOf(0, 3, 30)),
            remindHour = 21,
            anchorDate = "2018-05-01",
            updatedAt = 1_710_000_000_000,
        )
        val json = gson.toJson(CountdownBackup.Document(countdowns = listOf(CountdownBackup.toEvent(original))))
        val restored = gson.fromJson(json, CountdownBackup.Document::class.java)
            .let { doc -> CountdownBackup.fromEvent(doc.countdowns.single()) }

        assertNotNull("往返后应可解析", restored)
        restored!!
        assertEquals(original.title, restored.title)
        assertEquals(original.date, restored.date)
        assertEquals(original.mode, restored.mode)
        assertEquals(original.createdAt, restored.createdAt)
        assertEquals(original.note, restored.note)
        assertEquals(original.repeatRule, restored.repeatRule)
        assertEquals(original.isLunar, restored.isLunar)
        assertEquals(original.lunarMonth, restored.lunarMonth)
        assertEquals(original.lunarDay, restored.lunarDay)
        assertEquals(original.lunarLeapMonth, restored.lunarLeapMonth)
        assertEquals(original.pinned, restored.pinned)
        assertEquals(original.colorKey, restored.colorKey)
        assertEquals(original.remindEnabled, restored.remindEnabled)
        assertEquals(original.remindDaysBefore, restored.remindDaysBefore)
        assertEquals(original.remindHour, restored.remindHour)
        assertEquals(original.anchorDate, restored.anchorDate)
    }

    @Test
    fun exportIsSupersetReadableByLegacyClients() {
        // 旧版客户端只认 title / targetDate / type，我们的导出必须继续带上它们。
        // 这里直接查 JSON 字节，而不是查内存对象 —— 兼容性的定义就在字节上。
        val json = gson.toJson(
            CountdownBackup.Document(
                countdowns = listOf(
                    CountdownBackup.toEvent(CountdownEntity(title = "体检", date = "2026-04-01", type = 0)),
                ),
            ),
        )
        val root = com.google.gson.JsonParser.parseString(json).asJsonObject
        val item = root.getAsJsonArray("countdowns").get(0).asJsonObject
        assertEquals("体检", item.get("title").asString)
        assertEquals("countdown", item.get("type").asString)
        assertEquals(millis(2026, 4, 1), item.get("targetDate").asLong)
        assertEquals(2, root.get("schemaVersion").asInt)
        // 同时带上 v2 字段，供本版本读回
        assertEquals("2026-04-01", item.get("date").asString)
    }

    @Test
    fun isoDateWinsOverLegacyMillis() {
        val ev = CountdownBackup.Event(
            title = "冲突",
            date = "2030-01-01",
            targetDate = millis(1999, 9, 9),
        )
        assertEquals("2030-01-01", CountdownBackup.fromEvent(ev)?.date)
    }

    // ---- 脏数据 ----

    @Test
    fun unusableRecordsAreRejectedNotGuessed() {
        assertNull("没标题", CountdownBackup.fromEvent(CountdownBackup.Event(title = "  ")))
        assertNull("两个日期字段都没有", CountdownBackup.fromEvent(CountdownBackup.Event(title = "x")))
        assertNull(
            "非法 ISO 日期且没有毫秒兜底",
            CountdownBackup.fromEvent(CountdownBackup.Event(title = "x", date = "2030-13-45")),
        )
    }

    @Test
    fun corruptAnchorDateDroppedButEventKept() {
        val ev = CountdownBackup.Event(
            title = "锚点坏了",
            date = "2030-01-01",
            anchorDate = "not-a-date",
        )
        val e = CountdownBackup.fromEvent(ev)
        assertNotNull(e)
        assertNull(e!!.anchorDate)
        assertEquals("2030-01-01", e.date)
    }

    @Test
    fun createdAtZeroFallsBackToNow() {
        val before = System.currentTimeMillis()
        val e = CountdownBackup.fromEvent(CountdownBackup.Event(title = "新", date = "2030-01-01"))!!
        assertTrue(e.createdAt >= before)
    }

    @Test
    fun colorTagOnlyMapsWhenItIsActuallyAPaletteKey() {
        // 旧版存的是十六进制色值，不能当成 v2 的色板 key 用
        assertEquals(
            "",
            CountdownBackup.fromEvent(CountdownBackup.Event(title = "x", date = "2030-01-01", colorTag = "#4F7CFF"))!!.colorKey,
        )
        assertEquals(
            "coral",
            CountdownBackup.fromEvent(CountdownBackup.Event(title = "x", date = "2030-01-01", colorTag = "coral"))!!.colorKey,
        )
        assertEquals(
            "mint",
            CountdownBackup.fromEvent(
                CountdownBackup.Event(title = "x", date = "2030-01-01", colorTag = "coral", colorKey = "mint"),
            )!!.colorKey,
        )
    }

    @Test
    fun dedupeKeyIgnoresSurroundingWhitespace() {
        val a = CountdownEntity(title = " 生日 ", date = "2000-05-05")
        val b = CountdownEntity(title = "生日", date = "2000-05-05")
        assertEquals(CountdownBackup.dedupeKey(a), CountdownBackup.dedupeKey(b))
        assertTrue(CountdownBackup.dedupeKey(a) != CountdownBackup.dedupeKey(b.copy(date = "2000-05-06")))
    }

    @Test
    fun remindersSurviveWhenReopenedFromExport() {
        // 提醒配置一旦在导出时丢掉，换机后用户的"提前 3 天"就变成"当天"
        val e = CountdownEntity(
            title = "还信用卡",
            date = "2026-04-09",
            remindDaysBefore = RemindDays.encode(listOf(1, 3, 7)),
            remindHour = 8,
        )
        val json = gson.toJson(CountdownBackup.Document(countdowns = listOf(CountdownBackup.toEvent(e))))
        val back = gson.fromJson(json, CountdownBackup.Document::class.java)
        val restored = CountdownBackup.fromEvent(back.countdowns.single())!!
        assertEquals(listOf(1, 3, 7), RemindDays.decode(restored.remindDaysBefore))
        assertEquals(8, restored.remindHour)
        val lunar = com.flechazo.toolbox.feature.countdown.TableLunarCalendar()
        assertEquals(25L, CountdownEngine.buildItem(restored, LocalDate.of(2026, 3, 15), lunar).daysToNext)
    }

    private fun millis(y: Int, m: Int, d: Int): Long =
        LocalDate.of(y, m, d).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
