package com.flechazo.toolbox.feature.period

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flechazo.toolbox.core.data.PeriodRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/** 经期记录页的完整状态。所有派生值都由 [records] + [today] 计算，避免多份真值。 */
data class PeriodUiState(
    val records: List<PeriodRecord> = emptyList(),
    val logs: List<DailyLog> = emptyList(),
    val settings: PeriodSettings = PeriodSettings(),
    val prediction: PeriodPrediction? = null,
    val month: YearMonth = YearMonth.now(),
    val today: LocalDate = LocalDate.now(),
    /** 最近一次删除，用于「撤销」。 */
    val lastDeleted: PeriodRecord? = null,
    val message: String? = null,
) {
    val hasRecords: Boolean get() = records.isNotEmpty()

    /** 进行中的经期（end 缺失），取最近一次。 */
    val ongoingRecord: PeriodRecord?
        get() = records.filter { it.isOngoing }.maxByOrNull { it.start }

    fun recordCovering(date: LocalDate): PeriodRecord? = records.firstOrNull { it.contains(date) }

    fun logOn(date: LocalDate): DailyLog? = logs.firstOrNull { it.date == date }

    /** 今天是本次经期第几天；不在经期内为 null。 */
    val periodDay: Int?
        get() = periodDayIndex(records, today, prediction?.stats?.periodLength ?: DEFAULT_PERIOD)

    /** 今天是当前周期第几天。 */
    val cycleDay: Int? get() = cycleDayIndex(records, today)
}

@HiltViewModel
class PeriodViewModel @Inject constructor(
    private val repository: PeriodRepository,
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.now())
    private val today = MutableStateFlow(LocalDate.now())
    private val lastDeleted = MutableStateFlow<PeriodRecord?>(null)
    private val message = MutableStateFlow<String?>(null)

    val state: StateFlow<PeriodUiState> = combine(
        repository.data,
        month,
        today,
        lastDeleted,
        message,
    ) { data, currentMonth, currentToday, deleted, currentMessage ->
        PeriodUiState(
            records = data.records,
            logs = data.logs,
            settings = data.settings,
            prediction = predictPeriod(data.records, currentToday, data.settings),
            month = currentMonth,
            today = currentToday,
            lastDeleted = deleted,
            message = currentMessage,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeriodUiState())

    init {
        // 旧版「开始日数组」→ 周期记录，只做一次。
        viewModelScope.launch { repository.ensureMigrated() }
    }

    // ---- 导航 ----

    fun previousMonth() {
        month.value = month.value.minusMonths(1)
    }

    fun nextMonth() {
        month.value = month.value.plusMonths(1)
    }

    fun goToToday() {
        today.value = LocalDate.now()
        month.value = YearMonth.from(today.value)
    }

    // ---- 记录 ----

    /** 标记某日为一次经期的开始；与已有记录间隔过近时会被 [sanitizeRecords] 合并。 */
    fun markStart(date: LocalDate) = viewModelScope.launch {
        if (date.isAfter(LocalDate.now())) {
            message.value = "不能记录未来日期"
            return@launch
        }
        val current = repository.data.first()
        if (current.records.any { it.contains(date) }) {
            message.value = "该日期已在经期内"
            return@launch
        }
        val records = sanitizeRecords(current.records + PeriodRecord(start = date), today.value)
        repository.save(current.copy(records = records))
        message.value = "已记录经期开始"
    }

    /** 给最近一次进行中的经期补结束日。 */
    fun markEnd(date: LocalDate) = viewModelScope.launch {
        if (date.isAfter(LocalDate.now())) {
            message.value = "不能记录未来日期"
            return@launch
        }
        val current = repository.data.first()
        val ongoing = current.records.filter { it.isOngoing }.maxByOrNull { it.start }
        if (ongoing == null) {
            message.value = "还没有进行中的经期，请先记录开始日"
            return@launch
        }
        val end = if (date.isBefore(ongoing.start)) ongoing.start else date
        val records = sanitizeRecords(
            current.records.map { if (it.start == ongoing.start) it.copy(end = end) else it },
            today.value,
        )
        repository.save(current.copy(records = records))
        message.value = "已记录经期结束"
    }

    fun deleteRecord(start: LocalDate) = viewModelScope.launch {
        val current = repository.data.first()
        val removed = current.records.firstOrNull { it.start == start } ?: return@launch
        lastDeleted.value = removed
        repository.save(current.copy(records = current.records.filterNot { it.start == start }))
    }

    fun undoDelete() = viewModelScope.launch {
        val removed = lastDeleted.value ?: return@launch
        val current = repository.data.first()
        val records = sanitizeRecords(current.records + removed, today.value)
        repository.save(current.copy(records = records))
        lastDeleted.value = null
    }

    fun clearUndo() {
        lastDeleted.value = null
    }

    // ---- 每日记录 ----

    fun setFlow(date: LocalDate, level: FlowLevel?) = viewModelScope.launch {
        val current = repository.data.first()
        val target = current.records.firstOrNull { it.contains(date) } ?: return@launch
        val flows = if (level == null) target.flows - date else target.flows + (date to level)
        repository.save(
            current.copy(
                records = current.records.map {
                    if (it.start == target.start) it.copy(flows = flows) else it
                },
            ),
        )
    }

    fun saveLog(log: DailyLog) = viewModelScope.launch {
        val current = repository.data.first()
        val logs = current.logs.filterNot { it.date == log.date } +
            (if (log.isEmpty) emptyList() else listOf(log))
        repository.save(current.copy(logs = logs.sortedByDescending { it.date }))
    }

    // ---- 设置 ----

    fun updateSettings(settings: PeriodSettings) = viewModelScope.launch {
        val current = repository.data.first()
        repository.save(current.copy(settings = settings))
    }

    fun acceptConsent() = viewModelScope.launch {
        val current = repository.data.first()
        repository.save(current.copy(settings = current.settings.copy(consentAccepted = true)))
    }

    fun clearMessage() {
        message.value = null
    }

    // ---- 导入 / 导出 ----

    /** 导出为 JSON，可直接再导入。 */
    suspend fun exportJson(): String = PeriodCodec.encode(repository.data.first())

    /** 合并导入：同 start 的经期与同日期的日志以导入文件为准，本地设置保持不变。 */
    fun importJson(raw: String) = viewModelScope.launch {
        val imported = runCatching { PeriodCodec.decodeOrThrow(raw) }.getOrNull()
        if (imported == null) {
            message.value = "导入失败：文件格式不正确"
            return@launch
        }
        val current = repository.data.first()
        val recordsByStart = current.records.associateBy { it.start }.toMutableMap()
        imported.records.forEach { recordsByStart[it.start] = it }
        val logsByDate = current.logs.associateBy { it.date }.toMutableMap()
        imported.logs.forEach { logsByDate[it.date] = it }
        repository.save(
            current.copy(
                records = recordsByStart.values.sortedByDescending { it.start },
                logs = logsByDate.values.sortedByDescending { it.date },
            ),
        )
        message.value = "已导入 ${imported.records.size} 次经期、${imported.logs.size} 条每日记录"
    }

    /** 清空全部记录（设置保留）。 */
    fun clearAllData() = viewModelScope.launch {
        val current = repository.data.first()
        repository.save(current.copy(records = emptyList(), logs = emptyList()))
        message.value = "已清空全部记录"
    }
}
