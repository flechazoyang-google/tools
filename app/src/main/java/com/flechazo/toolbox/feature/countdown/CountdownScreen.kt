package com.flechazo.toolbox.feature.countdown

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.flechazo.toolbox.core.designsystem.components.FeedbackBlock
import com.flechazo.toolbox.core.designsystem.components.FeedbackType
import com.flechazo.toolbox.core.designsystem.components.ToolScaffold
import com.flechazo.toolbox.core.designsystem.components.ToolSectionCard
import com.flechazo.toolbox.core.designsystem.components.ToolTextField
import com.flechazo.toolbox.core.notify.CountdownNotifications
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject

data class CountdownUiState(
    val events: List<CountdownEntity> = emptyList(),
)

@HiltViewModel
class CountdownViewModel @Inject constructor(
    private val repository: CountdownRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) : ViewModel() {

    val state: StateFlow<CountdownUiState> = repository.observeAll()
        .map { CountdownUiState(events = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CountdownUiState())

    init {
        CountdownNotifications.ensureChannel(context)
        // 事件变化时重排提醒；也顺带覆盖重启后系统闹钟丢失的情况
        viewModelScope.launch {
            repository.observeAll().collect { events ->
                events.forEach {
                    CountdownNotifications.schedule(context, it.id, it.title, it.date)
                }
            }
        }
    }

    fun add(title: String, date: String, type: Int) {
        viewModelScope.launch { repository.add(title, date, type) }
    }

    fun delete(id: Long) {
        CountdownNotifications.cancel(context, id)
        viewModelScope.launch { repository.delete(id) }
    }
}

@Composable
fun CountdownScreen(onBack: () -> Unit, viewModel: CountdownViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { /* 用户拒绝时静默降级：不排提醒，其余功能不受影响 */ }

    ToolScaffold(
        title = "倒数日",
        subtitle = "记录重要日期，距离 / 已过自动计算",
        onBack = onBack,
        actions = {
            ExtendedFloatingActionButton(
                onClick = { showDialog = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("添加") },
            )
        },
    ) {
        if (state.events.isEmpty()) {
            FeedbackBlock(text = "还没有事件，点右上角「添加」创建", type = FeedbackType.EMPTY)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.events.forEach { event ->
                    EventCard(event = event, onDelete = { viewModel.delete(event.id) })
                }
            }
        }
    }

    if (showDialog) {
        AddEventDialog(
            onDismiss = { showDialog = false },
            onConfirm = { title, date, type ->
                viewModel.add(title, date, type)
                showDialog = false
                // 首次添加时申请通知权限（API 33+），否则提醒无法弹出
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                    !CountdownNotifications.canNotify(context)
                ) {
                    permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            },
        )
    }
}

/**
 * 倒数日 / 纪念日的剩余天数文案。
 *
 * [type] 0 = 倒数日（目标在未来），1 = 纪念日（目标在过去）。
 * 旧实现把 `type == 1` 分支放在符号判断之前，导致过去的纪念日显示"已 -1699 天"，
 * 且 `days + 1` 让"明天"显示成"还剩 2 天"。
 */
internal fun countdownLabel(target: LocalDate?, type: Int, today: LocalDate): String {
    if (target == null) return "日期无效"
    val days = ChronoUnit.DAYS.between(today, target)
    return if (type == 1) {
        when {
            days < 0L -> "已 ${-days} 天"
            days == 0L -> "就是今天"
            else -> "还有 $days 天"
        }
    } else {
        when {
            days > 0L -> "还剩 $days 天"
            days == 0L -> "就是今天"
            else -> "已过 ${-days} 天"
        }
    }
}

@Composable
private fun EventCard(event: CountdownEntity, onDelete: () -> Unit) {
    val target = remember(event.date) {
        runCatching { LocalDate.parse(event.date) }.getOrNull()
    }
    val label = countdownLabel(target, event.type, LocalDate.now())

    ToolSectionCard(title = event.title) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(
                    event.date,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDelete) { Text("删除") }
        }
    }
}

/**
 * 只读日期展示行：整行可点，点击后打开日历。
 * 不用 readOnly 的 TextField 是因为输入框会吞掉点击事件，外层 clickable 不生效。
 */
@Composable
private fun DateField(date: LocalDate, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "日期",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    date.format(DateTimeFormatter.ISO_LOCAL_DATE) +
                        "  " + date.format(DateTimeFormatter.ofPattern("EEEE", Locale.CHINA)),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Icon(
                Icons.Filled.CalendarMonth,
                contentDescription = "选择日期",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEventDialog(onDismiss: () -> Unit, onConfirm: (String, String, Int) -> Unit) {
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var type by remember { mutableStateOf(0) }
    var showPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加事件") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ToolTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("名称") },
                    singleLine = true,
                )
                // 日期用日历选择，而不是手输字符串（手输容易格式出错）
                DateField(date = date, onClick = { showPicker = true })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == 0,
                        onClick = { type = 0 },
                        label = { Text("倒数日") },
                    )
                    FilterChip(
                        selected = type == 1,
                        onClick = { type = 1 },
                        label = { Text("纪念日") },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (title.isNotBlank()) onConfirm(title, date.toString(), type) },
                enabled = title.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )

    if (showPicker) {
        // M3 DatePicker 以 UTC 解释毫秒值，初值必须用"当天 00:00 UTC"
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(java.time.ZoneOffset.UTC)
                .toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        date = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneOffset.UTC).toLocalDate()
                    }
                    showPicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("取消") } },
        ) {
            DatePicker(state = pickerState)
        }
    }
}