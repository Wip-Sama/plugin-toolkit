package org.wip.plugintoolkit.features.settings.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.wip.plugintoolkit.core.utils.PlatformLocalization
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository
import org.wip.plugintoolkit.features.settings.model.AppLanguage
import org.wip.plugintoolkit.features.settings.model.AppSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.wip.plugintoolkit.core.update.UpdateInfo
import org.wip.plugintoolkit.core.update.UpdateService
import org.wip.plugintoolkit.core.utils.PlatformUtils

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val updateService: UpdateService
) : ViewModel() {

    var availableUpdate by mutableStateOf<UpdateInfo?>(null)
        private set

    var isDownloadingUpdate by mutableStateOf(false)
        private set

    val downloadProgress = updateService.downloadProgress

    fun checkForUpdates() {
        viewModelScope.launch {
            availableUpdate = updateService.checkForUpdates()
        }
    }

    fun dismissUpdate() {
        availableUpdate = null
    }

    fun downloadAndInstallUpdate() {
        val update = availableUpdate ?: return
        viewModelScope.launch {
            isDownloadingUpdate = true
            val dest = "${repository.getSettingsDir()}/${update.fileName}"
            val result = updateService.downloadUpdate(update, dest)
            if (result.isSuccess) {
                PlatformUtils.installUpdate(dest)
            } else {
                isDownloadingUpdate = false
                // TODO: Show error toast
            }
        }
    }

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

