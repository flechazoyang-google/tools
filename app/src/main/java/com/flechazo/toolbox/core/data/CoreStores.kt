package com.flechazo.toolbox.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.flechazo.toolbox.feature.period.PeriodCodec
import com.flechazo.toolbox.feature.period.PeriodData
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** App-level preferences: theme mode, dynamic color switch. */
@Singleton
class SettingsRepository @Inject constructor(
    @Named("settings") private val store: DataStore<Preferences>,
) {
    private val keyTheme = stringPreferencesKey("theme_mode")
    private val keyDynamic = booleanPreferencesKey("dynamic_color")
    private val keyLastUpdateCheck = longPreferencesKey("last_update_check_ms")

    val themeMode: Flow<ThemeMode> = store.data.map { prefs ->
        runCatching { ThemeMode.valueOf(prefs[keyTheme] ?: ThemeMode.SYSTEM.name) }
            .getOrDefault(ThemeMode.SYSTEM)
    }

    val dynamicColor: Flow<Boolean> = store.data.map { it[keyDynamic] ?: true }

    /** 上次「检查更新」的时间戳，用于启动自动检查的每日限频。 */
    val lastUpdateCheckMs: Flow<Long> = store.data.map { it[keyLastUpdateCheck] ?: 0L }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[keyTheme] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        store.edit { it[keyDynamic] = enabled }
    }

    suspend fun setLastUpdateCheckMs(value: Long) {
        store.edit { it[keyLastUpdateCheck] = value }
    }
}

/** Favorites + recent usage for tools, persisted locally. */
@Singleton
class ToolsStateRepository @Inject constructor(
    @Named("toolsState") private val store: DataStore<Preferences>,
) {    private val keyFavorites = stringSetPreferencesKey("favorites")
    private val keyRecent = stringPreferencesKey("recent_ids")
    private val keyRecentTime = longPreferencesKey("recent_time_prefix_")

    val favorites: Flow<Set<String>> = store.data.map { it[keyFavorites] ?: emptySet() }

    val recentIds: Flow<List<String>> = store.data.map { prefs ->
        (prefs[keyRecent] ?: "").split(",").filter { it.isNotBlank() }
    }

    suspend fun toggleFavorite(id: String) {
        store.edit { prefs ->
            val current = prefs[keyFavorites] ?: emptySet()
            prefs[keyFavorites] = if (id in current) current - id else current + id
        }
    }

    suspend fun recordRecent(id: String) {
        store.edit { prefs ->
            val list = (prefs[keyRecent] ?: "").split(",").filter { it.isNotBlank() }.toMutableList()
            list.remove(id)
            list.add(0, id)
            prefs[keyRecent] = list.take(10).joinToString(",")
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object CoreStoreModule {

    @Provides @Singleton @Named("settings")
    fun provideSettingsStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }

    @Provides @Singleton @Named("toolsState")
    fun provideToolsStateStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("tools_state") }

    @Provides @Singleton @Named("currency")
    fun provideCurrencyStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("currency_cache") }

    @Provides @Singleton @Named("pomodoro")
    fun providePomodoroStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("pomodoro_stats") }

    @Provides @Singleton @Named("period")
    fun providePeriodStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("period_records") }

    @Provides @Singleton
    fun provideGson(): Gson = Gson()
}

/**
 * 经期记录：周期（起止 + 每日经量）+ 每日主观记录 + 设置。
 *
 * v2 结构整体存为一份 JSON（`period_data_v2`），旧版的 `starts` 字符串数组
 * 在首次读取时迁移为周期记录并保留一个版本，便于回滚。
 */
@Singleton
class PeriodRepository @Inject constructor(
    @Named("period") private val store: DataStore<Preferences>,
) {
    private val keyData = stringPreferencesKey("period_data_v2")
    private val keyLegacyStarts = stringPreferencesKey("starts")

    val data: Flow<PeriodData> = store.data.map { read(it) }

    /** 首次读取时把旧版「开始日数组」迁移为新结构；幂等，可重复调用。 */
    suspend fun ensureMigrated() {
        store.edit { prefs ->
            if (prefs[keyData].isNullOrBlank() && !prefs[keyLegacyStarts].isNullOrBlank()) {
                val records = PeriodCodec.decodeLegacyStarts(prefs[keyLegacyStarts])
                prefs[keyData] = PeriodCodec.encode(PeriodData(records = records))
            }
        }
    }

    suspend fun save(data: PeriodData) {
        store.edit { prefs -> prefs[keyData] = PeriodCodec.encode(data) }
    }

    private fun read(prefs: Preferences): PeriodData {
        val raw = prefs[keyData]
        if (!raw.isNullOrBlank()) return PeriodCodec.decode(raw)
        // 迁移尚未落盘时，直接按旧格式呈现，避免中间态丢数据。
        val legacy = PeriodCodec.decodeLegacyStarts(prefs[keyLegacyStarts])
        return if (legacy.isEmpty()) PeriodData() else PeriodData(records = legacy)
    }
}

/** 番茄钟当日统计。跨天自动归零（不写盘，读取时判定）。 */
data class PomodoroStats(
    val date: String = "",
    val cycles: Int = 0,
    val focusMinutes: Int = 0,
)

@Singleton
class PomodoroStatsRepository @Inject constructor(
    @Named("pomodoro") private val store: DataStore<Preferences>,
) {
    private val keyDate = stringPreferencesKey("date")
    private val keyCycles = intPreferencesKey("cycles")
    private val keyFocusMinutes = intPreferencesKey("focus_minutes")

    val stats: Flow<PomodoroStats> = store.data.map { prefs ->
        val today = LocalDate.now().toString()
        if (prefs[keyDate] != today) {
            PomodoroStats(today, 0, 0)
        } else {
            PomodoroStats(today, prefs[keyCycles] ?: 0, prefs[keyFocusMinutes] ?: 0)
        }
    }

    /** 完成一个专注循环后累加统计。 */
    suspend fun recordCompletedWork(focusMinutes: Int) {
        val today = LocalDate.now().toString()
        store.edit { prefs ->
            if (prefs[keyDate] != today) {
                prefs[keyDate] = today
                prefs[keyCycles] = 0
                prefs[keyFocusMinutes] = 0
            }
            prefs[keyCycles] = (prefs[keyCycles] ?: 0) + 1
            prefs[keyFocusMinutes] = (prefs[keyFocusMinutes] ?: 0) + focusMinutes
        }
    }
}
