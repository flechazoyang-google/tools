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

    val themeMode: Flow<ThemeMode> = store.data.map { prefs ->
        runCatching { ThemeMode.valueOf(prefs[keyTheme] ?: ThemeMode.SYSTEM.name) }
            .getOrDefault(ThemeMode.SYSTEM)
    }

    val dynamicColor: Flow<Boolean> = store.data.map { it[keyDynamic] ?: true }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[keyTheme] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        store.edit { it[keyDynamic] = enabled }
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

/** 经期开始日期集合，DataStore 持久化（替代原先裸 SharedPreferences）。 */
@Singleton
class PeriodRepository @Inject constructor(
    @Named("period") private val store: DataStore<Preferences>,
    private val gson: Gson,
) {
    private val keyStarts = stringPreferencesKey("starts")

    val starts: Flow<List<LocalDate>> = store.data.map { prefs -> decode(prefs[keyStarts]) }

    suspend fun add(date: LocalDate) {
        store.edit { prefs ->
            val next = (decode(prefs[keyStarts]) + date).distinct().sortedDescending()
            prefs[keyStarts] = gson.toJson(next.map { it.toString() })
        }
    }

    suspend fun remove(date: LocalDate) {
        store.edit { prefs ->
            val next = decode(prefs[keyStarts]).filterNot { it == date }.sortedDescending()
            prefs[keyStarts] = gson.toJson(next.map { it.toString() })
        }
    }

    private fun decode(raw: String?): List<LocalDate> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            gson.fromJson(raw, Array<String>::class.java)?.toList().orEmpty()
        }.getOrDefault(emptyList())
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .sortedDescending()
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
