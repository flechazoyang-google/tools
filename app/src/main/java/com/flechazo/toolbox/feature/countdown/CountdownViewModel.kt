package com.flechazo.toolbox.feature.countdown

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flechazo.toolbox.core.notify.CountdownNotifications
import com.flechazo.toolbox.core.notify.CountdownRescheduler
import com.flechazo.toolbox.core.util.DayTicker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class CountdownFilter(val label: String) {
    ALL("全部"),
    PINNED("置顶"),
    LUNAR("农历"),
}

/** 列表分组。保持 [GroupKind] 的声明顺序渲染。 */
data class CountdownGroup(val kind: GroupKind, val items: List<CountdownItem>)

data class CountdownUiState(
    val groups: List<CountdownGroup> = emptyList(),
    val hero: CountdownItem? = null,
    val totalEvents: Int = 0,
    val filteredCount: Int = 0,
    val today: LocalDate = LocalDate.now(),
    val sort: CountdownEngine.SortMode = CountdownEngine.SortMode.NEAREST,
    val filter: CountdownFilter = CountdownFilter.ALL,
    /** 需要高亮滚动定位的事件（从通知点进来），消费后由 UI 置回 null */
    val highlightId: Long? = null,
)

/** 一次性提示；带 [undoAction] 时 UI 渲染成"撤销"按钮。 */
data class CountdownMessage(val text: String, val undoAction: (() -> Unit)? = null)

@HiltViewModel
class CountdownViewModel @Inject constructor(
    private val repository: CountdownRepository,
    /** 表单也需要它来做农历合法性判定，故为 public */
    val lunar: LunarCalendar,
    private val rescheduler: CountdownRescheduler,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val sortFlow = MutableStateFlow(CountdownEngine.SortMode.NEAREST)
    private val filterFlow = MutableStateFlow(CountdownFilter.ALL)
    private val highlightFlow = MutableStateFlow<Long?>(null)

    private val _messages = MutableSharedFlow<CountdownMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<CountdownMessage> = _messages.asSharedFlow()

    /** 跨零点的"今天"。页面挂着过夜天数也会自己走对（修 P0-6）。 */
    private val todayFlow = DayTicker.dayFlow().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalDate.now(),
    )

    val state: StateFlow<CountdownUiState> = combine(
        repository.observeAll(),
        todayFlow,
        sortFlow,
        filterFlow,
        // highlightFlow 必须作为 combine 的**输入**：早先是在 lambda 里读 `.value`，
        // 于是 requestHighlight() 不会触发任何重算，通知点进来的高亮/滚动永远不生效。
        highlightFlow,
    ) { events, today, sort, filter, highlightId ->
        val all = events.map { CountdownEngine.buildItem(it, today, lunar) }
        val visible = all.filter { item ->
            when (filter) {
                CountdownFilter.ALL -> true
                CountdownFilter.PINNED -> item.event.pinned
                CountdownFilter.LUNAR -> item.event.isLunar
            }
        }
        val sorted = CountdownEngine.sorted(visible, sort)
        val groups = GroupKind.entries.mapNotNull { kind ->
            val items = sorted.filter { CountdownEngine.groupOf(it) == kind }
            if (items.isEmpty()) null else CountdownGroup(kind, items)
        }
        CountdownUiState(
            groups = groups,
            // Hero 只给"全部"视图：过滤后的列表里挑头名会让用户以为只剩这一个
            hero = if (filter == CountdownFilter.ALL) sorted.firstOrNull { it.nextDate != null } else null,
            totalEvents = all.size,
            filteredCount = sorted.size,
            today = today,
            sort = sort,
            filter = filter,
            highlightId = highlightId,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CountdownUiState())

    fun setSort(sort: CountdownEngine.SortMode) { sortFlow.value = sort }
    fun setFilter(filter: CountdownFilter) { filterFlow.value = filter }
    fun consumeHighlight() { highlightFlow.value = null }

    /** 通知点进来时由页面调用：滚到那张卡片并高亮一次。 */
    fun requestHighlight(id: Long) { highlightFlow.value = id }

    fun save(draft: CountdownDraft, existingId: Long?) {
        viewModelScope.launch {
            val existing = existingId?.let { repository.byId(it) }
            val entity = draft.toEntity(lunar, todayFlow.value, existing)
            if (entity == null) {
                _messages.tryEmit(CountdownMessage("标题不能为空"))
                return@launch
            }
            val saved = if (existingId == null) {
                val id = repository.create(entity)
                entity.copy(id = id)
            } else {
                repository.save(entity)
                entity
            }
            rescheduler.rescheduleEvent(saved.id)
            _messages.tryEmit(
                CountdownMessage(
                    if (existingId == null) "已添加「${saved.title}」" else "已更新「${saved.title}」",
                ),
            )
            maybeAskNotificationPermission(saved)
        }
    }

    fun delete(id: Long, title: String) {
        viewModelScope.launch {
            val removed = repository.deleteAndGet(id) ?: return@launch
            rescheduler.cancelEvent(id)
            _messages.tryEmit(
                CountdownMessage("已删除「$title」") {
                    viewModelScope.launch {
                        repository.restore(removed)
                        rescheduler.rescheduleEvent(removed.id)
                    }
                },
            )
        }
    }

    fun togglePin(id: Long, pinned: Boolean) {
        viewModelScope.launch {
            val e = repository.byId(id) ?: return@launch
            repository.save(e.copy(pinned = pinned))
            _messages.tryEmit(CountdownMessage(if (pinned) "已置顶" else "已取消置顶"))
        }
    }

    /**
     * 只在**用户确实开启了提醒**时才要通知权限。
     *
     * 旧实现是"保存任意事件后立刻弹权限"，属于未经同意就要权限。这里改成：
     * 用户显式开了提醒 → 才请求；请求被拒 → 静默降级，记录/排序/天数计算全部照常。
     */
    private fun maybeAskNotificationPermission(entity: CountdownEntity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (!entity.remindEnabled) return
        if (CountdownNotifications.canNotify(context)) return
        _requestNotificationPermission.value = true
    }

    /** UI 观察到 true 时发起权限请求，随后调用 [onPermissionResult] 复位。 */
    private val _requestNotificationPermission = MutableStateFlow(false)
    val requestNotificationPermission: StateFlow<Boolean> = _requestNotificationPermission.asStateFlow()

    fun onPermissionResult() {
        _requestNotificationPermission.value = false
    }

    fun rescheduleAll() {
        viewModelScope.launch { rescheduler.rescheduleAll() }
    }
}
