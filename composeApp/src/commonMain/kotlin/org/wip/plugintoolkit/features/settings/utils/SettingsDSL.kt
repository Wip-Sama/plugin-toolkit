package org.wip.plugintoolkit.features.settings.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import org.wip.plugintoolkit.features.settings.model.AppSettings
import org.wip.plugintoolkit.features.settings.model.SettingDefinition
import org.wip.plugintoolkit.features.settings.ui.SettingNavKey
import kotlin.reflect.KProperty1

/**
 * DSL for building [SettingsRegistry] definitions.
 */
class SettingsRegistryBuilder {
    val definitions = mutableListOf<SettingDefinition>()
    val sideEffects = mutableMapOf<String, (AppSettings) -> Unit>()

    fun nav(key: SettingNavKey, block: NavBuilder.() -> Unit) {
        NavBuilder(key, this).apply(block)
    }

    /**
     * Legacy/Manual registration for definitions that don't fit the DSL.
     */
    fun add(definition: SettingDefinition) {
        definitions.add(definition)
    }
}

class NavBuilder(val navKey: SettingNavKey, val registryBuilder: SettingsRegistryBuilder) {
    fun section(title: SettingText, block: SectionBuilder.() -> Unit) {
        SectionBuilder(navKey, title, registryBuilder).apply(block)
    }
}

