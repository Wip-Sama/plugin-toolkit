package org.wip.plugintoolkit.features.settings.definitions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ViewSidebar
import androidx.compose.material.icons.filled.WebAsset
import kotlin.math.round
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.features.settings.model.AppLanguage
import org.wip.plugintoolkit.features.settings.model.AppSettings
import org.wip.plugintoolkit.features.settings.model.AppTheme
import org.wip.plugintoolkit.features.settings.model.AppearanceSettings
import org.wip.plugintoolkit.features.settings.model.GeneralSettings
import org.wip.plugintoolkit.features.settings.model.LocalizationSettings
import org.wip.plugintoolkit.features.settings.model.SidebarStartMode
import org.wip.plugintoolkit.features.settings.ui.ScalingControl
import org.wip.plugintoolkit.features.settings.ui.SettingNavKey
import org.wip.plugintoolkit.features.settings.utils.SettingText
import org.wip.plugintoolkit.features.settings.utils.SettingsRegistryBuilder
import org.wip.plugintoolkit.features.settings.utils.TimezoneUtils
import org.wip.plugintoolkit.features.settings.utils.bindGroup
import plugintoolkit.composeapp.generated.resources.*

/**
 * Registers application theme, accent color, scaling, animations, and localization settings.
 */
fun SettingsRegistryBuilder.appearanceDefinitions() {
    nav(SettingNavKey.Appearance) {
        // ── Appearance ───────────────────────────────────────────────
        section(Res.string.setting_appearance) {
            bindGroup(AppSettings::appearance, { copy(appearance = it) }) {
                dropdown(
                    AppearanceSettings::theme,
                    Res.string.setting_theme,
                    Icons.Default.Brightness6,
                    options = AppTheme.entries,
                    subtitle = SettingText.Raw("Choose between System, Light, Dark or Amoled"),
                    labelProvider = { it.name }
                ) { copy(theme = it) }

                switch(
                    AppearanceSettings::followSystemAccent,
                    Res.string.setting_follow_system_accent,
                    Icons.Default.AutoFixHigh,
                    subtitle = SettingText.Resource(Res.string.setting_follow_system_accent_subtitle)
                ) { copy(followSystemAccent = it) }
            }

            SettingCustom(
                id = "appearance.accentColor",
                title = Res.string.setting_accent_color,
                subtitle = SettingText.Raw("Manually select the accent color for the application"),
                icon = Icons.Default.Brightness6,
                enabled = { !it.appearance.followSystemAccent },
                control = { settings, onUpdate ->
                    org.wip.plugintoolkit.features.settings.ui.AccentColorControl(settings, onUpdate)
                }
            )

            bindGroup(AppSettings::appearance, { copy(appearance = it) }) {
                switch(
                    AppearanceSettings::useAccentInTheme,
                    Res.string.setting_use_accent_in_theme,
                    Icons.Default.Palette,
                    subtitle = SettingText.Resource(Res.string.setting_use_accent_in_theme_subtitle)
                ) { copy(useAccentInTheme = it) }

                switch(
                    AppearanceSettings::useCustomTitleBar,
                    Res.string.setting_custom_title_bar,
                    Icons.Default.WebAsset,
                    subtitle = SettingText.Resource(Res.string.setting_custom_title_bar_desc)
                ) { copy(useCustomTitleBar = it) }

                dropdown(
                    AppearanceSettings::sidebarStartMode,
                    Res.string.setting_sidebar_start_mode,
                    Icons.Default.ViewSidebar,
                    options = SidebarStartMode.entries,
                    subtitle = SettingText.Resource(Res.string.setting_sidebar_start_mode_subtitle),
                    labelProvider = { mode ->
                        when (mode) {
                            SidebarStartMode.Expanded -> stringResource(Res.string.sidebar_start_mode_expanded)
                            SidebarStartMode.Collapsed -> stringResource(Res.string.sidebar_start_mode_collapsed)
                            SidebarStartMode.Remember -> stringResource(Res.string.sidebar_start_mode_remember)
                        }
                    }
                ) { copy(sidebarStartMode = it) }
            }

            SettingCustom(
                id = "general.scaling",
                title = Res.string.setting_scaling,
                subtitle = SettingText.Resource(Res.string.setting_scaling_subtitle),
                icon = Icons.Default.AspectRatio,
                control = { settings, onUpdate ->
                    ScalingControl(
                        scaling = settings.general.scaling,
                        onScalingChange = { newScale ->
                            onUpdate(settings.copy(general = settings.general.copy(scaling = newScale)))
                        }
                    )
                }
            )

            bindGroup(AppSettings::general, { copy(general = it) }) {
                switch(
                    GeneralSettings::animationsEnabled,
                    Res.string.setting_animations_enabled,
                    Icons.Default.Animation,
                    subtitle = SettingText.Resource(Res.string.setting_animations_enabled_subtitle)
                ) { copy(animationsEnabled = it) }
            }
        }

        // ── Localization ─────────────────────────────────────────────
        section(Res.string.section_localization) {
            bindGroup(AppSettings::localization, { copy(localization = it) }) {
                switch(
                    LocalizationSettings::useSystemLanguage,
                    Res.string.setting_use_system_language,
                    Icons.Default.Language,
                    subtitle = SettingText.Resource(Res.string.setting_use_system_language_subtitle)
                ) { copy(useSystemLanguage = it) }

                dropdown(
                    LocalizationSettings::language,
                    Res.string.setting_language,
                    Icons.Default.Language,
                    options = AppLanguage.entries,
                    subtitle = SettingText.Raw("Select your preferred language"),
                    enabled = { !it.localization.useSystemLanguage },
                    labelProvider = { it.label }
                ) { copy(language = it) }

                switch(
                    LocalizationSettings::useSystemTimezone,
                    SettingText.Raw("Use System Timezone"),
                    Icons.Default.Map,
                    subtitle = SettingText.Raw("Automatically detect your local timezone from the system")
                ) { copy(useSystemTimezone = it) }

                dropdown(
                    LocalizationSettings::timezone,
                    Res.string.setting_timezone,
                    Icons.Default.Schedule,
                    options = TimezoneUtils.getAvailableZoneIds(),
                    subtitle = SettingText.Raw("Manual timezone selection"),
                    enabled = { !it.localization.useSystemTimezone },
                    labelProvider = { it }
                ) { copy(timezone = it) }
            }
        }
    }
}
