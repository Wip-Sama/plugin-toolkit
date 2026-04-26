package com.wip.kpm_cpm_wotoolkit.features.settings.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wip.kpm_cpm_wotoolkit.features.settings.logic.SettingsRepository
import com.wip.kpm_cpm_wotoolkit.features.settings.model.AppSettings
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: SettingsRepository = SettingsRepository()
) : ViewModel() {
    
    var settings by mutableStateOf(repository.loadSettings())
        private set

    fun updateSettings(update: (AppSettings) -> AppSettings) {
        settings = update(settings)
        save()
    }

    private fun save() {
        viewModelScope.launch {
            repository.saveSettings(settings)
        }
    }
}
