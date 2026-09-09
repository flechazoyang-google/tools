package com.flechazo.toolbox.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flechazo.toolbox.core.designsystem.components.FeedbackBlock
import com.flechazo.toolbox.core.designsystem.components.FeedbackType
import com.flechazo.toolbox.core.designsystem.components.SectionHeader
import com.flechazo.toolbox.core.designsystem.components.ToolCard
import com.flechazo.toolbox.core.registry.ToolDef
import com.flechazo.toolbox.core.registry.ToolCategory

@Composable
fun HomeScreen(
    openTool: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(2) }) {
            Column {
                Text(
                    "工具箱",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
                SearchField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                )
            }
        }

        if (state.showSearch) {
            val results = state.searchResults.orEmpty()
            if (results.isEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    FeedbackBlock(text = "没有匹配的工具", type = FeedbackType.EMPTY)
                }
            } else {
                items(results, key = { "search_${it.id}" }) { tool ->
                    ToolCard(
                        title = tool.title,
                        description = tool.description,
                        icon = tool.icon,
                        categoryKey = tool.category.key,
                        favorite = false,
                        onClick = { openTool(tool.id) },
                    )
                }
            }
        } else {
            if (state.favoriteTools.isNotEmpty()) {
                item(span = { GridItemSpan(2) }) { SectionHeader("收藏") }
                items(state.favoriteTools, key = { "fav_${it.id}" }) { tool ->
                    ToolCard(
                        title = tool.title,
                        description = tool.description,
                        icon = tool.icon,
                        categoryKey = tool.category.key,
                        favorite = true,
                        onClick = { openTool(tool.id) },
                    )
                }
            }

            if (state.recentTools.isNotEmpty()) {
                item(span = { GridItemSpan(2) }) { SectionHeader("最近使用") }
                item(span = { GridItemSpan(2) }) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.recentTools, key = { it.id }) { tool ->
                            ToolCard(
                                title = tool.title,
                                description = tool.description,
                                icon = tool.icon,
                                categoryKey = tool.category.key,
                                favorite = false,
                                onClick = { openTool(tool.id) },
                                modifier = Modifier.width(150.dp),
                            )
                        }
                    }
                }
            }

            ToolCategory.entries.sortedBy { it.order }.forEach { category ->
                val tools = com.flechazo.toolbox.core.registry.ToolCatalog.byCategory(category)
                if (tools.isNotEmpty()) {
                    item(span = { GridItemSpan(2) }, key = "cat_${category.key}") {
                        SectionHeader(category.label)
                    }
                    items(tools, key = { "${category.key}_${it.id}" }) { tool ->
                        ToolCard(
                            title = tool.title,
                            description = tool.description,
                            icon = tool.icon,
                            categoryKey = tool.category.key,
                            favorite = state.favoriteTools.any { it.id == tool.id },
                            onClick = { openTool(tool.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp)),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            androidx.compose.foundation.text.BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(
                            "搜索工具…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
