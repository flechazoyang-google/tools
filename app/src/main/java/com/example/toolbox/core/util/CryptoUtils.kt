package com.example.toolbox.core.util

import java.security.MessageDigest

fun sha256(input: String): String =
    MessageDigest.getInstance("SHA-256").digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
