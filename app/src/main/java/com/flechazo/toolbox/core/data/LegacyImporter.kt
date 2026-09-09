package com.flechazo.toolbox.core.data

import android.content.Context
import com.flechazo.toolbox.feature.countdown.CountdownRepository
import com.flechazo.toolbox.feature.password_vault.VaultCrypto
import com.flechazo.toolbox.feature.password_vault.VaultEntry
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** DTOs matching the legacy Toolbox backup file (toolbox_backup.json). */
data class LegacyCountdown(
    val title: String = "",
    val targetDate: Long = 0,
    val colorTag: String = "#4F7CFF",
    val isPinned: Boolean = false,
    val type: String = "countdown",
    val isLunar: Boolean = false,
    val lunarMonth: Int = 0,
    val lunarDay: Int = 0,
)

data class LegacyPassword(
    val site: String = "",
    val account: String = "",
    val password: String = "",
    val note: String = "",
    val tag: String = "",
    val isFavorite: Boolean = false,
)

data class LegacyBackup(
    val passwords: List<LegacyPassword> = emptyList(),
    val countdowns: List<LegacyCountdown> = emptyList(),
)

data class ImportResult(
    val countdownsImported: Int = 0,
    val countdownsSkipped: Int = 0,
    val lunarSkipped: Int = 0,
    val passwordsPending: Int = 0,
)

/**
 * Imports data exported by the legacy Toolbox app ("导出数据" → toolbox_backup.json).
 * Countdowns go into the new Room store; passwords need the vault master password
 * (existing vault is decrypted & merged, a missing vault is created with the given password).
 */
@Singleton
class LegacyImporter @Inject constructor(
    private val countdownRepo: CountdownRepository,
    @ApplicationContext private val context: Context,
    private val gson: Gson,
) {

    /** Parse the backup file and import countdowns. Returns per-part results; password count is pending. */
    suspend fun import(json: String): ImportResult = withContext(Dispatchers.IO) {
        val backup = parse(json)
        var imported = 0
        var skipped = 0
        var lunar = 0

        val existing = countdownRepo.observeAll().first()
        val existingKeys = existing.map { it.title to it.date }.toMutableSet()

        backup.countdowns.forEach { item ->
            if (item.title.isBlank() || item.targetDate <= 0) {
                skipped++
                return@forEach
            }
            if (item.isLunar) {
                lunar++ // 新版暂不支持农历，跳过并计数
                return@forEach
            }
            val date = Instant.ofEpochMilli(item.targetDate)
                .atZone(ZoneId.systemDefault()).toLocalDate().toString()
            val key = item.title to date
            if (key in existingKeys) {
                skipped++
                return@forEach
            }
            val type = if (item.type == "countdown") 0 else 1
            countdownRepo.add(item.title, date, type)
            existingKeys.add(key)
            imported++
        }

        ImportResult(
            countdownsImported = imported,
            countdownsSkipped = skipped,
            lunarSkipped = lunar,
            passwordsPending = backup.passwords.size,
        )
    }

    /** Parse a legacy backup payload. Throws if the JSON is not a usable backup object. */
    fun parse(json: String): LegacyBackup {
        val backup = gson.fromJson(json, LegacyBackup::class.java)
            ?: throw IllegalArgumentException("备份文件为空或格式不正确")
        if (backup.countdowns.isEmpty() && backup.passwords.isEmpty()) {
            throw IllegalArgumentException("备份文件中没有可导入的数据")
        }
        return backup
    }

    /**
     * Merge legacy passwords into the vault (creating it with [master] if needed).
     *
     * Takes the original backup JSON so the operation is stateless: the previous
     * implementation cached the parsed passwords in a field that was never assigned,
     * which made this method always import zero entries.
     *
     * @return number of newly added entries; -1 is not returned (callers map failures).
     */
    suspend fun importPasswords(json: String, master: CharArray): Int = withContext(Dispatchers.IO) {
        val entries = parse(json).passwords
            .filter { it.site.isNotBlank() && it.password.isNotBlank() }
            .map {
                VaultEntry(
                    id = java.util.UUID.randomUUID().toString(),
                    title = it.site,
                    username = it.account,
                    password = it.password,
                    note = it.note.ifBlank { it.tag },
                )
            }
        if (entries.isEmpty()) return@withContext 0

        // 已有密码箱时必须先用主密码成功解密；解密失败直接抛出，
        // 否则会用一个错误的主密码覆盖掉用户原有的密码箱（数据丢失）。
        val existingFile = VaultCrypto.readFile(context)
        val merged: List<VaultEntry> =
            if (existingFile != null) VaultCrypto.decrypt(existingFile, master) else emptyList()

        val existingKeys = merged.map { it.title to it.username }.toSet()
        val fresh = entries.filter { (it.title to it.username) !in existingKeys }
        if (fresh.isEmpty()) return@withContext 0

        VaultCrypto.writeFile(context, VaultCrypto.encrypt(merged + fresh, master))
        fresh.size
    }
}
