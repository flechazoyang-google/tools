package com.example.toolbox.feature.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
) : ViewModel() {

    val favoriteTools: StateFlow<Set<String>> = settings.favoriteTools.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptySet(),
    )

    fun toggleFavorite(id: String) {
        viewModelScope.launch { settings.toggleFavorite(id) }
    }

    fun openTool(id: String) {
        viewModelScope.launch { settings.pushRecent(id) }
    }
}
