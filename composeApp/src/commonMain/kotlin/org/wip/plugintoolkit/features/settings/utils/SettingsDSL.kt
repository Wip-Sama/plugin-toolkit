package org.wip.plugintoolkit.features.settings.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.StringResource
import org.wip.plugintoolkit.features.settings.model.AppSettings
import org.wip.plugintoolkit.features.settings.model.SettingDefinition
import org.wip.plugintoolkit.features.settings.ui.SettingNavKey
import kotlin.reflect.KProperty1

/**
 * Main builder for constructing a [SettingsRegistry] using type-safe Kotlin DSL.
 */
class SettingsRegistryBuilder {
    val definitions = mutableListOf<SettingDefinition>()
    val sideEffects = mutableMapOf<String, suspend (AppSettings) -> Unit>()

    /**
     * Declares a navigation page scope in the settings registry.
     *
     * @param key Navigation key representing the target settings page.
     * @param block Configuration builder scoped to the navigation key.
     */
    fun nav(key: SettingNavKey, block: NavBuilder.() -> Unit) {
        NavBuilder(key, this).apply(block)
    }

    /**
     * Legacy or manual registration for custom setting definitions.
     *
     * @param definition A fully initialized [SettingDefinition].
     */
    fun add(definition: SettingDefinition) {
        definitions.add(definition)
    }
}

/**
 * Builder for declaring sections under a specific navigation key.
 *
 * @property navKey Target settings navigation key.
 * @property registryBuilder Parent registry builder instance.
 */
class NavBuilder(val navKey: SettingNavKey, val registryBuilder: SettingsRegistryBuilder) {
    /**
     * Declares a section header with [SettingText].
     */
    fun section(title: SettingText, block: SectionBuilder.() -> Unit) {
        SectionBuilder(navKey, title, registryBuilder).apply(block)
    }

    /**
     * Declares a section header using Compose [StringResource].
     */
    fun section(title: StringResource, block: SectionBuilder.() -> Unit) {
        SectionBuilder(navKey, SettingText.Resource(title), registryBuilder).apply(block)
    }
}

/**
 * Scoped builder for defining settings within a single section.
 *
 * @property navKey Navigation key for the section's parent page.
 * @property sectionTitle Section header title.
 * @property registryBuilder Parent registry builder instance.
 */
