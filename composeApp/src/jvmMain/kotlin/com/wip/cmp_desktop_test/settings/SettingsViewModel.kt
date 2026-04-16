package com.wip.cmp_desktop_test.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
