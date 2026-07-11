package com.example.toolbox.feature.countdown

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.toolbox.core.components.CommonCard
import com.example.toolbox.core.components.TopBar
import com.example.toolbox.core.util.LunarUtils
import com.example.toolbox.core.util.triggerVibration
import com.example.toolbox.core.util.daysUntil
import com.example.toolbox.core.util.formatDate
import com.example.toolbox.core.util.startOfToday
import com.example.toolbox.data.local.entity.CountdownEntity

// region Constants & Helpers

private val EVENT_COLORS = listOf("#4F7CFF", "#EF4444", "#F59E0B", "#10B981", "#8B5CF6", "#EC4899")
private val EVENT_TYPES = listOf(
    "countdown" to "倒数日",
    "anniversary" to "纪念日",
    "birthday" to "生日",
)

/** Form result data class — bundles all fields for add/edit. */
data class CountdownFormData(
    val title: String,
    val targetDate: Long,
    val colorTag: String,
    val type: String,
    val isLunar: Boolean = false,
    val lunarMonth: Int = 0,
    val lunarDay: Int = 0,
)

private fun daysUntilBirthday(targetEpochMillis: Long): Long {
    val cal = java.util.Calendar.getInstance()
    val todayYear = cal.get(java.util.Calendar.YEAR)
    val bday = java.util.Calendar.getInstance().apply { timeInMillis = targetEpochMillis }
    val bMonth = bday.get(java.util.Calendar.MONTH)
    val bDay = bday.get(java.util.Calendar.DAY_OF_MONTH)

    val thisYear = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.YEAR, todayYear); set(java.util.Calendar.MONTH, bMonth); set(java.util.Calendar.DAY_OF_MONTH, bDay)
        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }
    val todayStart = startOfToday()
    if (thisYear.timeInMillis >= todayStart) return (thisYear.timeInMillis - todayStart) / (1000 * 60 * 60 * 24)
    val nextYear = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.YEAR, todayYear + 1); set(java.util.Calendar.MONTH, bMonth); set(java.util.Calendar.DAY_OF_MONTH, bDay)
        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }
    return (nextYear.timeInMillis - todayStart) / (1000 * 60 * 60 * 24)
}

private fun daysSince(targetEpochMillis: Long): Long {
    return (startOfToday() - targetEpochMillis) / (1000 * 60 * 60 * 24)
}

private fun typeLabel(type: String): String = when (type) {
    "anniversary" -> "纪念日"; "birthday" -> "生日"; else -> "倒数日"
}

private fun isBuiltIn(entity: CountdownEntity): Boolean =
    BUILT_IN_HOLIDAYS.any { it.id.hashCode().toLong() == entity.id }

// endregion

// region Main Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountdownScreen(viewModel: CountdownViewModel = hiltViewModel()) {
    val items by viewModel.items.collectAsState()
    val hiddenIds by viewModel.hiddenIds.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var editEntity by remember { mutableStateOf<CountdownEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<CountdownEntity?>(null) }
    var showHolidayPanel by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopBar(
                title = "倒数日 · 纪念日",
                actions = {
                    IconButton(onClick = { showHolidayPanel = true }) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = "管理节日",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = "新增事件")
            }
        },
    ) { inner ->
        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("还没有事件，点右下角添加一个吧～", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            LazyColumn(
                contentPadding = inner,
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items, key = { it.id }) { entity ->
                    Box(Modifier.animateItem()) {
                        val builtIn = isBuiltIn(entity)
                        CountdownCard(
                            entity = entity,
                            isBuiltIn = builtIn,
                            onDelete = { if (!builtIn) deleteTarget = entity },
                            onPin = { if (!builtIn) viewModel.togglePin(entity) },
                            onEdit = { if (!builtIn) editEntity = entity },
                        )
                    }
                }
            }
        }
    }

    // Holiday management panel
    if (showHolidayPanel) {
        HolidayPanel(
            hiddenIds = hiddenIds,
            onToggle = { id, visible -> viewModel.setHolidayVisible(id, visible) },
            onDismiss = { showHolidayPanel = false },
        )
    }

    if (showAdd) {
        AddCountdownDialog(onDismiss = { showAdd = false }) { data ->
            viewModel.add(data.title, data.targetDate, data.colorTag, data.type, data.isLunar, data.lunarMonth, data.lunarDay)
            showAdd = false
        }
    }

    editEntity?.let { entity ->
        EditCountdownDialog(
            entity = entity,
            onDismiss = { editEntity = null },
        ) { data ->
            viewModel.update(entity, data.title, data.targetDate, data.colorTag, data.type, data.isLunar, data.lunarMonth, data.lunarDay)
            editEntity = null
        }
    }

    deleteTarget?.let { entity ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("确认删除") },
            text = { Text("确定要删除「${entity.title}」吗？此操作不可撤销。") },
            confirmButton = { Button(onClick = { viewModel.delete(entity); deleteTarget = null }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }
}

