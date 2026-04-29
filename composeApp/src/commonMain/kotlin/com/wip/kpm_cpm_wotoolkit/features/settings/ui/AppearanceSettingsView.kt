@file:OptIn(ExperimentalMaterial3Api::class)

package com.wip.kpm_cpm_wotoolkit.features.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.wip.kpm_cpm_wotoolkit.features.colorpicker.model.ColorPickerType
import com.wip.kpm_cpm_wotoolkit.features.colorpicker.ui.ColorPickerDialog
import com.wip.kpm_cpm_wotoolkit.features.settings.model.AppLanguage
import com.wip.kpm_cpm_wotoolkit.features.settings.model.AppTheme
import com.wip.kpm_cpm_wotoolkit.features.settings.utils.TimezoneUtils
import com.wip.kpm_cpm_wotoolkit.features.settings.viewmodel.SettingsViewModel
import com.wip.kpm_cpm_wotoolkit.shared.components.settings.ExpressiveMenu
import com.wip.kpm_cpm_wotoolkit.shared.components.settings.SettingsGroup
import com.wip.kpm_cpm_wotoolkit.shared.components.settings.SettingsItem
import com.wip.kpm_cpm_wotoolkit.shared.components.settings.SettingsSlider
import com.wip.kpm_cpm_wotoolkit.shared.components.settings.SettingsSwitch
import kpm_cpm_wotoolkit.composeapp.generated.resources.Res
import kpm_cpm_wotoolkit.composeapp.generated.resources.section_localization
import kpm_cpm_wotoolkit.composeapp.generated.resources.setting_accent_color
import kpm_cpm_wotoolkit.composeapp.generated.resources.setting_appearance
import kpm_cpm_wotoolkit.composeapp.generated.resources.setting_language
import kpm_cpm_wotoolkit.composeapp.generated.resources.setting_scaling
import kpm_cpm_wotoolkit.composeapp.generated.resources.setting_theme
import kpm_cpm_wotoolkit.composeapp.generated.resources.setting_timezone
import kpm_cpm_wotoolkit.composeapp.generated.resources.setting_use_system_language
import kpm_cpm_wotoolkit.composeapp.generated.resources.setting_use_system_language_subtitle
import org.jetbrains.compose.resources.stringResource
import com.wip.kpm_cpm_wotoolkit.core.theme.WOTheme

