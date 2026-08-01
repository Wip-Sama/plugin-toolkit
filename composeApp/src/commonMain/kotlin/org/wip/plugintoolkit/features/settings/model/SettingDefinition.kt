package org.wip.plugintoolkit.features.settings.model

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import org.wip.plugintoolkit.features.settings.ui.SettingNavKey
import org.wip.plugintoolkit.features.settings.utils.SettingText

/**
 * Sealed base class representing a single setting item definition.
 * Each subclass models a distinct UI control type (switch, dropdown, slider, numeric input, action button, or custom component).
 *
 * Setting definitions are registered in [org.wip.plugintoolkit.features.settings.utils.SettingsRegistry]
 * and rendered dynamically by [org.wip.plugintoolkit.features.settings.ui.AutoSettingsView].
 *
 * To add a new setting type:
 * 1. Add a new data class subclassing [SettingDefinition].
 * 2. Add a rendering branch in `RenderSettingDefinition` inside `AutoSettingsView.kt`.
 */
sealed class SettingDefinition {
    /** Unique dot-separated identifier for the setting (e.g., "appearance.theme"). */
    abstract val id: String

    /** Display title for the setting item. */
    abstract val title: SettingText

    /** Optional subtitle or description detailing the setting's effect. */
    abstract val subtitle: SettingText?

    /** Icon graphic displayed next to the setting title. */
    abstract val icon: ImageVector

    /** Title of the UI section under which this setting is grouped. */
    abstract val sectionTitle: SettingText

    /** Navigation page location where this setting is rendered. */
    abstract val navKey: SettingNavKey

    /** Predicate evaluating whether this setting control should be interactable based on current [AppSettings]. */
    abstract val enabled: (AppSettings) -> Boolean

    /**
     * Represents a binary boolean toggle switch setting.
     *
     * @property getValue Lambda extracting the current boolean state from [AppSettings].
     * @property setValue Lambda returning an updated [AppSettings] with the new boolean value applied.
     */
    data class SwitchSetting(
        override val id: String,
        override val title: SettingText,
        override val subtitle: SettingText?,
        override val icon: ImageVector,
        override val sectionTitle: SettingText,
        override val navKey: SettingNavKey,
        override val enabled: (AppSettings) -> Boolean = { true },
        val getValue: (AppSettings) -> Boolean,
        val setValue: (AppSettings, Boolean) -> AppSettings
    ) : SettingDefinition()

    /**
     * Represents a single-choice dropdown selection setting.
     *
     * @param T The option value type (typically an enum or string).
     * @property options Available selectable options list.
     * @property getValue Lambda extracting the current option value from [AppSettings].
     * @property setValue Lambda returning an updated [AppSettings] with the newly selected option applied.
     * @property labelProvider Composable lambda rendering the display label for an option value.
     */
    data class DropdownSetting<T>(
        override val id: String,
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

    /**
     * Represents a continuous or discrete floating-point slider setting.
     *
     * @property getValue Lambda extracting the current float value from [AppSettings].
     * @property setValue Lambda returning an updated [AppSettings] with the new float value applied.
     * @property valueRange Range of valid float values for the slider.
     * @property steps Number of discrete snap steps within [valueRange] (0 for continuous).
     * @property subtitleProvider Optional dynamic subtitle provider evaluated at render time (e.g. "75%").
     */
    data class SliderSetting(
        override val id: String,
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
        val subtitleProvider: ((AppSettings) -> String)? = null
    ) : SettingDefinition()

    /**
     * Represents an integer numeric stepper/input setting.
     *
     * @property getValue Lambda extracting the current integer value from [AppSettings].
     * @property setValue Lambda returning an updated [AppSettings] with the new integer value applied.
     * @property valueRange Allowed range of integer values.
     */
    data class NumericSetting(
        override val id: String,
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

    /**
     * Represents a stateless clickable action row (e.g., "Open Log Folder").
     *
     * @property onClick Action callback executed when the row is clicked.
     */
    data class ActionSetting(
        override val id: String,
        override val title: SettingText,
        override val subtitle: SettingText?,
        override val icon: ImageVector,
        override val sectionTitle: SettingText,
        override val navKey: SettingNavKey,
        override val enabled: (AppSettings) -> Boolean = { true },
        val onClick: () -> Unit
    ) : SettingDefinition()

    /**
     * Represents a fully custom Composable control for non-standard setting inputs.
     *
     * @property onClick Optional click handler for the row container.
     * @property control Composable content lambda receiving current [AppSettings] and an update callback.
     */
    data class CustomSetting(
        override val id: String,
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
