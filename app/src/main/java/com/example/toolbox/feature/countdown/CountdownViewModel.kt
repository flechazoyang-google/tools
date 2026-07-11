package com.example.toolbox.feature.countdown

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toolbox.core.util.SnackbarEventBus
import com.example.toolbox.core.util.LunarUtils
import com.example.toolbox.core.util.startOfToday
import com.example.toolbox.data.local.datastore.HolidayDataStore
import com.example.toolbox.data.local.entity.CountdownEntity
import com.example.toolbox.data.repository.CountdownRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CountdownViewModel @Inject constructor(
    private val repo: CountdownRepository,
    val holidayStore: HolidayDataStore,  // exposed for UI holiday panel
    private val eventBus: SnackbarEventBus,
) : ViewModel() {

    /** Exposed hidden holiday IDs for the holiday panel. */
    val hiddenIds: StateFlow<Set<String>> = holidayStore.hiddenIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /** Combined list: user-created entities + visible built-in holidays, sorted. */
    val items: StateFlow<List<CountdownEntity>> = combine(
        repo.observeAll(),
        holidayStore.hiddenIds,
    ) { entities, hiddenIds ->
        val today = startOfToday()
        val userItems = entities.map { e ->
            if (e.isLunar && e.lunarMonth > 0 && e.lunarDay > 0) {
                // Dynamically compute next solar date for lunar events
                val nextSolar = LunarUtils.lunarToNextSolar(e.lunarMonth, e.lunarDay, today)
                e.copy(targetDate = nextSolar)
            } else e
        }
        val holidayItems = BUILT_IN_HOLIDAYS
            .filter { it.id !in hiddenIds }
            .map { it.toEntity(today) }

        (userItems + holidayItems).sortedWith(
            compareBy<CountdownEntity> { !it.isPinned }
                .thenBy { it.targetDate }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun add(
        title: String,
        targetDate: Long,
        colorTag: String,
        type: String = "countdown",
        isLunar: Boolean = false,
        lunarMonth: Int = 0,
        lunarDay: Int = 0,
    ) {
        viewModelScope.launch {
            val finalTarget = if (isLunar && lunarMonth > 0 && lunarDay > 0) {
                LunarUtils.lunarToNextSolar(lunarMonth, lunarDay, startOfToday())
            } else targetDate
            repo.add(
                CountdownEntity(
                    title = title,
                    targetDate = finalTarget,
                    colorTag = colorTag,
                    type = type,
                    isLunar = isLunar,
                    lunarMonth = lunarMonth,
                    lunarDay = lunarDay,
                )
            )
            eventBus.send("已添加「${title}」")
        }
    }

    fun delete(entity: CountdownEntity) {
        viewModelScope.launch {
            repo.delete(entity)
            eventBus.send("已删除「${entity.title}」")
        }
    }

    fun togglePin(entity: CountdownEntity) {
        viewModelScope.launch {
            repo.togglePin(entity)
            eventBus.send(if (entity.isPinned) "已取消置顶" else "已置顶")
        }
    }

    fun update(
        entity: CountdownEntity,
        title: String,
        targetDate: Long,
        colorTag: String,
        type: String,
        isLunar: Boolean = false,
        lunarMonth: Int = 0,
        lunarDay: Int = 0,
    ) {
        viewModelScope.launch {
            val finalTarget = if (isLunar && lunarMonth > 0 && lunarDay > 0) {
                LunarUtils.lunarToNextSolar(lunarMonth, lunarDay, startOfToday())
            } else targetDate
            repo.update(
                entity.copy(
                    title = title,
                    targetDate = finalTarget,
                    colorTag = colorTag,
                    type = type,
                    isLunar = isLunar,
                    lunarMonth = lunarMonth,
                    lunarDay = lunarDay,
                )
            )
            eventBus.send("已更新事件")
        }
    }

    fun setHolidayVisible(id: String, visible: Boolean) {
        viewModelScope.launch { holidayStore.setVisible(id, visible) }
    }
}
