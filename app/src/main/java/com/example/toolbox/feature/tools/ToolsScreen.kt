package com.example.toolbox.feature.tools

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.toolbox.core.components.CommonCard
import com.example.toolbox.core.tool.Tool
import com.example.toolbox.core.tool.ToolCategory
import com.example.toolbox.core.tool.ToolRegistry

@Composable
fun ToolsScreen(
    navController: NavHostController,
    viewModel: ToolsViewModel = hiltViewModel(),
) {
    var selectedCategory by remember { mutableStateOf<ToolCategory?>(null) }
    var slideDirection by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    val allTools = ToolRegistry.tools
    val favoriteIds by viewModel.favoriteTools.collectAsState()

    val categories = remember(allTools.size) {
        listOf(null as ToolCategory?) +
            ToolCategory.entries.filter { cat -> allTools.any { it.category == cat } }
    }
    val currentIdx = categories.indexOf(selectedCategory).coerceAtLeast(0)

    val filtered = remember(searchQuery, selectedCategory, allTools.size) {
        val byCategory = if (selectedCategory == null) allTools
            else allTools.filter { it.category == selectedCategory }
        if (searchQuery.isBlank()) byCategory
        else byCategory.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.description.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(selectedCategory) {
                val threshold = 50.dp.toPx(); var accumulated = 0f
                detectHorizontalDragGestures(
                    onDragEnd = { accumulated = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume(); accumulated += dragAmount
                        if (accumulated > threshold && currentIdx > 0) { accumulated = 0f; selectedCategory = categories[currentIdx - 1] }
                        else if (accumulated < -threshold && currentIdx < categories.size - 1) { accumulated = 0f; selectedCategory = categories[currentIdx + 1] }
                    },
                )
            }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // ---- 搜索栏 ----
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp).size(20.dp))
                OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it },
                    placeholder = { Text("搜索工具…", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                    singleLine = true, modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent, focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent),
                    textStyle = MaterialTheme.typography.bodyMedium)
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.Close, contentDescription = "清除", modifier = Modifier.size(16.dp)) }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // ---- 分类标签栏 ----
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CategoryTab(label = "全部", count = allTools.size, selected = selectedCategory == null, onClick = { slideDirection = 0; selectedCategory = null })
            ToolCategory.entries.forEach { cat ->
                val count = allTools.count { it.category == cat }
                if (count > 0) CategoryTab(label = cat.label, count = count, selected = selectedCategory == cat, onClick = { slideDirection = 0; selectedCategory = cat }, color = cat.color)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (searchQuery.isNotBlank()) {
            Text("找到 ${filtered.size} 个工具", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
        }

        // ---- 工具网格 ----
        Column(modifier = Modifier.fillMaxWidth().animateContentSize(animationSpec = tween(300))) {
            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Text(if (searchQuery.isNotBlank()) "没有搜索到匹配的工具" else "该分类下暂无工具",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                filtered.chunked(2).forEachIndexed { rowIdx, rowItems ->
                    AnimatedVisibility(visible = true, enter = fadeIn(animationSpec = tween(400, delayMillis = rowIdx * 60)) + slideInVertically(animationSpec = tween(400, delayMillis = rowIdx * 60), initialOffsetY = { it / 4 })) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                            rowItems.forEach { tool ->
                                ToolGridCard(
                                    tool = tool,
                                    isFavorite = tool.id in favoriteIds,
                                    onClick = { viewModel.openTool(tool.id); navController.navigate(tool.route) },
                                    onToggleFavorite = { viewModel.toggleFavorite(tool.id) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun CategoryTab(label: String, count: Int, selected: Boolean, onClick: () -> Unit, color: androidx.compose.ui.graphics.Color? = null) {
    Surface(onClick = onClick, shape = RoundedCornerShape(10.dp),
        color = if (selected) (color ?: MaterialTheme.colorScheme.primaryContainer).copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.height(32.dp)) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(4.dp))
            Surface(shape = RoundedCornerShape(8.dp), color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)) {
                Text("$count", style = MaterialTheme.typography.labelSmall, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
            }
        }
    }
}

@Composable
private fun ToolGridCard(
    tool: Tool,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CommonCard(onClick = onClick, modifier = modifier.height(72.dp)) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxSize().padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(10.dp), color = tool.category.color.copy(alpha = 0.12f), modifier = Modifier.size(24.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(tool.icon, tool.title, tint = tool.category.color, modifier = Modifier.size(14.dp)) }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(tool.title, style = MaterialTheme.typography.titleMedium)
                    Text(tool.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(40.dp)) {
                    Icon(if (isFavorite) Icons.Filled.Star else Icons.Outlined.Star, "收藏",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
