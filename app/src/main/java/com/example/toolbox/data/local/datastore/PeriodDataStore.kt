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
 * Period tracker data — stores a list of period records (start/end dates) as JSON.
 */
class PeriodDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val gson: Gson,
) {
    val periods: Flow<List<PeriodRecord>> = dataStore.data.map { prefs ->
        val json = prefs[PERIODS_KEY] ?: return@map emptyList()
        val type = object : TypeToken<List<PeriodRecord>>() {}.type
        gson.fromJson(json, type) ?: emptyList()
    }

    suspend fun savePeriods(records: List<PeriodRecord>) {
        dataStore.edit { it[PERIODS_KEY] = gson.toJson(records) }
    }

    companion object {
        private val PERIODS_KEY = stringPreferencesKey("period_records")
    }
}

/** One menstrual period event. */
data class PeriodRecord(
    val startDateMillis: Long,  // epoch ms, start of day (00:00 UTC+8)
    val endDateMillis: Long,    // epoch ms, end of day (00:00 UTC+8) — inclusive
)
