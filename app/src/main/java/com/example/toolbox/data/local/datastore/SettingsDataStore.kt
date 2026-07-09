package com.example.toolbox.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.toolbox.core.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * App-wide preferences: theme mode + recently-used tool ids (most recent first, capped at 8).
 */
class SettingsDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        ThemeMode.fromOrdinal(prefs[THEME_KEY] ?: ThemeMode.SYSTEM.ordinal)
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_KEY] = mode.ordinal }
    }

    val recentTools: Flow<List<String>> = dataStore.data.map { prefs ->
        (prefs[RECENT_KEY] ?: "").split(',').filter { it.isNotBlank() }
    }

    suspend fun pushRecent(toolId: String) {
        dataStore.edit { prefs ->
            val list = (prefs[RECENT_KEY] ?: "")
                .split(',')
                .filter { it.isNotBlank() }
                .toMutableList()
            list.remove(toolId)
            list.add(0, toolId)
            while (list.size > 8) list.removeAt(list.lastIndex)
            prefs[RECENT_KEY] = list.joinToString(",")
        }
    }

    // ---- Password-box master-password gate (verifier only; encryption uses Keystore) ----
    val masterHash: Flow<String?> = dataStore.data.map { prefs -> prefs[MASTER_HASH_KEY] }

    suspend fun setMasterHash(hash: String) {
        dataStore.edit { it[MASTER_HASH_KEY] = hash }
    }

    companion object {
        val THEME_KEY = intPreferencesKey("theme_mode")
        val RECENT_KEY = stringPreferencesKey("recent_tools")
        val MASTER_HASH_KEY = stringPreferencesKey("master_hash")
    }
}
