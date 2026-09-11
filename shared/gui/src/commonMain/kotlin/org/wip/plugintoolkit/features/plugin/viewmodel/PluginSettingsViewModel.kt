package org.wip.plugintoolkit.features.plugin.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.job.model.JobStatus
import org.wip.plugintoolkit.features.plugin.logic.PluginManager
import org.wip.plugintoolkit.features.plugin.utils.SettingsUtils

class PluginSettingsViewModel(
    val pkg: String,
    private val pluginManager: PluginManager,
    private val jobManager: JobManager,
) : ViewModel() {
    private val _store = MutableStateFlow(pluginManager.loadPluginSettings(pkg))
    val store = _store.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy = _isBusy.asStateFlow()

    val manifest = pluginManager.getManifest(pkg)

    val locks = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    init {
        viewModelScope.launch {
            pluginManager.refreshLocks(pkg)
        }
        viewModelScope.launch {
            jobManager.jobs.collect { jobs ->
                val busy = jobs.any { it.pluginId == pkg && it.status == JobStatus.Running }
                _isBusy.value = busy
            }
        }
        viewModelScope.launch {
            pluginManager.pluginLocksState.collect { map ->
                map[pkg]?.let { locks.value = it }
            }
        }
        viewModelScope.launch {
            pluginManager.pluginSettingsState.collect { map ->
                map[pkg]?.let { loadedStore ->
                    _store.value = loadedStore
                }
            }
        }
    }

    fun updateSetting(key: String, value: JsonElement) {
        _store.update { current ->
            val updated = current.copy(settings = current.settings + (key to value))
            viewModelScope.launch {
                val newLocks = pluginManager.refreshLocks(pkg, updated)
                locks.value = newLocks
            }
            updated
        }
    }

    fun updateGlobalParam(key: String, value: JsonElement) {
        _store.update { current ->
            val updated = current.copy(globalParams = current.globalParams + (key to value))
            viewModelScope.launch {
                val newLocks = pluginManager.refreshLocks(pkg, updated)
                locks.value = newLocks
            }
            updated
        }
    }

    fun updateCapabilityParam(capability: String, key: String, value: JsonElement) {
        _store.update { current ->
            val currentCaps = current.capabilityParams.toMutableMap()
            val capParams = currentCaps[capability]?.toMutableMap() ?: mutableMapOf()
            capParams[key] = value
            currentCaps[capability] = capParams
            val updated = current.copy(capabilityParams = currentCaps)
            viewModelScope.launch {
                val newLocks = pluginManager.refreshLocks(pkg, updated)
                locks.value = newLocks
            }
            updated
        }
    }

    fun save() {
        val currentStore = _store.value
        val mergedSettings = currentStore.settings.toMutableMap()
        manifest?.settings?.forEach { (key, meta) ->
            if (!mergedSettings.containsKey(key)) {
                val eff = SettingsUtils.resolveEffectiveJson(null, meta.defaultValue, meta.type)
                if (eff != null) {
                    mergedSettings[key] = eff
                }
            }
        }
        val toSave = currentStore.copy(settings = mergedSettings)
        pluginManager.savePluginSettings(pkg, toSave)
        viewModelScope.launch {
            pluginManager.checkAndResumeSetup(pkg)
        }
    }

    fun refreshLocks() {
        viewModelScope.launch {
            val newLocks = pluginManager.refreshLocks(pkg, _store.value)
            locks.value = newLocks
        }
    }

    fun runAction(actionName: String, parameters: Map<String, JsonElement> = emptyMap()) {
        viewModelScope.launch {
            val action = manifest?.actions?.find { it.functionName == actionName || it.name == actionName }
            if (action != null) {
                pluginManager.runAction(pkg, action, parameters)
            }
        }
    }
}
