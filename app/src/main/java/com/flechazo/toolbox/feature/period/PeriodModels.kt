package com.flechazo.toolbox.feature.period

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 经量分级。[SPOTTING] 为点滴出血，与正式经量区分（Clue 亦把点滴与经量分开记录）。 */
enum class FlowLevel(val label: String) {
    SPOTTING("点滴"),
    LIGHT("少"),
    MEDIUM("中"),
    HEAVY("多"),
}

/**
 * 一次经期。[end] 为 null 表示尚未结束（进行中）。
 *
 * 模型刻意把「周期开始日」与「经期日」分开：旧实现把用户点的每一天都当成一次经期
 * 开始，导致"平均周期"与"已记录次数"双双算错（见 docs/PERIOD_OPTIMIZATION_PLAN.md §2.2）。
 */
data class PeriodRecord(
    val start: LocalDate,
    val end: LocalDate? = null,
    /** 每日经量，键为日期；缺省按 [FlowLevel.MEDIUM] 渲染。 */
    val flows: Map<LocalDate, FlowLevel> = emptyMap(),
    val note: String = "",
) {
    val isOngoing: Boolean get() = end == null

    /** 已知经期长度（含首尾）；[end] 缺失时为 null。 */
    val lengthDays: Int? get() = end?.let { ChronoUnit.DAYS.between(start, it).toInt() + 1 }

    /** 该次经期覆盖的最后一天；进行中时即 [start]。 */
    val lastDay: LocalDate get() = end ?: start

    fun contains(date: LocalDate): Boolean = date >= start && (end == null || date <= end)
}

/** 可记录的经期症状。 */
enum class Symptom(val label: String) {
    CRAMPS("痛经"),
    BACK_PAIN("腰酸"),
    HEADACHE("头痛"),
    BREAST_TENDERNESS("乳房胀痛"),
    FATIGUE("疲劳"),
    MOOD_SWING("情绪波动"),
    INSOMNIA("失眠"),
    APPETITE("食欲变化"),
    NAUSEA("恶心"),
    DIGESTION("腹泻/便秘"),
    SKIN("皮肤问题"),
    DISCHARGE("白带异常"),
}

/** 疼痛程度。 */
enum class PainLevel(val label: String) {
    NONE("无"),
    MILD("轻"),
    MODERATE("中"),
    SEVERE("重"),
}

/** 每日主观记录，与 [PeriodRecord] 解耦：非经期日也能记。 */
data class DailyLog(
    val date: LocalDate,
    val symptoms: Set<Symptom> = emptySet(),
    val pain: PainLevel? = null,
    val note: String = "",
) {
    val isEmpty: Boolean
        get() = symptoms.isEmpty() && pain == null && note.isBlank()
}

/** 用户偏好与手动覆盖。 */
data class PeriodSettings(
    /** 手动指定周期长度（21..45），null 表示自动估算。 */
    val cycleLengthOverride: Int? = null,
    /** 手动指定经期长度（2..10），null 表示自动估算。 */
    val periodLengthOverride: Int? = null,
    val predictEnabled: Boolean = true,
    val showFertileWindow: Boolean = true,
    val showCycleDay: Boolean = true,
    /** 是否已同意本机处理敏感个人信息（PIPL 第 29 条单独同意）。 */
    val consentAccepted: Boolean = false,
)

/** 落盘的顶层文档；[schemaVersion] 用于将来演进与导入校验。 */
data class PeriodData(
    val schemaVersion: Int = CURRENT_SCHEMA,
    val records: List<PeriodRecord> = emptyList(),
    val logs: List<DailyLog> = emptyList(),
    val settings: PeriodSettings = PeriodSettings(),
) {
    companion object {
        const val CURRENT_SCHEMA = 2
    }
}
