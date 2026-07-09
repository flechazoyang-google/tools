package com.example.toolbox.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toolbox.core.theme.ThemeMode
import com.example.toolbox.data.local.datastore.SettingsDataStore
import com.example.toolbox.data.repository.CountdownRepository
import com.example.toolbox.data.repository.PasswordRepository
import com.example.toolbox.data.local.entity.CountdownEntity
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsDataStore,
    private val passwordRepo: PasswordRepository,
    private val countdownRepo: CountdownRepository,
    @ApplicationContext private val context: Context,
    private val gson: Gson,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settings.themeMode.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ThemeMode.SYSTEM,
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    /** Export passwords + countdowns to <external-files>/toolbox_backup.json. Returns the path or null. */
    suspend fun exportData(): String? = runCatching {
        val passwords = passwordRepo.exportAll()
        val countdowns = countdownRepo.observeAll().first().map { CountdownExport.fromEntity(it) }
        val payload = BackupPayload(passwords, countdowns)
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(dir, "toolbox_backup.json")
        file.writeText(gson.toJson(payload))
        file.absolutePath
    }.getOrNull()

    /** Import from the backup file. Merges into existing data. */
    suspend fun importData(): Boolean = runCatching {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(dir, "toolbox_backup.json")
        if (!file.exists()) return false
        val payload = gson.fromJson(file.readText(), BackupPayload::class.java) ?: return false
        passwordRepo.importAll(payload.passwords)
        payload.countdowns.forEach { countdownRepo.add(it.toEntity()) }
        true
    }.getOrNull() ?: false
}
