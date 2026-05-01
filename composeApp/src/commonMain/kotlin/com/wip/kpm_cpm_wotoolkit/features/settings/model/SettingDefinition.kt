package com.wip.kpm_cpm_wotoolkit.features.settings.model

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.wip.kpm_cpm_wotoolkit.features.settings.ui.SettingNavKey
import com.wip.kpm_cpm_wotoolkit.features.settings.utils.SettingText

/**
 * Base definition for a single setting item.
 * Each subclass represents a different control type (switch, dropdown, slider, etc.).
 *
 * To add a new setting type in the future:
 *   1. Add a new data class extending SettingDefinition
 *   2. Add a rendering branch in AutoSettingsView
 */
sealed class SettingDefinition {
    /** Display title for the setting */
    abstract val title: SettingText

    /** Optional subtitle / description */
    abstract val subtitle: SettingText?

    /** Icon shown next to the setting */
    abstract val icon: ImageVector

    /** Section header this setting belongs to (for grouping) */
    abstract val sectionTitle: SettingText

    /** Which settings page this definition belongs to */
    abstract val navKey: SettingNavKey

    /** Whether this setting is currently enabled, evaluated against the current AppSettings */
    abstract val enabled: (AppSettings) -> Boolean

    // ── Boolean toggle ───────────────────────────────────────────────────
    data class SwitchSetting(
        override val title: SettingText,
        override val subtitle: SettingText?,
        override val icon: ImageVector,
        override val sectionTitle: SettingText,
        override val navKey: SettingNavKey,
        override val enabled: (AppSettings) -> Boolean = { true },
        val getValue: (AppSettings) -> Boolean,
        val setValue: (AppSettings, Boolean) -> AppSettings
    ) : SettingDefinition()

    // ── Dropdown selection ───────────────────────────────────────────────
    data class DropdownSetting<T>(
        override val title: SettingText,
        override val subtitle: SettingText?,
        override val icon: ImageVector,
        override val sectionTitle: SettingText,
        override val navKey: SettingNavKey,
        override val enabled: (AppSettings) -> Boolean = { true },
        val options: List<T>,
        val getValue: (AppSettings) -> T,
        val setValue: (AppSettings, T) -> AppSettings,
        val labelProvider: @Composable (T) -> String
    ) : SettingDefinition()

    // ── Float slider ─────────────────────────────────────────────────────
    data class SliderSetting(
        override val title: SettingText,
        override val subtitle: SettingText?,
        override val icon: ImageVector,
        override val sectionTitle: SettingText,
        override val navKey: SettingNavKey,
        override val enabled: (AppSettings) -> Boolean = { true },
        val getValue: (AppSettings) -> Float,
        val setValue: (AppSettings, Float) -> AppSettings,
        val valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
        val steps: Int = 0,
        /** Optional dynamic subtitle provider (e.g. for "75%"), evaluated at render time */
        val subtitleProvider: ((AppSettings) -> String)? = null
    ) : SettingDefinition()

    // ── Integer numeric input ────────────────────────────────────────────
    data class NumericSetting(
        override val title: SettingText,
        override val subtitle: SettingText?,
        override val icon: ImageVector,
        override val sectionTitle: SettingText,
        override val navKey: SettingNavKey,
        override val enabled: (AppSettings) -> Boolean = { true },
        val getValue: (AppSettings) -> Int,
        val setValue: (AppSettings, Int) -> AppSettings,
        val valueRange: IntRange = 1..1000
    ) : SettingDefinition()

    // ── Clickable action (no persistent state) ───────────────────────────
    data class ActionSetting(
        override val title: SettingText,
        override val subtitle: SettingText?,
        override val icon: ImageVector,
        override val sectionTitle: SettingText,
        override val navKey: SettingNavKey,
        override val enabled: (AppSettings) -> Boolean = { true },
        val onClick: () -> Unit
    ) : SettingDefinition()

    // ── Escape-hatch: fully custom composable control ────────────────────
    /**
     * Use this for settings that don't fit into any of the standard types.
     * The [content] lambda receives the current AppSettings and an update callback.
     */
    data class CustomSetting(
        override val title: SettingText,
        override val subtitle: SettingText?,
        override val icon: ImageVector,
        override val sectionTitle: SettingText,
        override val navKey: SettingNavKey,
        override val enabled: (AppSettings) -> Boolean = { true },
        val onClick: (() -> Unit)? = null,
        val control: @Composable (settings: AppSettings, update: (AppSettings) -> Unit) -> Unit
    ) : SettingDefinition()
}
