package com.example.toolbox.core.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

fun startOfToday(): Long {
    val c = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return c.timeInMillis
}

/** Whole days from today (00:00) until [targetEpochMillis]. Negative if the date has passed. */
fun daysUntil(targetEpochMillis: Long): Long {
    val diff = targetEpochMillis - startOfToday()
    return TimeUnit.MILLISECONDS.toDays(diff)
}

fun formatDate(epochMillis: Long): String = dateFormat.format(epochMillis)

fun greetingNow(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "早上好"
        in 12..17 -> "下午好"
        else -> "晚上好"
    }
}
