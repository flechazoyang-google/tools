package com.example.toolbox.feature.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toolbox.data.local.datastore.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ToolsViewModel @Inject constructor(
    private val settings: SettingsDataStore,
) : ViewModel() {

    fun openTool(id: String) {
        viewModelScope.launch { settings.pushRecent(id) }
    }
}
