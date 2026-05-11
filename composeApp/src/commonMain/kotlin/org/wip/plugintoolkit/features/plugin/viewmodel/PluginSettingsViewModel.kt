package org.wip.plugintoolkit.features.plugin.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import kotlinx.serialization.json.JsonElement
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.job.model.JobStatus
import org.wip.plugintoolkit.features.plugin.logic.PluginManager

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

    init {
        viewModelScope.launch {
            jobManager.jobs.collect { jobs ->
                val busy = jobs.any { it.pluginId == pkg && it.status == JobStatus.Running }
                _isBusy.value = busy
            }
        }
    }

    fun updateSetting(key: String, value: JsonElement) {
        _store.update { it.copy(settings = it.settings + (key to value)) }
    }

    fun updateGlobalParam(key: String, value: JsonElement) {
        _store.update { it.copy(globalParams = it.globalParams + (key to value)) }
    }

    fun updateCapabilityParam(capability: String, key: String, value: JsonElement) {
        val currentCaps = _store.value.capabilityParams.toMutableMap()
        val capParams = currentCaps[capability]?.toMutableMap() ?: mutableMapOf()
        capParams[key] = value
        currentCaps[capability] = capParams
        _store.update { it.copy(capabilityParams = currentCaps) }
    }

    fun save() {
        pluginManager.savePluginSettings(pkg, _store.value)
        viewModelScope.launch {
            pluginManager.checkAndResumeSetup(pkg)
        }
    }

    fun runAction(actionName: String) {
        viewModelScope.launch {
            val action = manifest?.actions?.find { it.functionName == actionName || it.name == actionName }
            if (action != null) {
                pluginManager.runAction(pkg, action)
            }
        }
    }
}
