package com.example.toolbox.data.repository

import com.example.toolbox.data.local.dao.PasswordDao
import com.example.toolbox.data.local.entity.PasswordEntity
import com.example.toolbox.data.security.CryptoHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

data class PasswordInput(
    val site: String,
    val account: String,
    val password: String,
    val note: String = "",
    val tag: String = "",
    val isFavorite: Boolean = false,
)

data class PasswordExport(
    val site: String,
    val account: String,
    val password: String,
    val note: String,
    val tag: String,
    val isFavorite: Boolean,
)

class PasswordRepository @Inject constructor(
    private val dao: PasswordDao,
    private val crypto: CryptoHelper,
) {
    fun observeAll(): Flow<List<PasswordEntity>> = dao.observeAll()
    fun search(q: String): Flow<List<PasswordEntity>> = dao.search(q)
    suspend fun count(): Int = dao.count()

    suspend fun add(input: PasswordInput) {
        dao.insert(
            PasswordEntity(
                site = input.site,
                account = input.account,
                encryptedPassword = crypto.encrypt(input.password),
                note = input.note,
                tag = input.tag,
                isFavorite = input.isFavorite,
            )
        )
    }

    suspend fun update(entity: PasswordEntity, newPassword: String? = null) {
        val updated = if (newPassword != null) {
            entity.copy(encryptedPassword = crypto.encrypt(newPassword))
        } else {
            entity
        }
        dao.update(updated)
    }

    suspend fun delete(entity: PasswordEntity) = dao.delete(entity)

    fun decrypt(encrypted: String): String = crypto.decrypt(encrypted)

    suspend fun exportAll(): List<PasswordExport> =
        dao.observeAll().first().map {
            PasswordExport(
                site = it.site,
                account = it.account,
                password = crypto.decrypt(it.encryptedPassword),
                note = it.note,
                tag = it.tag,
                isFavorite = it.isFavorite,
            )
        }

    suspend fun importAll(items: List<PasswordExport>) {
        items.forEach {
            dao.insert(
                PasswordEntity(
                    site = it.site,
                    account = it.account,
                    encryptedPassword = crypto.encrypt(it.password),
                    note = it.note,
                    tag = it.tag,
                    isFavorite = it.isFavorite,
                )
            )
        }
    }
}
