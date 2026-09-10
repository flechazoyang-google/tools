package com.flechazo.toolbox.feature.tools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flechazo.toolbox.core.designsystem.components.ToolScaffold
import com.flechazo.toolbox.core.designsystem.components.ToolSectionCard
import com.flechazo.toolbox.core.designsystem.theme.categoryColor
import com.flechazo.toolbox.core.registry.ToolCatalog
import com.flechazo.toolbox.core.registry.ToolCategory

/**
 * 单个类目的工具列表（首页分类入口卡的落地页，规范 §六）。
 *
 * 首页把 6 个类目收成色块入口，具体清单放到这里——这样首页能保持"导航台"
 * 的密度，而清单页可以是完整、可滚动的。
 */
@Composable
fun CategoryScreen(
    category: ToolCategory,
    onBack: () -> Unit,
    openTool: (String) -> Unit,
    viewModel: ToolsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tools = remember(category) { ToolCatalog.byCategory(category) }

    ToolScaffold(title = category.label, onBack = onBack) {
        ToolSectionCard(
            title = "${tools.size} 个工具",
            accentColor = categoryColor(category.key).on,
        ) {
            tools.forEach { tool ->
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
