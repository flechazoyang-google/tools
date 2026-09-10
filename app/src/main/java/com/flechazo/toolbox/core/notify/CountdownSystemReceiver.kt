package com.flechazo.toolbox.core.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 提醒的**第二层保障**：系统事件驱动的重排。
 *
 * 覆盖三类会让闹钟失效的场景：
 * - `BOOT_COMPLETED`：开机后 `AlarmManager` 的闹钟被系统清空（重构方案 P0-3 的根因）
 * - `ACTION_TIME_SET` / `ACTION_DATE_CHANGED`：用户改系统时间，所有已排时刻整体偏移
 * - `ACTION_TIMEZONE_CHANGED`：跨时区后本地 9:00 的定义变了
 *
 * 注意 `exported="true"` 是硬要求 —— 这些广播来自 system uid，不导出就收不到；
 * 该 receiver 只读自己的数据库、不接收外部数据，导出面没有引入攻击面。
 */
@AndroidEntryPoint
class CountdownSystemReceiver : BroadcastReceiver() {

    @Inject
    lateinit var rescheduler: CountdownRescheduler

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in HANDLED) return
        // 含数据库读取，必须 goAsync，否则 onReceive 返回即进程可能被回收
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                // 开机后补发窗口放宽到 12 小时：晚上关机、早上开机时当天的提醒还来得及
                val backfill = if (action == Intent.ACTION_BOOT_COMPLETED) 12L else 0L
                rescheduler.rescheduleAll(backfillHours = backfill)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            "android.intent.action.DATE_CHANGED",
        )
    }
}

/** 供非 Hilt 管理的类（如 Worker）取依赖用。 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface CountdownEntryPoint {
    fun rescheduler(): CountdownRescheduler
}
