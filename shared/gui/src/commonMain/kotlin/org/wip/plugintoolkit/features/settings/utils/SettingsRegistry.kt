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
 * Central stateful registry containing all application [SettingDefinition]s and their associated side effects.
 *
 * Supports static DSL registration as well as dynamic registration/unregistration for plugin extensions.
 *
 * @param initialDefinitions Initial list of setting definitions.
 * @param initialSideEffects Initial mapping of setting IDs to side-effect callbacks.
 */
class SettingsRegistry(
    initialDefinitions: List<SettingDefinition> = emptyList(),
    initialSideEffects: Map<String, suspend (AppSettings) -> Unit> = emptyMap()
) {
    private val _definitions = MutableStateFlow(initialDefinitions)

    /** StateFlow emitting the current list of registered setting definitions. */
    val definitions: StateFlow<List<SettingDefinition>> = _definitions.asStateFlow()

    private val sideEffects = initialSideEffects.toMutableMap()

    init {
        Logger.i { "SettingsRegistry initialized with ${initialDefinitions.size} definitions." }
    }

    /**
     * Registers a list of new setting definitions into the active registry.
     *
     * @param newDefinitions Definitions to add.
     */
    fun register(newDefinitions: List<SettingDefinition>) {
        _definitions.update { it + newDefinitions }
    }

    /**
     * Unregisters settings matching the provided set of unique setting IDs.
     *
     * @param settingIds Identifiers of settings to remove.
     */
    fun unregister(settingIds: Set<String>) {
        _definitions.update { it.filterNot { def -> def.id in settingIds } }
        settingIds.forEach { sideEffects.remove(it) }
    }

    /**
     * Dynamically registers a setting definition along with a side-effect callback.
     *
     * @param definition Setting definition to register.
     * @param sideEffect Asynchronous callback executed whenever this setting value changes.
     */
    suspend fun registerWithSideEffect(
        definition: SettingDefinition,
        sideEffect: suspend (AppSettings) -> Unit
    ) {
        register(listOf(definition))
        sideEffects[definition.id] = sideEffect
    }

    /**
     * Compares old and new [AppSettings] states and triggers registered side-effects for changed settings.
     *
     * @param oldSettings Settings state before modification.
     * @param newSettings Settings state after modification.
     */
    suspend fun triggerSideEffects(oldSettings: AppSettings, newSettings: AppSettings) {
        _definitions.value.forEach { def ->
            val sideEffect = sideEffects[def.id] ?: return@forEach

            @Suppress("UNCHECKED_CAST")
            val changed = when (def) {
                is SettingDefinition.SwitchSetting -> def.getValue(oldSettings) != def.getValue(newSettings)
                is SettingDefinition.DropdownSetting<*> -> (def as SettingDefinition.DropdownSetting<Any>).getValue(
                    oldSettings
                ) != (def as SettingDefinition.DropdownSetting<Any>).getValue(newSettings)

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

    /**
     * Retrieves all definitions registered for a given navigation key, grouped by section title.
     *
     * @param navKey Settings page identifier.
     * @return Map of section title to definition list.
     */
    fun getDefinitionsForPage(navKey: SettingNavKey): Map<SettingText, List<SettingDefinition>> {
        return _definitions.value
            .filter { it.navKey == navKey }
            .groupBy { it.sectionTitle }
    }

    companion object
}
