package com.flechazo.toolbox.core.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.flechazo.toolbox.feature.countdown.CountdownEntity
import com.flechazo.toolbox.feature.countdown.CountdownReminders
import com.flechazo.toolbox.feature.countdown.LunarCalendar
import com.flechazo.toolbox.feature.countdown.RemindDays
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 倒数日提醒排期器（应用级，取代原先散落在 ViewModel 里的直接调用）。
 *
 * 两条刻意的设计约束：
 *
 * 1. **只用非精确闹钟**（`setWindow` 而非 `setExactAndAllowWhileIdle`）。
 *    API 31+ 精确闹钟需要用户去系统设置手动授予 `SCHEDULE_EXACT_ALARM`，
 *    而"当天 9 点提醒"对十几分钟的偏差不敏感，换来零授权门槛是划算的。
 * 2. **取消闹钟不依赖排期记录**。每个事件的待取消闹钟恰好落在 [RemindDays.OPTIONS]
 *    这几档上，直接遍历 cancel 即可 —— 无需为维护"当前已排期集合"再造一层持久化。
 */
@Singleton
class CountdownScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val alarmManager: AlarmManager?
        get() = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    /** 为一个事件排期；同时先清掉它旧的各档闹钟，避免用户改档位后残留。 */
    fun arm(
        event: CountdownEntity,
        lunar: LunarCalendar,
        today: LocalDate = LocalDate.now(),
        now: LocalDateTime = LocalDateTime.now(),
    ) {
        cancel(event.id)
        val triggers = CountdownReminders.upcoming(event, today, now, lunar)
        val manager = alarmManager ?: return
        triggers.forEach { t ->
            val millis = t.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            if (millis <= System.currentTimeMillis()) return@forEach
            val pi = pendingIntent(event, t)
            // minSdk 26，setWindow（API 19+）恒可用，无需版本分支
            runCatching { manager.setWindow(AlarmManager.RTC_WAKEUP, millis, WINDOW_FLEX_MS, pi) }
        }
    }

    /** 取消某事件所有档位的闹钟。 */
    fun cancel(id: Long) {
        val manager = alarmManager ?: return
        RemindDays.OPTIONS.forEach { d ->
            val code = CountdownReminders.requestCode(id, d)
            val pi = PendingIntent.getBroadcast(
                context,
                code,
                Intent(context, CountdownAlarmReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            ) ?: return@forEach
            runCatching { manager.cancel(pi) }
            runCatching { pi.cancel() }
        }
    }

    private fun pendingIntent(event: CountdownEntity, trigger: CountdownReminders.Trigger): PendingIntent {
        val code = CountdownReminders.requestCode(event.id, trigger.daysBefore)
        val intent = Intent(context, CountdownAlarmReceiver::class.java).apply {
            putExtra(CountdownAlarmReceiver.EXTRA_ID, event.id)
            putExtra(CountdownAlarmReceiver.EXTRA_TITLE, event.title)
            putExtra(CountdownAlarmReceiver.EXTRA_DAYS_BEFORE, trigger.daysBefore)
            // 把"哪一次发生"一起带上：重复事件明年、后年的提醒内容才不会错位
            putExtra(CountdownAlarmReceiver.EXTRA_OCCURRENCE, trigger.occurrence.toString())
            putExtra(CountdownAlarmReceiver.EXTRA_REPEAT, event.repeat)
        }
        return PendingIntent.getBroadcast(
            context,
            code,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val WINDOW_FLEX_MS = 10L * 60 * 1000
    }
}