@Composable
fun AppearanceSettingsView(viewModel: SettingsViewModel) {
    val appearance = viewModel.settings.appearance
    val localization = viewModel.settings.localization
    val general = viewModel.settings.general

    var showColorPicker by remember { mutableStateOf(false) }

    ColorPickerDialog(
        show = showColorPicker,
        initialType = ColorPickerType.Classic(),
        onDismissRequest = { showColorPicker = false },
        onPickedColor = { color ->
            viewModel.updateSettings {
                it.copy(
                    appearance = it.appearance.copy(accentColor = color.toArgb().toLong())
                )
            }
            showColorPicker = false
        }
    )

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // --- Theme Section ---
        SettingsGroup(title = stringResource(Res.string.setting_appearance)) {
            SettingsItem(
                title = stringResource(Res.string.setting_theme),
                subtitle = "Choose between System, Light, Dark or Amoled",
                icon = Icons.Default.Brightness6,
                control = {
                    ExpressiveMenu(
                        options = AppTheme.entries,
                        selectedOption = appearance.theme,
                        onOptionSelected = { theme ->
                            viewModel.updateSettings {
                                it.copy(appearance = it.appearance.copy(theme = theme))
                            }
                        },
                        labelProvider = { it.name }
                    )
                }
            )

            SettingsItem(
                title = "Follow System Accent",
                subtitle = "Automatically use the accent color from your operating system",
                icon = Icons.Default.AutoFixHigh,
                control = {
                    SettingsSwitch(
                        checked = appearance.followSystemAccent, onCheckedChange = { checked ->
                            viewModel.updateSettings {
                                it.copy(
                                    appearance = it.appearance.copy(
                                        followSystemAccent = checked
                                    )
                                )
                            }
                        }
                    )
                }
            )

            SettingsItem(
                title = stringResource(Res.string.setting_accent_color),
                subtitle = "Manually select the accent color for the application",
                icon = Icons.Default.ColorLens,
                enabled = !appearance.followSystemAccent,
                onClick = { if (!appearance.followSystemAccent) showColorPicker = true },
                control = {
                    Box(
                        modifier = Modifier
                            .size(WOTheme.dimensions.iconLarge)
                            .clip(CircleShape)
                            .background(Color(appearance.accentColor))
                            .border(
                                1.dp, MaterialTheme.colorScheme.outline, CircleShape
                            ).clickable(
                                enabled = !appearance.followSystemAccent
                            ) { showColorPicker = true }
                    )
                }
            )

            SettingsItem(
                title = stringResource(Res.string.setting_scaling),
                subtitle = "${(general.scaling * 100).toInt()}%",
                icon = Icons.Default.AspectRatio,
                control = {
                    SettingsSlider(
                        value = general.scaling, onValueChange = { valScale ->
                            viewModel.updateSettings {
                                it.copy(general = it.general.copy(scaling = valScale))
                            }
                        }, valueRange = 0.5f..2.0f, steps = 5
                    )
                }
            )
        }

        // --- Localization Section ---
        SettingsGroup(title = stringResource(Res.string.section_localization)) {
            SettingsItem(
                title = stringResource(Res.string.setting_use_system_language),
                subtitle = stringResource(Res.string.setting_use_system_language_subtitle),
                icon = Icons.Default.Language,
                control = {
                    SettingsSwitch(
                        checked = localization.useSystemLanguage, onCheckedChange = { checked ->
                            viewModel.updateSettings {
                                it.copy(
                                    localization = it.localization.copy(
                                        useSystemLanguage = checked
                                    )
                                )
                            }
                        }
                    )
                }
            )

            SettingsItem(
                title = stringResource(Res.string.setting_language),
                subtitle = "Select your preferred language",
                icon = Icons.Default.Language,
                enabled = !localization.useSystemLanguage,
                control = {
                    ExpressiveMenu(
                        options = AppLanguage.entries,
                        selectedOption = localization.language,
                        onOptionSelected = { lang ->
                            viewModel.updateSettings {
                                it.copy(
                                    localization = it.localization.copy(language = lang)
                                )
                            }
                        },
                        labelProvider = { it.label },
                        enabled = !localization.useSystemLanguage
                    )
                }
            )

            SettingsItem(
                title = "Use System Timezone",
                subtitle = "Automatically detect your local timezone from the system",
                icon = Icons.Default.Map,
                control = {
                    SettingsSwitch(
                        checked = localization.useSystemTimezone, onCheckedChange = { checked ->
                            viewModel.updateSettings {
                                it.copy(
                                    localization = it.localization.copy(
                                        useSystemTimezone = checked
                                    )
                                )
                            }
                        }
                    )
                }
            )

            val availableTimezones = remember { TimezoneUtils.getAvailableZoneIds() }
            SettingsItem(
                title = stringResource(Res.string.setting_timezone),
                subtitle = "Manual timezone selection",
                icon = Icons.Default.Schedule,
                enabled = !localization.useSystemTimezone,
                control = {
                    ExpressiveMenu(
                        options = availableTimezones,
                        selectedOption = if (localization.useSystemTimezone) TimezoneUtils.getSystemDefaultId()
                        else localization.timezone,
                        onOptionSelected = { tz ->
                            viewModel.updateSettings {
                                it.copy(localization = it.localization.copy(timezone = tz))
                            }
                        },
                        labelProvider = { it },
                        enabled = !localization.useSystemTimezone
                    )
                }
            )
        }
    }
}

