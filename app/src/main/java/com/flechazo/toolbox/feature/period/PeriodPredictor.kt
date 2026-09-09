package com.flechazo.toolbox.feature.period

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** 周期规律性。阈值对齐 FIGO 2018 的「周期波动」口径（≤7 / 8–9 / >9 天）。 */
enum class Regularity(val label: String) {
    REGULAR("规律"),
    SLIGHT("轻度波动"),
    IRREGULAR("不规律"),
    UNKNOWN("数据不足"),
}

/** 预测置信度。仅用于文案与区间宽度，任何档位都不构成避孕依据。 */
enum class Confidence { LOW, MEDIUM, HIGH }

/** 周期统计。 */
data class CycleStats(
    /** 近期加权平均周期长度（或手动覆盖值）。 */
    val cycleLength: Int,
    /** 周期波动范围 = max - min（FIGO 规律性口径）。 */
    val variationDays: Int,
    /** 周期长度总体标准差，用于预测区间宽度。 */
    val sdDays: Int,
    val regularity: Regularity,
    /** 参与计算的周期数。 */
    val basedOnCycles: Int,
    /** 经期长度中位数（或手动覆盖值）。 */
    val periodLength: Int,
    /** 经期长度是否来自真实记录/手动设置，而非默认值。 */
    val periodLengthKnown: Boolean,
)

/** 预测结果。所有日期均为「估算」。 */
data class PeriodPrediction(
    val windowStart: LocalDate,
    val windowEnd: LocalDate,
    /** 中心值 = 预测区间中点。 */
    val nextStart: LocalDate,
    val predictedPeriodEnd: LocalDate,
    /** > 0 表示预计已推迟。 */
    val daysLate: Int,
    /** 迟到超过一个周期 → 疑似漏记。 */
    val missedCycle: Boolean,
    /** 估算排卵日（黄体期按 14 天）。 */
    val ovulation: LocalDate,
    val fertileStart: LocalDate,
    val fertileEnd: LocalDate,
    val confidence: Confidence,
    val stats: CycleStats,
) {
    /** 预测经期覆盖的日期。 */
    val periodDays: List<LocalDate>
        get() = dayRange(nextStart, predictedPeriodEnd)

    /** 预测区间覆盖的日期。 */
    val windowDays: List<LocalDate>
        get() = dayRange(windowStart, windowEnd)

    /** 易孕期覆盖的日期。 */
    val fertileDays: List<LocalDate>
        get() = dayRange(fertileStart, fertileEnd)
}

private fun dayRange(from: LocalDate, to: LocalDate): List<LocalDate> {
    if (to.isBefore(from)) return emptyList()
    return generateSequence(from) { it.plusDays(1) }
        .takeWhile { !it.isAfter(to) }
        .toList()
}

internal const val MIN_CYCLE_GAP = 15
internal const val MAX_CYCLE_GAP = 60
internal const val MAX_CYCLES_USED = 6
internal const val DEFAULT_CYCLE = 28
internal const val DEFAULT_PERIOD = 5
internal const val LUTEAL_PHASE = 14

/**
 * 清洗记录：剔除未来日期、修正 end < start、过滤越界的经量，并合并间隔过近的重复录入。
 *
 * 纯函数，便于单测。任何进入预测/展示的路径都应先过这里。
 */
internal fun sanitizeRecords(records: List<PeriodRecord>, today: LocalDate): List<PeriodRecord> {
    val cleaned = records
        .filter { !it.start.isAfter(today) }
        .map { record ->
            val end = record.end?.takeIf { !it.isBefore(record.start) && !it.isAfter(today) }
            record.copy(
                end = end,
                flows = record.flows.filterKeys { key ->
                    key >= record.start && (end == null || key <= end)
                },
            )
        }
        .sortedBy { it.start }

    val merged = mutableListOf<PeriodRecord>()
    for (record in cleaned) {
        val last = merged.lastOrNull()
        if (last != null && ChronoUnit.DAYS.between(last.lastDay, record.start) < MIN_CYCLE_GAP) {
            merged[merged.size - 1] = last.copy(
                end = maxOf(last.lastDay, record.lastDay),
                flows = last.flows + record.flows,
                note = listOf(last.note, record.note).filter { it.isNotBlank() }.distinct().joinToString(" "),
            )
        } else {
            merged += record
        }
    }
    return merged
}

/**
 * 预测下次经期 / 预测区间 / 估算排卵日与易孕期。
 *
 * 与旧实现的区别：
 * 1. 只使用「周期开始日」，不再把经期内的每一天当成一次开始；
 * 2. 周期长度取最近 6 个周期的**近期加权平均**，而非全历史中位数；
 * 3. 输出**预测区间**（宽度由实际波动决定）而不是单一日期；
 * 4. **不静默顺延**：迟到就返回 [PeriodPrediction.daysLate]，漏记就置 [PeriodPrediction.missedCycle]。
 *
 * 记录为空或关闭预测时返回 null。
 */
