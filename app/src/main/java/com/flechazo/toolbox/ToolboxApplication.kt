package com.flechazo.toolbox

import android.app.Application
import com.flechazo.toolbox.core.notify.CountdownNotifications
import com.flechazo.toolbox.core.notify.CountdownReminderWorker
import com.flechazo.toolbox.core.notify.CountdownRescheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class ToolboxApplication : Application() {

    @Inject
    lateinit var countdownRescheduler: CountdownRescheduler

    override fun onCreate() {
        super.onCreate()
        CountdownNotifications.ensureChannel(this)
        // 第三层保障：每日兜底任务（幂等登记，KEEP 策略不会因重启 App 而顺延）
        CountdownReminderWorker.enqueue(this)
        // 冷启动重排闹钟：覆盖"开机广播被厂商 ROM 拦掉"的情形。
        // 这里刻意用 backfillHours = 0 —— 冷启动只补排期、不补发通知，
        // 否则用户当天已经划掉的那条提醒会在每次打开 App 时再弹一遍。
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            runCatching { countdownRescheduler.rescheduleAll(backfillHours = 0) }
        }
    }
}
