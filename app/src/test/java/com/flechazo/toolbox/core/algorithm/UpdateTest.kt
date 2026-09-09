package com.flechazo.toolbox.core.algorithm

import com.flechazo.toolbox.core.update.isNewer
import com.flechazo.toolbox.core.update.parseVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateTest {

    // ---------------- 版本解析 ----------------

    @Test
    fun parseVersionHandlesPrefixesAndSuffixes() {
        assertEquals(listOf(1, 1, 2), parseVersion("1.1.2"))
        assertEquals(listOf(1, 1, 2), parseVersion("v1.1.2"))
        assertEquals(listOf(1, 1, 2), parseVersion("V1.1.2"))
        assertEquals(listOf(1, 1, 2), parseVersion("1.1.2-beta.1"))
        assertEquals(listOf(1, 1, 2), parseVersion(" 1.1.2 "))
        assertEquals(listOf(1, 1), parseVersion("1.1"))
        assertNull(parseVersion(""))
        assertNull(parseVersion("abc"))
        assertNull(parseVersion("v1.x.0"))
    }

    // ---------------- 版本比较 ----------------

    @Test
    fun newerVersionDetected() {
        assertTrue(isNewer("1.1.1", "v1.1.2"))
        assertTrue(isNewer("1.1.1", "1.2.0"))
        assertTrue(isNewer("1.1.1", "2.0.0"))
        // 数值比较而非字典序：1.1.10 比 1.1.9 新
        assertTrue(isNewer("1.1.9", "1.1.10"))
        // 缺位补 0
        assertTrue(isNewer("1.1", "1.1.1"))
    }

    @Test
    fun sameOrOlderIsNotNewer() {
        assertFalse(isNewer("1.1.2", "v1.1.2"))
        assertFalse(isNewer("1.1.2", "1.1.1"))
        assertFalse(isNewer("1.2.0", "1.1.9"))
        assertFalse(isNewer("1.1.1", "1.1"))
    }

    @Test
    fun prereleaseSuffixIgnored() {
        // 只比较 - 之前的数字部分
        assertFalse(isNewer("1.1.2", "1.1.2-beta.1"))
        assertTrue(isNewer("1.1.1", "1.1.2-beta.1"))
    }

    @Test
    fun malformedTagNeverPrompts() {
        assertFalse(isNewer("1.1.1", "abc"))
        assertFalse(isNewer("1.1.1", ""))
        assertFalse(isNewer("abc", "1.1.2"))
    }
}
