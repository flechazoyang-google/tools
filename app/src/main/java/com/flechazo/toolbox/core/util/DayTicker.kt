package com.flechazo.toolbox.core.util

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * "今天"这个值的权威来源。
 *
 * 倒数日、经期这类工具的天数全部依赖 `LocalDate.now()`，但 `StateFlow` 不会在午夜
 * 自己重新发射 —— 页面挂着过夜，"还有 1 天"到第二天还是"还有 1 天"（重构方案 P0-6）。
 * 这里显式睡到下一个午夜再发新值。
 *
 * 注意 [flow] 是冷流：每个订阅者各自计时；订阅者全部退出后自动停止，不留后台任务。
 */
object DayTicker {

    /**
     * @param clock 注入以便单测模拟跨天；生产用默认实现。
     */
    fun dayFlow(clock: () -> LocalDate = { LocalDate.now() }): Flow<LocalDate> = flow {
        var current = clock()
        emit(current)
        while (true) {
            delay(millisUntilNextDay())
            // 睡醒后重新取值，而不是 current.plusDays(1)：跨时区/改系统日期时以时钟为准
            val next = clock()
            if (next != current) {
                current = next
                emit(current)
            }
        }
    }

    /** 到下一个本地零点的毫秒数，最少 1 秒（防改时间导致的 0 延迟空转）。 */
    fun millisUntilNextDay(now: LocalDateTime = LocalDateTime.now(ZoneId.systemDefault())): Long {
        val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay()
        return Duration.between(now, nextMidnight).toMillis().coerceAtLeast(1_000L)
    }
}
