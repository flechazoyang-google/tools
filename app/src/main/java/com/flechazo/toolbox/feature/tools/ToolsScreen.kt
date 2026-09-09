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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.flechazo.toolbox.core.data.ToolsStateRepository
import com.flechazo.toolbox.core.designsystem.theme.CategoryColor
import com.flechazo.toolbox.core.designsystem.theme.CategoryColors
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
    viewModel: ToolsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
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

@Composable
private fun ToolRow(
    tool: ToolDef,
    favorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val catColor: CategoryColor = CategoryColors[tool.category.key]
        ?: CategoryColor(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(catColor.container),
            contentAlignment = Alignment.Center,
        ) {
            Icon(tool.icon, contentDescription = null, tint = catColor.on, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
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
                .clip(RoundedCornerShape(100))
                .clickable(onClick = onToggleFavorite)
                .padding(10.dp),
        )
    }
    Spacer(Modifier.height(2.dp))
}
