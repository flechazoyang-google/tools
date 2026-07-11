package com.example.toolbox.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Stores which built-in holidays are hidden by the user.
 * Saves a set of disabled holiday IDs as JSON.
 */
class HolidayDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val gson: Gson,
) {
    val hiddenIds: Flow<Set<String>> = dataStore.data.map { prefs ->
        val json = prefs[HIDDEN_KEY] ?: return@map emptySet()
        val type = object : TypeToken<Set<String>>() {}.type
        gson.fromJson(json, type) ?: emptySet()
    }

    suspend fun setVisible(id: String, visible: Boolean) {
        dataStore.edit { prefs ->
            val json = prefs[HIDDEN_KEY] ?: "[]"
            val type = object : TypeToken<Set<String>>() {}.type
            val current = gson.fromJson<Set<String>>(json, type)?.toMutableSet() ?: mutableSetOf()
            if (visible) current.remove(id) else current.add(id)
            prefs[HIDDEN_KEY] = gson.toJson(current)
        }
    }

    companion object {
        private val HIDDEN_KEY = stringPreferencesKey("holiday_hidden_ids")
    }
}