class SectionBuilder(
    val navKey: SettingNavKey,
    val sectionTitle: SettingText,
    val registryBuilder: SettingsRegistryBuilder
) {
    internal fun generateId(p1: KProperty1<*, *>, p2: KProperty1<*, *>? = null, p3: KProperty1<*, *>? = null): String {
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
        sideEffect: (suspend (AppSettings) -> Unit)? = null,
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

    fun <T> SettingSwitch(
        p1: KProperty1<AppSettings, T>,
        p2: KProperty1<T, Boolean>,
        title: StringResource,
        icon: ImageVector,
        subtitle: SettingText? = null,
        enabled: (AppSettings) -> Boolean = { true },
        sideEffect: (suspend (AppSettings) -> Unit)? = null,
        setValue: (AppSettings, Boolean) -> AppSettings
    ) = SettingSwitch(p1, p2, SettingText.Resource(title), icon, subtitle, enabled, sideEffect, setValue)

    fun <T, V> SettingSwitch(
        p1: KProperty1<AppSettings, T>,
        p2: KProperty1<T, V>,
        p3: KProperty1<V, Boolean>,
        title: SettingText,
        icon: ImageVector,
        subtitle: SettingText? = null,
        enabled: (AppSettings) -> Boolean = { true },
        sideEffect: (suspend (AppSettings) -> Unit)? = null,
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
        sideEffect: (suspend (AppSettings) -> Unit)? = null,
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

    fun <T, V> SettingDropdown(
        p1: KProperty1<AppSettings, T>,
        p2: KProperty1<T, V>,
        title: StringResource,
        icon: ImageVector,
        options: List<V>,
        subtitle: SettingText? = null,
        enabled: (AppSettings) -> Boolean = { true },
        sideEffect: (suspend (AppSettings) -> Unit)? = null,
        labelProvider: @Composable (V) -> String,
        setValue: (AppSettings, V) -> AppSettings
    ) = SettingDropdown(p1, p2, SettingText.Resource(title), icon, options, subtitle, enabled, sideEffect, labelProvider, setValue)

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
        sideEffect: (suspend (AppSettings) -> Unit)? = null,
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

    fun <T> SettingSlider(
        p1: KProperty1<AppSettings, T>,
        p2: KProperty1<T, Float>,
        title: StringResource,
        icon: ImageVector,
        valueRange: ClosedFloatingPointRange<Float>,
        steps: Int = 0,
        subtitle: SettingText? = null,
        enabled: (AppSettings) -> Boolean = { true },
        sideEffect: (suspend (AppSettings) -> Unit)? = null,
        subtitleProvider: ((AppSettings) -> String)? = null,
        setValue: (AppSettings, Float) -> AppSettings
    ) = SettingSlider(p1, p2, SettingText.Resource(title), icon, valueRange, steps, subtitle, enabled, sideEffect, subtitleProvider, setValue)

    // --- Numeric ---

    fun <T> SettingNumeric(
        p1: KProperty1<AppSettings, T>,
        p2: KProperty1<T, Int>,
        title: SettingText,
        icon: ImageVector,
        valueRange: IntRange,
        subtitle: SettingText? = null,
        enabled: (AppSettings) -> Boolean = { true },
        sideEffect: (suspend (AppSettings) -> Unit)? = null,
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

    fun <T> SettingNumeric(
        p1: KProperty1<AppSettings, T>,
        p2: KProperty1<T, Int>,
        title: StringResource,
        icon: ImageVector,
        valueRange: IntRange,
        subtitle: SettingText? = null,
        enabled: (AppSettings) -> Boolean = { true },
        sideEffect: (suspend (AppSettings) -> Unit)? = null,
        setValue: (AppSettings, Int) -> AppSettings
    ) = SettingNumeric(p1, p2, SettingText.Resource(title), icon, valueRange, subtitle, enabled, sideEffect, setValue)

    fun <T, V> SettingNumeric(
        p1: KProperty1<AppSettings, T>,
        p2: KProperty1<T, V>,
        p3: KProperty1<V, Int>,
        title: SettingText,
        icon: ImageVector,
        valueRange: IntRange,
        subtitle: SettingText? = null,
        enabled: (AppSettings) -> Boolean = { true },
        sideEffect: (suspend (AppSettings) -> Unit)? = null,
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

    fun <T, V> SettingNumeric(
        p1: KProperty1<AppSettings, T>,
        p2: KProperty1<T, V>,
        p3: KProperty1<V, Int>,
        title: StringResource,
        icon: ImageVector,
        valueRange: IntRange,
        subtitle: SettingText? = null,
        enabled: (AppSettings) -> Boolean = { true },
        sideEffect: (suspend (AppSettings) -> Unit)? = null,
        setValue: (AppSettings, Int) -> AppSettings
    ) = SettingNumeric(p1, p2, p3, SettingText.Resource(title), icon, valueRange, subtitle, enabled, sideEffect, setValue)

    // --- Long Numeric ---

    fun <T> SettingLongNumeric(
        p1: KProperty1<AppSettings, T>,
        p2: KProperty1<T, Long>,
        title: SettingText,
        icon: ImageVector,
        valueRange: IntRange,
        subtitle: SettingText? = null,
        enabled: (AppSettings) -> Boolean = { true },
        sideEffect: (suspend (AppSettings) -> Unit)? = null,
        setValue: (AppSettings, Long) -> AppSettings
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
            getValue = { settings -> p2.get(p1.get(settings)).toInt() },
            setValue = { s, v -> setValue(s, v.toLong()) },
            valueRange = valueRange
        )
        registryBuilder.definitions.add(definition)
        sideEffect?.let { registryBuilder.sideEffects[id] = it }
    }

    fun <T> SettingLongNumeric(
        p1: KProperty1<AppSettings, T>,
        p2: KProperty1<T, Long>,
        title: StringResource,
        icon: ImageVector,
        valueRange: IntRange,
        subtitle: SettingText? = null,
        enabled: (AppSettings) -> Boolean = { true },
        sideEffect: (suspend (AppSettings) -> Unit)? = null,
        setValue: (AppSettings, Long) -> AppSettings
    ) = SettingLongNumeric(p1, p2, SettingText.Resource(title), icon, valueRange, subtitle, enabled, sideEffect, setValue)

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

    fun SettingAction(
        id: String,
        title: StringResource,
        icon: ImageVector,
        subtitle: SettingText? = null,
        enabled: (AppSettings) -> Boolean = { true },
        onClick: () -> Unit
    ) = SettingAction(id, SettingText.Resource(title), icon, subtitle, enabled, onClick)

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

    fun SettingCustom(
        id: String,
        title: StringResource,
        icon: ImageVector,
        subtitle: SettingText? = null,
        enabled: (AppSettings) -> Boolean = { true },
        onClick: (() -> Unit)? = null,
        control: @Composable (settings: AppSettings, onUpdate: (AppSettings) -> Unit) -> Unit
    ) = SettingCustom(id, SettingText.Resource(title), icon, subtitle, enabled, onClick, control)
}

/**
 * Scoped builder for defining settings belonging to a specific [AppSettings] sub-group [G].
 * Reduces boilerplate by automatically inferring state accessors and updates.
 *
 * @param G Settings group type (e.g., [org.wip.plugintoolkit.features.settings.model.LoggingSettings]).
 * @property groupProp Property reference from [AppSettings] to group [G].
 * @property updateGroup Function that produces updated [AppSettings] given a modified group [G].
 * @property sectionBuilder Parent section builder instance.
 */
class GroupSectionBuilder<G>(
    val groupProp: KProperty1<AppSettings, G>,
    val updateGroup: AppSettings.(G) -> AppSettings,
    val sectionBuilder: SectionBuilder
) {
    /**
     * Declares a boolean switch setting for a property in group [G].
     */
    fun switch(
        prop: KProperty1<G, Boolean>,
        title: SettingText,
        icon: ImageVector,
        subtitle: SettingText? = null,
        enabled: (AppSettings) -> Boolean = { true },
        sideEffect: (suspend (AppSettings) -> Unit)? = null,
        update: G.(Boolean) -> G
    ) {
        sectionBuilder.SettingSwitch(
            p1 = groupProp,
            p2 = prop,
            title = title,
            icon = icon,
            subtitle = subtitle,
            enabled = enabled,
            sideEffect = sideEffect,
            setValue = { s, v ->
                val group = groupProp.get(s)
                val updatedGroup = group.update(v)
                s.updateGroup(updatedGroup)
            }
        )
    }

    fun switch(
        prop: KProperty1<G, Boolean>,
        title: StringResource,
        icon: ImageVector,
        subtitle: SettingText? = null,
        enabled: (AppSettings) -> Boolean = { true },
        sideEffect: (suspend (AppSettings) -> Unit)? = null,
        update: G.(Boolean) -> G
    ) = switch(prop, SettingText.Resource(title), icon, subtitle, enabled, sideEffect, update)

    /**
     * Declares a dropdown menu setting for an options property in group [G].
     */
    fun <V> dropdown(
        prop: KProperty1<G, V>,
        title: SettingText,
        icon: ImageVector,
        options: List<V>,
        subtitle: SettingText? = null,
        enabled: (AppSettings) -> Boolean = { true },
        sideEffect: (suspend (AppSettings) -> Unit)? = null,
        labelProvider: @Composable (V) -> String = { it.toString() },
        update: G.(V) -> G
    ) {
        sectionBuilder.SettingDropdown(
            p1 = groupProp,
            p2 = prop,
            title = title,
            icon = icon,
            options = options,
            subtitle = subtitle,
            enabled = enabled,
            sideEffect = sideEffect,
            labelProvider = labelProvider,
            setValue = { s, v ->
                val group = groupProp.get(s)
                val updatedGroup = group.update(v)
                s.updateGroup(updatedGroup)
            }
        )
    }

    fun <V> dropdown(
        prop: KProperty1<G, V>,
        title: StringResource,
        icon: ImageVector,
        options: List<V>,
        subtitle: SettingText? = null,
        enabled: (AppSettings) -> Boolean = { true },
        sideEffect: (suspend (AppSettings) -> Unit)? = null,
        labelProvider: @Composable (V) -> String = { it.toString() },
        update: G.(V) -> G
    ) = dropdown(prop, SettingText.Resource(title), icon, options, subtitle, enabled, sideEffect, labelProvider, update)

    /**
     * Declares an integer numeric input setting for a property in group [G].
     */
    fun numeric(
        prop: KProperty1<G, Int>,
        title: SettingText,
        icon: ImageVector,
        range: IntRange,
        subtitle: SettingText? = null,
        enabled: (AppSettings) -> Boolean = { true },
        sideEffect: (suspend (AppSettings) -> Unit)? = null,
        update: G.(Int) -> G
    ) {
        sectionBuilder.SettingNumeric(
            p1 = groupProp,
            p2 = prop,
            title = title,
            icon = icon,
            valueRange = range,
            subtitle = subtitle,
            enabled = enabled,
            sideEffect = sideEffect,
            setValue = { s, v ->
                val group = groupProp.get(s)
                val updatedGroup = group.update(v)
                s.updateGroup(updatedGroup)
            }
        )
    }

    fun numeric(
        prop: KProperty1<G, Int>,
        title: StringResource,
        icon: ImageVector,
        range: IntRange,
        subtitle: SettingText? = null,
        enabled: (AppSettings) -> Boolean = { true },
        sideEffect: (suspend (AppSettings) -> Unit)? = null,
        update: G.(Int) -> G
    ) = numeric(prop, SettingText.Resource(title), icon, range, subtitle, enabled, sideEffect, update)

    /**
     * Declares a long numeric setting (bridged to Int input) for a property in group [G].
     */
    fun longNumeric(
        prop: KProperty1<G, Long>,
        title: SettingText,
        icon: ImageVector,
        range: IntRange,
        subtitle: SettingText? = null,
        enabled: (AppSettings) -> Boolean = { true },
        sideEffect: (suspend (AppSettings) -> Unit)? = null,
        update: G.(Long) -> G
    ) {
        sectionBuilder.SettingLongNumeric(
            p1 = groupProp,
            p2 = prop,
            title = title,
            icon = icon,
            valueRange = range,
            subtitle = subtitle,
            enabled = enabled,
            sideEffect = sideEffect,
            setValue = { s, v ->
                val group = groupProp.get(s)
                val updatedGroup = group.update(v)
                s.updateGroup(updatedGroup)
            }
        )
    }

    fun longNumeric(
        prop: KProperty1<G, Long>,
        title: StringResource,
        icon: ImageVector,
        range: IntRange,
        subtitle: SettingText? = null,
        enabled: (AppSettings) -> Boolean = { true },
        sideEffect: (suspend (AppSettings) -> Unit)? = null,
        update: G.(Long) -> G
    ) = longNumeric(prop, SettingText.Resource(title), icon, range, subtitle, enabled, sideEffect, update)

    /**
     * Declares a float slider setting for a property in group [G].
     */
    fun slider(
        prop: KProperty1<G, Float>,
        title: SettingText,
        icon: ImageVector,
        range: ClosedFloatingPointRange<Float>,
        steps: Int = 0,
        subtitle: SettingText? = null,
        enabled: (AppSettings) -> Boolean = { true },
        sideEffect: (suspend (AppSettings) -> Unit)? = null,
        subtitleProvider: ((AppSettings) -> String)? = null,
        update: G.(Float) -> G
    ) {
        sectionBuilder.SettingSlider(
            p1 = groupProp,
            p2 = prop,
            title = title,
            icon = icon,
            valueRange = range,
            steps = steps,
            subtitle = subtitle,
            enabled = enabled,
            sideEffect = sideEffect,
            subtitleProvider = subtitleProvider,
            setValue = { s, v ->
                val group = groupProp.get(s)
                val updatedGroup = group.update(v)
                s.updateGroup(updatedGroup)
            }
        )
    }

    fun slider(
        prop: KProperty1<G, Float>,
        title: StringResource,
        icon: ImageVector,
        range: ClosedFloatingPointRange<Float>,
        steps: Int = 0,
        subtitle: SettingText? = null,
        enabled: (AppSettings) -> Boolean = { true },
        sideEffect: (suspend (AppSettings) -> Unit)? = null,
        subtitleProvider: ((AppSettings) -> String)? = null,
        update: G.(Float) -> G
    ) = slider(prop, SettingText.Resource(title), icon, range, steps, subtitle, enabled, sideEffect, subtitleProvider, update)
}

/**
 * Binds setting definitions under a single [AppSettings] group property to simplify registration.
 *
 * @param groupProp Property reference from [AppSettings] to group [G].
 * @param updateGroup Lambda returning updated [AppSettings] given a modified group [G].
 * @param block Builder scope for group [G] settings.
 */
fun <G> SectionBuilder.bindGroup(
    groupProp: KProperty1<AppSettings, G>,
    updateGroup: AppSettings.(G) -> AppSettings,
    block: GroupSectionBuilder<G>.() -> Unit
) {
    GroupSectionBuilder(groupProp, updateGroup, this).apply(block)
}

/**
 * Extension for [SettingsRegistry.Companion] to construct a registry using DSL.
 */
fun SettingsRegistry.Companion.build(block: SettingsRegistryBuilder.() -> Unit): SettingsRegistry {
    val builder = SettingsRegistryBuilder().apply(block)
    return SettingsRegistry(
        initialDefinitions = builder.definitions,
        initialSideEffects = builder.sideEffects
    )
}
