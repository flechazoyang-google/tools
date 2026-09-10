package com.flechazo.toolbox.feature.countdown

import java.time.LocalDate
import java.time.Period
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.min

/**
 * 倒数日语义引擎。**全部纯函数**：不读 `LocalDate.now()`、不碰 Android API，
 * 因此可在 JVM 单测里完整覆盖（含农历回退策略）。
 */
object CountdownEngine {

    /** ToolCatalog 里的注册 id；通知 deep link 与埋点都用它，避免字符串散落各处。 */
    const val TOOL_ID = "countdown"

    /** 农历逐年扫描的上限，防脏数据把循环跑飞 */
    private const val MAX_LUNAR_YEAR_SCAN = 6

    /** 解析 yyyy-MM-dd；脏数据返回 null，调用方降级显示。 */
    fun parseDate(raw: String?): LocalDate? =
        raw?.let { runCatching { LocalDate.parse(it.trim()) }.getOrNull() }

    // -------------------------------------------------------------------------
    // 发生日推进（修 P0-2：生日/纪念日按年自动滚动）
    // -------------------------------------------------------------------------

    /**
     * 下一次发生日（>= [today]）。返回 null 表示"不再发生"：
     * 一次性事件且日期已过、或数据非法、或农历越界。
     */
    fun nextOccurrence(
        e: CountdownEntity,
        today: LocalDate,
        lunar: LunarCalendar,
    ): LocalDate? {
        val base = parseDate(e.date) ?: return null
        // 第一次发生就是事件本身设定的那一天，不能被"滚动"提前吃掉
        if (!base.isBefore(today)) return base

        return when (e.repeatRule) {
            RepeatRule.NONE -> null

            // 全部用算术而非 while 推进：一次性求余/定位，天然无死循环风险
            RepeatRule.WEEKLY -> {
                val rem = ChronoUnit.DAYS.between(base, today) % 7
                if (rem == 0L) today else today.plusDays(7 - rem)
            }

            RepeatRule.MONTHLY -> {
                var c = clampToMonth(YearMonth.from(today), base.dayOfMonth)
                if (c.isBefore(today)) c = clampToMonth(YearMonth.from(today).plusMonths(1), base.dayOfMonth)
                if (c.isBefore(base)) null else c
            }

            RepeatRule.YEARLY_SOLAR -> {
                var c = clampToMonth(YearMonth.of(today.year, base.monthValue), base.dayOfMonth)
                if (c.isBefore(today)) c = clampToMonth(YearMonth.of(today.year + 1, base.monthValue), base.dayOfMonth)
                if (c.isBefore(base)) null else c
            }

            RepeatRule.YEARLY_LUNAR -> nextLunar(e, base, today, lunar)
        }
    }

    /** 上一次发生日（<= [today]）；无则 null。 */
    fun lastOccurrence(
        e: CountdownEntity,
        today: LocalDate,
        lunar: LunarCalendar,
    ): LocalDate? {
        val base = parseDate(e.date) ?: return null
        if (base.isAfter(today)) return null

        return when (e.repeatRule) {
            RepeatRule.NONE -> base

            RepeatRule.WEEKLY -> {
                val delta = ChronoUnit.DAYS.between(base, today)
                if (delta < 0) null else today.minusDays(delta % 7)
            }

            RepeatRule.MONTHLY -> {
                var c = clampToMonth(YearMonth.from(today), base.dayOfMonth)
                if (c.isAfter(today)) c = clampToMonth(YearMonth.from(today).minusMonths(1), base.dayOfMonth)
                if (c.isBefore(base)) null else c
            }

            RepeatRule.YEARLY_SOLAR -> {
                var c = clampToMonth(YearMonth.of(today.year, base.monthValue), base.dayOfMonth)
                if (c.isAfter(today)) c = clampToMonth(YearMonth.of(today.year - 1, base.monthValue), base.dayOfMonth)
                if (c.isBefore(base)) null else c
            }

            RepeatRule.YEARLY_LUNAR -> lastLunar(e, base, today, lunar)
        }
    }

    /**
     * 把 [dayOfMonth] 落到目标月的最后一天之内。
     * 覆盖两条边界：每月 31 号 → 2 月取月末；每年 2/29 → 平年取 2/28。
     */
    private fun clampToMonth(month: YearMonth, dayOfMonth: Int): LocalDate =
        month.atDay(min(dayOfMonth, month.lengthOfMonth()))

    private fun lunarFields(e: CountdownEntity, base: LocalDate, lunar: LunarCalendar): LunarDate? {
        val m = e.lunarMonth
        val d = e.lunarDay
        if (m in 1..12 && d in 1..30) {
            return LunarDate(year = 0, month = m, day = d, isLeap = e.lunarLeapMonth)
        }
        // 表单没填农历字段（例如从 v1 迁移过来的数据）：按 base 反推
        return lunar.solarToLunar(base)
    }

