package com.flechazo.toolbox.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flechazo.toolbox.core.designsystem.components.FeedbackBlock
import com.flechazo.toolbox.core.designsystem.components.FeedbackType
import com.flechazo.toolbox.core.designsystem.components.SectionHeader
import com.flechazo.toolbox.core.designsystem.theme.Space
import com.flechazo.toolbox.core.designsystem.theme.ToolShape
import com.flechazo.toolbox.core.designsystem.theme.horizontalSafePadding
import com.flechazo.toolbox.core.designsystem.theme.rememberToolHaptics
import com.flechazo.toolbox.core.designsystem.theme.statusBarTopInset
import com.flechazo.toolbox.core.registry.ToolCatalog
import com.flechazo.toolbox.core.registry.ToolCategory

/**
 * 首页（Bento 布局，规范 §六）。
 *
 * 结构：`大标题 + 常驻搜索栏` → `最近使用`（1 大 + 4 小）→ `收藏`（1 大 + 4 小）
 * → `分类`（6 张色块入口卡）。
 *
 * 与改造前的差别：以前是 27 张等大卡片铺满，找东西只能靠扫；现在同类信息压进
 * 同一泳道，用**卡片尺寸**表达优先级，一眼能看出"哪个是我常用的"。
 * 首页不再承载完整清单——那是「工具」页的职责。
 */
@Composable
fun HomeScreen(
    openTool: (String) -> Unit,
    openCategory: (ToolCategory) -> Unit,
    bottomBarPadding: Dp = 0.dp,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxSize()
            // 左右让开刘海（横屏时它在侧边）；纵向交给 contentPadding
            .horizontalSafePadding(),
        // 顶部/底部走 contentPadding 而不是给根节点加 padding：视口保持全屏，
        // 内容才会从状态栏和玻璃底栏**下面**滚过——玻璃层透出的就是这些内容。
        contentPadding = PaddingValues(
            start = Space.lg,
            end = Space.lg,
            top = statusBarTopInset() + Space.sm,
            bottom = bottomBarPadding + Space.sm,
        ),
        horizontalArrangement = Arrangement.spacedBy(Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        item(span = { GridItemSpan(2) }, key = "header") {
            HomeHeader(query = state.query, onQueryChange = viewModel::onQueryChange)
        }

        if (state.showSearch) {
            val results = state.searchResults.orEmpty()
            if (results.isEmpty()) {
                item(span = { GridItemSpan(2) }, key = "search_empty") {
                    FeedbackBlock(text = "没有匹配的工具", type = FeedbackType.EMPTY)
                }
            } else {
                items(results, key = { "search_${it.id}" }) { tool ->
                    CompactToolCard(tool = tool, onClick = { openTool(tool.id) })
                }
            }
        } else {
            // ---- 最近使用：1 张大卡 + 最多 4 张小卡 ----
            state.recentHero?.let { hero ->
                item(span = { GridItemSpan(2) }, key = "recent_header") {
                    SectionHeader("最近使用", modifier = Modifier.padding(top = Space.md))
                }
                item(span = { GridItemSpan(2) }, key = "recent_hero") {
                    HeroToolCard(tool = hero, onClick = { openTool(hero.id) })
                }
            }
            if (state.recentQuick.isNotEmpty()) {
                items(state.recentQuick, key = { "recent_${it.id}" }) { tool ->
                    CompactToolCard(tool = tool, onClick = { openTool(tool.id) })
                }
            }

            // ---- 收藏：同样 1 大 + 最多 4 小；与最近使用交叉去重，不重复占卡 ----
            state.favoriteHero?.let { hero ->
                item(span = { GridItemSpan(2) }, key = "favorite_header") {
                    SectionHeader(
                        title = "收藏",
                        modifier = Modifier.padding(top = Space.md),
                        trailing = favoriteCountLabel(state.favoriteTotal),
                    )
                }
                item(span = { GridItemSpan(2) }, key = "favorite_hero") {
                    HeroToolCard(tool = hero, onClick = { openTool(hero.id) })
                }
            }
            if (state.otherFavorites.isNotEmpty()) {
                items(state.otherFavorites, key = { "favorite_${it.id}" }) { tool ->
                    CompactToolCard(tool = tool, onClick = { openTool(tool.id) })
                }
            }

            // ---- 全新安装：没有常用工具时给一句引导，而不是留一片空白 ----
            if (state.isFresh) {
                item(span = { GridItemSpan(2) }, key = "fresh_hint") {
                    FeedbackBlock(
                        text = "还没有常用工具。从下面挑一个开始，用过的会自动出现在这里。",
                        type = FeedbackType.EMPTY,
                    )
                }
            }

            // ---- 分类：6 张色块入口卡，2 行 3 列 ----
            item(span = { GridItemSpan(2) }, key = "category_header") {
                SectionHeader("分类", modifier = Modifier.padding(top = Space.md))
            }
            item(span = { GridItemSpan(2) }, key = "category_grid") {
                CategoryGrid(categories = state.categories, onOpen = openCategory)
            }
        }
    }
}

