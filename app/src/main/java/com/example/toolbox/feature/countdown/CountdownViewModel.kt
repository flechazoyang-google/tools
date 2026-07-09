package com.example.toolbox.feature.countdown

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toolbox.data.local.entity.CountdownEntity
import com.example.toolbox.data.repository.CountdownRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CountdownViewModel @Inject constructor(
    private val repo: CountdownRepository,
) : ViewModel() {

    val items: StateFlow<List<CountdownEntity>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun add(title: String, targetDate: Long, colorTag: String, type: String = "countdown") {
        viewModelScope.launch {
            repo.add(CountdownEntity(title = title, targetDate = targetDate, colorTag = colorTag, type = type))
        }
    }

    fun delete(entity: CountdownEntity) {
        viewModelScope.launch { repo.delete(entity) }
    }

    fun togglePin(entity: CountdownEntity) {
        viewModelScope.launch { repo.togglePin(entity) }
    }
}