    private fun nextLunar(
        e: CountdownEntity,
        base: LocalDate,
        today: LocalDate,
        lunar: LunarCalendar,
    ): LocalDate? {
        val f = lunarFields(e, base, lunar) ?: return null
        // 游标从"今天所在的农历年"起算，而不是 base 的农历年。
        // 早先用 base 年起步 + 固定 6 年的窗口：base 是 2025 年的事件，扫到 2031 年就再也
        // 够不到未来了 —— 农历生日会在若干年后静默停摆，永远显示"已过 N 天"。
        val cursor = lunar.solarToLunar(today)?.year ?: return null
        var year = cursor
        repeat(MAX_LUNAR_YEAR_SCAN) {
            if (!lunar.isValidYear(year)) return null
            val r = LunarResolver.resolve(lunar, f.copy(year = year))
            if (r != null && !r.solar.isBefore(today) && !r.solar.isBefore(base)) return r.solar
            year++
        }
        return null
    }

    private fun lastLunar(
        e: CountdownEntity,
        base: LocalDate,
        today: LocalDate,
        lunar: LunarCalendar,
    ): LocalDate? {
        val f = lunarFields(e, base, lunar) ?: return null
        val start = lunar.solarToLunar(today) ?: return base
        var year = start.year
        repeat(MAX_LUNAR_YEAR_SCAN) {
            if (!lunar.isValidYear(year)) return base
            val r = LunarResolver.resolve(lunar, f.copy(year = year))
            if (r != null && !r.solar.isAfter(today) && !r.solar.isBefore(base)) return r.solar
            year--
        }
        return base
    }

    // -------------------------------------------------------------------------
    // 派生模型
    // -------------------------------------------------------------------------

    fun buildItem(
        e: CountdownEntity,
        today: LocalDate,
        lunar: LunarCalendar,
    ): CountdownItem {
        val base = parseDate(e.date)
        val next = nextOccurrence(e, today, lunar)
        val last = lastOccurrence(e, today, lunar)
        val anchor = parseDate(e.anchorDate)
        val progress = if (anchor != null && next != null && next.isAfter(anchor)) {
            val total = ChronoUnit.DAYS.between(anchor, next).toFloat()
            val done = ChronoUnit.DAYS.between(anchor, today).toFloat()
            if (total <= 0f) null else (done / total).coerceIn(0f, 1f)
        } else {
            null
        }
        val lunarLabel = next?.let { n -> lunar.solarToLunar(n)?.let { l -> formatLunarDay(l) } }

        return CountdownItem(
            event = e,
            base = base,
            nextDate = next,
            lastDate = last,
            daysToNext = next?.let { ChronoUnit.DAYS.between(today, it) } ?: 0L,
            daysSinceLast = last?.let { ChronoUnit.DAYS.between(it, today) } ?: 0L,
            isToday = next != null && next == today,
            progress = progress,
            lunarLabel = lunarLabel,
            lunarFallback = base != null && next != null && e.isLunar && lunarDiffers(e, base, next, lunar),
        )
    }

    /**
     * 实际生效的农历日期与用户设定的不一致 → 发生了"闰月缺失"或"该月无此日"回退，
     * UI 必须标注（否则用户会以为生日算错了）。
     */
    private fun lunarDiffers(
        e: CountdownEntity,
        base: LocalDate,
        occurred: LocalDate,
        lunar: LunarCalendar,
    ): Boolean {
        val req = lunarFields(e, base, lunar) ?: return false
        val actual = lunar.solarToLunar(occurred) ?: return false
        return actual.month != req.month || actual.day != req.day || actual.isLeap != req.isLeap
    }

    // -------------------------------------------------------------------------
    // 文案（取代旧 countdownLabel(target, type, today)）
    // -------------------------------------------------------------------------

    /** 1 万位以上加千分位，避免"12345天"糊成一片。 */
    fun formatDays(n: Long): String = "%,d".format(n).replace(',', ' ')

    /**
     * 方向由"日期 vs 今天"推导，而不是让用户去理解"倒数日/纪念日"这两个词（修 P0-4）。
     * [mode] 只决定"已经过去了"这件事怎么措辞。
     */
    fun displayOf(item: CountdownItem): CountdownDisplay {
        if (item.base == null) {
            return CountdownDisplay(DisplayKind.INVALID, "—", "", "日期无效", null, null, null)
        }
        val next = item.nextDate
        if (next == null) {
            val since = item.daysSinceLast
            return if (item.event.mode == EventMode.ELAPSED) {
                CountdownDisplay(
                    kind = DisplayKind.SINCE,
                    bigNumber = formatDays(since),
                    unit = "天",
                    headline = "已 $since 天",
                    subline = null,
                    breakdown = breakdownOf(item.base, item),
                    progress = null,
                )
            } else {
                CountdownDisplay(
                    kind = DisplayKind.PASSED,
                    bigNumber = formatDays(since),
                    unit = "天",
                    headline = "已过 $since 天",
                    subline = null,
                    breakdown = breakdownOf(item.base, item),
                    progress = null,
                )
            }
        }
        if (item.isToday) {
            return CountdownDisplay(
                kind = DisplayKind.TODAY,
                bigNumber = "今天",
                unit = "",
                headline = "就是今天",
                subline = null,
                breakdown = breakdownOf(item.base, item),
                progress = item.progress,
            )
        }
        return CountdownDisplay(
            kind = DisplayKind.TO_TARGET,
            bigNumber = formatDays(item.daysToNext),
            unit = "天",
            headline = "还有 ${item.daysToNext} 天",
            subline = null,
            breakdown = if (item.event.mode == EventMode.ELAPSED) breakdownOf(item.base, item) else null,
            progress = item.progress,
        )
    }

