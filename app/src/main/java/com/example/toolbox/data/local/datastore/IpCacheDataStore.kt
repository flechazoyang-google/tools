package com.example.toolbox.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Short-lived IP lookup cache. Stores a map of ip -> [IpInfo], capped at 20 entries.
 * Reduces network calls for repeat queries.
 */
class IpCacheDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val gson: Gson,
) {
    private val key = stringPreferencesKey("ip_cache")

    private suspend fun readMap(): Map<String, IpInfo> {
        val json = dataStore.data.first()[key] ?: return emptyMap()
        if (json.isBlank()) return emptyMap()
        return runCatching {
            gson.fromJson<Map<String, IpInfo>>(json, ipMapType)
        }.getOrDefault(emptyMap())
    }

    fun observe(): Flow<List<IpInfo>> = dataStore.data.map { prefs ->
        val json = prefs[key] ?: ""
        if (json.isBlank()) return@map emptyList()
        runCatching {
            gson.fromJson<Map<String, IpInfo>>(json, ipMapType)
        }.getOrDefault(emptyMap()).values.sortedByDescending { it.cachedAt }
    }

    suspend fun get(ip: String): IpInfo? = readMap()[ip]

    suspend fun put(info: IpInfo) {
        dataStore.edit { prefs ->
            val map = readMap().toMutableMap()
            map[info.ip] = info
            val trimmed = map.values
                .sortedByDescending { it.cachedAt }
                .take(20)
                .associateBy { it.ip }
            prefs[key] = gson.toJson(trimmed)
        }
    }

    companion object {
        private val ipMapType = object : TypeToken<Map<String, IpInfo>>() {}.type
    }
}