// endregion

// region Holiday Panel

@Composable
private fun HolidayPanel(
    hiddenIds: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("内置节日管理") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "开启后将在列表中显示对应节日",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                BUILT_IN_HOLIDAYS.forEach { holiday ->
                    val visible = holiday.id !in hiddenIds
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(runCatching { Color(android.graphics.Color.parseColor(holiday.colorTag)) }.getOrDefault(MaterialTheme.colorScheme.primary)),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(holiday.title, style = MaterialTheme.typography.bodyMedium)
                        }
                        Switch(checked = visible, onCheckedChange = { onToggle(holiday.id, it) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}

// endregion

// region CountdownCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountdownCard(
    entity: CountdownEntity,
    isBuiltIn: Boolean,
    onDelete: () -> Unit,
    onPin: () -> Unit,
    onEdit: () -> Unit,
) {
    val context = LocalContext.current
    val color = runCatching { Color(android.graphics.Color.parseColor(entity.colorTag)) }.getOrDefault(MaterialTheme.colorScheme.primary)
    var menuExpanded by remember { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.EndToStart && !isBuiltIn) {
                onDelete()
                true
            } else false
        },
    )

    // Compute days — for lunar recurring events, compute dynamically
    val today = remember { startOfToday() }
    val days: Long
    if (entity.isLunar && entity.lunarMonth > 0 && entity.lunarDay > 0 &&
        (entity.type == "birthday" || entity.type == "anniversary" || isBuiltIn)
    ) {
        // Recurring lunar event: compute next occurrence
        val nextSolar = LunarUtils.lunarToNextSolar(entity.lunarMonth, entity.lunarDay, today)
        days = when (entity.type) {
            "anniversary" -> daysSince(nextSolar)
            else -> (nextSolar - today) / (1000 * 60 * 60 * 24)
        }
    } else {
        days = when (entity.type) {
            "anniversary" -> daysSince(entity.targetDate)
            "birthday" -> daysUntilBirthday(entity.targetDate)
            else -> daysUntil(entity.targetDate)
        }
    }

    val isBirthdayToday = entity.type == "birthday" && days == 0L
    val (mainText, subText) = when {
        isBirthdayToday -> "${entity.title}生日快乐" to "🎂"
        entity.type == "anniversary" -> "${entity.title}已经 $days 天" to "🎉"
        entity.type == "birthday" -> "${entity.title}生日还有 $days 天" to "🎂"
        days >= 0 -> "距离${entity.title}还有 $days 天" to ""
        else -> "已过期" to ""
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            if (!isBuiltIn) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFFF4444), RoundedCornerShape(16.dp))
                        .padding(end = 20.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = "删除", tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
        },
    ) {
        CommonCard(Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.width(6.dp).fillMaxSize().clip(RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)).background(color))
                Column(modifier = Modifier.weight(1f).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(entity.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        if (entity.isPinned) Icon(Icons.Filled.PushPin, null, tint = color, modifier = Modifier.size(18.dp))
                        if (!isBuiltIn) {
                            Box {
                                IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "更多") }
                                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                    DropdownMenuItem(text = { Text(if (entity.isPinned) "取消置顶" else "置顶") }, onClick = { onPin(); menuExpanded = false })
                                    DropdownMenuItem(text = { Text("编辑") }, leadingIcon = { Icon(Icons.Filled.Edit, null) }, onClick = { onEdit(); menuExpanded = false })
                                    DropdownMenuItem(text = { Text("删除") }, onClick = { triggerVibration(context, 10); onDelete(); menuExpanded = false })
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(typeLabel(entity.type), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (entity.isLunar && entity.lunarMonth > 0 && entity.lunarDay > 0) {
                            Text(" · 农历", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(LunarUtils.formatLunar(entity.lunarMonth, entity.lunarDay), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text(" · ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatDate(entity.targetDate), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(mainText, style = MaterialTheme.typography.displaySmall, color = color)
                        if (subText.isNotEmpty()) { Spacer(modifier = Modifier.width(6.dp)); Text(subText, style = MaterialTheme.typography.titleLarge) }
                    }
                }
            }
        }
    }
}

// endregion

// region Add / Edit Dialogs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCountdownDialog(onDismiss: () -> Unit, onConfirm: (CountdownFormData) -> Unit) {
    CountdownFormDialog(title = "新增事件", onDismiss = onDismiss, onConfirm = onConfirm)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditCountdownDialog(
    entity: CountdownEntity,
    onDismiss: () -> Unit,
    onConfirm: (CountdownFormData) -> Unit,
) {
    key(entity.id) {
        CountdownFormDialog(
            title = "编辑事件",
            initialTitle = entity.title,
            initialDate = entity.targetDate,
            initialColor = entity.colorTag,
            initialType = entity.type,
            initialIsLunar = entity.isLunar,
            initialLunarMonth = entity.lunarMonth,
            initialLunarDay = entity.lunarDay,
            onDismiss = onDismiss,
            onConfirm = onConfirm,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountdownFormDialog(
    title: String,
    initialTitle: String = "",
    initialDate: Long = startOfToday(),
    initialColor: String = EVENT_COLORS[0],
    initialType: String = "countdown",
    initialIsLunar: Boolean = false,
    initialLunarMonth: Int = 0,
    initialLunarDay: Int = 0,
    onDismiss: () -> Unit,
    onConfirm: (CountdownFormData) -> Unit,
) {
    var name by remember { mutableStateOf(initialTitle) }
    var selectedColor by remember { mutableStateOf(initialColor) }
    var selectedType by remember { mutableStateOf(initialType) }
    var showPicker by remember { mutableStateOf(false) }
    var isLunar by remember { mutableStateOf(initialIsLunar) }
    var lunarMonth by remember { mutableIntStateOf(if (initialLunarMonth > 0) initialLunarMonth else 1) }
    var lunarDay by remember { mutableIntStateOf(if (initialLunarDay > 0) initialLunarDay else 1) }

    val dateState = rememberDatePickerState(initialSelectedDateMillis = initialDate)

    if (showPicker && !isLunar) {
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = { TextButton(onClick = { showPicker = false }) { Text("确定") } },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("取消") } },
        ) { DatePicker(state = dateState) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && (if (isLunar) lunarMonth > 0 && lunarDay > 0 else dateState.selectedDateMillis != null),
                onClick = {
                    val millis = dateState.selectedDateMillis ?: startOfToday()
                    onConfirm(
                        CountdownFormData(
                            title = name.trim(),
                            targetDate = millis,
                            colorTag = selectedColor,
                            type = selectedType,
                            isLunar = isLunar,
                            lunarMonth = if (isLunar) lunarMonth else 0,
                            lunarDay = if (isLunar) lunarDay else 0,
                        )
                    )
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("事件名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                Text("类型", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EVENT_TYPES.forEach { (value, label) ->
                        OutlinedButton(
                            onClick = { selectedType = value },
                            shape = RoundedCornerShape(12.dp),
                            colors = if (selectedType == value) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                    else ButtonDefaults.outlinedButtonColors(),
                            modifier = Modifier.weight(1f),
                        ) { Text(label, fontSize = MaterialTheme.typography.labelLarge.fontSize) }
                    }
                }

                // Solar / Lunar toggle
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("农历日期", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Switch(checked = isLunar, onCheckedChange = { isLunar = it })
                }

                if (isLunar) {
                    // Lunar month/day picker
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("月份", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        // Two rows of 6 month buttons
                        for (row in 0..1) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                for (col in 0..5) {
                                    val idx = row * 6 + col
                                    if (idx < 12) {
                                        val m = idx + 1
                                        OutlinedButton(
                                            onClick = { lunarMonth = m },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = if (lunarMonth == m) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                                    else ButtonDefaults.outlinedButtonColors(),
                                            modifier = Modifier.weight(1f).height(34.dp),
                                            contentPadding = ButtonDefaults.TextButtonContentPadding,
                                        ) { Text(LunarUtils.LUNAR_MONTH_NAMES[idx], fontSize = 10.sp) }
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                        // Day selector — show 30 day buttons in a wrap layout
                        Text("日期", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // Simplified: 3 rows of 10 day chips
                    for (row in 0..2) {
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth()) {
                            for (col in 0..9) {
                                val d = row * 10 + col + 1
                                if (d <= 30) {
                                    OutlinedButton(
                                        onClick = { lunarDay = d },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = if (lunarDay == d) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                                else ButtonDefaults.outlinedButtonColors(),
                                        modifier = Modifier.weight(1f).height(32.dp),
                                        contentPadding = ButtonDefaults.TextButtonContentPadding,
                                    ) { Text("$d", fontSize = 10.sp) }
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = dateState.selectedDateMillis?.let { formatDate(it) } ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(when (selectedType) { "anniversary" -> "起始日期"; "birthday" -> "生日日期"; else -> "目标日期" }) },
                        modifier = Modifier.fillMaxWidth().clickable { showPicker = true },
                        trailingIcon = { IconButton(onClick = { showPicker = true }) { Icon(Icons.Filled.DateRange, null) } },
                    )
                }

                Text("颜色标记", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    EVENT_COLORS.forEach { c ->
                        val clr = Color(android.graphics.Color.parseColor(c))
                        Surface(shape = CircleShape, color = clr, modifier = Modifier.size(28.dp).clickable { selectedColor = c }) {
                            if (selectedColor == c) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.PushPin, null, tint = Color.White, modifier = Modifier.size(16.dp)) } }
                        }
                    }
                }
            }
        },
    )
}

// endregion