internal fun predictPeriod(
    records: List<PeriodRecord>,
    today: LocalDate,
    settings: PeriodSettings = PeriodSettings(),
): PeriodPrediction? {
    if (!settings.predictEnabled) return null

    val clean = sanitizeRecords(records, today)
    if (clean.isEmpty()) return null

    val starts = clean.map { it.start }.distinct().sorted()
    val cycles = starts.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b).toInt() }
        .filter { it in MIN_CYCLE_GAP..MAX_CYCLE_GAP }
        .takeLast(MAX_CYCLES_USED)

    val cycleLength = (settings.cycleLengthOverride ?: weightedMean(cycles) ?: DEFAULT_CYCLE)
        .coerceIn(21, 45)
    val sd = standardDeviation(cycles)

    // 区间宽度：数据越少越宽，波动越大越宽。
    val window = when {
        cycles.isEmpty() -> 5
        cycles.size == 1 -> 3
        else -> sd.roundToInt().coerceIn(1, 5)
    }

    val lastStart = starts.last()
    var center = lastStart.plusDays(cycleLength.toLong())
    var daysLate = ChronoUnit.DAYS.between(center, today).toInt()
    var missed = false
    var guard = 0
    while (daysLate > cycleLength && guard < 24) {
        center = center.plusDays(cycleLength.toLong())
        daysLate = ChronoUnit.DAYS.between(center, today).toInt()
        missed = true
        guard++
    }

    val knownLengths = clean.mapNotNull { it.lengthDays }
    val periodLength = (settings.periodLengthOverride
        ?: median(knownLengths)?.roundToInt()
        ?: DEFAULT_PERIOD).coerceIn(2, 10)
    val periodLengthKnown = settings.periodLengthOverride != null || knownLengths.isNotEmpty()

    val variation = if (cycles.size >= 2) cycles.max() - cycles.min() else 0
    val regularity = when {
        cycles.isEmpty() -> Regularity.UNKNOWN
        variation <= 7 -> Regularity.REGULAR
        variation <= 9 -> Regularity.SLIGHT
        else -> Regularity.IRREGULAR
    }
    val confidence = when {
        cycles.isEmpty() -> Confidence.LOW
        cycles.size <= 3 -> Confidence.MEDIUM
        sd <= 3.0 -> Confidence.HIGH
        else -> Confidence.MEDIUM
    }

    val ovulation = center.minusDays(LUTEAL_PHASE.toLong())
    return PeriodPrediction(
        windowStart = center.minusDays(window.toLong()),
        windowEnd = center.plusDays(window.toLong()),
        nextStart = center,
        predictedPeriodEnd = center.plusDays((periodLength - 1).toLong()),
        daysLate = daysLate,
        missedCycle = missed,
        ovulation = ovulation,
        fertileStart = ovulation.minusDays(5),
        fertileEnd = ovulation.plusDays(1),
        confidence = confidence,
        stats = CycleStats(
            cycleLength = cycleLength,
            variationDays = variation,
            sdDays = sd.roundToInt(),
            regularity = regularity,
            basedOnCycles = cycles.size,
            periodLength = periodLength,
            periodLengthKnown = periodLengthKnown,
        ),
    )
}

/** 今天是本次经期的第几天（1 起算）；不在经期内返回 null。 */
internal fun periodDayIndex(
    records: List<PeriodRecord>,
    today: LocalDate,
    defaultLength: Int = DEFAULT_PERIOD,
): Int? {
    val record = records
        .filter { !it.start.isAfter(today) }
        .maxByOrNull { it.start }
        ?: return null
    val lastDay = record.end ?: record.start.plusDays((defaultLength - 1).toLong())
    if (today.isAfter(lastDay)) return null
    return ChronoUnit.DAYS.between(record.start, today).toInt() + 1
}

/** 今天是当前周期的第几天（1 起算）；无记录返回 null。 */
internal fun cycleDayIndex(records: List<PeriodRecord>, today: LocalDate): Int? {
    val start = records.map { it.start }.filter { !it.isAfter(today) }.maxOrNull() ?: return null
    return ChronoUnit.DAYS.between(start, today).toInt() + 1
}

/** 最近一次经期长度（用于"预计 N 天后结束"），无已知长度时返回 null。 */
internal fun expectedPeriodLength(prediction: PeriodPrediction?): Int =
    prediction?.stats?.periodLength ?: DEFAULT_PERIOD

/** 近期加权平均：权重 1..n，越近权重越大。 */
private fun weightedMean(values: List<Int>): Int? {
    if (values.isEmpty()) return null
    var weighted = 0.0
    var total = 0.0
    values.forEachIndexed { index, value ->
        val weight = (index + 1).toDouble()
        weighted += value * weight
        total += weight
    }
    return (weighted / total).roundToInt()
}

private fun standardDeviation(values: List<Int>): Double {
    if (values.size < 2) return 0.0
    val mean = values.sum().toDouble() / values.size
    val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
    return sqrt(variance)
}

private fun median(values: List<Int>): Double? {
    if (values.isEmpty()) return null
    val sorted = values.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) {
        sorted[mid].toDouble()
    } else {
        (sorted[mid - 1] + sorted[mid]) / 2.0
    }
}
