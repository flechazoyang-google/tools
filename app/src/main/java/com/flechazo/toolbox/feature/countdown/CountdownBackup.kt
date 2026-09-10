package com.flechazo.toolbox.feature.countdown

import java.time.LocalDate
import java.time.ZoneId

/**
 * 倒数日备份文件的编解码。**纯函数**，因此"旧文件仍可导入"和"导出可再导入"
 * 都能被单测锁住 —— 这两条一旦回归，用户换机就会丢数据，且只在真机上才暴露。
 *
 * 格式策略：**导出的 JSON 是旧格式的超集**。
 * 每条记录同时带 `targetDate`(毫秒) + `type`(字符串) 这些 v1 键，
 * 以及 v2 的新字段。这样：
 * - 旧版 App 读我们的导出：Gson 忽略未知字段，照常工作
 * - 我们读旧版导出：v2 字段缺失 → 走 [v1Defaults] 的兜底值
 * - 我们读自己的导出：全字段还原
 */
object CountdownBackup {

    const val SCHEMA_VERSION = 2

    /** 与旧版 `toolbox_backup.json` 的记录结构对齐，额外字段全部可空/带默认。 */
    data class Event(
        // ---- v1 兼容字段 ----
        val title: String = "",
        val targetDate: Long = 0L,
        val type: String = "countdown",
        val colorTag: String = "",
        val isPinned: Boolean = false,
        val isLunar: Boolean = false,
        val lunarMonth: Int = 0,
        val lunarDay: Int = 0,
        // ---- v2 字段 ----
        val date: String = "",
        val note: String = "",
        val repeat: Int = 0,
        val lunarLeapMonth: Boolean = false,
        val colorKey: String = "",
        val remindEnabled: Boolean? = null,
        val remindDaysBefore: String? = null,
        val remindHour: Int? = null,
        val anchorDate: String? = null,
        val createdAt: Long = 0L,
    )

    data class Document(
        val schemaVersion: Int = SCHEMA_VERSION,
        val countdowns: List<Event> = emptyList(),
    )

    /** 去重键：标题 + 落库日期。与旧实现保持一致，避免重复导入产生双份事件。 */
    fun dedupeKey(e: CountdownEntity): String = "${e.title.trim()}|${e.date}"

    fun toEvent(e: CountdownEntity): Event = Event(
        title = e.title,
        targetDate = CountdownEngine.parseDate(e.date)
            ?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli() ?: 0L,
        type = if (e.type == EventMode.ELAPSED.ordinal) TYPE_ANNIVERSARY else TYPE_COUNTDOWN,
        colorTag = e.colorKey,
        isPinned = e.pinned,
        isLunar = e.isLunar,
        lunarMonth = e.lunarMonth,
        lunarDay = e.lunarDay,
        date = e.date,
        note = e.note,
        repeat = e.repeat,
        lunarLeapMonth = e.lunarLeapMonth,
        colorKey = e.colorKey,
        remindEnabled = e.remindEnabled,
        remindDaysBefore = e.remindDaysBefore,
        remindHour = e.remindHour,
        anchorDate = e.anchorDate,
        createdAt = e.createdAt,
    )

    /**
     * 解码一条记录。
     *
     * 日期优先级：v2 的 `date`(ISO) > v1 的 `targetDate`(毫秒)。
     * 两者都拿不到 → null（调用方计入 invalid，不静默造一条 1970 的脏数据）。
     *
     * 提醒字段的兜底不靠这里判断版本：DTO 的字段全是"可空 + 有默认"，
     * v1 文件缺字段时 Gson 留 null → 落到 `?: true` / `?: '0'` / `?: 9`，
     * 正好等价于 v1 的真实行为（详见 §5.3 规则 2）。
     */
    fun fromEvent(ev: Event): CountdownEntity? {
        if (ev.title.isBlank()) return null
        val date = resolveDate(ev) ?: return null
        val mode = if (ev.type.equals(TYPE_ANNIVERSARY, ignoreCase = true)) {
            EventMode.ELAPSED
        } else {
            EventMode.COUNTDOWN
        }
        return CountdownEntity(
            title = ev.title.trim(),
            date = date.toString(),
            type = mode.ordinal,
            createdAt = ev.createdAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
            note = ev.note,
            repeat = ev.repeat,
            isLunar = ev.isLunar,
            lunarMonth = ev.lunarMonth,
            lunarDay = ev.lunarDay,
            lunarLeapMonth = ev.lunarLeapMonth,
            pinned = ev.isPinned,
            colorKey = ev.colorKey.ifBlank { ev.colorTag.takeIf { CountdownPalette.isKnown(it) } ?: "" },
            // v1 文件没有提醒字段：默认"开 + 当天"，等价于 v1 的真实行为
            remindEnabled = ev.remindEnabled ?: true,
            remindDaysBefore = ev.remindDaysBefore ?: RemindDays.CURRENT_DAY,
            remindHour = (ev.remindHour ?: 9).coerceIn(0, 23),
            anchorDate = ev.anchorDate?.takeIf { CountdownEngine.parseDate(it) != null },
            updatedAt = 0L,
        )
    }

    private fun resolveDate(ev: Event): LocalDate? {
        CountdownEngine.parseDate(ev.date)?.let { return it }
        // 用 != 0 而不是 > 0：v1 存的是 epoch 毫秒，1970 年之前是**负数**。
        // 用 > 0 会把祖辈的生日整条静默丢掉（单测抓到过这个 bug）。
        if (ev.targetDate != 0L) {
            return runCatching {
                java.time.Instant.ofEpochMilli(ev.targetDate).atZone(ZoneId.systemDefault()).toLocalDate()
            }.getOrNull()
        }
        return null
    }

    const val TYPE_COUNTDOWN = "countdown"
    const val TYPE_ANNIVERSARY = "anniversary"
}
