package com.example.toolbox.feature.tools

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
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
    var selectedCategory by remember { mutableStateOf<ToolCategory?>(null) } // null = 全部
    val allTools = ToolRegistry.tools

    // 有效的分类列表：null（全部）+ 有工具的 category，用于左右滑动切换
    val categories = remember(allTools.size) {
        listOf(null as ToolCategory?) +
            ToolCategory.entries.filter { cat -> allTools.any { it.category == cat } }
    }
    val currentIdx = categories.indexOf(selectedCategory).coerceAtLeast(0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(selectedCategory) {
                val threshold = 50.dp.toPx()
                var accumulated = 0f
                detectHorizontalDragGestures(
                    onDragEnd = { accumulated = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        accumulated += dragAmount
                        if (accumulated > threshold && currentIdx > 0) {
                            accumulated = 0f
                            selectedCategory = categories[currentIdx - 1]
                        } else if (accumulated < -threshold && currentIdx < categories.size - 1) {
                            accumulated = 0f
                            selectedCategory = categories[currentIdx + 1]
                        }
                    },
                )
            }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // 分类标签栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CategoryTab(
                label = "全部",
                selected = selectedCategory == null,
                onClick = { selectedCategory = null },
            )
            ToolCategory.entries.forEach { cat ->
                val count = allTools.count { it.category == cat }
                if (count > 0) {
                    CategoryTab(
                        label = cat.label,
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 当前分类的工具网格
        val filtered = if (selectedCategory == null) allTools
            else allTools.filter { it.category == selectedCategory }

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "没有匹配的工具",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            val chunked = filtered.chunked(2)
            chunked.forEach { rowItems ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(bottom = 10.dp),
                ) {
                    rowItems.forEach { tool ->
                        ToolGridCard(
                            tool = tool,
                            onClick = {
                                viewModel.openTool(tool.id)
                                navController.navigate(tool.route)
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun CategoryTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.height(32.dp),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ToolGridCard(
    tool: Tool,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CommonCard(onClick = onClick, modifier = modifier.height(72.dp)) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                modifier = Modifier.padding(start = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = tool.category.color.copy(alpha = 0.12f),
                    modifier = Modifier.size(24.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(tool.icon, tool.title, tint = tool.category.color, modifier = Modifier.size(14.dp))
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(tool.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        tool.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
