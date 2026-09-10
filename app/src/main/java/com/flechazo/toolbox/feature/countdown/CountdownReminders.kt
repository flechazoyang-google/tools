package com.flechazo.toolbox.feature.countdown

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 提醒触发点计算。**纯函数**，不依赖 Android，可在 JVM 单测里完整覆盖。
 *
 * 一个事件只排"下一个（和再下一个）发生日"的提醒，而不是提前一年把闹钟塞满：
 * 每次 `rescheduleAll` 都会重算，重复事件自然随之滚动，闹钟数量恒装有界。
 */
object CountdownReminders {

    data class Trigger(
        /** 提前天数，0 = 当天 */
        val daysBefore: Int,
        /** 本地触发时刻 */
        val at: LocalDateTime,
        /** 对应的那一次发生日 */
        val occurrence: LocalDate,
    )

    /** 待排期的未来触发点（`at > now`），交给 [com.flechazo.toolbox.core.notify.CountdownScheduler] 落闹钟。 */
    fun upcoming(
        e: CountdownEntity,
        today: LocalDate,
        now: LocalDateTime,
        lunar: LunarCalendar,
        max: Int = RemindDays.MAX_TRIGGERS,
    ): List<Trigger> = scan(e, today, lunar, max) { it.isAfter(now) }

    /**
     * 刚刚错过、应当补发的触发点（`now - window < at <= now`）。
     *
     * 覆盖厂商 ROM 强杀进程 / Doze 丢弃闹钟的情形 —— 由每日兜底任务调用。
     * 错过超过 [windowHours] 就不补了：一条"还有 3 天"的提醒晚到半天仍有意义，
     * 晚到三天只会变成噪音。
     */
    fun missed(
        e: CountdownEntity,
        today: LocalDate,
        now: LocalDateTime,
        lunar: LunarCalendar,
        windowHours: Long = 12,
    ): List<Trigger> {
        val floor = now.minusHours(windowHours)
        return scan(e, today, lunar, RemindDays.MAX_TRIGGERS) {
            it.isAfter(floor) && !it.isAfter(now)
        }
    }

    private fun scan(
        e: CountdownEntity,
        today: LocalDate,
        lunar: LunarCalendar,
        max: Int,
        keep: (LocalDateTime) -> Boolean,
    ): List<Trigger> {
        if (!e.remindEnabled) return emptyList()
        val days = RemindDays.decode(e.remindDaysBefore)
        if (days.isEmpty()) return emptyList()
        val hour = e.remindHour.coerceIn(0, 23)

        val out = ArrayList<Trigger>(max)
        var occurrence = CountdownEngine.nextOccurrence(e, today, lunar)
        var guard = 0
        while (occurrence != null && guard < MAX_OCCURRENCE_SCAN) {
            guard++
            for (d in days) {
                val at = occurrence.minusDays(d.toLong()).atTime(hour, 0)
                if (keep(at)) out += Trigger(d, at, occurrence)
            }
            val next = CountdownEngine.nextOccurrence(e, occurrence.plusDays(1), lunar)
            // 返回同一天的两种情况都要停：一次性事件已耗尽，或数据退化导致原地打转
            occurrence = if (next == null || next == occurrence) null else next
        }
        return out.sortedBy { it.at }.take(max)
    }

    /**
     * 闹钟 requestCode，按 `(事件 id, 提前天数)` 编码。
     * 同一档提醒重复排期会自然覆盖旧闹钟而不堆积；`id < 4_000_000` 时天然无碰撞。
     *
     * 先给 id 封顶再乘：直接 `id * 512` 在极端 id 下会**溢出成负数**，
     * 于是绕过"超过 Int.MAX_VALUE 就夹住"的保护，把两个事件映射到同一个 code ——
     * 表现为后排的提醒静默顶掉前排的。脏数据不该能造成这种串台。
     */
    fun requestCode(id: Long, daysBefore: Int): Int {
        val cappedId = id.coerceIn(0L, MAX_SAFE_ID)
        return (cappedId * TIER_SPAN + daysBefore.coerceIn(0, 511)).toInt()
    }

    private const val TIER_SPAN = 512L
    private const val MAX_SAFE_ID = (Int.MAX_VALUE - 511) / TIER_SPAN

    private const val MAX_OCCURRENCE_SCAN = 8
}