    /** "3 年 2 个月 17 天"；不足 100 天返回 null（UI 只显示天数即可，不必堆砌）。 */
    fun decompose(from: LocalDate, to: LocalDate): DurationParts? {
        if (from.isAfter(to)) return null
        val total = ChronoUnit.DAYS.between(from, to)
        if (total < 100) return null
        val p = Period.between(from, to)
        return DurationParts(p.years, p.months, p.days, total)
    }

    private fun breakdownOf(base: LocalDate, item: CountdownItem): String? {
        val ref = item.lastDate ?: return null
        val parts = decompose(base, ref) ?: return null
        return parts.label
    }

    /** 农历"四月廿一"这种日名，不含年与闰月前缀。 */
    fun formatLunarDay(d: LunarDate): String {
        val months = arrayOf("正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "腊")
        val ones = arrayOf("十", "一", "二", "三", "四", "五", "六", "七", "八", "九")
        val monthName = months.getOrElse(d.month - 1) { "?" } + "月"
        val dayName = when {
            d.day == 10 -> "初十"
            d.day == 20 -> "二十"
            d.day == 30 -> "三十"
            d.day < 10 -> "初" + ones[d.day]
            d.day < 20 -> "十" + ones[d.day - 10]
            d.day < 30 -> "廿" + ones[d.day - 20]
            else -> "三十"
        }
        return (if (d.isLeap) "闰" else "") + monthName + dayName
    }

    // -------------------------------------------------------------------------
    // 排序与分组（修 P0-5）
    // -------------------------------------------------------------------------

    enum class SortMode(val label: String) {
        NEAREST("临近优先"),
        CREATED("最近添加"),
        TITLE("按名称"),
    }

    /** 置顶永远优先；组内按选定方式。 */
    fun comparator(mode: SortMode): Comparator<CountdownItem> {
        val byPinned = compareByDescending<CountdownItem> { it.event.pinned }
        val byMode: Comparator<CountdownItem> = when (mode) {
            SortMode.NEAREST -> compareBy<CountdownItem> { it.nextDate == null }.thenBy { it.daysToNext }
            SortMode.CREATED -> compareByDescending { it.event.createdAt }
            SortMode.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.event.title }
        }
        return byPinned.then(byMode)
    }

    fun sorted(items: List<CountdownItem>, mode: SortMode): List<CountdownItem> =
        items.sortedWith(comparator(mode))

    /** 列表分组。 */
    fun groupOf(item: CountdownItem): GroupKind {
        if (item.nextDate == null) return GroupKind.PASSED
        return when {
            item.isToday -> GroupKind.TODAY
            item.daysToNext <= 7 -> GroupKind.WITHIN_WEEK
            item.daysToNext <= 30 -> GroupKind.WITHIN_MONTH
            else -> GroupKind.LATER
        }
    }
}

enum class GroupKind(val label: String) {
    TODAY("就是今天"),
    WITHIN_WEEK("本周内"),
    WITHIN_MONTH("本月内"),
    LATER("更远的未来"),
    PASSED("已过去"),
}

/** 年 / 月 / 日分解。 */
data class DurationParts(val years: Int, val months: Int, val days: Int, val totalDays: Long) {
    val label: String
        get() = buildString {
            if (years > 0) append("$years 年 ")
            if (months > 0) append("$months 个月 ")
            if (days > 0 || (years == 0 && months == 0)) append("$days 天")
        }.trim()
}

/** 一次事件的派生展示数据。不落库，由 [CountdownEngine.buildItem] 生成。 */
data class CountdownItem(
    val event: CountdownEntity,
    val base: LocalDate?,
    val nextDate: LocalDate?,
    val lastDate: LocalDate?,
    val daysToNext: Long,
    val daysSinceLast: Long,
    val isToday: Boolean,
    val progress: Float?,
    /** "四月廿一"；非农历事件也可能有值（作为副行补充） */
    val lunarLabel: String?,
    val lunarFallback: Boolean,
) {
    val display: CountdownDisplay get() = CountdownEngine.displayOf(this)
}

enum class DisplayKind { TODAY, TO_TARGET, SINCE, PASSED, INVALID }

data class CountdownDisplay(
    val kind: DisplayKind,
    val bigNumber: String,
    val unit: String,
    val headline: String,
    val subline: String?,
    val breakdown: String?,
    val progress: Float?,
)
