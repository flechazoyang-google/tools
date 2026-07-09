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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.toolbox.core.components.CommonCard
import com.example.toolbox.core.components.TopBar
import com.example.toolbox.core.util.daysUntil
import com.example.toolbox.core.util.formatDate
import com.example.toolbox.core.util.startOfToday
import com.example.toolbox.data.local.entity.CountdownEntity
import java.util.Calendar

private val EVENT_COLORS = listOf("#4F7CFF", "#EF4444", "#F59E0B", "#10B981", "#8B5CF6", "#EC4899")
private val EVENT_TYPES = listOf(
    "countdown" to "倒数日",
    "anniversary" to "纪念日",
    "birthday" to "生日",
)

/** 计算下一次生日还有多少天（忽略年份）。0 表示今天生日。 */
private fun daysUntilBirthday(targetEpochMillis: Long): Long {
    val cal = Calendar.getInstance()
    val todayYear = cal.get(Calendar.YEAR)
    val bday = Calendar.getInstance().apply { timeInMillis = targetEpochMillis }
    val bMonth = bday.get(Calendar.MONTH)
    val bDay = bday.get(Calendar.DAY_OF_MONTH)

    // 今年的生日
    val thisYear = Calendar.getInstance().apply {
        set(Calendar.YEAR, todayYear)
        set(Calendar.MONTH, bMonth)
        set(Calendar.DAY_OF_MONTH, bDay)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    val todayStart = startOfToday()
    if (thisYear.timeInMillis >= todayStart) {
        return (thisYear.timeInMillis - todayStart) / (1000 * 60 * 60 * 24)
    }
    // 今年的已过，算明年
    val nextYear = Calendar.getInstance().apply {
        set(Calendar.YEAR, todayYear + 1)
        set(Calendar.MONTH, bMonth)
        set(Calendar.DAY_OF_MONTH, bDay)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return (nextYear.timeInMillis - todayStart) / (1000 * 60 * 60 * 24)
}

/** 计算纪念日已过天数 */
private fun daysSince(targetEpochMillis: Long): Long {
    val diff = startOfToday() - targetEpochMillis
    return diff / (1000 * 60 * 60 * 24)
}

/** 获取类型图标文字 */
private fun typeEmoji(type: String): String = when (type) {
    "anniversary" -> "🎉"
    "birthday" -> "🎂"
    else -> ""
}

/** 获取类型标签 */
private fun typeLabel(type: String): String = when (type) {
    "countdown" -> "倒数日"
    "anniversary" -> "纪念日"
    "birthday" -> "生日"
    else -> type
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountdownScreen(viewModel: CountdownViewModel = hiltViewModel()) {
    val items by viewModel.items.collectAsState()
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopBar(title = "倒数日 · 纪念日") },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = "新增事件")
            }
        },
    ) { inner ->
        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                Text("还没有事件，点右下角添加一个吧～", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                contentPadding = inner,
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items, key = { it.id }) { entity ->
                    CountdownCard(entity, onDelete = { viewModel.delete(entity) }, onPin = { viewModel.togglePin(entity) })
                }
            }
        }
    }

    if (showAdd) {
        AddCountdownDialog(onDismiss = { showAdd = false }) { title, date, color, type ->
            viewModel.add(title, date, color, type)
            showAdd = false
        }
    }
}

@Composable
private fun CountdownCard(entity: CountdownEntity, onDelete: () -> Unit, onPin: () -> Unit) {
    val color = runCatching { Color(android.graphics.Color.parseColor(entity.colorTag)) }.getOrDefault(MaterialTheme.colorScheme.primary)
    var menuExpanded by remember { mutableStateOf(false) }

    val days = when (entity.type) {
        "anniversary" -> daysSince(entity.targetDate)
        "birthday" -> daysUntilBirthday(entity.targetDate)
        else -> daysUntil(entity.targetDate) // countdown
    }

    val isBirthdayToday = entity.type == "birthday" && days == 0L

    val (mainText, subText) = when {
        isBirthdayToday -> "${entity.title}生日快乐" to "🎂"
        entity.type == "anniversary" -> "${entity.title}已经 $days 天" to "🎉"
        entity.type == "birthday" -> "${entity.title}生日还有 $days 天" to "🎂"
        days >= 0 -> "距离${entity.title}已经 $days 天" to ""
        else -> "已过期" to ""
    }

    CommonCard(Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.width(6.dp).fillMaxSize().clip(RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)).background(color))
            Column(modifier = Modifier.weight(1f).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(entity.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (entity.isPinned) Icon(Icons.Filled.PushPin, null, tint = color, modifier = Modifier.size(18.dp))
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(text = { Text(if (entity.isPinned) "取消置顶" else "置顶") }, onClick = { onPin(); menuExpanded = false })
                            DropdownMenuItem(text = { Text("删除") }, onClick = { onDelete(); menuExpanded = false })
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(typeLabel(entity.type), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(" · ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatDate(entity.targetDate), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        mainText,
                        style = MaterialTheme.typography.displaySmall,
                        color = color,
                    )
                    if (subText.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(subText, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCountdownDialog(onDismiss: () -> Unit, onConfirm: (String, Long, String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(EVENT_COLORS[0]) }
    var selectedType by remember { mutableStateOf("countdown") }
    var showPicker by remember { mutableStateOf(false) }
    val dateState = rememberDatePickerState(initialSelectedDateMillis = startOfToday())

    if (showPicker) {
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = { showPicker = false }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = dateState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                enabled = title.isNotBlank() && dateState.selectedDateMillis != null,
                onClick = {
                    val millis = dateState.selectedDateMillis ?: return@Button
                    onConfirm(title.trim(), millis, selectedColor, selectedType)
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("新增事件", style = MaterialTheme.typography.titleLarge)

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("事件名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // 类型选择
                Text("类型", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EVENT_TYPES.forEach { (value, label) ->
                        OutlinedButton(
                            onClick = { selectedType = value },
                            shape = RoundedCornerShape(12.dp),
                            colors = if (selectedType == value)
                                androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                )
                            else androidx.compose.material3.ButtonDefaults.outlinedButtonColors(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(label, fontSize = MaterialTheme.typography.labelLarge.fontSize)
                        }
                    }
                }

                OutlinedTextField(
                    value = dateState.selectedDateMillis?.let { formatDate(it) } ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = {
                        Text(when (selectedType) {
                            "anniversary" -> "起始日期"
                            "birthday" -> "生日日期"
                            else -> "目标日期"
                        })
                    },
                    modifier = Modifier.fillMaxWidth().clickable { showPicker = true },
                    trailingIcon = { IconButton(onClick = { showPicker = true }) { Icon(Icons.Filled.DateRange, null) } },
                )

                Text("颜色标记", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    EVENT_COLORS.forEach { c ->
                        val color = Color(android.graphics.Color.parseColor(c))
                        Surface(
                            shape = CircleShape,
                            color = color,
                            modifier = Modifier.size(28.dp).clickable { selectedColor = c },
                        ) {
                            if (selectedColor == c) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.PushPin, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}
