package org.wip.plugintoolkit.features.settings.utils

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.wip.plugintoolkit.features.settings.model.AppSettings
import org.wip.plugintoolkit.features.settings.model.SettingDefinition
import org.wip.plugintoolkit.features.settings.ui.SettingNavKey

/**
 * Central registry for all application settings.
 * Modularized to collect definitions from multiple sources via DSL or dynamic registration.
 */
class SettingsRegistry(
    initialDefinitions: List<SettingDefinition> = emptyList(),
    initialSideEffects: Map<String, (AppSettings) -> Unit> = emptyMap()
) {
    private val _definitions = MutableStateFlow<List<SettingDefinition>>(initialDefinitions)
    val definitions: StateFlow<List<SettingDefinition>> = _definitions.asStateFlow()

    private val sideEffects = initialSideEffects.toMutableMap()

    init {
        Logger.i { "SettingsRegistry initialized with ${initialDefinitions.size} definitions." }
    }

    fun register(newDefinitions: List<SettingDefinition>) {
        _definitions.update { it + newDefinitions }
    }

    fun unregister(settingIds: Set<String>) {
        _definitions.update { it.filterNot { def -> def.id in settingIds } }
        settingIds.forEach { sideEffects.remove(it) }
    }

    fun registerWithSideEffect(
        definition: SettingDefinition,
        sideEffect: (AppSettings) -> Unit
    ) {
        register(listOf(definition))
        sideEffects[definition.id] = sideEffect
    }

    /**
     * Executes any registered side effects for the given settings update.
     */
    fun triggerSideEffects(oldSettings: AppSettings, newSettings: AppSettings) {
        _definitions.value.forEach { def ->
            val sideEffect = sideEffects[def.id] ?: return@forEach
            
            val changed = when (def) {
                is SettingDefinition.SwitchSetting -> def.getValue(oldSettings) != def.getValue(newSettings)
                is SettingDefinition.DropdownSetting<*> -> (def as SettingDefinition.DropdownSetting<Any>).getValue(oldSettings) != (def as SettingDefinition.DropdownSetting<Any>).getValue(newSettings)
                is SettingDefinition.SliderSetting -> def.getValue(oldSettings) != def.getValue(newSettings)
                is SettingDefinition.NumericSetting -> def.getValue(oldSettings) != def.getValue(newSettings)
                else -> false
            }

            if (changed) {
                Logger.d { "Triggering side effect for setting: ${def.id}" }
                sideEffect(newSettings)
            }
        }
    }

    /** Get all definitions for a specific nav key, grouped by section */
    fun getDefinitionsForPage(navKey: SettingNavKey): Map<SettingText, List<SettingDefinition>> {
        return _definitions.value
            .filter { it.navKey == navKey }
            .groupBy { it.sectionTitle }
    }

    companion object
}
