package com.flechazo.toolbox.feature.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.flechazo.toolbox.core.data.ToolsStateRepository
import com.flechazo.toolbox.core.designsystem.theme.CategoryColor
import com.flechazo.toolbox.core.designsystem.theme.Space
import com.flechazo.toolbox.core.designsystem.theme.ToolShape
import com.flechazo.toolbox.core.designsystem.theme.categoryColor
import com.flechazo.toolbox.core.designsystem.theme.horizontalSafePadding
import com.flechazo.toolbox.core.designsystem.theme.rememberToolHaptics
import com.flechazo.toolbox.core.designsystem.theme.statusBarTopInset
import com.flechazo.toolbox.core.registry.ToolCatalog
import com.flechazo.toolbox.core.registry.ToolCategory
import com.flechazo.toolbox.core.registry.ToolDef
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ToolsUiState(
    val groups: List<Pair<ToolCategory, List<ToolDef>>> = emptyList(),
    val favorites: Set<String> = emptySet(),
)

@HiltViewModel
class ToolsViewModel @Inject constructor(
    private val toolsState: ToolsStateRepository,
) : ViewModel() {

    val state: StateFlow<ToolsUiState> = combine(toolsState.favorites) { favorites ->
        ToolsUiState(
            groups = ToolCategory.entries.sortedBy { it.order }
                .map { cat -> cat to ToolCatalog.byCategory(cat) }
                .filter { it.second.isNotEmpty() },
            favorites = favorites.first(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ToolsUiState())

    fun toggleFavorite(id: String) {
        viewModelScope.launch { toolsState.toggleFavorite(id) }
    }
}

@Composable
fun ToolsScreen(
    openTool: (String) -> Unit,
    bottomBarPadding: Dp = 0.dp,
    viewModel: ToolsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            // 左右让开刘海；纵向用 contentPadding，内容才能从状态栏/玻璃栏下滚过
            .horizontalSafePadding(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = statusBarTopInset() + 8.dp,
            bottom = bottomBarPadding + 8.dp,
        ),
    ) {
        item {
            Text(
                "全部工具",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
            )
        }
        state.groups.forEach { (category, tools) ->
            item(key = "header_${category.key}") {
                Text(
                    category.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
            }
            items(tools, key = { it.id }) { tool ->
                ToolRow(
                    tool = tool,
                    favorite = tool.id in state.favorites,
                    onClick = { openTool(tool.id) },
                    onToggleFavorite = { viewModel.toggleFavorite(tool.id) },
                )
            }
        }
    }
}

/** 工具列表行。分类页（[CategoryScreen]）复用同一个行样式，保证两处观感一致。 */
@Composable
internal fun ToolRow(
    tool: ToolDef,
    favorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val haptics = rememberToolHaptics()
    val catColor: CategoryColor = categoryColor(tool.category.key)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Space.xs)
            .clip(ToolShape.md)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.sm, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(ToolShape.md)
                .background(catColor.container),
            contentAlignment = Alignment.Center,
        ) {
            Icon(tool.icon, contentDescription = null, tint = catColor.on, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(tool.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(
                tool.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            if (favorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
            contentDescription = if (favorite) "取消收藏" else "收藏",
            tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier
                .size(40.dp)
                .clip(ToolShape.full)
                .clickable {
                    haptics.confirm()
                    onToggleFavorite()
                }
                .padding(10.dp),
        )
    }
    Spacer(Modifier.height(2.dp))
}
