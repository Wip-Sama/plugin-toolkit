package org.wip.plugintoolkit.features.settings.logic

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.wip.plugintoolkit.features.settings.model.AppSettings

class SettingsRepository(
    private val persistence: SettingsPersistence,
    private val scope: CoroutineScope
) {
    
    private val _settings = MutableStateFlow(persistence.load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val saveChannel = Channel<AppSettings>(Channel.CONFLATED)

    init {
        scope.launch {
            saveChannel.receiveAsFlow()
                .debounce(500)
                .collect { 
                    persistence.save(it)
                }
        }
    }

    /**
     * Updates settings atomically and schedules a debounced save to disk.
     */
    fun updateSettings(update: (AppSettings) -> AppSettings) {
        _settings.value = update(_settings.value)
        saveChannel.trySend(_settings.value)
    }

    fun getSettingsDir(): String = persistence.getSettingsDir()
    
    fun getJobsDir(): String = persistence.getJobsDir()
    
    fun openLogFolder() = persistence.openLogFolder()

    /**
     * Legacy method for immediate access. Use [settings] Flow for reactive updates.
     * TODO: Remove / Deprecate this
     */
    fun loadSettings(): AppSettings = _settings.value

    /**
     * Legacy method. Prefer [updateSettings] for atomic updates.
     * TODO: Remove / Deprecate this
     */
    fun saveSettings(settings: AppSettings) {
        updateSettings { settings }
    }
}
