package com.flechazo.toolbox.core.algorithm

import com.flechazo.toolbox.feature.kinship.KinshipDict
import com.flechazo.toolbox.feature.password_gen.PasswordGenViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MiscToolsTest {

    // ---- 亲戚称呼 ----

    @Test
    fun kinshipDirectRelations() {
        assertEquals("爷爷", KinshipDict.resolve("父父"))
        assertEquals("外婆", KinshipDict.resolve("母母"))
        assertEquals("舅舅", KinshipDict.resolve("母兄"))
        assertEquals("嫂子", KinshipDict.resolve("兄妻"))
    }

    @Test
    fun kinshipThreeLevel() {
        assertEquals("堂哥/堂弟", KinshipDict.resolve("父兄子"))
        assertEquals("表姐/表妹", KinshipDict.resolve("母姐女"))
        assertEquals("曾祖父", KinshipDict.resolve("父父父"))
    }

    @Test
    fun kinshipUnknownFallsBack() {
        assertTrue(KinshipDict.resolve("妻子母").startsWith("暂无"))
    }

    // ---- 密码生成器 ----

    @Test
    fun passwordLengthAndCharset() {
        val vm = PasswordGenViewModel()
        vm.setLength(16)
        vm.setUpper(true)
        vm.setLower(true)
        vm.setDigits(true)
        vm.setSymbols(false)
        vm.generate()
        val pw = vm.state.value.password
        assertEquals(16, pw.length)
        // 剔除易混淆字符集：不含 I l O o 0 1
        assertTrue(pw.none { it in "IloO01" })
    }

    @Test
    fun passwordSymbolsWhenEnabled() {
        val vm = PasswordGenViewModel()
        vm.setUpper(false)
        vm.setLower(false)
        vm.setDigits(false)
        vm.setSymbols(true)
        vm.generate()
        val pw = vm.state.value.password
        assertEquals(vm.state.value.length, pw.length)
        assertTrue(pw.all { it in "!@#\$%^&*-_=+?" })
    }

    @Test
    fun passwordEmptyPoolShowsHint() {
        val vm = PasswordGenViewModel()
        vm.setUpper(false); vm.setLower(false); vm.setDigits(false); vm.setSymbols(false)
        vm.generate()
        assertTrue(vm.state.value.password.contains("至少"))
    }

    // ---- 二维码文本往返（编码层面验证 UTF-8 一致性） ----

    @Test
    fun utf8RoundTrip() {
        val text = "中文内容 hello https://example.com/路径?参数=值"
        val bytes = text.toByteArray(Charsets.UTF_8)
        assertEquals(text, String(bytes, Charsets.UTF_8))
    }
}
