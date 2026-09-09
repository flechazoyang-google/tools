package com.flechazo.toolbox.core.algorithm

import com.flechazo.toolbox.feature.password_vault.VaultCrypto
import com.flechazo.toolbox.feature.password_vault.VaultEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class VaultCryptoTest {

    private val entries = listOf(
        VaultEntry("id-1", "示例站点", "user@example.com", "p@ssw0rd中文", "备注"),
        VaultEntry("id-2", "GitHub", "yang-genhao", "gh_token_123", ""),
    )

    @Test
    fun encryptDecryptRoundTrip() {
        val blob = VaultCrypto.encrypt(entries, "主密码123".toCharArray())
        val decrypted = VaultCrypto.decrypt(blob, "主密码123".toCharArray())
        assertEquals(entries, decrypted)
    }

    @Test
    fun wrongPasswordFails() {
        val blob = VaultCrypto.encrypt(entries, "correct".toCharArray())
        assertThrows(Exception::class.java) {
            VaultCrypto.decrypt(blob, "wrong".toCharArray())
        }
    }

    @Test
    fun ciphertextDiffersAcrossCalls() {
        // 每次加密使用随机 salt/iv，密文应不同
        val a = VaultCrypto.encrypt(entries, "pw".toCharArray())
        val b = VaultCrypto.encrypt(entries, "pw".toCharArray())
        assert(!a.contentEquals(b))
    }

    @Test
    fun tamperedDataFails() {
        val blob = VaultCrypto.encrypt(entries, "pw".toCharArray())
        blob[blob.size - 1] = (blob[blob.size - 1].toInt() xor 0x01).toByte()
        assertThrows(Exception::class.java) {
            VaultCrypto.decrypt(blob, "pw".toCharArray())
        }
    }

    @Test
    fun corruptedMagicFails() {
        val blob = VaultCrypto.encrypt(entries, "pw".toCharArray())
        blob[0] = 'X'.code.toByte()
        assertThrows(Exception::class.java) {
            VaultCrypto.decrypt(blob, "pw".toCharArray())
        }
    }
}
