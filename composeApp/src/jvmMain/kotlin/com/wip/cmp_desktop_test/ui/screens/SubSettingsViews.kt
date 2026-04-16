package com.wip.cmp_desktop_test.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import cmp_desktop_test.composeapp.generated.resources.*
import com.wip.cmp_desktop_test.settings.*
import com.wip.cmp_desktop_test.ui.components.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun AppearanceSettingsView(viewModel: SettingsViewModel) {
    val appearance = viewModel.settings.appearance
    val localization = viewModel.settings.localization
    val general = viewModel.settings.general
    
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // --- Theme Section ---
        SettingsGroup(title = stringResource(Res.string.setting_appearance)) {
            SettingsItem(
                title = stringResource(Res.string.setting_theme),
                subtitle = "Choose between System, Light, Dark or Amoled",
                icon = Icons.Default.Brightness6,
                control = {
                    SettingsDropdown(
                        options = AppTheme.entries,
                        selectedOption = appearance.theme,
                        onOptionSelected = { theme -> viewModel.updateSettings { it.copy(appearance = it.appearance.copy(theme = theme)) } },
                        labelProvider = { it.name }
                    )
                }
            )
            
            SettingsItem(
                title = stringResource(Res.string.setting_adaptive_color),
                subtitle = "Use the accent color for adaptive UI elements",
                icon = Icons.Default.ColorLens,
                control = {
                    SettingsSwitch(
                        checked = appearance.useAdaptiveColor,
                        onCheckedChange = { checked -> viewModel.updateSettings { it.copy(appearance = it.appearance.copy(useAdaptiveColor = checked)) } }
                    )
                }
            )
        }

        // --- Localization Section ---
        SettingsGroup(title = stringResource(Res.string.section_localization)) {
            SettingsItem(
                title = stringResource(Res.string.setting_language),
                subtitle = "Select your preferred language",
                icon = Icons.Default.Language,
                control = {
                    SettingsDropdown(
                        options = AppLanguage.entries,
                        selectedOption = localization.language,
                        onOptionSelected = { lang -> viewModel.updateSettings { it.copy(localization = it.localization.copy(language = lang)) } },
                        labelProvider = { it.label }
                    )
                }
            )

            SettingsItem(
                title = stringResource(Res.string.setting_timezone),
                subtitle = "Set your local timezone",
                icon = Icons.Default.Schedule,
                control = {
                    SettingsDropdown(
                        options = listOf("GMT+1", "GMT+2", "GMT+3", "GMT+0", "GMT-1"),
                        selectedOption = localization.timezone,
                        onOptionSelected = { tz -> viewModel.updateSettings { it.copy(localization = it.localization.copy(timezone = tz)) } },
                        labelProvider = { it }
                    )
                }
            )
        }

        // --- General Section ---
        SettingsGroup(title = stringResource(Res.string.section_general)) {
            SettingsItem(
                title = stringResource(Res.string.setting_scaling),
                subtitle = "${(general.scaling * 100).toInt()}%",
                icon = Icons.Default.AspectRatio,
                control = {
                    SettingsSlider(
                        value = general.scaling,
                        onValueChange = { valScale -> viewModel.updateSettings { it.copy(general = it.general.copy(scaling = valScale)) } },
                        valueRange = 0.5f..2.0f
                    )
                }
            )

            SettingsItem(
                title = stringResource(Res.string.setting_animations),
                subtitle = "Toggle all UI animations",
                icon = Icons.Default.Animation,
                control = {
                    SettingsSwitch(
                        checked = general.animationsEnabled,
                        onCheckedChange = { checked -> viewModel.updateSettings { it.copy(general = it.general.copy(animationsEnabled = checked)) } }
                    )
                }
            )

            if (general.animationsEnabled) {
                SettingsItem(
                    title = stringResource(Res.string.setting_animation_speed),
                    subtitle = "${(general.animationSpeed * 100).toInt()}%",
                    icon = Icons.Default.Speed,
                    control = {
                        SettingsSlider(
                            value = general.animationSpeed,
                            onValueChange = { speed -> viewModel.updateSettings { it.copy(general = it.general.copy(animationSpeed = speed)) } },
                            valueRange = 0.25f..2.0f
                        )
                    }
                )
            }
        }
    }
}
