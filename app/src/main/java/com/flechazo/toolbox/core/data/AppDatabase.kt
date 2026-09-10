package com.flechazo.toolbox.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.flechazo.toolbox.feature.countdown.CountdownDao
import com.flechazo.toolbox.feature.countdown.CountdownEntity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 全应用共享的 Room 数据库。
 *
 * 原先 `AppDatabase` 与 `DatabaseModule` 定义在 `feature/countdown/CountdownData.kt` 里 ——
 * 整个 App 的 DB 归属被放进了一个 feature 包（重构方案 AR-1）。现上移到 `core/data`。
 *
 * 版本历史：v1 只有 `countdown_events` 的 5 列；v2 为该表追加 13 列。
 */
@Database(
    entities = [CountdownEntity::class],
    version = 2,
    // 导出 schema 是迁移可被校验的前提；schemaLocation 由 build.gradle.kts 的 KSP 参数指定
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun countdownDao(): CountdownDao

    companion object {
        const val NAME = "toolbox.db"

        /**
         * v1 → v2：**只做 `ADD COLUMN`**，不重命名、不删除、不改类型。
         *
         * 每条 DDL 的列序必须与 [CountdownEntity] 中新增字段的声明顺序一致，
         * `DEFAULT` 子句必须与实体上的 `@ColumnInfo(defaultValue = ...)` 逐字相同 ——
         * Room 升级后会用 `PRAGMA table_info` 重建 `CREATE TABLE` 并比对 identity hash，
         * 差一个默认值就判定"迁移未正确执行"并在打开数据库时抛异常。
         *
         * 提醒两列的默认值刻意等于 v1 行为（当天 09:00 提醒）：若默认关闭，
         * 老用户升级后提醒会全部静默失效。
         *
         * 这份列表被 `MIGRATION_1_2` 执行、也被 `CountdownSchemaTest` 与 KSP 生成的
         * `schemas/…/2.json` 对账 —— 改实体忘了改这里，测试会红。
         */
        val MIGRATION_1_2_SQL: List<String> = listOf(
            "ALTER TABLE countdown_events ADD COLUMN note TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE countdown_events ADD COLUMN repeat_rule INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE countdown_events ADD COLUMN isLunar INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE countdown_events ADD COLUMN lunarMonth INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE countdown_events ADD COLUMN lunarDay INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE countdown_events ADD COLUMN lunarLeapMonth INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE countdown_events ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE countdown_events ADD COLUMN colorKey TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE countdown_events ADD COLUMN remindEnabled INTEGER NOT NULL DEFAULT 1",
            "ALTER TABLE countdown_events ADD COLUMN remindDaysBefore TEXT NOT NULL DEFAULT '0'",
            "ALTER TABLE countdown_events ADD COLUMN remindHour INTEGER NOT NULL DEFAULT 9",
            "ALTER TABLE countdown_events ADD COLUMN anchorDate TEXT",
            "ALTER TABLE countdown_events ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0",
        )

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_2_SQL.forEach { db.execSQL(it) }
            }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            // 绝不能用 fallbackToDestructiveMigration()：那会在升级时静默删光用户的事件。
            // 宁可让问题可见，也不能拿用户数据赌。
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()

    @Provides
    fun provideCountdownDao(db: AppDatabase): CountdownDao = db.countdownDao()
}
