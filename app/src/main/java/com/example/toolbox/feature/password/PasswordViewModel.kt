package com.example.toolbox.feature.password

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toolbox.core.util.sha256
import com.example.toolbox.data.local.datastore.SettingsDataStore
import com.example.toolbox.data.local.entity.PasswordEntity
import com.example.toolbox.data.repository.PasswordInput
import com.example.toolbox.data.repository.PasswordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PasswordViewModel @Inject constructor(
    private val repo: PasswordRepository,
    private val settings: SettingsDataStore,
) : ViewModel() {

    val items: StateFlow<List<PasswordEntity>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val masterHash: StateFlow<String?> = settings.masterHash
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    var unlocked by mutableStateOf(false)
        private set

    /** True when no master password has been set yet. */
    val needsSetup: Boolean
        get() = masterHash.value == null

    fun setupMaster(password: String) {
        viewModelScope.launch {
            settings.setMasterHash(sha256(password))
            unlocked = true
        }
    }

    fun unlock(password: String): Boolean {
        val ok = sha256(password) == masterHash.value
        if (ok) unlocked = true
        return ok
    }

    /** Unlock via biometric authentication — skips master password check. */
    fun unlockWithBiometric() {
        unlocked = true
    }

    fun lock() {
        unlocked = false
    }

    fun add(input: PasswordInput) = viewModelScope.launch { repo.add(input) }

    fun delete(entity: PasswordEntity) = viewModelScope.launch { repo.delete(entity) }

    fun toggleFavorite(entity: PasswordEntity) = viewModelScope.launch {
        repo.update(entity.copy(isFavorite = !entity.isFavorite))
    }

    fun decrypt(entity: PasswordEntity): String = repo.decrypt(entity.encryptedPassword)
}
