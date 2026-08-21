package org.wip.plugintoolkit.features.settings.logic

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.wip.plugintoolkit.features.settings.model.AppSettings
import kotlin.time.Duration.Companion.milliseconds

private sealed interface LoadState {
    data class Loading(val pending: PersistentList<(AppSettings) -> AppSettings> = persistentListOf()) : LoadState
    data object Ready : LoadState
}

@OptIn(FlowPreview::class)
class SettingsRepository(
    val persistence: SettingsPersistence,
    scope: CoroutineScope
) {

    private val loadState = atomic<LoadState>(LoadState.Loading())

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    private val saveChannel = Channel<AppSettings>(Channel.CONFLATED)

    init {
        scope.launch {
            val loaded = persistence.load()
            var applied = loaded
            var hadPending = false
            loadState.update { state ->
                when (state) {
                    is LoadState.Loading -> {
                        for (fn in state.pending) {
                            applied = fn(applied)
                        }
                        hadPending = state.pending.isNotEmpty()
                        LoadState.Ready
                    }
                    is LoadState.Ready -> state
                }
            }
            _settings.value = applied
            _isLoaded.value = true
            if (hadPending) {
                saveChannel.trySend(applied)
            }
            saveChannel.receiveAsFlow()
                .debounce(500.milliseconds)
                .collect {
                    persistence.save(it)
                }
        }
    }

    /**
     * Updates settings atomically and schedules a debounced save to disk.
     */
    fun updateSettings(update: (AppSettings) -> AppSettings) {
        loadState.update { state ->
            when (state) {
                is LoadState.Loading -> LoadState.Loading(state.pending.add(update))
                is LoadState.Ready -> state
            }
        }
        _settings.value = update(_settings.value)
        saveChannel.trySend(_settings.value)
    }

    fun getSettingsDir(): String = persistence.getSettingsDir()

    fun getJobsDir(): String = persistence.getJobsDir()

    fun openLogFolder() = persistence.openLogFolder()

    fun openLatestLog() = persistence.openLatestLog()

    /**
     * Legacy method for immediate access. Use [settings] Flow for reactive updates.
     * TODO: Remove / Deprecate this
     */
    @Deprecated("Use [settings] Flow for reactive updates")
    fun loadSettings(): AppSettings = _settings.value

    /**
     * Legacy method. Prefer [updateSettings] for atomic updates.
     * TODO: Remove / Deprecate this
     */
    @Deprecated("Use [updateSettings] for atomic updates")
    fun saveSettings(settings: AppSettings) {
        updateSettings { settings }
    }
}
