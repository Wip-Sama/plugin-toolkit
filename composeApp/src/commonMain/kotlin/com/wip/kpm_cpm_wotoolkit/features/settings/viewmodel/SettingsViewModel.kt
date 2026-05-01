package com.wip.kpm_cpm_wotoolkit.features.settings.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wip.kpm_cpm_wotoolkit.core.utils.PlatformLocalization
import com.wip.kpm_cpm_wotoolkit.features.settings.logic.SettingsRepository
import com.wip.kpm_cpm_wotoolkit.features.settings.model.AppLanguage
import com.wip.kpm_cpm_wotoolkit.features.settings.model.AppSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: SettingsRepository
) : ViewModel() {

    var settings by mutableStateOf(repository.loadSettings())
        private set

    val currentLanguageCode: StateFlow<String> = snapshotFlow { settings.localization }
        .map { localization ->
            if (localization.useSystemLanguage) {
                val systemLang = PlatformLocalization.getSystemLanguage()
                if (systemLang == "it") "it" else "en"
            } else {
                when (localization.language) {
                    AppLanguage.Italian -> "it"
                    AppLanguage.English -> "en"
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "en")

    fun updateSettings(update: (AppSettings) -> AppSettings) {
        settings = update(settings)
        save()
    }

    private fun save() {
        viewModelScope.launch {
            repository.saveSettings(settings)
        }
    }

    fun openLogFolder() {
        repository.openLogFolder()
    }
}

