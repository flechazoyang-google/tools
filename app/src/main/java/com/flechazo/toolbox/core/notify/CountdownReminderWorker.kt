package com.flechazo.toolbox.core.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * 提醒的**第三层保障**：每日兜底。
 *
 * 前两层（事件闹钟、开机/改时广播）都依赖系统按约定投递，而国内 ROM 会把两者都掐掉。
 * 这个每日任务做两件事：把闹钟重排一遍（自愈），以及补发最近 12 小时内漏掉的提醒。
 *
 * 刻意不用 `@HiltWorker`：那要额外引入 `androidx.hilt:hilt-work` 与 `hilt-compiler`
 * 两个构件，而本应用 release 包只有约 2 MB。用 [EntryPointAccessors] 取依赖，
 * 零新增依赖达成同样效果。
 */
class CountdownReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val entry = EntryPointAccessors.fromApplication(
            applicationContext,
            CountdownEntryPoint::class.java,
        )
        return runCatching { entry.rescheduler().rescheduleAll(backfillHours = BACKFILL_HOURS) }
            .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }

    companion object {
        const val UNIQUE_NAME = "countdown_daily_safety_net"
        private const val BACKFILL_HOURS = 12L

        /**
         * 排一次即可（[ExistingPeriodicWorkPolicy.KEEP]）—— 重复 enqueue 会重置周期并把
         * 首次执行时间推后，所以只在应用启动时以 KEEP 策略登记，不随事件变更重排。
         *
         * 首次延迟到下一个 00:20 左右：跨过零点才能拿到"今天"的正确发生日，
         * 又不至于在凌晨 0 点整点被系统丢进队列深处。
         */
        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<CountdownReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(initialDelayMinutes(), TimeUnit.MINUTES)
                // 不做联网约束：本功能全程离线，加约束反而会被无限期挂起
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        private fun initialDelayMinutes(): Long {
            val zone = ZoneId.systemDefault()
            val now = LocalDateTime.now(zone)
            val next = now.toLocalDate().plusDays(if (now.toLocalTime() < TARGET_TIME) 0 else 1).atTime(TARGET_TIME)
            return Duration.between(now, next).toMinutes().coerceAtLeast(1)
        }

        private val TARGET_TIME: LocalTime = LocalTime.of(0, 20)
    }
}
