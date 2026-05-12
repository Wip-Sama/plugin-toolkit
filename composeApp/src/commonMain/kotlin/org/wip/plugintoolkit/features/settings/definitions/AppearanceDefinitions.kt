package org.wip.plugintoolkit.features.settings.definitions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Schedule
import org.wip.plugintoolkit.features.settings.model.*
import org.wip.plugintoolkit.features.settings.ui.SettingNavKey
import org.wip.plugintoolkit.features.settings.utils.*
import plugintoolkit.composeapp.generated.resources.*

fun SettingsRegistryBuilder.appearanceDefinitions() {
    nav(SettingNavKey.Appearance) {
        // ── Appearance ───────────────────────────────────────────────
        section(SettingText.Raw("Appearance")) {
            SettingDropdown(
                p1 = AppSettings::appearance,
                p2 = AppearanceSettings::theme,
                title = SettingText.Resource(Res.string.setting_theme),
                subtitle = SettingText.Raw("Choose between System, Light, Dark or Amoled"),
                icon = Icons.Default.Brightness6,
                options = AppTheme.entries,
                labelProvider = { it.name },
                setValue = { s, v -> s.copy(appearance = s.appearance.copy(theme = v)) }
            )

            SettingSwitch(
                p1 = AppSettings::appearance,
                p2 = AppearanceSettings::followSystemAccent,
                title = SettingText.Raw("Follow System Accent"),
                subtitle = SettingText.Raw("Automatically use the accent color from your operating system"),
                icon = Icons.Default.AutoFixHigh,
                setValue = { s, v -> s.copy(appearance = s.appearance.copy(followSystemAccent = v)) }
            )

            SettingCustom(
                id = "appearance.accentColor",
                title = SettingText.Resource(Res.string.setting_accent_color),
                subtitle = SettingText.Raw("Manually select the accent color for the application"),
                icon = Icons.Default.Brightness6,
                enabled = { !it.appearance.followSystemAccent },
                control = { settings, onUpdate ->
                    // Logic moved from AccentColorControlProvider
                    org.wip.plugintoolkit.features.settings.ui.AccentColorControl(settings, onUpdate)
                }
            )

            SettingSlider(
                p1 = AppSettings::general,
                p2 = GeneralSettings::scaling,
                title = SettingText.Resource(Res.string.setting_scaling),
                icon = Icons.Default.AspectRatio,
                valueRange = 0.5f..2.0f,
                steps = 5,
                subtitleProvider = { "${(it.general.scaling * 100).toInt()}%" },
                setValue = { s, v -> s.copy(general = s.general.copy(scaling = v)) }
            )
        }

        // ── Localization ─────────────────────────────────────────────
        section(SettingText.Raw("Localization")) {
            SettingSwitch(
                p1 = AppSettings::localization,
                p2 = LocalizationSettings::useSystemLanguage,
                title = SettingText.Resource(Res.string.setting_use_system_language),
                subtitle = SettingText.Resource(Res.string.setting_use_system_language_subtitle),
                icon = Icons.Default.Language,
                setValue = { s, v -> s.copy(localization = s.localization.copy(useSystemLanguage = v)) }
            )

            SettingDropdown(
                p1 = AppSettings::localization,
                p2 = LocalizationSettings::language,
                title = SettingText.Resource(Res.string.setting_language),
                subtitle = SettingText.Raw("Select your preferred language"),
                icon = Icons.Default.Language,
                enabled = { !it.localization.useSystemLanguage },
                options = AppLanguage.entries,
                labelProvider = { it.label },
                setValue = { s, v -> s.copy(localization = s.localization.copy(language = v)) }
            )

            SettingSwitch(
                p1 = AppSettings::localization,
                p2 = LocalizationSettings::useSystemTimezone,
                title = SettingText.Raw("Use System Timezone"),
                subtitle = SettingText.Raw("Automatically detect your local timezone from the system"),
                icon = Icons.Default.Map,
                setValue = { s, v -> s.copy(localization = s.localization.copy(useSystemTimezone = v)) }
            )

            SettingDropdown(
                p1 = AppSettings::localization,
                p2 = LocalizationSettings::timezone,
                title = SettingText.Resource(Res.string.setting_timezone),
                subtitle = SettingText.Raw("Manual timezone selection"),
                icon = Icons.Default.Schedule,
                enabled = { !it.localization.useSystemTimezone },
                options = TimezoneUtils.getAvailableZoneIds(),
                labelProvider = { it },
                setValue = { s, v -> s.copy(localization = s.localization.copy(timezone = v)) }
            )
        }
    }
}
