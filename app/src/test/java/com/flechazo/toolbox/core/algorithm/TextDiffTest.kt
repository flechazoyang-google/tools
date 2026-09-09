package com.flechazo.toolbox.core.algorithm

import com.flechazo.toolbox.feature.text_diff.DiffLine
import com.flechazo.toolbox.feature.text_diff.computeDiff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TextDiffTest {

    private fun diffOf(a: String, b: String): List<DiffLine> = computeDiff(a, b)

    @Test
    fun identicalTexts() {
        val diff = diffOf("a\nb\nc", "a\nb\nc")
        assertEquals(listOf('=', '=', '='), diff.map { it.marker })
    }

    @Test
    fun singleLineChange() {
        val diff = diffOf("hello\nworld", "hello\nthere")
        // 期望：hello 相同，world 被删、there 被加（顺序不限但成对出现）
        assertEquals('=', diff.first().marker)
        assertEquals("hello", diff.first().text)
        val removed = diff.filter { it.marker == '-' }.map { it.text }
        val added = diff.filter { it.marker == '+' }.map { it.text }
        assertTrue("world" in removed)
        assertTrue("there" in added)
    }

    @Test
    fun pureAddition() {
        val diff = diffOf("a", "a\nb\nc")
        assertEquals(listOf('=', '+', '+'), diff.map { it.marker })
    }

    @Test
    fun pureDeletion() {
        val diff = diffOf("a\nb\nc", "a")
        assertEquals(listOf('=', '-', '-'), diff.map { it.marker })
    }

    @Test
    fun emptyVsContent() {
        // 注意："".lines() 在 Kotlin 中返回 [""]（一行空串），因此有一个 '-' 空行
        val diff = diffOf("", "x\ny")
        assertEquals(listOf('-', '+', '+'), diff.map { it.marker })
    }

    @Test
    fun reconstructionMatchesB() {
        val a = "one\ntwo\nthree\nfour"
        val b = "one\ntwo\n2.5\nthree\nfive"
        val diff = diffOf(a, b)
        val rebuilt = diff.filter { it.marker != '-' }.joinToString("\n") { it.text }
        assertEquals(b, rebuilt)
        val rebuiltA = diff.filter { it.marker != '+' }.joinToString("\n") { it.text }
        assertEquals(a, rebuiltA)
    }

    @Test
    fun tooManyLinesRejected() {
        // 防止 O(n*m) DP 表把内存打爆
        val many = (1..2_001).joinToString("\n")
        assertThrows(IllegalArgumentException::class.java) { computeDiff(many, "x") }
    }
}
