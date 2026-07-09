package com.example.toolbox.feature.currency

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toolbox.data.local.entity.CurrencyRateEntity
import com.example.toolbox.data.repository.CurrencyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CurrencyViewModel @Inject constructor(
    private val repo: CurrencyRepository,
) : ViewModel() {

    val rateMap: StateFlow<Map<String, Double>> = repo.observeRates()
        .map { repo.getRateMap(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val updatedAt: StateFlow<Long?> = repo.observeRates()
        .map { it?.updatedAt }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    var isRefreshing = MutableStateFlow(false)
        private set

    var isOffline = MutableStateFlow(false)
        private set

    init {
        refresh()
    }

    /** Refresh in the background so the first paint always uses cached rates (fast cold start). */
    fun refresh() {
        viewModelScope.launch {
            isRefreshing.value = true
            val hadCache = rateMap.value.isNotEmpty()
            val fresh = repo.refresh()
            isRefreshing.value = false
            isOffline.value = fresh == null && !hadCache
        }
    }
}
