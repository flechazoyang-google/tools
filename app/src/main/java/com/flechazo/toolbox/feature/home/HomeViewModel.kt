package com.flechazo.toolbox.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flechazo.toolbox.core.data.ToolsStateRepository
import com.flechazo.toolbox.core.registry.ToolCatalog
import com.flechazo.toolbox.core.registry.ToolCategory
import com.flechazo.toolbox.core.registry.ToolDef
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** 首页「分类」区的一张入口卡。 */
data class CategorySummary(
    val category: ToolCategory,
    val count: Int,
)

/**
 * 首页状态 = 两条泳道 + 分类入口（规范 §六）。
 *
 * "最近使用"和"收藏"各自只有一张大卡（[recentHero] / [favoriteHero]），
 * 其余走小卡——尺寸差就是优先级。
 */
data class HomeUiState(
    val query: String = "",
    val recentHero: ToolDef? = null,
    val recentQuick: List<ToolDef> = emptyList(),
    val favoriteHero: ToolDef? = null,
    val otherFavorites: List<ToolDef> = emptyList(),
    val favoriteTotal: Int = 0,
    val categories: List<CategorySummary> = emptyList(),
    val searchResults: List<ToolDef>? = null,
) {
    val showSearch: Boolean get() = searchResults != null

    /** 全新安装：既没收藏也没用过，首页只剩一句引导 + 分类入口。 */
    val isFresh: Boolean
        get() = recentHero == null && recentQuick.isEmpty() &&
            favoriteHero == null && otherFavorites.isEmpty()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val toolsState: ToolsStateRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val state: StateFlow<HomeUiState> = combine(
        query,
        toolsState.favorites,
        toolsState.recentIds,
    ) { q, favorites, recents ->
        val trimmed = q.trim()
        // 权重排序仍在 ViewModel 做：priority 相同（多数为 0）时按 ToolCatalog 登记次序稳定排列
        val favoriteIds = ToolCatalog.all
            .filter { it.id in favorites }
            .sortedBy { it.priority }
            .map { it.id }
        val feed = HomeFeed.select(favoriteIds = favoriteIds, recentIds = recents)

        HomeUiState(
            query = q,
            recentHero = feed.recentHeroId?.let(ToolCatalog::byId),
            recentQuick = feed.recentQuickIds.mapNotNull(ToolCatalog::byId),
            favoriteHero = feed.favoriteHeroId?.let(ToolCatalog::byId),
            otherFavorites = feed.otherFavoriteIds.mapNotNull(ToolCatalog::byId),
            favoriteTotal = feed.favoriteTotal,
            categories = CategorySummaries,
            searchResults = if (trimmed.isEmpty()) null else ToolCatalog.search(trimmed),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    private companion object {
        /**
         * 分类清单是编译期就定死的（ToolCatalog 静态），构建一次即可，
         * 不必每帧跟着 favorites/recents 重算。
         */
        val CategorySummaries: List<CategorySummary> = ToolCategory.entries
            .sortedBy { it.order }
            .map { CategorySummary(it, ToolCatalog.byCategory(it).size) }
            .filter { it.count > 0 }
    }
}
