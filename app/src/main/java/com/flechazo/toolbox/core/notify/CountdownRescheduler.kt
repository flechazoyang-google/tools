package com.flechazo.toolbox.core.notify

import android.content.Context
import com.flechazo.toolbox.feature.countdown.CountdownDao
import com.flechazo.toolbox.feature.countdown.CountdownReminders
import com.flechazo.toolbox.feature.countdown.LunarCalendar
import com.flechazo.toolbox.feature.countdown.RepeatRule
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 全量重排 + 漏发补发。开机、改系统时间/时区、事件增删改、每日兜底任务共用这一套逻辑。
 *
 * [rescheduleAll] 设计成**幂等**：先按事件重排（内部会先 cancel 再 arm），因此可以被
 * 任意频次重复调用而不会累积闹钟。
 */
@Singleton
class CountdownRescheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: CountdownDao,
    private val scheduler: CountdownScheduler,
    private val lunar: LunarCalendar,
) {
    /**
     * 重排所有事件的提醒闹钟，并补发最近 [backfillHours] 小时内错过的提醒。
     *
     * @return 补发的提醒条数
     */
    suspend fun rescheduleAll(backfillHours: Long = 0): Int = withContext(Dispatchers.IO) {
        CountdownNotifications.ensureChannel(context)
        val today = LocalDate.now()
        val now = LocalDateTime.now()
        val events = dao.snapshot()
        events.forEach { scheduler.arm(it, lunar, today, now) }
        if (backfillHours <= 0) return@withContext 0

        val active = CountdownNotifications.activeNotificationIds(context)
        var posted = 0
        events.forEach { e ->
            // 通知还挂在栏里没被划掉 → 不重复打扰；用 filter 而不是内联 return@forEach，
            // 免得两层 forEach 的标签重名（Kotlin 会警告，读者更要猜半天跳的是哪层）
            CountdownReminders.missed(e, today, now, lunar, backfillHours)
                .filterNot { CountdownNotifications.notificationId(e.id, it.daysBefore) in active }
                .forEach { t ->
                    CountdownNotifications.post(
                        context = context,
                        eventId = e.id,
                        title = e.title,
                        daysBefore = t.daysBefore,
                        occurrence = t.occurrence,
                        repeat = e.repeatRule.takeIf { r -> r != RepeatRule.NONE },
                    )
                    posted++
                }
        }
        posted
    }

    /** 事件被删除后清掉它的闹钟。 */
    fun cancelEvent(id: Long) = scheduler.cancel(id)

    /** 单个事件改动后只重排它自己，避免整表扫描。 */
    suspend fun rescheduleEvent(id: Long) = withContext(Dispatchers.IO) {
        val e = dao.byId(id)
        if (e == null) {
            scheduler.cancel(id)
        } else {
            scheduler.arm(e, lunar, LocalDate.now(), LocalDateTime.now())
        }
    }
}
