package com.example.toolbox.feature.period

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toolbox.data.local.datastore.PeriodDataStore
import com.example.toolbox.data.local.datastore.PeriodRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class PeriodViewModel @Inject constructor(
    private val dataStore: PeriodDataStore,
) : ViewModel() {

    val periods: StateFlow<List<PeriodRecord>> = dataStore.periods.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList(),
    )

    fun toggleDate(dateMillis: Long) {
        viewModelScope.launch {
            val current = periods.value.toMutableList()
            val dayStart = startOfDay(dateMillis)

            // Check if this date is already in a period
            val existingIdx = current.indexOfFirst { dayStart in it.startDateMillis..it.endDateMillis }
            if (existingIdx >= 0) {
                val rec = current[existingIdx]
                // Remove single day from period, or remove whole record
                if (rec.startDateMillis == rec.endDateMillis && rec.startDateMillis == dayStart) {
                    current.removeAt(existingIdx)
                } else if (dayStart == rec.startDateMillis) {
                    current[existingIdx] = rec.copy(startDateMillis = rec.startDateMillis + DAY_MS)
                } else if (dayStart == rec.endDateMillis) {
                    current[existingIdx] = rec.copy(endDateMillis = rec.endDateMillis - DAY_MS)
                } else {
                    // Split: remove day in middle of period
                    current.removeAt(existingIdx)
                    current.add(PeriodRecord(rec.startDateMillis, dayStart - DAY_MS))
                    current.add(PeriodRecord(dayStart + DAY_MS, rec.endDateMillis))
                }
            } else {
                // Add or merge with adjacent period days
                val adjacent = current.filter { it.endDateMillis == dayStart - DAY_MS || it.startDateMillis == dayStart + DAY_MS }
                if (adjacent.isEmpty()) {
                    current.add(PeriodRecord(dayStart, dayStart))
                } else {
                    // Merge
                    current.removeAll(adjacent.toSet())
                    val newStart = adjacent.minOf { it.startDateMillis }.coerceAtMost(dayStart)
                    val newEnd = adjacent.maxOf { it.endDateMillis }.coerceAtLeast(dayStart)
                    current.add(PeriodRecord(newStart, newEnd))
                }
            }

            dataStore.savePeriods(current.sortedBy { it.startDateMillis })
        }
    }

    fun isPeriodDay(dateMillis: Long, records: List<PeriodRecord>): Boolean {
        val d = startOfDay(dateMillis)
        return records.any { d in it.startDateMillis..it.endDateMillis }
    }

    companion object {
        private const val DAY_MS = 86_400_000L
        private val TZ = java.util.TimeZone.getTimeZone("Asia/Shanghai")

        fun startOfDay(millis: Long): Long {
            val c = Calendar.getInstance(TZ).apply {
                timeInMillis = millis
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return c.timeInMillis
        }
    }
}

/** Prediction / statistics helper. */
data class PeriodStats(
    val cycleLengthAvg: Int = 28,      // days
    val periodLengthAvg: Int = 5,       // days
    val nextPredictedStart: Long? = null,
    val nextPredictedEnd: Long? = null,
    val daysUntilNext: Int? = null,
    // Ovulation / fertile window
    val ovulationDay: Long? = null,     // predicted ovulation date
    val fertileStart: Long? = null,     // fertile window start (ovulation - 5)
    val fertileEnd: Long? = null,       // fertile window end (ovulation + 1)
)

fun computeStats(records: List<PeriodRecord>, today: Long): PeriodStats {
    val sorted = records.sortedBy { it.startDateMillis }
    if (sorted.isEmpty()) return PeriodStats()

    // Cycle lengths: days between consecutive period starts
    val cycleLengths = mutableListOf<Int>()
    for (i in 1 until sorted.size) {
        val diff = ((sorted[i].startDateMillis - sorted[i - 1].startDateMillis) / 86_400_000L).toInt()
        if (diff in 20..45) cycleLengths.add(diff)
    }

    // Period lengths
    val periodLengths = sorted.map {
        ((it.endDateMillis - it.startDateMillis) / 86_400_000L + 1).toInt()
    }

    val avgCycle = if (cycleLengths.isNotEmpty()) {
        cycleLengths.takeLast(3).average().toInt().coerceIn(21, 42)
    } else 28

    val avgPeriod = if (periodLengths.isNotEmpty()) {
        periodLengths.takeLast(3).average().toInt().coerceIn(2, 10)
    } else 5

    val lastPeriod = sorted.lastOrNull()
    val predictedStart = if (lastPeriod != null) {
        lastPeriod.startDateMillis + avgCycle * 86_400_000L
    } else null

    val predictedEnd = predictedStart?.let { it + (avgPeriod - 1) * 86_400_000L }

    val daysUntil = predictedStart?.let {
        ((it - today) / 86_400_000L).toInt()
    }

    return PeriodStats(
        cycleLengthAvg = avgCycle,
        periodLengthAvg = avgPeriod,
        nextPredictedStart = predictedStart,
        nextPredictedEnd = predictedEnd,
        daysUntilNext = daysUntil,
        ovulationDay = predictedStart?.let { it - 14 * 86_400_000L },
        fertileStart = predictedStart?.let { it - 19 * 86_400_000L },  // ovulation - 5
        fertileEnd = predictedStart?.let { it - 13 * 86_400_000L },    // ovulation + 1
    )
}
