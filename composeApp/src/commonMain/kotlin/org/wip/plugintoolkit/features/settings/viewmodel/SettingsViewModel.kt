package org.wip.plugintoolkit.features.settings.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.wip.plugintoolkit.core.notification.NotificationService
import org.wip.plugintoolkit.core.update.UpdateInfo
import org.wip.plugintoolkit.core.update.UpdateService
import org.wip.plugintoolkit.core.utils.PlatformLocalization
import org.wip.plugintoolkit.core.utils.PlatformUtils
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.job.model.JobStatus
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository
import org.wip.plugintoolkit.features.settings.model.AppLanguage
import org.wip.plugintoolkit.features.settings.model.AppSettings
import org.wip.plugintoolkit.features.settings.model.SettingsEvent
import org.wip.plugintoolkit.features.settings.model.SettingsToast
import org.wip.plugintoolkit.features.settings.model.UpdateState

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val updateService: UpdateService,
    private val notificationService: NotificationService,
    private val jobManager: JobManager
) : ViewModel() {

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    var availableUpdate by mutableStateOf<UpdateInfo?>(null)
        private set

    var isDownloadingUpdate by mutableStateOf(false)
        private set

    val downloadProgress = updateService.downloadProgress

    fun checkForUpdates(isManual: Boolean = false) {
        viewModelScope.launch {
            Logger.i { "Checking for updates" }
            _updateState.value = UpdateState.Checking
            if (isManual) {
                _events.emit(SettingsEvent.ShowToast(SettingsToast.UpdateCheckStarted))
            }
            
            val update = updateService.checkForUpdates()
            availableUpdate = update
            
            if (update != null) {
                _updateState.value = UpdateState.UpdateAvailable(update)
                if (isManual) {
                    _events.emit(SettingsEvent.ShowToast(SettingsToast.UpdateNewVersionFound, listOf(update.version)))
                }
            } else {
                _updateState.value = UpdateState.UpToDate
                if (isManual) {
                    _events.emit(SettingsEvent.ShowToast(SettingsToast.UpdateNoUpdates))
                }
            }
        }
    }

    fun dismissUpdate() {
        availableUpdate = null
        _updateState.value = UpdateState.Idle
    }

    fun downloadAndInstallUpdate() {
        val update = availableUpdate ?: return
        
        // Check for running jobs first
        val runningJobs = jobManager.jobs.value.filter { it.status == JobStatus.Running }
        if (runningJobs.isNotEmpty()) {
            _updateState.value = UpdateState.NeedsConfirmation(update, runningJobs.size)
            return
        }

        performUpdate(update)
    }

    fun confirmUpdate(force: Boolean) {
        val update = availableUpdate ?: return
        viewModelScope.launch {
            if (force) {
                Logger.i { "Force updating: stopping all jobs" }
                jobManager.stopAll()
            }
            performUpdate(update)
        }
    }

    private fun performUpdate(update: UpdateInfo) {
        viewModelScope.launch {
            isDownloadingUpdate = true
            val dest = "${repository.getSettingsDir()}/${update.fileName}"
            val file = java.io.File(dest)
            
            // Caching check: If file exists and size matches, skip download
            if (file.exists() && file.length() == update.size) {
                Logger.i { "Update already downloaded: $dest" }
                PlatformUtils.installUpdate(dest)
                isDownloadingUpdate = false
                return@launch
            }

            val result = updateService.downloadUpdate(update, dest)
            if (result.isSuccess) {
                PlatformUtils.installUpdate(dest)
                // If we reach here, it means the installer failed to launch or didn't exit the app
                isDownloadingUpdate = false
            } else {
                isDownloadingUpdate = false
                _events.emit(SettingsEvent.ShowToast(SettingsToast.UpdateCheckFailed))
                _updateState.value = UpdateState.Error
            }
        }
    }

    // Connect to repository settings flow
    val settings: StateFlow<AppSettings> = repository.settings

    val currentLanguageCode: StateFlow<String> = settings
        .map { s ->
            if (s.localization.useSystemLanguage) {
                val systemLang = PlatformLocalization.getSystemLanguage()
                if (systemLang == "it") "it" else "en"
            } else {
                when (s.localization.language) {
                    AppLanguage.Italian -> "it"
                    AppLanguage.English -> "en"
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "en")

    fun updateSettings(update: (AppSettings) -> AppSettings) {
        repository.updateSettings(update)
    }

    fun openLogFolder() {
        repository.openLogFolder()
    }

    fun openLatestLog() {
        repository.openLatestLog()
    }
}
