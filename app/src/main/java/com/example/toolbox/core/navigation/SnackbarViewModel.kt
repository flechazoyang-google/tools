package com.example.toolbox.core.navigation

import androidx.lifecycle.ViewModel
import com.example.toolbox.core.util.SnackbarEventBus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SnackbarViewModel @Inject constructor(
    val eventBus: SnackbarEventBus,
) : ViewModel()