class SectionBuilder(
    val navKey: SettingNavKey,
    val sectionTitle: SettingText,
    val registryBuilder: SettingsRegistryBuilder
) {
    // --- Helpers ---

    private fun generateId(p1: KProperty1<*, *>, p2: KProperty1<*, *>? = null, p3: KProperty1<*, *>? = null): String {
        return buildString {
            append(p1.name)
            p2?.let { append("."); append(it.name) }
            p3?.let { append("."); append(it.name) }
        }
    }

    // --- Switch ---

    fun <T> SettingSwitch(
        p1: KProperty1<AppSettings, T>,
        p2: KProperty1<T, Boolean>,
        title: SettingText,
        icon: ImageVector,
        subtitle: SettingText? = null,
        enabled: (AppSettings) -> Boolean = { true },
        sideEffect: ((AppSettings) -> Unit)? = null,
        setValue: (AppSettings, Boolean) -> AppSettings
    ) {
        val id = generateId(p1, p2)
        val definition = SettingDefinition.SwitchSetting(
            id = id,
            title = title,
            subtitle = subtitle,
            icon = icon,
            sectionTitle = sectionTitle,
            navKey = navKey,
            enabled = enabled,
            getValue = { settings -> p2.get(p1.get(settings)) },
            setValue = setValue
        )
        registryBuilder.definitions.add(definition)
        sideEffect?.let { registryBuilder.sideEffects[id] = it }
    }

    fun <T, V> SettingSwitch(
        p1: KProperty1<AppSettings, T>,
        p2: KProperty1<T, V>,
        p3: KProperty1<V, Boolean>,
        title: SettingText,
        icon: ImageVector,
        subtitle: SettingText? = null,
        enabled: (AppSettings) -> Boolean = { true },
        sideEffect: ((AppSettings) -> Unit)? = null,
        setValue: (AppSettings, Boolean) -> AppSettings
    ) {
        val id = generateId(p1, p2, p3)
        val definition = SettingDefinition.SwitchSetting(
            id = id,
            title = title,
            subtitle = subtitle,
            icon = icon,
            sectionTitle = sectionTitle,
            navKey = navKey,
            enabled = enabled,
            getValue = { settings -> p3.get(p2.get(p1.get(settings))) },
            setValue = setValue
        )
        registryBuilder.definitions.add(definition)
        sideEffect?.let { registryBuilder.sideEffects[id] = it }
    }

    // --- Dropdown ---

    fun <T, V> SettingDropdown(
        p1: KProperty1<AppSettings, T>,
        p2: KProperty1<T, V>,
        title: SettingText,
        icon: ImageVector,
        options: List<V>,
        subtitle: SettingText? = null,
        enabled: (AppSettings) -> Boolean = { true },
        sideEffect: ((AppSettings) -> Unit)? = null,
        labelProvider: @Composable (V) -> String,
        setValue: (AppSettings, V) -> AppSettings
    ) {
        val id = generateId(p1, p2)
        val definition = SettingDefinition.DropdownSetting(
            id = id,
            title = title,
            subtitle = subtitle,
            icon = icon,
            sectionTitle = sectionTitle,
            navKey = navKey,
            enabled = enabled,
            options = options,
            getValue = { settings -> p2.get(p1.get(settings)) },
            setValue = setValue,
            labelProvider = labelProvider
        )
        registryBuilder.definitions.add(definition)
        sideEffect?.let { registryBuilder.sideEffects[id] = it }
    }

    // --- Slider ---

    fun <T> SettingSlider(
        p1: KProperty1<AppSettings, T>,
        p2: KProperty1<T, Float>,
        title: SettingText,
        icon: ImageVector,
        valueRange: ClosedFloatingPointRange<Float>,
        steps: Int = 0,
        subtitle: SettingText? = null,
        enabled: (AppSettings) -> Boolean = { true },
        sideEffect: ((AppSettings) -> Unit)? = null,
        subtitleProvider: ((AppSettings) -> String)? = null,
        setValue: (AppSettings, Float) -> AppSettings
    ) {
        val id = generateId(p1, p2)
        val definition = SettingDefinition.SliderSetting(
            id = id,
            title = title,
            subtitle = subtitle,
            icon = icon,
            sectionTitle = sectionTitle,
            navKey = navKey,
            enabled = enabled,
            getValue = { settings -> p2.get(p1.get(settings)) },
            setValue = setValue,
            valueRange = valueRange,
            steps = steps,
            subtitleProvider = subtitleProvider
        )
        registryBuilder.definitions.add(definition)
        sideEffect?.let { registryBuilder.sideEffects[id] = it }
    }

    // --- Numeric ---

    fun <T> SettingNumeric(
        p1: KProperty1<AppSettings, T>,
        p2: KProperty1<T, Int>,
        title: SettingText,
        icon: ImageVector,
        valueRange: IntRange,
        subtitle: SettingText? = null,
        enabled: (AppSettings) -> Boolean = { true },
        sideEffect: ((AppSettings) -> Unit)? = null,
        setValue: (AppSettings, Int) -> AppSettings
    ) {
        val id = generateId(p1, p2)
        val definition = SettingDefinition.NumericSetting(
            id = id,
            title = title,
            subtitle = subtitle,
            icon = icon,
            sectionTitle = sectionTitle,
            navKey = navKey,
            enabled = enabled,
            getValue = { settings -> p2.get(p1.get(settings)) },
            setValue = setValue,
            valueRange = valueRange
        )
        registryBuilder.definitions.add(definition)
        sideEffect?.let { registryBuilder.sideEffects[id] = it }
    }

    fun <T, V> SettingNumeric(
        p1: KProperty1<AppSettings, T>,
        p2: KProperty1<T, V>,
        p3: KProperty1<V, Int>,
        title: SettingText,
        icon: ImageVector,
        valueRange: IntRange,
        subtitle: SettingText? = null,
        enabled: (AppSettings) -> Boolean = { true },
        sideEffect: ((AppSettings) -> Unit)? = null,
        setValue: (AppSettings, Int) -> AppSettings
    ) {
        val id = generateId(p1, p2, p3)
        val definition = SettingDefinition.NumericSetting(
            id = id,
            title = title,
            subtitle = subtitle,
            icon = icon,
            sectionTitle = sectionTitle,
            navKey = navKey,
            enabled = enabled,
            getValue = { settings -> p3.get(p2.get(p1.get(settings))) },
            setValue = setValue,
            valueRange = valueRange
        )
        registryBuilder.definitions.add(definition)
        sideEffect?.let { registryBuilder.sideEffects[id] = it }
    }

    // --- Action ---

    fun SettingAction(
        id: String,
        title: SettingText,
        icon: ImageVector,
        subtitle: SettingText? = null,
        enabled: (AppSettings) -> Boolean = { true },
        onClick: () -> Unit
    ) {
        val definition = SettingDefinition.ActionSetting(
            id = id,
            title = title,
            subtitle = subtitle,
            icon = icon,
            sectionTitle = sectionTitle,
            navKey = navKey,
            enabled = enabled,
            onClick = onClick
        )
        registryBuilder.definitions.add(definition)
    }

    // --- Custom ---

    fun SettingCustom(
        id: String,
        title: SettingText,
        icon: ImageVector,
        subtitle: SettingText? = null,
        enabled: (AppSettings) -> Boolean = { true },
        onClick: (() -> Unit)? = null,
        control: @Composable (settings: AppSettings, onUpdate: (AppSettings) -> Unit) -> Unit
    ) {
        val definition = SettingDefinition.CustomSetting(
            id = id,
            title = title,
            subtitle = subtitle,
            icon = icon,
            sectionTitle = sectionTitle,
            navKey = navKey,
            enabled = enabled,
            onClick = onClick,
            control = control
        )
        registryBuilder.definitions.add(definition)
    }
}

/**
 * Extension for SettingsRegistry to build from DSL.
 */
fun SettingsRegistry.Companion.build(block: SettingsRegistryBuilder.() -> Unit): SettingsRegistry {
    val builder = SettingsRegistryBuilder().apply(block)
    return SettingsRegistry(
        initialDefinitions = builder.definitions,
        initialSideEffects = builder.sideEffects
    )
}
