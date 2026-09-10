package com.flechazo.toolbox.feature.countdown

import java.time.LocalDate

/**
 * 编辑表单的草稿态。与 [CountdownEntity] 分开，是因为表单需要"农历月/日"这种
 * 用户心智里的输入，而落库必须是一个确定的公历日；同时编辑时要保住 `createdAt`。
 *
 * 纯 Kotlin，可单测（[toEntity] 的农历解析是本工具最容易出错的地方）。
 */
data class CountdownDraft(
    val title: String = "",
    val mode: EventMode = EventMode.COUNTDOWN,
    val solarDate: LocalDate = LocalDate.now(),
    val isLunar: Boolean = false,
    /** 仅 isLunar 为真时有意义 */
    val lunarMonth: Int = 1,
    val lunarDay: Int = 1,
    val lunarLeap: Boolean = false,
    val repeat: RepeatRule = RepeatRule.NONE,
    val remindDays: List<Int> = listOf(0),
    val remindHour: Int = 9,
    val pinned: Boolean = false,
    val colorKey: String = "",
    val note: String = "",
    /** 进度条起点，null = 不显示 */
    val anchorDate: LocalDate? = null,
) {

    val titleValid: Boolean get() = title.isNotBlank()

    /** 重复规则里"每年"与农历的组合关系（表单需要据此禁用/联动选项）。 */
    val effectiveRepeat: RepeatRule
        get() = when {
            repeat == RepeatRule.YEARLY_SOLAR && isLunar -> RepeatRule.YEARLY_LUNAR
            repeat == RepeatRule.YEARLY_LUNAR && !isLunar -> RepeatRule.YEARLY_SOLAR
            else -> repeat
        }

    /**
     * 草稿 → 实体。返回 null 表示"不能保存"（目前只有标题为空这一种）。
     *
     * 农历事件的落库日期是一个**确定的公历日**，由 [resolveLunarAnchor] 求出；
     * 农历字段本身另存，供每年推进时重新解析（闰月与大小月每年都在变）。
     */
    fun toEntity(lunar: LunarCalendar, today: LocalDate, existing: CountdownEntity? = null): CountdownEntity? {
        if (!titleValid) return null
        val rule = effectiveRepeat

        val resolved: LocalDate = if (isLunar) resolveLunarAnchor(lunar, today) else solarDate

        return CountdownEntity(
            id = existing?.id ?: 0L,
            title = title.trim(),
            date = resolved.toString(),
            type = mode.ordinal,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            note = note.trim(),
            repeat = rule.ordinal,
            isLunar = isLunar,
            lunarMonth = if (isLunar) lunarMonth else 0,
            lunarDay = if (isLunar) lunarDay else 0,
            lunarLeapMonth = isLunar && lunarLeap,
            pinned = pinned,
            colorKey = colorKey,
            remindEnabled = remindDays.isNotEmpty(),
            remindDaysBefore = RemindDays.encode(remindDays),
            remindHour = remindHour.coerceIn(0, 23),
            anchorDate = anchorDate?.toString(),
            updatedAt = System.currentTimeMillis(),
        )
    }

    /**
     * 把农历月/日解析成一个确定的公历日作为落库锚点。
     *
     * - `COUNTDOWN`：取 **今天或之后** 的第一次发生（用户关心的就是"还有几天"）
     * - `ELAPSED`：取 **今天或之前** 的最近一次发生（农历生日不能跑到未来去）
     *
     * 农历数据越界（超出支持年份）时降级为表单上那个公历日期，**不阻断保存**。
     */
    private fun resolveLunarAnchor(lunar: LunarCalendar, today: LocalDate): LocalDate {
        val start = lunar.solarToLunar(today) ?: return solarDate
        val requested = LunarDate(year = start.year, month = lunarMonth, day = lunarDay, isLeap = lunarLeap)
        val backwards = mode == EventMode.ELAPSED
        var year = start.year
        repeat(4) {
            val r = LunarResolver.resolve(lunar, requested.copy(year = year))
            if (r != null) {
                val hit = if (backwards) !r.solar.isAfter(today) else !r.solar.isBefore(today)
                if (hit) return r.solar
            }
            year += if (backwards) -1 else 1
        }
        return solarDate
    }

    companion object {
        /**
         * 实体 → 表单草稿。
         *
         * 农历字段缺失时（v1 备份里 `isLunar=true` 但没带 `lunarMonth/lunarDay` 的老数据）
         * 按落库的公历日反推一次 —— 反推出来的是"那一天恰好是农历几月初几"，
         * 比给用户显示一个空白的农历选择器诚实，也避免了老数据在编辑一次后被静默改成公历事件。
         */
        fun fromEntity(e: CountdownEntity, lunar: LunarCalendar, today: LocalDate): CountdownDraft {
            val solar = CountdownEngine.parseDate(e.date) ?: today
            val stored = LunarDate(0, e.lunarMonth, e.lunarDay, e.lunarLeapMonth)
            val derived = if (e.isLunar &&
                (stored.month !in 1..12 || stored.day !in 1..30)
            ) {
                lunar.solarToLunar(solar) ?: stored
            } else {
                stored
            }
            return CountdownDraft(
                title = e.title,
                mode = e.mode,
                solarDate = solar,
                isLunar = e.isLunar,
                lunarMonth = derived.month.takeIf { it in 1..12 } ?: 1,
                lunarDay = derived.day.takeIf { it in 1..30 } ?: 1,
                lunarLeap = derived.isLeap,
                repeat = e.repeatRule,
                remindDays = RemindDays.decode(e.remindDaysBefore)
                    .ifEmpty { if (e.remindEnabled) listOf(0) else emptyList() },
                remindHour = e.remindHour.coerceIn(0, 23),
                pinned = e.pinned,
                colorKey = e.colorKey,
                note = e.note,
                anchorDate = CountdownEngine.parseDate(e.anchorDate),
            )
        }
    }
}
