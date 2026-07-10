package com.example.toolbox.feature.home

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.example.toolbox.core.util.triggerVibration
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.toolbox.core.components.CommonCard
import com.example.toolbox.core.components.SectionHeader
import com.example.toolbox.core.tool.Tool
import com.example.toolbox.core.tool.ToolRegistry

@Composable
fun HomeScreen(
    navController: NavHostController,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val recentIds by viewModel.recentTools.collectAsState()
    val favoriteIds by viewModel.favoriteTools.collectAsState()
    val featured = remember { ToolRegistry.featuredTools.take(6) }
    val recentTools = remember(recentIds) {
        recentIds.mapNotNull { id -> ToolRegistry.tools.firstOrNull { it.id == id } }
    }
    val favoriteTools = remember(favoriteIds) {
        ToolRegistry.tools.filter { it.id in favoriteIds }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        SectionHeader("常用工具")
        Spacer(modifier = Modifier.height(12.dp))

        // 非懒加载网格（避免嵌套 verticalScroll 的兼容问题）
        featured.chunked(2).forEach { rowTools ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowTools.forEach { tool ->
                    FeaturedToolCard(tool, modifier = Modifier.weight(1f)) {
                        viewModel.openTool(tool.id)
                        navController.navigate(tool.route) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
                if (rowTools.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        SectionHeader("最近使用")
        Spacer(modifier = Modifier.height(10.dp))
        if (recentTools.isEmpty()) {
            Text("还没有使用记录，去工具页逛逛吧～",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 4.dp))
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(recentTools) { tool ->
                    Box(Modifier.animateItem()) {
                        RecentToolChip(tool) {
                            viewModel.openTool(tool.id)
                            navController.navigate(tool.route)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ---- 收藏工具 ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionHeader("收藏工具")
            if (favoriteTools.isNotEmpty()) {
                SeeAllButton(onClick = { navController.navigate("favorites") })
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        if (favoriteTools.isEmpty()) {
            Text("还没有收藏的工具，在工具页点击星标收藏",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 4.dp))
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(favoriteTools.take(3)) { tool ->
                    Box(Modifier.animateItem()) {
                        FavoriteToolChip(
                            tool = tool,
                            onClick = { viewModel.openTool(tool.id); navController.navigate(tool.route) },
                            onToggleFavorite = { viewModel.toggleFavorite(tool.id) },
                        )
                    }
                }
                item {
                    Box(Modifier.animateItem()) {
                        FavoritesEntryChip(onClick = { navController.navigate("favorites") })
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SeeAllButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("查看全部", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun FavoriteToolChip(tool: Tool, onClick: () -> Unit, onToggleFavorite: () -> Unit) {
    val context = LocalContext.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.height(34.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(tool.icon, tool.title, tint = tool.category.color, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(tool.title, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            IconButton(onClick = { triggerVibration(context); onToggleFavorite() }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Star, "取消收藏", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun FavoritesEntryChip(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        modifier = Modifier.height(34.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Star, "收藏", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("我的收藏", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(4.dp))
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun FeaturedToolCard(tool: Tool, modifier: Modifier = Modifier, onClick: () -> Unit) {
    CommonCard(onClick = onClick, modifier = modifier.height(72.dp)) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
            Row(modifier = Modifier.padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(10.dp), color = tool.category.color.copy(alpha = 0.12f), modifier = Modifier.size(24.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(tool.icon, tool.title, tint = tool.category.color, modifier = Modifier.size(14.dp))
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(tool.title, style = MaterialTheme.typography.titleMedium)
                    Text(tool.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun RecentToolChip(tool: Tool, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.height(34.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(tool.icon, tool.title, tint = tool.category.color, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(tool.title, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}
