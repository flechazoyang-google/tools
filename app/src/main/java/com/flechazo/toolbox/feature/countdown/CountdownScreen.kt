package com.flechazo.toolbox.feature.countdown

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flechazo.toolbox.core.designsystem.components.FeedbackBlock
import com.flechazo.toolbox.core.designsystem.components.FeedbackType
import com.flechazo.toolbox.core.designsystem.components.SegmentedTabs
import com.flechazo.toolbox.core.designsystem.components.ToolScaffold
import com.flechazo.toolbox.core.navigation.DeepLink
import com.flechazo.toolbox.core.notify.CountdownNotifications
import kotlinx.coroutines.delay

/** 倒数日页面。数据与时间语义全在 ViewModel / Engine 里，这里只做渲染。 */
@Composable
fun CountdownScreen(
    onBack: () -> Unit,
    viewModel: CountdownViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val requestPermission by viewModel.requestNotificationPermission.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    var editing by remember { mutableStateOf<CountdownEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(CountdownDraft()) }

    // 通知点进来时带的事件 id（一次性消费，重组不会重复触发滚动）
    LaunchedEffect(Unit) {
        DeepLink.consumeEventId()?.let { viewModel.requestHighlight(it) }
    }

    // 用户拒绝时静默降级：不排提醒，但记录 / 排序 / 天数计算全部照常
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.onPermissionResult() }

    LaunchedEffect(requestPermission) {
        if (requestPermission && !CountdownNotifications.canNotify(context)) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { msg ->
            val action = if (msg.undoAction != null) "撤销" else null
            val result = snackbarHostState.showSnackbar(
                message = msg.text,
                actionLabel = action,
                duration = if (action != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) msg.undoAction?.invoke()
        }
    }

    val rows = remember(state.groups, state.hero) { buildRows(state) }

    // 打开编辑器：把实体回填成草稿（农历事件按落库的农历字段回显，而不是公历日）
    val openFor: (CountdownEntity) -> Unit = { e ->
        draft = CountdownDraft.fromEntity(e, viewModel.lunar, state.today)
        editing = e
        showEditor = true
    }

    // 从通知点进来：滚到那张卡片并高亮一下，然后消费掉
    LaunchedEffect(state.highlightId, rows) {
        val target = state.highlightId ?: return@LaunchedEffect
        val index = rows.indexOfFirst { it is RowSpec.Event && it.item.event.id == target }
        if (index >= 0) {
            listState.animateScrollToItem(index.coerceAtLeast(0))
            delay(2_400)
            viewModel.consumeHighlight()
        } else {
            viewModel.consumeHighlight()
        }
    }

    Box(Modifier.fillMaxSize()) {
        ToolScaffold(
            title = "倒数日",
            subtitle = "记录重要的日子，还剩几天自动计算",
            onBack = onBack,
            scrollable = false,
            actions = {
                Box {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Filled.SwapVert, contentDescription = "排序方式")
                    }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        CountdownEngine.SortMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        mode.label,
                                        color = if (mode == state.sort) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                },
                                onClick = {
                                    viewModel.setSort(mode)
                                    showSortMenu = false
                                },
                            )
                        }
                    }
                }
                ExtendedFloatingActionButton(
                    onClick = {
                        draft = CountdownDraft()
                        editing = null
                        showEditor = true
                    },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("添加") },
                )
            },
        ) {
            if (state.totalEvents > 0) {
                SegmentedTabs(
                    options = CountdownFilter.entries.toList(),
                    selected = state.filter,
                    label = { it.label },
                    onSelect = viewModel::setFilter,
                )
            }

            if (rows.isEmpty()) {
                FeedbackBlock(
                    text = if (state.totalEvents == 0) {
                        "还没有事件。生日、考试、还信用卡——重要的日子都值得记下来"
                    } else {
                        "这个筛选下没有事件"
                    },
                    type = FeedbackType.EMPTY,
                )
                if (state.totalEvents == 0) {
                    QuickTemplates(
                        onPick = { preset ->
                            draft = preset()
                            editing = null
                            showEditor = true
                        },
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(rows, key = { it.key }) { row ->
                        when (row) {
                            is RowSpec.Hero -> CountdownHeroCard(
                                item = row.item,
                                onClick = { openFor(row.item.event) },
                            )
                            is RowSpec.Header -> Text(
                                "${row.kind.label} · ${row.count}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = if (row.index == 0) 0.dp else 6.dp),
                            )
                            is RowSpec.Event -> CountdownEventCard(
                                item = row.item,
                                highlighted = state.highlightId == row.item.event.id,
                                onClick = { openFor(row.item.event) },
                                onLongClick = {
                                    viewModel.togglePin(row.item.event.id, !row.item.event.pinned)
                                },
                            )
                            is RowSpec.Empty -> Unit
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
        )
    }

    if (showEditor) {
        CountdownEditor(
            initial = draft,
            isEditing = editing != null,
            lunar = viewModel.lunar,
            today = state.today,
            onDismiss = { showEditor = false },
            onSave = { saved ->
                viewModel.save(saved, editing?.id)
                showEditor = false
            },
            onDelete = if (editing != null) {
                {
                    val e = editing!!
                    showEditor = false
                    viewModel.delete(e.id, e.title)
                }
            } else {
                null
            },
        )
    }
}

private sealed interface RowSpec {
    val key: Any

    data class Hero(val item: CountdownItem) : RowSpec {
        override val key: Any = "hero-${item.event.id}"
    }

    data class Header(val kind: GroupKind, val count: Int, val index: Int) : RowSpec {
        override val key: Any = "header-${kind.name}"
    }

    data class Event(val item: CountdownItem) : RowSpec {
        override val key: Any = "event-${item.event.id}"
    }

    data object Empty : RowSpec {
        override val key: Any = "empty"
    }
}

/** Hero 卡不重复出现在分组列表里，否则同一个事件在首屏看到两遍。 */
private fun buildRows(state: CountdownUiState): List<RowSpec> {
    if (state.totalEvents == 0) return listOf(RowSpec.Empty)
    val rows = ArrayList<RowSpec>(state.filteredCount + state.groups.size + 1)
    state.hero?.let { rows += RowSpec.Hero(it) }
    val heroId = state.hero?.event?.id
    state.groups.forEachIndexed { gi, group ->
        val items = group.items.filterNot { it.event.id == heroId }
        if (items.isEmpty()) return@forEachIndexed
        rows += RowSpec.Header(group.kind, items.size, gi)
        items.forEach { rows += RowSpec.Event(it) }
    }
    return rows.ifEmpty { listOf(RowSpec.Empty) }
}

/** 空态给三个一键模板：第一次用的人不知道该记什么，比一句"点右上角添加"有用。 */
@Composable
private fun QuickTemplates(onPick: (() -> CountdownDraft) -> Unit) {
    val templates = listOf(
        "记一个生日" to {
            CountdownDraft(title = "生日", repeat = RepeatRule.YEARLY_SOLAR, mode = EventMode.COUNTDOWN)
        },
        "记一段已经开始的日子的天数" to {
            CountdownDraft(
                title = "在一起的日子",
                mode = EventMode.ELAPSED,
                solarDate = java.time.LocalDate.now().minusYears(1),
            )
        },
        "记一个还没到的截止日" to {
            CountdownDraft(
                title = "截止日",
                mode = EventMode.COUNTDOWN,
                solarDate = java.time.LocalDate.now().plusDays(30),
            )
        },
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        templates.forEach { (label, preset) ->
            TextButton(onClick = { onPick(preset) }, modifier = Modifier.fillMaxWidth()) {
                Text(label)
            }
        }
    }
}
