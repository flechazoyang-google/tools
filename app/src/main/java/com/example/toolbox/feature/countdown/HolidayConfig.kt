package com.example.toolbox.feature.countdown

import com.example.toolbox.core.util.LunarUtils
import com.example.toolbox.core.util.startOfToday
import com.example.toolbox.data.local.entity.CountdownEntity

/**
 * Built-in holiday configuration.
 * Each holiday defines its name, month, day, whether it's lunar, and display color.
 */
data class BuiltInHoliday(
    val id: String,
    val title: String,
    val month: Int,            // month (1-12 for solar, 1-12 for lunar)
    val day: Int,              // day of month
    val isLunar: Boolean,
    val colorTag: String,
    val defaultVisible: Boolean = true,
)

/** Predefined holiday list — mixed solar and lunar holidays. */
val BUILT_IN_HOLIDAYS = listOf(
    // --- Lunar holidays ---
    BuiltInHoliday("spring_festival",  "春节",     1,  1,  isLunar = true,  colorTag = "#EF4444"),
    BuiltInHoliday("lantern",         "元宵节",   1,  15, isLunar = true,  colorTag = "#F59E0B"),
    BuiltInHoliday("dragon_boat",     "端午节",   5,  5,  isLunar = true,  colorTag = "#10B981"),
    BuiltInHoliday("qixi",            "七夕",     7,  7,  isLunar = true,  colorTag = "#EC4899"),
    BuiltInHoliday("mid_autumn",      "中秋节",   8,  15, isLunar = true,  colorTag = "#F59E0B"),
    BuiltInHoliday("double_ninth",    "重阳节",   9,  9,  isLunar = true,  colorTag = "#8B5CF6"),
    BuiltInHoliday("new_year_eve",    "除夕",     12, 30, isLunar = true,  colorTag = "#EF4444"),

    // --- Solar holidays ---
    BuiltInHoliday("new_year",        "元旦",     1,  1,  isLunar = false, colorTag = "#4F7CFF"),
    BuiltInHoliday("valentine",       "情人节",   2,  14, isLunar = false, colorTag = "#EC4899"),
    BuiltInHoliday("women_day",       "妇女节",   3,  8,  isLunar = false, colorTag = "#EC4899"),
    BuiltInHoliday("qingming",        "清明节",   4,  5,  isLunar = false, colorTag = "#8B5CF6"),
    BuiltInHoliday("labor_day",       "劳动节",   5,  1,  isLunar = false, colorTag = "#4F7CFF"),
    BuiltInHoliday("national_day",    "国庆节",   10, 1,  isLunar = false, colorTag = "#EF4444"),
    BuiltInHoliday("christmas",       "圣诞节",   12, 25, isLunar = false, colorTag = "#4F7CFF"),
)

/**
 * Convert a BuiltInHoliday to a CountdownEntity for display.
 * For lunar holidays, targetDate is dynamically computed;
 * for solar holidays, targetDate is the next occurrence of the fixed date.
 */
fun BuiltInHoliday.toEntity(today: Long): CountdownEntity {
    val targetMs = if (isLunar) {
        LunarUtils.lunarToNextSolar(month, day, today)
    } else {
        nextSolarDate(month, day, today)
    }
    return CountdownEntity(
        id = id.hashCode().toLong(),  // stable fake ID for built-in items
        title = title,
        targetDate = targetMs,
        type = "countdown",
        colorTag = colorTag,
        isPinned = false,
        isLunar = isLunar,
        lunarMonth = if (isLunar) month else 0,
        lunarDay = if (isLunar) day else 0,
    )
}

/** Compute the next solar date (epoch millis) for a fixed month/day. */
private fun nextSolarDate(month: Int, day: Int, today: Long): Long {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = today }
    val thisYear = cal.get(java.util.Calendar.YEAR)
    val tz = java.util.TimeZone.getTimeZone("Asia/Shanghai")

    // Try this year
    val thisYearCal = java.util.Calendar.getInstance(tz).apply {
        set(thisYear, month - 1, day, 0, 0, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    if (thisYearCal.timeInMillis >= today) return thisYearCal.timeInMillis

    // Try next year
    return java.util.Calendar.getInstance(tz).apply {
        set(thisYear + 1, month - 1, day, 0, 0, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
}

/** Holiday visibility key for DataStore: "holiday_visible_{id}". */
fun holidayVisibleKey(id: String) = "holiday_visible_$id"
