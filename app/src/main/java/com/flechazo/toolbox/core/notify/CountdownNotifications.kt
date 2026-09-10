package com.flechazo.toolbox.core.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.flechazo.toolbox.MainActivity
import com.flechazo.toolbox.R
import com.flechazo.toolbox.feature.countdown.CountdownEngine
import com.flechazo.toolbox.feature.countdown.CountdownReminders
import com.flechazo.toolbox.feature.countdown.CountdownRepository
import com.flechazo.toolbox.feature.countdown.RepeatRule
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 倒数日提醒通知的**呈现层**。排期逻辑在 [CountdownScheduler]，这里只管渠道、文案、分组与跳转。
 *
 * 通知 id 与闹钟 requestCode 共用 [CountdownReminders.requestCode] 的编码，
 * 因此"同一事件同一档提醒"重复触发时是覆盖而非叠加 —— 也由此不需要额外的"已提醒"状态表。
 */
object CountdownNotifications {

    const val CHANNEL_ID = "countdown_reminders"
    private const val GROUP_KEY = "com.flechazo.toolbox.countdown"

    private val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 EEE", Locale.CHINA)

    fun notificationId(eventId: Long, daysBefore: Int): Int =
        CountdownReminders.requestCode(eventId, daysBefore)

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "倒数日提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "重要日子的当天与提前提醒"
            },
        )
    }

    /** 是否已获得通知权限（API 33 以下无需授权，恒为 true）。 */
    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * 发一条日期提醒。
     *
     * @param occurrence 那一次发生的公历日期；null 时文案退化为不带日期
     * @param repeat [RepeatRule] 的短标签（"每年"），null 表示一次性事件
     */
    fun post(
        context: Context,
        eventId: Long,
        title: String,
        daysBefore: Int,
        occurrence: LocalDate?,
        repeat: RepeatRule?,
    ) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val manager = NotificationManagerCompat.from(context)
        val repeatShort = repeat?.shortLabel?.takeIf { it.isNotBlank() }

        val body = if (daysBefore <= 0) {
            buildString {
                append("就是今天")
                occurrence?.let { append(" · ").append(it.format(SHORT_DATE)) }
                repeatShort?.let { append(" · ").append(it) }
            }
        } else {
            buildString {
                append("还有 ").append(daysBefore).append(" 天")
                occurrence?.let { append("（").append(it.format(SHORT_DATE)).append("）") }
                repeatShort?.let { append(" · ").append(it) }
            }
        }

        val nid = notificationId(eventId, daysBefore)
        val contentIntent = PendingIntent.getActivity(
            context,
            nid,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_TOOL_ID, CountdownEngine.TOOL_ID)
                putExtra(MainActivity.EXTRA_EVENT_ID, eventId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // 锁屏不外露：事件标题可能是"离婚协议签署"这类内容
        val privacyFallback = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("日期提醒")
            .setContentText("有一个重要的日子临近")
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setGroup(GROUP_KEY)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(privacyFallback)
            .build()

        runCatching { manager.notify(nid, notification) }
    }

    /** 通知栏里仍活跃的通知 id，供每日兜底任务去重。 */
    fun activeNotificationIds(context: Context): Set<Int> =
        runCatching {
            NotificationManagerCompat.from(context).activeNotifications.map { it.id }.toSet()
        }.getOrDefault(emptySet())
}

/**
 * 闹钟到点后的广播接收器。
 *
 * 注入仓储是为了**发通知前回到数据库确认事件仍然存在**：闹钟可能在事件删除后残留
 * （取消失败、系统在省电策略下延后补发），此时不该再打扰用户。
 */
@AndroidEntryPoint
class CountdownAlarmReceiver : BroadcastReceiver() {

    @Inject
    @ApplicationContext
    lateinit var appContext: Context

    @Inject
    lateinit var repository: CountdownRepository

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        if (id < 0L) return
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val daysBefore = intent.getIntExtra(EXTRA_DAYS_BEFORE, 0)
        val scheduledOccurrence = CountdownEngine.parseDate(intent.getStringExtra(EXTRA_OCCURRENCE))
        val repeatOrdinal = intent.getIntExtra(EXTRA_REPEAT, -1)

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val live = repository.byId(id) ?: return@launch // 事件已删除 → 静默跳过
                CountdownNotifications.post(
                    context = appContext,
                    eventId = id,
                    title = title.ifBlank { live.title },
                    daysBefore = daysBefore,
                    occurrence = scheduledOccurrence ?: CountdownEngine.parseDate(live.date),
                    repeat = if (repeatOrdinal >= 0) RepeatRule.from(repeatOrdinal) else live.repeatRule,
                )
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_ID = "event_id"
        const val EXTRA_TITLE = "event_title"
        const val EXTRA_DAYS_BEFORE = "days_before"
        const val EXTRA_OCCURRENCE = "occurrence"
        const val EXTRA_REPEAT = "repeat_rule"
    }
}
