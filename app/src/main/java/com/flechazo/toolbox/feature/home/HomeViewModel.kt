package com.flechazo.toolbox.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flechazo.toolbox.core.data.ToolsStateRepository
import com.flechazo.toolbox.core.registry.ToolDef
import com.flechazo.toolbox.core.registry.ToolCatalog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HomeUiState(
    val query: String = "",
    val favoriteTools: List<ToolDef> = emptyList(),
    val recentTools: List<ToolDef> = emptyList(),
    val searchResults: List<ToolDef>? = null,
) {
    val showSearch: Boolean get() = searchResults != null
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
        HomeUiState(
            query = q,
            favoriteTools = ToolCatalog.all.filter { it.id in favorites }.sortedBy { it.priority },
            recentTools = recents.mapNotNull { id -> ToolCatalog.all.firstOrNull { it.id == id } }.take(6),
            searchResults = if (trimmed.isEmpty()) null else ToolCatalog.search(trimmed),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }
}
