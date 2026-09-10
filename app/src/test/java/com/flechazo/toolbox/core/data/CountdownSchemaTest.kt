package com.flechazo.toolbox.core.data

import com.flechazo.toolbox.feature.countdown.CountdownEntity
import com.flechazo.toolbox.feature.countdown.RemindDays
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1 → v2 迁移的构建期对账。
 *
 * **为什么需要它**：`Migration` 的正确性 normally 靠 `MigrationTestHelper`（instrumentation）
 * 或在 JVM 上跑 Robolectric + 真实 SQLite。本项目两者都不用（不引入 instrumentation 设备依赖，
 * 也不为一条迁移引入 Robolectric 的构建开销），于是"手写的 ALTER 与 Room 期望的表结构是否一致"
 * 就完全依赖人眼 —— 而这条链路一旦错了，用户升级时要么直接抛异常打不开 App，要么静默丢数据。
 *
 * 这里改用 KSP 生成的 `schemas/…/2.json`（Room 升级后校验的**权威依据**）与手写的 DDL 对账：
 * 列名、列序、类型、NOT NULL、DEFAULT 逐字比对。这不等于跑了迁移，但它把唯一会出错的地方
 * （DDL 与实体漂移）钉死了。真机覆盖安装仍需作为发布前检查项。
 */
class CountdownSchemaTest {

    private val schemaJson: JsonObject by lazy {
        val file = findSchema()
        JsonParser.parseString(file.readText(Charsets.UTF_8)).asJsonObject
            .getAsJsonObject("database")
    }

