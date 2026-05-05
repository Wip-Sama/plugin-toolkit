package org.wip.plugintoolkit.features.plugin.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonElement
import org.wip.plugintoolkit.features.plugin.logic.PluginLoader
import org.wip.plugintoolkit.features.plugin.logic.PluginManager

class PluginSettingsViewModel(
    val pkg: String,
    private val pluginManager: PluginManager,
) : ViewModel() {
    private val _store = MutableStateFlow(pluginManager.loadPluginSettings(pkg))
    val store = _store.asStateFlow()

    val manifest = PluginLoader.getPluginById(pkg)?.getManifest()

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
    }
}
