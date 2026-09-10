package com.flechazo.toolbox.feature.countdown

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 主显示口径。**注意：持久化列名仍是历史的 `type`**（见 [CountdownEntity.type]），
 * 枚举序号与旧值天然兼容：`0 → COUNTDOWN`、`1 → ELAPSED`。
 */
enum class EventMode(val label: String) {
    /** 目标在未来：主显示"还有 N 天" */
    COUNTDOWN("还没到的日子"),

    /** 目标在过去：主显示"已 N 天"（出生、恋爱开始等） */
    ELAPSED("已经过去的日子"),
    ;

    companion object {
        fun from(v: Int): EventMode = entries.getOrElse(v) { COUNTDOWN }
    }
}

/** 重复规则。序号即持久化值，只能追加不能重排。 */
enum class RepeatRule(val label: String, val shortLabel: String) {
    NONE("不重复", ""),
    WEEKLY("每周", "每周"),
    MONTHLY("每月", "每月"),
    YEARLY_SOLAR("每年（公历）", "每年"),
    YEARLY_LUNAR("每年（农历）", "农历每年"),
    ;

    val isYearly: Boolean get() = this == YEARLY_SOLAR || this == YEARLY_LUNAR

    companion object {
        fun from(v: Int): RepeatRule = entries.getOrElse(v) { NONE }
    }
}

/**
 * 倒数日 / 纪念日事件。
 *
 * 版本历史：
 * - v1：`id / title / date / type / createdAt`
 * - v2：追加下列 13 列（全部带 SQL DEFAULT，见 `core/data/AppDatabase.kt` 的 MIGRATION_1_2）
 *
 * **字段声明顺序必须与迁移里 ALTER 的顺序一致**：Room 的 schema identity hash 包含
 * `CREATE TABLE` 语句的列序，而 `ALTER TABLE ADD COLUMN` 只能把新列追加到末尾。
 * 顺序错一位，升级后 `validateMigration` 就会判定"迁移未正确执行"并在打开数据库时崩溃。
 */
@Entity(tableName = "countdown_events")
data class CountdownEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /** 公历 yyyy-MM-dd。农历事件下，此字段是"最近一次已解析到的公历日"缓存，仅用于排序与展示 */
    val date: String,
    /** 持久化列名沿用 `type`，语义见 [EventMode]（v1 的 0/1 与 v2 完全兼容） */
    val type: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),

    // ---- v2 新增：顺序即 ALTER 顺序，勿调整 ----
    @ColumnInfo(defaultValue = "") val note: String = "",
    // 列名用 repeat_rule：`repeat` 在部分 SQL 方言里是保留字，避开无谓风险
    @ColumnInfo(name = "repeat_rule", defaultValue = "0") val repeat: Int = RepeatRule.NONE.ordinal,
    @ColumnInfo(defaultValue = "0") val isLunar: Boolean = false,
    @ColumnInfo(defaultValue = "0") val lunarMonth: Int = 0,
    @ColumnInfo(defaultValue = "0") val lunarDay: Int = 0,
    @ColumnInfo(defaultValue = "0") val lunarLeapMonth: Boolean = false,
    @ColumnInfo(defaultValue = "0") val pinned: Boolean = false,
    @ColumnInfo(defaultValue = "") val colorKey: String = "",
    // 默认值必须等于 v1 行为（每个事件当天 09:00 提醒），否则老用户升级后提醒静默失效
    @ColumnInfo(defaultValue = "1") val remindEnabled: Boolean = true,
    @ColumnInfo(defaultValue = "0") val remindDaysBefore: String = RemindDays.CURRENT_DAY,
    @ColumnInfo(defaultValue = "9") val remindHour: Int = 9,
    /** 进度条起点；null = 不显示进度。唯一无 DEFAULT 的新列（可空） */
    val anchorDate: String? = null,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0L,
) {
    val mode: EventMode get() = EventMode.from(type)
    val repeatRule: RepeatRule get() = RepeatRule.from(repeat)
}

/**
 * `remindDaysBefore` 在 SQLite 里存 CSV 字符串（Room 无数组支持），这里负责编解码。
 * 解码宽容：脏数据、越界值、重复值一律静默修正，绝不让一条坏数据打崩整个列表。
 */
object RemindDays {
    /** 当天提醒的固定编码，同时也是 v2 新建事件的默认值 */
    const val CURRENT_DAY = "0"

    /** 可选档位：关闭 / 当天 / 提前 1、3、7、30 天 */
    val OPTIONS = listOf(0, 1, 3, 7, 30)

    /** 单个事件最多排 6 个待触发点，防极端配置下把 AlarmManager 排爆 */
    const val MAX_TRIGGERS = 6

    fun encode(days: List<Int>): String =
        days.filter { it in 0..365 }.distinct().sorted().joinToString(",")

    fun decode(csv: String?): List<Int> =
        csv?.split(',')
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.filter { it in 0..365 }
            ?.distinct()
            ?.sorted()
            ?: emptyList()

    fun label(days: List<Int>): String = when {
        days.isEmpty() -> "不提醒"
        days == listOf(0) -> "当天提醒"
        else -> days.joinToString("、") { if (it == 0) "当天" else "提前 $it 天" }
    }
}
