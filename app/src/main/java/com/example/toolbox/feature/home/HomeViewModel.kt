package com.example.toolbox.feature.home

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
class HomeViewModel @Inject constructor(
    private val settings: SettingsDataStore,
) : ViewModel() {

    val recentTools: StateFlow<List<String>> = settings.recentTools.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList(),
    )

    val favoriteTools: StateFlow<Set<String>> = settings.favoriteTools.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptySet(),
    )

    fun openTool(id: String) {
        viewModelScope.launch { settings.pushRecent(id) }
    }

    fun toggleFavorite(id: String) {
        viewModelScope.launch { settings.toggleFavorite(id) }
    }
}
