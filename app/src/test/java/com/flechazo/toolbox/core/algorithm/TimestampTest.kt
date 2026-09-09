package com.flechazo.toolbox.core.algorithm

import com.flechazo.toolbox.feature.timestamp.relativeTo
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

class TimestampTest {

    private val now: Instant = Instant.parse("2026-01-10T12:00:00Z")

    @Test
    fun pastIsPast() {
        // 旧实现方向反了：过去的时间会显示成"5 分钟后"
        assertEquals("5 分钟前", relativeTo(now.minus(Duration.ofMinutes(5)), now))
        assertEquals("3 小时前", relativeTo(now.minus(Duration.ofHours(3)), now))
        assertEquals("2 天前", relativeTo(now.minus(Duration.ofDays(2)), now))
        assertEquals("2 个月前", relativeTo(now.minus(Duration.ofDays(70)), now))
    }

    @Test
    fun futureIsFuture() {
        assertEquals("5 分钟后", relativeTo(now.plus(Duration.ofMinutes(5)), now))
        assertEquals("3 小时后", relativeTo(now.plus(Duration.ofHours(3)), now))
        assertEquals("2 天后", relativeTo(now.plus(Duration.ofDays(2)), now))
        assertEquals("2 个月后", relativeTo(now.plus(Duration.ofDays(70)), now))
    }

    @Test
    fun justNow() {
        assertEquals("刚刚", relativeTo(now.minus(Duration.ofSeconds(30)), now))
        assertEquals("刚刚", relativeTo(now, now))
        assertEquals("刚刚", relativeTo(now.plus(Duration.ofSeconds(30)), now))
    }
}
