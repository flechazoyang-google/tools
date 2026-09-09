package com.flechazo.toolbox.feature.period

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 经期预测算法的回归测试。
 *
 * 期望值由 docs/PERIOD_OPTIMIZATION_PLAN.md §6.4 的夹具表给出，并与原型实现核对过。
 */
class PeriodPredictorTest {

    private val today: LocalDate = LocalDate.of(2026, 3, 3)

    private fun starts(vararg days: LocalDate) = days.map { PeriodRecord(start = it) }

    // ---------------- 基本预测 ----------------

    @Test
    fun emptyRecordsReturnsNull() {
        assertNull(predictPeriod(emptyList(), today))
    }

    @Test
    fun predictDisabledReturnsNull() {
        val records = starts(today.minusDays(56), today.minusDays(28), today)
        assertNull(predictPeriod(records, today, PeriodSettings(predictEnabled = false)))
    }

    @Test
    fun regularCycleProducesNarrowWindow() {
        val records = starts(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 1, 29),
            LocalDate.of(2026, 2, 26),
        )
        val prediction = predictPeriod(records, today)!!

        assertEquals(28, prediction.stats.cycleLength)
        assertEquals(2, prediction.stats.basedOnCycles)
        assertEquals(LocalDate.of(2026, 3, 26), prediction.nextStart)
        assertEquals(LocalDate.of(2026, 3, 25), prediction.windowStart)
        assertEquals(LocalDate.of(2026, 3, 27), prediction.windowEnd)
        assertEquals(LocalDate.of(2026, 3, 30), prediction.predictedPeriodEnd)
        assertEquals(LocalDate.of(2026, 3, 12), prediction.ovulation)
        assertEquals(LocalDate.of(2026, 3, 7), prediction.fertileStart)
        assertEquals(LocalDate.of(2026, 3, 13), prediction.fertileEnd)
        assertEquals(Regularity.REGULAR, prediction.stats.regularity)
        assertEquals(Confidence.MEDIUM, prediction.confidence)
        assertEquals(5, prediction.stats.periodLength)
        assertFalse(prediction.stats.periodLengthKnown)
        assertFalse(prediction.missedCycle)
    }

    @Test
    fun lateStartIsReportedNotSilentlyRolledForward() {
        val records = starts(
            LocalDate.of(2025, 12, 7),
            LocalDate.of(2026, 1, 4),
            LocalDate.of(2026, 2, 1),
        )
        val prediction = predictPeriod(records, LocalDate.of(2026, 3, 13))!!

        // 中心 3/1，今天 3/13 → 推迟 12 天；旧实现会显示"还有 16 天"。
        assertEquals(LocalDate.of(2026, 3, 1), prediction.nextStart)
        assertEquals(12, prediction.daysLate)
        assertFalse(prediction.missedCycle)
        assertEquals(LocalDate.of(2026, 2, 28), prediction.windowStart)
        assertEquals(LocalDate.of(2026, 3, 2), prediction.windowEnd)
    }

    @Test
    fun missedCycleIsFlagged() {
        val records = starts(LocalDate.of(2025, 12, 7), LocalDate.of(2026, 1, 4))
        val prediction = predictPeriod(records, LocalDate.of(2026, 3, 13))!!

        assertTrue(prediction.missedCycle)
        assertEquals(LocalDate.of(2026, 3, 1), prediction.nextStart)
        assertEquals(12, prediction.daysLate)
    }

    @Test
    fun irregularCycleWidensWindow() {
        val records = starts(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 1, 27),
            LocalDate.of(2026, 3, 2),
        )
        val prediction = predictPeriod(records, today)!!

        assertEquals(31, prediction.stats.cycleLength)
        assertEquals(4, prediction.stats.sdDays)
        assertEquals(8, prediction.stats.variationDays)
        assertEquals(Regularity.SLIGHT, prediction.stats.regularity)
        assertEquals(LocalDate.of(2026, 4, 2), prediction.nextStart)
        assertEquals(LocalDate.of(2026, 3, 29), prediction.windowStart)
        assertEquals(LocalDate.of(2026, 4, 6), prediction.windowEnd)
    }

    @Test
    fun singleRecordUsesDefaultCycleAndWideWindow() {
        val records = starts(LocalDate.of(2026, 2, 26))
        val prediction = predictPeriod(records, today)!!

        assertEquals(28, prediction.stats.cycleLength)
        assertEquals(0, prediction.stats.basedOnCycles)
        assertEquals(Regularity.UNKNOWN, prediction.stats.regularity)
        assertEquals(Confidence.LOW, prediction.confidence)
        assertEquals(LocalDate.of(2026, 3, 21), prediction.windowStart)
        assertEquals(LocalDate.of(2026, 3, 31), prediction.windowEnd)
    }

    // ---------------- 记录清洗 ----------------

    @Test
    fun consecutiveDaysCollapseIntoOnePeriod() {
        val records = starts(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 1, 2),
            LocalDate.of(2026, 1, 3),
            LocalDate.of(2026, 1, 4),
            LocalDate.of(2026, 1, 5),
        )
        val clean = sanitizeRecords(records, today)

        assertEquals(1, clean.size)
        assertEquals(LocalDate.of(2026, 1, 1), clean[0].start)
        assertEquals(LocalDate.of(2026, 1, 5), clean[0].end)
        assertEquals(5, clean[0].lengthDays)
    }

    @Test
    fun mixedRecordingStillUsesStartsOnly() {
        // 一次完整记录（1/1–1/5）+ 两次只记开始日；周期仍是 28 天。
        val records = listOf(
            PeriodRecord(start = LocalDate.of(2026, 1, 1), end = LocalDate.of(2026, 1, 5)),
            PeriodRecord(start = LocalDate.of(2026, 1, 29)),
            PeriodRecord(start = LocalDate.of(2026, 2, 26)),
        )
        val prediction = predictPeriod(records, today)!!

        assertEquals(28, prediction.stats.cycleLength)
        assertEquals(LocalDate.of(2026, 3, 26), prediction.nextStart)
        // 经期长度取已记录的中位数（仅一条已知长度 → 5）
        assertEquals(5, prediction.stats.periodLength)
        assertTrue(prediction.stats.periodLengthKnown)
    }

    @Test
    fun futureRecordsAreDropped() {
        val records = starts(LocalDate.of(2026, 3, 10))
        assertNull(predictPeriod(records, today))
    }

    @Test
    fun invertedEndIsTreatedAsUnknown() {
        val record = PeriodRecord(start = LocalDate.of(2026, 2, 1), end = LocalDate.of(2026, 1, 20))
        val clean = sanitizeRecords(listOf(record), today)

        assertEquals(1, clean.size)
        assertNull(clean[0].end)
        assertNull(clean[0].lengthDays)
    }

    @Test
    fun outlierGapIsExcludedFromCycleEstimate() {
        // 1/1 → 3/12 相隔 70 天，超出 15..60，不参与周期估算；剩下 28/28。
        val records = starts(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 3, 12),
            LocalDate.of(2026, 4, 9),
            LocalDate.of(2026, 5, 7),
        )
        val prediction = predictPeriod(records, LocalDate.of(2026, 5, 8))!!

        assertEquals(28, prediction.stats.cycleLength)
        assertEquals(2, prediction.stats.basedOnCycles)
    }

    @Test
    fun nearGapIsMergedAsSamePeriod() {
        // 相邻 14 天内的两次录入视为同一次经期的重复记录。
        val records = starts(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 10))
        val clean = sanitizeRecords(records, today)

        assertEquals(1, clean.size)
        assertEquals(LocalDate.of(2026, 1, 1), clean[0].start)
        assertEquals(LocalDate.of(2026, 1, 10), clean[0].end)
    }

    // ---------------- 手动覆盖 ----------------

    @Test
    fun manualOverridesWin() {
        val records = starts(LocalDate.of(2026, 2, 26))
        val prediction = predictPeriod(
            records,
            today,
            PeriodSettings(cycleLengthOverride = 30, periodLengthOverride = 7),
        )!!

        assertEquals(30, prediction.stats.cycleLength)
        assertEquals(LocalDate.of(2026, 3, 28), prediction.nextStart)
        assertEquals(7, prediction.stats.periodLength)
        assertTrue(prediction.stats.periodLengthKnown)
        assertEquals(LocalDate.of(2026, 4, 3), prediction.predictedPeriodEnd)
    }

    @Test
    fun confidenceRisesWithHistory() {
        val records = starts(
            LocalDate.of(2025, 11, 1),
            LocalDate.of(2025, 11, 29),
            LocalDate.of(2025, 12, 27),
            LocalDate.of(2026, 1, 24),
            LocalDate.of(2026, 2, 21),
        )
        val prediction = predictPeriod(records, today)!!

        assertEquals(4, prediction.stats.basedOnCycles)
        assertEquals(Confidence.HIGH, prediction.confidence)
    }

    // ---------------- 派生展示值 ----------------

    @Test
    fun periodDayIndexTracksOngoingPeriod() {
        val records = starts(today.minusDays(2))
        assertEquals(3, periodDayIndex(records, today))
    }

    @Test
    fun periodDayIndexStopsAfterDefaultLength() {
        val records = starts(today.minusDays(6))
        assertNull(periodDayIndex(records, today, defaultLength = 5))
    }

    @Test
    fun cycleDayIndexCountsFromLastStart() {
        val records = starts(today.minusDays(11))
        assertEquals(12, cycleDayIndex(records, today))
    }

    @Test
    fun periodDaysCoverPredictedLength() {
        val records = starts(LocalDate.of(2026, 2, 26))
        val prediction = predictPeriod(records, today)!!

        assertEquals(5, prediction.periodDays.size)
        assertEquals(LocalDate.of(2026, 3, 26), prediction.periodDays.first())
        assertEquals(LocalDate.of(2026, 3, 30), prediction.periodDays.last())
    }
}
