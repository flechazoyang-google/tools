package com.example.toolbox.feature.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toolbox.core.util.SnackbarEventBus
import com.example.toolbox.data.local.datastore.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val settings: SettingsDataStore,
    private val eventBus: SnackbarEventBus,
) : ViewModel() {

    val favoriteTools: StateFlow<Set<String>> = settings.favoriteTools.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptySet(),
    )

    fun toggleFavorite(id: String) {
        viewModelScope.launch {
            val wasFav = id in favoriteTools.value
            settings.toggleFavorite(id)
            eventBus.send(if (wasFav) "已取消收藏" else "已收藏")
        }
    }

    fun openTool(id: String) {
        viewModelScope.launch { settings.pushRecent(id) }
    }
}
