package com.example.toolbox.feature.password

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toolbox.core.util.SnackbarEventBus
import com.example.toolbox.core.util.sha256
import com.example.toolbox.data.local.datastore.SettingsDataStore
import com.example.toolbox.data.local.entity.PasswordEntity
import com.example.toolbox.data.repository.PasswordInput
import com.example.toolbox.data.repository.PasswordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val SESSION_DURATION_MS = 5 * 60 * 1000L

@HiltViewModel
class PasswordViewModel @Inject constructor(
    private val repo: PasswordRepository,
    private val settings: SettingsDataStore,
    private val eventBus: SnackbarEventBus,
) : ViewModel() {

    val items: StateFlow<List<PasswordEntity>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val masterHash: StateFlow<String?> = settings.masterHash
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private var unlockedUntilMs by mutableStateOf(0L)

    val unlocked: Boolean
        get() = System.currentTimeMillis() < unlockedUntilMs

    val sessionTimeLeftSec: Int
        get() {
            val left = unlockedUntilMs - System.currentTimeMillis()
            return (left / 1000).toInt().coerceAtLeast(0)
        }

    val needsSetup: Boolean
        get() = masterHash.value == null

    fun setupMaster(password: String) {
        viewModelScope.launch {
            settings.setMasterHash(sha256(password))
            extendSession()
        }
    }

    fun unlock(password: String): Boolean {
        val ok = sha256(password) == masterHash.value
        if (ok) extendSession()
        return ok
    }

    fun unlockWithBiometric() {
        extendSession()
    }

    fun lock() {
        unlockedUntilMs = 0L
    }

    private fun extendSession() {
        unlockedUntilMs = System.currentTimeMillis() + SESSION_DURATION_MS
    }

    fun add(input: PasswordInput) = viewModelScope.launch {
        repo.add(input)
        eventBus.send("已添加密码")
    }

    fun delete(entity: PasswordEntity) = viewModelScope.launch {
        repo.delete(entity)
        val name = entity.site.ifBlank { "未命名" }
        eventBus.send("已删除「${name}」")
    }

    fun toggleFavorite(entity: PasswordEntity) = viewModelScope.launch {
        val wasFav = entity.isFavorite
        repo.update(entity.copy(isFavorite = !wasFav))
        eventBus.send(if (wasFav) "已取消收藏" else "已收藏")
    }

    fun decrypt(entity: PasswordEntity): String = repo.decrypt(entity.encryptedPassword)

    fun update(entity: PasswordEntity, input: PasswordInput) {
        viewModelScope.launch {
            val updated = entity.copy(
                site = input.site,
                account = input.account,
                note = input.note,
                tag = input.tag,
                isFavorite = input.isFavorite,
            )
            if (input.password.isNotBlank()) {
                repo.update(updated, input.password)
            } else {
                repo.update(updated)
            }
            eventBus.send("已更新密码")
        }
    }
}
