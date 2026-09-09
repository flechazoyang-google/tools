package com.flechazo.toolbox.core.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.flechazo.toolbox.MainActivity
import com.flechazo.toolbox.R
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 倒数日 / 纪念日当天提醒。
 *
 * 使用 [AlarmManager.set]（非精确闹钟）而非 `setExactAndAllowWhileIdle`：
 * API 31+ 精确闹钟需要用户手动授予 `SCHEDULE_EXACT_ALARM`，
 * 而"当天提醒"对几分钟的偏差不敏感，用非精确闹钟可以零权限可用。
 */
object CountdownNotifications {

    const val CHANNEL_ID = "countdown_reminders"
    const val EXTRA_ID = "event_id"
    const val EXTRA_TITLE = "event_title"

    /** 提醒时间：事件当天 09:00。 */
    private val REMINDER_TIME: LocalTime = LocalTime.of(9, 0)

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "倒数日提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "倒数日 / 纪念日当天的提醒"
            },
        )
    }

    /** 是否已获得通知权限（API 33 以下始终为 true）。 */
    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    /** 为单个事件安排提醒；日期已过或就在今天更早时间则取消。 */
    fun schedule(context: Context, id: Long, title: String, dateIso: String) {
        val date = runCatching { LocalDate.parse(dateIso) }.getOrNull() ?: return
        val triggerAt = date.atTime(REMINDER_TIME).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (triggerAt <= System.currentTimeMillis()) {
            cancel(context, id)
            return
        }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent(context, id, title))
        }
    }

    fun cancel(context: Context, id: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching { alarmManager.cancel(pendingIntent(context, id, "")) }
    }

    fun notify(context: Context, id: Long, title: String) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val contentIntent = PendingIntent.getActivity(
            context,
            id.toInt(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText("就是今天")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        manager.notify(id.toInt(), notification)
    }

    private fun pendingIntent(context: Context, id: Long, title: String): PendingIntent {
        val intent = Intent(context, CountdownAlarmReceiver::class.java).apply {
            putExtra(EXTRA_ID, id)
            putExtra(EXTRA_TITLE, title)
        }
        return PendingIntent.getBroadcast(
            context,
            id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

class CountdownAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(CountdownNotifications.EXTRA_ID, -1L)
        val title = intent.getStringExtra(CountdownNotifications.EXTRA_TITLE).orEmpty()
        if (id >= 0L) CountdownNotifications.notify(context, id, title)
    }
}
