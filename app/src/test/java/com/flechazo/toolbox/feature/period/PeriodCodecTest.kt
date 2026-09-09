package com.flechazo.toolbox.feature.period

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 存储编解码与旧数据迁移的回归测试。 */
class PeriodCodecTest {

    @Test
    fun roundTripKeepsRecordsLogsAndSettings() {
        val data = PeriodData(
            records = listOf(
                PeriodRecord(
                    start = LocalDate.of(2026, 1, 1),
                    end = LocalDate.of(2026, 1, 5),
                    flows = mapOf(LocalDate.of(2026, 1, 1) to FlowLevel.HEAVY),
                    note = "痛经",
                ),
                PeriodRecord(start = LocalDate.of(2026, 1, 29)),
            ),
            logs = listOf(
                DailyLog(
                    date = LocalDate.of(2026, 1, 2),
                    symptoms = setOf(Symptom.CRAMPS, Symptom.FATIGUE),
                    pain = PainLevel.MODERATE,
                    note = "热水袋",
                ),
            ),
            settings = PeriodSettings(
                cycleLengthOverride = 30,
                periodLengthOverride = 6,
                predictEnabled = false,
                showFertileWindow = false,
                showCycleDay = false,
                consentAccepted = true,
            ),
        )

        val decoded = PeriodCodec.decode(PeriodCodec.encode(data))

        assertEquals(2, decoded.records.size)
        assertEquals(LocalDate.of(2026, 1, 1), decoded.records[0].start)
        assertEquals(LocalDate.of(2026, 1, 5), decoded.records[0].end)
        assertEquals(FlowLevel.HEAVY, decoded.records[0].flows[LocalDate.of(2026, 1, 1)])
        assertEquals("痛经", decoded.records[0].note)
        assertNull(decoded.records[1].end)
        assertEquals(1, decoded.logs.size)
        assertEquals(setOf(Symptom.CRAMPS, Symptom.FATIGUE), decoded.logs[0].symptoms)
        assertEquals(PainLevel.MODERATE, decoded.logs[0].pain)
        assertEquals(PeriodSettings(
            cycleLengthOverride = 30,
            periodLengthOverride = 6,
            predictEnabled = false,
            showFertileWindow = false,
            showCycleDay = false,
            consentAccepted = true,
        ), decoded.settings)
    }

    @Test
    fun encodedPayloadCarriesSchemaVersion() {
        val json = PeriodCodec.encode(PeriodData())
        assertTrue(json.contains("\"schemaVersion\":2"))
    }

    @Test
    fun decodeOfGarbageReturnsEmptyDocument() {
        val decoded = PeriodCodec.decode("这不是 JSON")
        assertEquals(PeriodData(), decoded)
    }

    @Test(expected = Exception::class)
    fun decodeOrThrowRejectsMalformedJson() {
        PeriodCodec.decodeOrThrow("{")
    }

    @Test
    fun outOfRangeOverridesAreDropped() {
        val json = """
            {"schemaVersion":2,"settings":{"cycleLengthOverride":99,"periodLengthOverride":1}}
        """.trimIndent()
        val decoded = PeriodCodec.decode(json)

        assertNull(decoded.settings.cycleLengthOverride)
        assertNull(decoded.settings.periodLengthOverride)
    }

    // ---------------- 旧版迁移 ----------------

    @Test
    fun legacyConsecutiveDaysBecomeOnePeriod() {
        val legacy = """["2026-01-01","2026-01-02","2026-01-03","2026-01-04","2026-01-05"]"""
        val records = PeriodCodec.decodeLegacyStarts(legacy)

        assertEquals(1, records.size)
        assertEquals(LocalDate.of(2026, 1, 1), records[0].start)
        assertEquals(LocalDate.of(2026, 1, 5), records[0].end)
        assertEquals(5, records[0].lengthDays)
    }

    @Test
    fun legacySingleDayKeepsUnknownEnd() {
        val legacy = """["2026-01-01","2026-01-29"]"""
        val records = PeriodCodec.decodeLegacyStarts(legacy)

        assertEquals(2, records.size)
        assertNull(records[0].end)
        assertNull(records[1].end)
    }

    @Test
    fun legacyMixedDaysSplitAtGaps() {
        val legacy = """["2026-02-26","2026-01-01","2026-01-02","2026-01-03","2026-01-29"]"""
        val records = PeriodCodec.decodeLegacyStarts(legacy)

        assertEquals(3, records.size)
        assertEquals(LocalDate.of(2026, 1, 1), records[0].start)
        assertEquals(LocalDate.of(2026, 1, 3), records[0].end)
        assertEquals(LocalDate.of(2026, 1, 29), records[1].start)
        assertEquals(LocalDate.of(2026, 2, 26), records[2].start)
    }

    @Test
    fun legacyEmptyOrInvalidReturnsEmpty() {
        assertTrue(PeriodCodec.decodeLegacyStarts(null).isEmpty())
        assertTrue(PeriodCodec.decodeLegacyStarts("").isEmpty())
        assertTrue(PeriodCodec.decodeLegacyStarts("{}").isEmpty())
    }

    @Test
    fun legacyFifteenTapsBecomeThreePeriods() {
        // 复现文档 §2.2 的 A 组数据：3 个 5 天经期被逐日点击成 15 条记录。
        val days = listOf(
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 3),
            LocalDate.of(2026, 1, 4), LocalDate.of(2026, 1, 5),
            LocalDate.of(2026, 1, 29), LocalDate.of(2026, 1, 30), LocalDate.of(2026, 1, 31),
            LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 2),
            LocalDate.of(2026, 2, 26), LocalDate.of(2026, 2, 27), LocalDate.of(2026, 2, 28),
            LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 2),
        )
        val legacy = days.joinToString(",", "[", "]") { "\"$it\"" }
        val records = PeriodCodec.decodeLegacyStarts(legacy)

        assertEquals(3, records.size)
        assertTrue(records.all { it.lengthDays == 5 })
        // 周期长度恢复为 28 天（旧实现显示 24 天）。
        val prediction = predictPeriod(records, LocalDate.of(2026, 3, 3))!!
        assertEquals(28, prediction.stats.cycleLength)
        assertEquals(2, prediction.stats.basedOnCycles)
    }
}
