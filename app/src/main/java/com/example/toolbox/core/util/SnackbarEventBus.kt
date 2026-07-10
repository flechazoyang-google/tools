package com.example.toolbox.core.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SnackbarEventBus @Inject constructor() {
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 1, replay = 0)
    val events: SharedFlow<String> = _events.asSharedFlow()

    fun send(message: String) {
        _events.tryEmit(message)
    }
}
