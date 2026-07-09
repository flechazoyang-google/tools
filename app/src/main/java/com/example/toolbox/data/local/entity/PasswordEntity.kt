package com.example.toolbox.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A password entry. [encryptedPassword] is AES-256-GCM encrypted via [com.example.toolbox.data.security.CryptoHelper];
 * the plaintext password never touches the database. site/account are stored in clear for list display.
 */
@Entity(tableName = "passwords")
data class PasswordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val site: String,
    val account: String,
    val encryptedPassword: String,
    val note: String = "",
    val tag: String = "",
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)
