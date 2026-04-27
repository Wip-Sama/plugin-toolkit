package com.wip.kpm_cpm_wotoolkit.features.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.wip.kpm_cpm_wotoolkit.features.settings.model.AppSettings
import com.wip.kpm_cpm_wotoolkit.features.settings.model.SettingDefinition
import com.wip.kpm_cpm_wotoolkit.features.settings.utils.SettingsRegistry
import com.wip.kpm_cpm_wotoolkit.features.settings.utils.resolve
import com.wip.kpm_cpm_wotoolkit.features.settings.viewmodel.SettingsViewModel
import com.wip.kpm_cpm_wotoolkit.shared.components.settings.SettingsDropdown
import com.wip.kpm_cpm_wotoolkit.shared.components.settings.SettingsGroup
import com.wip.kpm_cpm_wotoolkit.shared.components.settings.SettingsItem
import com.wip.kpm_cpm_wotoolkit.shared.components.settings.SettingsNumericInput
import com.wip.kpm_cpm_wotoolkit.shared.components.settings.SettingsSlider
import com.wip.kpm_cpm_wotoolkit.shared.components.settings.SettingsSwitch

/**
 * A fully auto-generated settings page for use in modules and dynamic settings.
 * Reads [SettingDefinition]s from the [SettingsRegistry] for the given [navKey],
 * groups them by section, and renders the appropriate prefab component for each.
 *
 * Custom pages (About, Notification History, etc.) bypass this entirely.
 */
@Composable
fun AutoSettingsView(
    navKey: SettingNavKey, viewModel: SettingsViewModel, registry: SettingsRegistry, modifier: Modifier = Modifier,
    /** Optional overrides for specific definitions (keyed by definition hashCode).
     *  Useful for injecting ViewModel-dependent controls like "Test Notifications". */
    controlOverrides: Map<Int, @Composable (AppSettings, (AppSettings) -> Unit) -> Unit> = emptyMap(),
    /** Optional action overrides for ActionSettings that need late-bound onClick (keyed by definition hashCode). */
    actionOverrides: Map<Int, () -> Unit> = emptyMap()
) {
    val allDefinitions by registry.definitions.collectAsState()
    val pageDefinitions = allDefinitions.filter { it.navKey == navKey }
    val grouped = pageDefinitions.groupBy { it.sectionTitle }

    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        grouped.forEach { (sectionTitle, definitions) ->
            val sectionName = sectionTitle.resolve()
            SettingsGroup(title = sectionName) {
                definitions.forEach { definition ->
                    RenderSettingDefinition(
                        definition = definition,
                        settings = viewModel.settings,
                        onUpdate = { updated ->
                            viewModel.updateSettings { updated }
                            // Execute side-effect if any
                            registry.getSideEffect(definition)?.invoke(updated)
                        },
                        controlOverride = controlOverrides[definition.hashCode()],
                        actionOverride = actionOverrides[definition.hashCode()]
                    )
                }
            }
        }
    }
}

/**
 * Renders a single [SettingDefinition] using the appropriate prefab component.
 * To support a new SettingDefinition subclass, simply add a new `is` branch here.
 */
@Composable
private fun RenderSettingDefinition(
    definition: SettingDefinition,
    settings: AppSettings,
    onUpdate: (AppSettings) -> Unit,
    controlOverride: (@Composable (AppSettings, (AppSettings) -> Unit) -> Unit)? = null,
    actionOverride: (() -> Unit)? = null
) {
    val isEnabled = definition.enabled(settings)

    when (definition) {
        is SettingDefinition.SwitchSetting -> {
            SettingsItem(
                title = definition.title.resolve(),
                subtitle = definition.subtitle?.resolve(),
                icon = definition.icon,
                enabled = isEnabled,
                control = {
                    SettingsSwitch(
                        checked = definition.getValue(settings), onCheckedChange = { checked ->
                            onUpdate(definition.setValue(settings, checked))
                        })
                })
        }

        is SettingDefinition.DropdownSetting<*> -> {
            @Suppress("UNCHECKED_CAST") RenderDropdownSetting(
                definition = definition as SettingDefinition.DropdownSetting<Any?>,
                settings = settings,
                onUpdate = onUpdate,
                isEnabled = isEnabled
            )
        }

        is SettingDefinition.SliderSetting -> {
            val dynamicSubtitle = definition.subtitleProvider?.invoke(settings) ?: definition.subtitle?.resolve()

            SettingsItem(
                title = definition.title.resolve(),
                subtitle = dynamicSubtitle,
                icon = definition.icon,
                enabled = isEnabled,
                control = {
                    SettingsSlider(
                        value = definition.getValue(settings), onValueChange = { value ->
                            onUpdate(definition.setValue(settings, value))
                        }, valueRange = definition.valueRange, steps = definition.steps, enabled = isEnabled
                    )
                })
        }

        is SettingDefinition.NumericSetting -> {
            SettingsItem(
                title = definition.title.resolve(),
                subtitle = definition.subtitle?.resolve(),
                icon = definition.icon,
                enabled = isEnabled,
                control = {
                    SettingsNumericInput(
                        value = definition.getValue(settings), onValueChange = { value ->
                            onUpdate(definition.setValue(settings, value))
                        }, valueRange = definition.valueRange, enabled = isEnabled
                    )
                })
        }

        is SettingDefinition.ActionSetting -> {
            SettingsItem(
                title = definition.title.resolve(),
                subtitle = definition.subtitle?.resolve(),
                icon = definition.icon,
                enabled = isEnabled,
                onClick = actionOverride ?: definition.onClick
            )
        }

        is SettingDefinition.CustomSetting -> {
            val control = controlOverride ?: definition.control
            SettingsItem(
                title = definition.title.resolve(),
                subtitle = definition.subtitle?.resolve(),
                icon = definition.icon,
                enabled = isEnabled,
                onClick = definition.onClick,
                control = {
                    control(settings, onUpdate)
                })
        }
    }
}

/**
 * Helper to render a typed DropdownSetting without unchecked cast warnings at the call site.
 */
@Composable
private fun <T> RenderDropdownSetting(
    definition: SettingDefinition.DropdownSetting<T>,
    settings: AppSettings,
    onUpdate: (AppSettings) -> Unit,
    isEnabled: Boolean
) {
    SettingsItem(
        title = definition.title.resolve(),
        subtitle = definition.subtitle?.resolve(),
        icon = definition.icon,
        enabled = isEnabled,
        control = {
            SettingsDropdown(
                options = definition.options,
                selectedOption = definition.getValue(settings),
                onOptionSelected = { value ->
                    onUpdate(definition.setValue(settings, value))
                },
                labelProvider = definition.labelProvider,
                enabled = isEnabled
            )
        })
}