/**
 * 大标题 + 工具数 + 常驻搜索栏。
 *
 * 搜索栏常驻而非"进页面才出现"：27 个工具时搜索是最高频入口。
 */
@Composable
private fun HomeHeader(query: String, onQueryChange: (String) -> Unit) {
    Column(modifier = Modifier.padding(top = Space.md, bottom = Space.xs)) {
        Text(
            "工具箱",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            "${ToolCatalog.visible.size} 个工具",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Space.md))
        SearchField(value = query, onValueChange = onQueryChange)
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    val haptics = rememberToolHaptics()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ToolShape.full),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = ToolShape.full,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(Space.sm))
            BasicTextField(
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
                modifier = Modifier.weight(1f),
            )
            // 以前只能靠退格清空；给一个显式的清除位
            if (value.isNotEmpty()) {
                val clearInteraction = remember { MutableInteractionSource() }
                Spacer(Modifier.width(Space.sm))
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "清除搜索",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(ToolShape.full)
                        .clickable(
                            interactionSource = clearInteraction,
                            indication = null,
                        ) {
                            haptics.tick()
                            onValueChange("")
                        }
                        .padding(7.dp),
                )
            }
        }
    }
}

/** 分类入口：一行三张、两行铺满，保证 6 个类目一屏可见，不需要横向滚动。 */
@Composable
private fun CategoryGrid(
    categories: List<CategorySummary>,
    onOpen: (ToolCategory) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        categories.chunked(CATEGORY_PER_ROW).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                row.forEach { summary ->
                    CategoryEntryCard(
                        category = summary.category,
                        count = summary.count,
                        onClick = { onOpen(summary.category) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // 末行不足三张时补齐空位，否则剩下的卡片会被拉伸到满宽
                repeat(CATEGORY_PER_ROW - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

private const val CATEGORY_PER_ROW = 3

/** 收藏泳道最多放得下这么多张卡（1 大 + 4 小）。 */
private val FavoriteLaneCapacity = HomeFeed.HERO + HomeFeed.QUICK

/**
 * 「收藏」表头右侧的总数标签。
 *
 * **只在收藏数真的超过泳道容量时才显示**：因为收藏泳道会和最近使用做交叉去重，
 * 少数收藏时"共 N 个"会和实际显示的张数对不上（例如共 2 个、只显示 1 张，
 * 另一张正在上面的最近使用里）。只有 N 超过容量时，这个数字才明确表示
 * "还有没显示出来的"，不会误导。
 */
@Composable
private fun favoriteCountLabel(total: Int): (@Composable () -> Unit)? {
    if (total <= FavoriteLaneCapacity) return null
    return {
        Text(
            "共 $total 个",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
