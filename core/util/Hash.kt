package com.example.toolbox.core.util

import java.security.MessageDigest

/** SHA-256 hex digest, used only as a verifier for the password-box master-password gate. */
fun sha256(input: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
}
