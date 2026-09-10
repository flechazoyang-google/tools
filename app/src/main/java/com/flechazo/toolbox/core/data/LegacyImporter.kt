package com.flechazo.toolbox.core.data

import android.content.Context
import com.flechazo.toolbox.feature.countdown.CountdownBackup
import com.flechazo.toolbox.feature.countdown.CountdownEntity
import com.flechazo.toolbox.feature.countdown.CountdownRepository
import com.flechazo.toolbox.feature.password_vault.VaultCrypto
import com.flechazo.toolbox.feature.password_vault.VaultEntry
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** 密码部分的结构，与旧版 `toolbox_backup.json` 对齐。 */
data class LegacyPassword(
    val site: String = "",
    val account: String = "",
    val password: String = "",
    val note: String = "",
    val tag: String = "",
    val isFavorite: Boolean = false,
)

/**
 * 旧版备份文件（`toolbox_backup.json`）的顶层结构。
 *
 * `countdowns` 直接用 [CountdownBackup.Event]：它的字段全是"可选 + 有默认值"，
 * 因此同一份 DTO 能吃下 v1（只有 title/targetDate/type）和 v2（全字段）两种文件。
 */
data class LegacyBackup(
    @SerializedName("schemaVersion") val schemaVersion: Int = 0,
    val passwords: List<LegacyPassword> = emptyList(),
    val countdowns: List<CountdownBackup.Event> = emptyList(),
)

data class ImportResult(
    val countdownsImported: Int = 0,
    val countdownsSkipped: Int = 0,
    /** 从 v1 文件升格导入的条数（提醒字段按当天 09:00 兜底） */
    val upgradedFromV1: Int = 0,
    val lunarImported: Int = 0,
    val invalid: Int = 0,
    val passwordsPending: Int = 0,
)

/**
 * 导入旧版 Toolbox 导出的备份。
 *
 * 农历事件**不再被跳过**：旧版备份里本来就带 `isLunar / lunarMonth / lunarDay`，
 * 之前因为新版没实现农历而整条丢弃，用户换机时这部分数据是静默消失的。
 */
@Singleton
class LegacyImporter @Inject constructor(
    private val countdownRepo: CountdownRepository,
    @ApplicationContext private val context: Context,
    private val gson: Gson,
) {

    /** 解析并导入倒数日部分；密码需要主密码，另走 [importPasswords]。 */
    suspend fun import(json: String): ImportResult = withContext(Dispatchers.IO) {
        val backup = parse(json)
        val fromV1 = backup.schemaVersion < CountdownBackup.SCHEMA_VERSION
        val existingKeys = countdownRepo.observeAll().first().map { CountdownBackup.dedupeKey(it) }.toMutableSet()

        var imported = 0
        var skipped = 0
        var lunar = 0
        var invalid = 0

        backup.countdowns.forEach { item ->
            val entity = CountdownBackup.fromEvent(item)
            if (entity == null) {
                invalid++
                return@forEach
            }
            val key = CountdownBackup.dedupeKey(entity)
            if (key in existingKeys) {
                skipped++
                return@forEach
            }
            // create() 只在 createdAt 为 0 时补时间戳，因此导入保留原始创建时间
            countdownRepo.create(entity)
            existingKeys.add(key)
            imported++
            if (entity.isLunar) lunar++
        }

        ImportResult(
            countdownsImported = imported,
            countdownsSkipped = skipped,
            upgradedFromV1 = if (fromV1) imported else 0,
            lunarImported = lunar,
            invalid = invalid,
            passwordsPending = backup.passwords.size,
        )
    }

    /** 解析备份载荷。不是可用的备份对象时抛异常，由调用方转成可读提示。 */
    fun parse(json: String): LegacyBackup {
        val backup = gson.fromJson(json, LegacyBackup::class.java)
            ?: throw IllegalArgumentException("备份文件为空或格式不正确")
        if (backup.countdowns.isEmpty() && backup.passwords.isEmpty()) {
            throw IllegalArgumentException("备份文件中没有可导入的数据")
        }
        return backup
    }

    /**
     * 把旧版密码合并进密码箱（没有密码箱时用 [master] 建一个）。
     *
     * 接收原始 JSON 以保证无状态：早前的实现把解析结果缓存在一个从未赋值的字段上，
     * 导致这个方法永远导入 0 条。
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
