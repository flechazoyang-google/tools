package com.example.toolbox.feature.ip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toolbox.data.local.datastore.IpInfo
import com.example.toolbox.data.repository.IpRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class IpViewModel @Inject constructor(
    private val repo: IpRepository,
) : ViewModel() {

    val cache: StateFlow<List<IpInfo>> = repo.observeCache()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _current = MutableStateFlow<IpInfo?>(null)
    val current: StateFlow<IpInfo?> = _current

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        detect()
    }

    fun detect() = query(null)

    fun query(ip: String?) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val res = repo.lookup(ip)
            _isLoading.value = false
            res.onSuccess { _current.value = it }.onFailure { _error.value = it.message ?: "查询失败" }
        }
    }
}