    private fun findSchema(): File {
        val rel = "schemas/${AppDatabase::class.java.name}/2.json"
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val candidate = File(dir, rel)
            if (candidate.isFile) return candidate
            val module = File(dir, "app/$rel")
            if (module.isFile) return module
            dir = dir.parentFile
        }
        throw IllegalStateException(
            "找不到 $rel。Room schema 必须先由一次成功构建生成（app/schemas 需提交进仓库）。",
        )
    }

    /** 从 createSql 里按声明顺序解析出 `` `name` TYPE ... `` 列定义。 */
    private fun columns(): List<Pair<String, String>> {
        val create = table.get("createSql").asString
        val body = create.substringAfter("(").substringBeforeLast(")")
        // 默认值里没有逗号（'' / '0' / 数字），按 ", " 切分是安全的
        return body.split(", ").mapNotNull { piece ->
            val m = Regex("""^`(\w+)`\s+(.+)$""").matchEntire(piece.trim())
            m?.let { it.groupValues[1] to it.groupValues[2] }
        }
    }

    private val table: JsonObject
        get() = schemaJson.getAsJsonArray("entities").get(0).asJsonObject

    private val v1Columns = listOf("id", "title", "date", "type", "createdAt")

    @Test
    fun schemaIsVersionTwoOfTheSameTable() {
        assertEquals(2, schemaJson.get("version").asInt)
        // 表名变了就是换了一张表，等于老数据全丢
        assertEquals("countdown_events", table.get("tableName").asString)
        assertEquals(18, columns().size)
    }

    @Test
    fun v1ColumnsAreUntouched() {
        val cols = columns().toMap()
        v1Columns.forEach { name ->
            assertNotNull("v1 列 $name 必须还在", cols[name])
        }
        assertEquals("INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL", cols["id"])
        assertEquals("TEXT NOT NULL", cols["title"])
        assertEquals("TEXT NOT NULL", cols["date"])
        assertEquals("INTEGER NOT NULL", cols["type"])
        assertEquals("INTEGER NOT NULL", cols["createdAt"])
    }

    @Test
    fun migrationAddsExactlyTheV2ColumnsInDeclarationOrder() {
        val added = columns().filter { it.first !in v1Columns }
        assertEquals(AppDatabase.MIGRATION_1_2_SQL.size, added.size)
        assertEquals("列序必须与实体声明顺序一致", added.map { it.first }, AppDatabase.MIGRATION_1_2_SQL.map {
            it.substringAfter("ADD COLUMN ").substringBefore(" ")
        })
        added.forEach { (name, def) ->
            val sql = AppDatabase.MIGRATION_1_2_SQL.first { it.contains("ADD COLUMN $name ") }
            assertEquals("$name 的列定义与 Room 期望不一致", def, sql.substringAfter("ADD COLUMN $name "))
        }
    }

    /**
     * 最关键的一条：迁移默认值必须等价于 v1 的真实行为。
     * `remindEnabled` 若默认 0、或 `remindDaysBefore` 若默认空，老用户升级后提醒全部静默失效。
     */
    @Test
    fun reminderDefaultsPreserveV1Behaviour() {
        val cols = columns().toMap()
        assertEquals("INTEGER NOT NULL DEFAULT 1", cols["remindEnabled"])
        assertEquals("TEXT NOT NULL DEFAULT '0'", cols["remindDaysBefore"])
        assertEquals("INTEGER NOT NULL DEFAULT 9", cols["remindHour"])

        // 实体侧的 Kotlin 默认值必须与 SQL 默认值同义
        val fresh = CountdownEntity(title = "t", date = "2026-01-01")
        assertTrue("Kotlin 默认值必须与 SQL 的 DEFAULT 1 一致", fresh.remindEnabled)
        assertEquals(listOf(0), RemindDays.decode(fresh.remindDaysBefore))
        assertEquals(RemindDays.CURRENT_DAY, fresh.remindDaysBefore)
        assertEquals(9, fresh.remindHour)
        assertEquals(0, fresh.repeat)
        assertEquals("", fresh.colorKey)
        assertEquals("", fresh.note)
    }

    @Test
    fun everyAddedNotNullColumnHasADefault() {
        // SQLite 不允许给已有数据的表加无默认值的 NOT NULL 列：ALTER 会直接失败
        columns().filter { it.first !in v1Columns }.forEach { (name, def) ->
            if (def.contains("NOT NULL")) {
                assertTrue("$name 是 NOT NULL 却没有 DEFAULT，迁移会炸", def.contains("DEFAULT"))
            }
        }
    }

    @Test
    fun repeatRuleIsStoredUnderItsColumnAlias() {
        // 实体字段叫 repeat，列名是 repeat_rule（repeat 是 SQLite 关键字风格的保留词）
        val cols = columns().toMap()
        assertTrue(cols.containsKey("repeat_rule"))
        assertTrue(!cols.containsKey("repeat"))
        assertEquals("INTEGER NOT NULL DEFAULT 0", cols["repeat_rule"])
    }

    @Test
    fun anchorDateIsTheOnlyNullableAddition() {
        val nullable = columns().filter { it.first !in v1Columns }.filter { !it.second.contains("NOT NULL") }
        assertEquals(listOf("anchorDate"), nullable.map { it.first })
    }

    @Test
    fun noDestructiveMigrationIsConfigured() {
        // DatabaseModule 里出现 fallbackToDestructiveMigration 就是拿用户数据赌；源码级看门狗。
        // 匹配带调用形式的写法，否则会命中本文件注释里对它的说明。
        val src = findSource("AppDatabase.kt").readText(Charsets.UTF_8)
        assertTrue(
            "禁止 fallbackToDestructiveMigration()：升级时会静默删光用户事件",
            !src.contains(".fallbackToDestructiveMigration("),
        )
        assertTrue("必须显式挂上 MIGRATION_1_2", src.contains("addMigrations(AppDatabase.MIGRATION_1_2)"))
    }

    private fun findSource(name: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val hit = File(dir, "src/main/java/com/flechazo/toolbox/core/data/$name")
            if (hit.isFile) return hit
            val mod = File(dir, "app/src/main/java/com/flechazo/toolbox/core/data/$name")
            if (mod.isFile) return mod
            dir = dir.parentFile
        }
        throw IllegalStateException("找不到源文件 $name，看门狗测试无法执行")
    }
}
