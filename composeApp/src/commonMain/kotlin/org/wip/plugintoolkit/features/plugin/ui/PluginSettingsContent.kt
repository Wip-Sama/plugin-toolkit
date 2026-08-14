package org.wip.plugintoolkit.features.plugin.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.ParameterMetadata
import org.wip.plugintoolkit.api.PluginAction
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.api.SettingMetadata
import org.wip.plugintoolkit.core.model.localized
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.plugin.model.resolveCustomSettings
import org.wip.plugintoolkit.features.plugin.utils.SettingsUtils
import org.wip.plugintoolkit.features.plugin.viewmodel.PluginSettingsViewModel
import org.wip.plugintoolkit.shared.components.ToolkitChip
import org.wip.plugintoolkit.shared.components.ToolkitChipStyle
import org.wip.plugintoolkit.shared.components.ToolkitTextField
import org.wip.plugintoolkit.shared.components.plugin.DynamicParameterInput
import org.wip.plugintoolkit.shared.components.settings.SettingsItem
import org.wip.plugintoolkit.shared.components.settings.getGroupedShape
import org.wip.plugintoolkit.shared.components.sidebar.NavigationSidebar
import org.wip.plugintoolkit.shared.components.sidebar.SidebarElement
import org.wip.plugintoolkit.shared.components.sidebar.SidebarSectionData
import org.wip.plugintoolkit.shared.components.tooltip
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.action_cancel
import plugintoolkit.composeapp.generated.resources.action_save
import plugintoolkit.composeapp.generated.resources.plugin_capabilities
import plugintoolkit.composeapp.generated.resources.plugin_settings_actions
import plugintoolkit.composeapp.generated.resources.plugin_settings_by_section
import plugintoolkit.composeapp.generated.resources.plugin_settings_capability
import plugintoolkit.composeapp.generated.resources.plugin_settings_custom
import plugintoolkit.composeapp.generated.resources.plugin_settings_global_defaults
import plugintoolkit.composeapp.generated.resources.plugin_settings_optional
import plugintoolkit.composeapp.generated.resources.plugin_settings_required
import plugintoolkit.composeapp.generated.resources.settings
import plugintoolkit.composeapp.generated.resources.settings_locked_capability
import plugintoolkit.composeapp.generated.resources.settings_no_results
import plugintoolkit.composeapp.generated.resources.settings_search_placeholder

import org.wip.plugintoolkit.shared.components.verticalFadingEdges

internal fun partitionSettings(
    settings: Map<String, SettingMetadata>
): Pair<Map<String, SettingMetadata>, Map<String, SettingMetadata>> =
    settings.filterValues { it.required } to settings.filterValues { !it.required }

@Composable
fun PluginSettingsContent(
    pkg: String,
    scrollToSetting: String? = null,
    onDismiss: (() -> Unit)? = null,
    viewModel: PluginSettingsViewModel = koinInject(parameters = { parametersOf(pkg) }),
    modifier: Modifier = Modifier
) {
    val store by viewModel.store.collectAsState()
    val isBusy by viewModel.isBusy.collectAsState()
    val locks by viewModel.locks.collectAsState()
    val manifest = viewModel.manifest ?: return

    var searchQuery by remember { mutableStateOf("") }
    var searchBySection by remember { mutableStateOf(false) }
    var selectedActionForParams by remember { mutableStateOf<PluginAction?>(null) }

    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val actionsTitle = stringResource(Res.string.plugin_settings_actions)
    val customTitle = stringResource(Res.string.plugin_settings_custom)
    val globalTitle = stringResource(Res.string.plugin_settings_global_defaults)
    val requiredTitle = stringResource(Res.string.plugin_settings_required)
    val optionalTitle = stringResource(Res.string.plugin_settings_optional)

    val capabilityTitles = manifest.capabilities.associate {
        it.name to stringResource(Res.string.plugin_settings_capability, it.name)
    }

    val actions = if (searchBySection) {
        if (searchQuery.isBlank() || actionsTitle.contains(searchQuery, ignoreCase = true)) manifest.actions else emptyList()
    } else {
        manifest.actions.filter {
            it.name.contains(searchQuery, ignoreCase = true) || it.description.contains(searchQuery, ignoreCase = true)
        }
    }

    val customSettings = if (searchBySection) {
        if (searchQuery.isBlank() || customTitle.contains(searchQuery, ignoreCase = true)) manifest.settings ?: emptyMap() else emptyMap()
    } else {
        manifest.settings?.filter { (key, meta) ->
            key.contains(searchQuery, ignoreCase = true) || meta.description.contains(searchQuery, ignoreCase = true)
        } ?: emptyMap()
    }

    val globalParams = if (searchBySection) {
        if (searchQuery.isBlank() || globalTitle.contains(searchQuery, ignoreCase = true)) manifest.defaultParameters ?: emptyMap() else emptyMap()
    } else {
        manifest.defaultParameters?.filter { (key, meta) ->
            key.contains(searchQuery, ignoreCase = true) || meta.description.contains(searchQuery, ignoreCase = true)
        } ?: emptyMap()
    }

    val capabilities = manifest.capabilities.mapNotNull { capability ->
        if (searchBySection) {
            val capTitle = capabilityTitles[capability.name] ?: capability.name
            if (searchQuery.isBlank() || capTitle.contains(searchQuery, ignoreCase = true)) capability else null
        } else {
            val filteredParams = capability.parameters?.filter { (key, meta) ->
                key.contains(searchQuery, ignoreCase = true) || meta.description.contains(searchQuery, ignoreCase = true)
            }
            if (!filteredParams.isNullOrEmpty()) {
                capability.copy(parameters = filteredParams)
            } else null
        }
    }

    val hasActions = actions.isNotEmpty()
    val hasCustomSettings = customSettings.isNotEmpty()
    val hasGlobalParams = globalParams.isNotEmpty()
    val hasCapabilities = capabilities.isNotEmpty()
    val hasAnyResults = hasActions || hasCustomSettings || hasGlobalParams || hasCapabilities
    val (requiredSettings, optionalSettings) = remember(customSettings) { partitionSettings(customSettings) }
    val customSettingRequesters = remember(customSettings.keys) {
        customSettings.keys.associateWith { BringIntoViewRequester() }
    }

    val lockedEnumOptions = remember(manifest) {
        val result = mutableMapOf<String, MutableList<String>>()
        fun scanType(type: DataType) {
            when (type) {
                is DataType.Enum -> {
                    type.optionRequirements.forEach { (option, requiredSettings) ->
                        requiredSettings.forEach { req ->
                            result.getOrPut(req) { mutableListOf() }.add(option)
                        }
                    }
                }
                is DataType.Array -> scanType(type.items)
                is DataType.Object -> type.properties.values.forEach { scanType(it) }
                is DataType.MapType -> scanType(type.valueType)
                else -> {}
            }
        }

        manifest.capabilities.forEach { cap ->
            cap.parameters?.values?.forEach { p -> scanType(p.type) }
        }
        manifest.defaultParameters?.values?.forEach { p -> scanType(p.type) }
        result
    }

    // Map section IDs to their starting LazyColumn item index
    val sectionIndices = remember(
        hasActions,
        hasCustomSettings,
        hasGlobalParams,
        capabilities,
        actionsTitle,
        customTitle,
        globalTitle,
        capabilityTitles
    ) {
        val map = mutableMapOf<String, Int>()
        var currentIndex = 0
        if (hasActions) map["section_actions"] = currentIndex++
        if (hasCustomSettings) map["section_custom"] = currentIndex++
        if (hasGlobalParams) map["section_global"] = currentIndex++
        capabilities.forEach { capability ->
            map["cap_${capability.name}"] = currentIndex++
        }
        map
    }

    // Dynamic sidebar elements
    val settingsSidebarElements = remember(hasActions, hasCustomSettings, hasGlobalParams, actionsTitle, customTitle, globalTitle) {
        buildList {
            if (hasActions) add(SidebarElement("section_actions", Icons.Default.PlayArrow, actionsTitle.localized))
            if (hasCustomSettings) add(SidebarElement("section_custom", Icons.Default.Settings, customTitle.localized))
            if (hasGlobalParams) add(SidebarElement("section_global", Icons.Default.Settings, globalTitle.localized))
        }
    }

    val capabilitiesSidebarElements = remember(capabilities) {
        capabilities.map { capability ->
            SidebarElement("cap_${capability.name}", Icons.Default.Bolt, capability.name.localized)
        }
    }

    val firstVisibleIndex by remember { derivedStateOf { lazyListState.firstVisibleItemIndex } }
    val activeSidebarKey = remember(firstVisibleIndex, sectionIndices) {
        sectionIndices.entries
            .filter { it.value <= firstVisibleIndex }
            .maxByOrNull { it.value }?.key ?: sectionIndices.keys.firstOrNull() ?: ""
    }

    // Auto-scroll to requested setting or section
    LaunchedEffect(scrollToSetting, sectionIndices, customSettings) {
        if (scrollToSetting != null) {
            val isCustomSetting = customSettings.containsKey(scrollToSetting)
            val targetKey = if (isCustomSetting) {
                "section_custom"
            } else if (capabilities.any { it.parameters?.containsKey(scrollToSetting) == true }) {
                val cap = capabilities.first { it.parameters?.containsKey(scrollToSetting) == true }
                "cap_${cap.name}"
            } else if (globalParams.containsKey(scrollToSetting)) {
                "section_global"
            } else {
                sectionIndices.keys.firstOrNull()
            }

            val targetIndex = targetKey?.let { sectionIndices[it] }
            if (targetIndex != null) {
                lazyListState.animateScrollToItem(targetIndex)
                if (isCustomSetting) {
                    withFrameNanos { }
                    customSettingRequesters[scrollToSetting]?.bringIntoView()
                }
            }
        }
    }

    val topFadeLength = if (lazyListState.canScrollBackward) ToolkitTheme.spacing.large else ToolkitTheme.spacing.none
    val bottomFadeLength = if (lazyListState.canScrollForward) ToolkitTheme.spacing.large else ToolkitTheme.spacing.none

    // 1. Macro-Layout Topology: Split View
    Row(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Left Navigation Sidebar
        NavigationSidebar(
            title = "".localized,
            bodySections = listOf(
                SidebarSectionData(
                    title = Res.string.settings.localized,
                    elements = settingsSidebarElements
                ),
                SidebarSectionData(
                    title = Res.string.plugin_capabilities.localized,
                    elements = capabilitiesSidebarElements
                )
            ),
            currentScreen = activeSidebarKey,
            onScreenSelected = { key ->
                val targetIndex = sectionIndices[key]
                if (targetIndex != null) {
                    coroutineScope.launch {
                        lazyListState.animateScrollToItem(targetIndex)
                    }
                }
            },
            isNavbarCollapsed = false,
            onToggleNavbar = {},
            canCollapse = false
        )

        // Vertical Divider separating sidebar and content
        VerticalDivider(
            modifier = Modifier.fillMaxHeight(),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = ToolkitTheme.opacity.divider)
        )

        // Right Content Area
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(ToolkitTheme.spacing.large)
        ) {
            // Header with Plugin Name
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = ToolkitTheme.spacing.small),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = manifest.plugin.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (manifest.plugin.description.isNotBlank()) {
                        Text(
                            text = manifest.plugin.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Search & By Section Filter Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = ToolkitTheme.spacing.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToolkitTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            stringResource(Res.string.settings_search_placeholder),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(ToolkitTheme.dimensions.iconMediumSmall)
                        )
                    },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Clear",
                                    modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                                )
                            }
                        }
                    } else null,
                    singleLine = true
                )

                Spacer(modifier = Modifier.width(ToolkitTheme.spacing.medium))

                Text(
                    text = stringResource(Res.string.plugin_settings_by_section),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                Switch(
                    checked = searchBySection,
                    onCheckedChange = { searchBySection = it }
                )

                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(ToolkitTheme.dimensions.iconMedium)
                            .padding(start = ToolkitTheme.spacing.medium),
                        strokeWidth = ToolkitTheme.dimensions.circularProgressStrokeWidth
                    )
                }
            }

            // Main Flat List with Fading Edges
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (!hasAnyResults) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(Res.string.settings_no_results),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    val providedSettings = remember(manifest, store.settings) {
                        store.resolveCustomSettings(manifest)
                    }

                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalFadingEdges(
                                topFadeLength = topFadeLength,
                                bottomFadeLength = bottomFadeLength,
                                almostOpaque = ToolkitTheme.opacity.almostOpaque
                            ),
                        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
                    ) {
                        // Actions Section
                        if (hasActions) {
                            item {
                                PluginSectionHeader(title = actionsTitle)
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = ToolkitTheme.spacing.small),
                                    verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall)
                                ) {
                                    actions.forEachIndexed { index, action ->
                                        val hasParams = !action.parameters.isNullOrEmpty()
                                        SettingsItem(
                                            title = action.name + if (hasParams) " *" else "",
                                            subtitle = action.description,
                                            icon = Icons.Default.PlayArrow,
                                            enabled = !isBusy,
                                            shape = getGroupedShape(index, actions.size),
                                            onClick = {
                                                if (hasParams) {
                                                    selectedActionForParams = action
                                                } else {
                                                    viewModel.runAction(action.functionName)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Custom Settings Section
                        if (hasCustomSettings) {
                            item {
                                PluginSectionHeader(title = customTitle)
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(top = ToolkitTheme.spacing.small),
                                    verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.mediumSmall)
                                ) {
                                    listOf(
                                        requiredTitle to requiredSettings,
                                        optionalTitle to optionalSettings
                                    ).forEach { (groupTitle, groupSettings) ->
                                        if (groupSettings.isNotEmpty()) {
                                            PluginSettingGroupHeader(groupTitle, groupSettings.size)
                                        }
                                        groupSettings.forEach { (key, meta) ->
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .bringIntoViewRequester(customSettingRequesters.getValue(key))
                                            ) {
                                                val value = store.settings[key] ?: meta.defaultValue
                                                DynamicParameterInput(
                                                    name = key,
                                                    metadata = ParameterMetadata(
                                                        description = meta.description,
                                                        type = meta.type,
                                                        defaultValue = meta.defaultValue,
                                                        constraints = meta.constraints,
                                                        required = meta.required,
                                                        secret = meta.secret,
                                                        semanticTypes = meta.semanticTypes,
                                                        autogeneratedPattern = meta.autogeneratedPattern
                                                    ),
                                                    value = SettingsUtils.jsonToString(value, meta.type),
                                                    onValueChange = {
                                                        viewModel.updateSetting(
                                                            key,
                                                            SettingsUtils.stringToJson(it, meta.type)
                                                        )
                                                    },
                                                    enabled = !isBusy && meta.autogeneratedPattern == null,
                                                    isAutoGenerated = meta.autogeneratedPattern != null,
                                                    providedSettings = providedSettings,
                                                    providedLocks = locks
                                                )

                                                val lockedOptionsForSetting = lockedEnumOptions[key]?.distinct() ?: emptyList()

                                                if (meta.requiredByCapabilities.isNotEmpty() || lockedOptionsForSetting.isNotEmpty()) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(
                                                                start = ToolkitTheme.spacing.medium,
                                                                bottom = ToolkitTheme.spacing.mediumSmall,
                                                                end = ToolkitTheme.spacing.medium
                                                            )
                                                            .horizontalScroll(rememberScrollState()),
                                                        horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
                                                    ) {
                                                        meta.requiredByCapabilities.forEach { capName ->
                                                            ToolkitChip(
                                                                text = stringResource(
                                                                    Res.string.settings_locked_capability,
                                                                    capName
                                                                ),
                                                                icon = {
                                                                    Icon(
                                                                        Icons.Default.Lock,
                                                                        contentDescription = null,
                                                                        modifier = Modifier.size(ToolkitTheme.dimensions.iconExtraSmall)
                                                                    )
                                                                },
                                                                style = ToolkitChipStyle.Tinted
                                                            )
                                                        }
                                                        if (lockedOptionsForSetting.isNotEmpty()) {
                                                            ToolkitChip(
                                                                text = "Unlocks Enum Options",
                                                                modifier = Modifier.tooltip(
                                                                    text = "Unlocks values:\n" + lockedOptionsForSetting.joinToString("\n"),
                                                                ),
                                                                icon = {
                                                                    Icon(
                                                                        Icons.Default.Lock,
                                                                        contentDescription = null,
                                                                        modifier = Modifier.size(ToolkitTheme.dimensions.iconExtraSmall)
                                                                    )
                                                                },
                                                                style = ToolkitChipStyle.Outlined
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Global Defaults Section
                        if (hasGlobalParams) {
                            item {
                                PluginSectionHeader(title = globalTitle)
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(top = ToolkitTheme.spacing.small),
                                    verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.mediumSmall)
                                ) {
                                    globalParams.forEach { (key, meta) ->
                                        val value = store.globalParams[key] ?: meta.defaultValue
                                        DynamicParameterInput(
                                            name = key,
                                            metadata = meta,
                                            value = SettingsUtils.jsonToString(value, meta.type),
                                            onValueChange = {
                                                viewModel.updateGlobalParam(
                                                    key,
                                                    SettingsUtils.stringToJson(it, meta.type)
                                                )
                                            },
                                            enabled = !isBusy,
                                            providedSettings = providedSettings
                                        )
                                    }
                                }
                            }
                        }

                        // Per-Capability Parameter Defaults
                        capabilities.forEach { capability ->
                            item {
                                val capTitle = capabilityTitles[capability.name] ?: capability.name
                                PluginSectionHeader(title = capTitle)
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(top = ToolkitTheme.spacing.small),
                                    verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.mediumSmall)
                                ) {
                                    capability.parameters?.forEach { (key, meta) ->
                                        val value = store.capabilityParams[capability.name]?.get(key) ?: meta.defaultValue
                                        DynamicParameterInput(
                                            name = key,
                                            metadata = meta.copy(required = false),
                                            value = SettingsUtils.jsonToString(value, meta.type),
                                            onValueChange = {
                                                viewModel.updateCapabilityParam(
                                                    capability.name,
                                                    key,
                                                    SettingsUtils.stringToJson(it, meta.type)
                                                )
                                            },
                                            enabled = !isBusy,
                                            providedSettings = providedSettings,
                                            providedLocks = locks
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Pinned Action Buttons at Bottom Right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = ToolkitTheme.spacing.medium),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onDismiss != null) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = true,
                        modifier = Modifier.padding(end = ToolkitTheme.spacing.small)
                    ) {
                        Text(if (isBusy) "Close" else stringResource(Res.string.action_cancel))
                    }
                }
                Button(
                    onClick = {
                        viewModel.save()
                        onDismiss?.invoke()
                    },
                    enabled = !isBusy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(stringResource(Res.string.action_save))
                }
            }
        }
    }

    selectedActionForParams?.let { action ->
        ActionParametersDialog(
            action = action,
            onDismiss = { selectedActionForParams = null },
            onConfirm = { params ->
                selectedActionForParams = null
                viewModel.runAction(action.functionName, params)
            }
        )
    }
}

/**
 * Renders a section header with accent Yellow text and bold typography.
 */
@Composable
private fun PluginSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = ToolkitTheme.spacing.extraSmall, bottom = ToolkitTheme.spacing.extraSmall)
    )
}

@Composable
private fun PluginSettingGroupHeader(title: String, count: Int) {
    Text(
        text = "$title ($count)",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            start = ToolkitTheme.spacing.medium,
            top = ToolkitTheme.spacing.small,
            bottom = ToolkitTheme.spacing.extraSmall
        )
    )
}

@Composable
private fun ActionParametersDialog(
    action: PluginAction,
    onDismiss: () -> Unit,
    onConfirm: (Map<String, kotlinx.serialization.json.JsonElement>) -> Unit
) {
    val paramState = remember(action) {
        val initialMap = mutableStateMapOf<String, String>()
        action.parameters?.forEach { (key, meta) ->
            initialMap[key] = SettingsUtils.jsonToString(meta.defaultValue, meta.type)
        }
        initialMap
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .padding(ToolkitTheme.spacing.large)
                .widthIn(max = ToolkitTheme.dimensions.dialogMaxWidth)
        ) {
            Column(modifier = Modifier.padding(ToolkitTheme.spacing.large)) {
                Text(
                    text = action.name,
                    style = MaterialTheme.typography.headlineSmall
                )
                if (action.description.isNotEmpty()) {
                    Text(
                        text = action.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = ToolkitTheme.spacing.small)
                    )
                }
                Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))

                action.parameters?.forEach { (key, meta) ->
                    DynamicParameterInput(
                        name = key,
                        metadata = meta,
                        value = paramState[key] ?: "",
                        onValueChange = { paramState[key] = it }
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = ToolkitTheme.spacing.large),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.padding(end = ToolkitTheme.spacing.small)
                    ) {
                        Text(stringResource(Res.string.action_cancel))
                    }
                    Button(
                        onClick = {
                            val jsonMap = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
                            paramState.entries.forEach { entry ->
                                val key = entry.key
                                val strVal = entry.value
                                val meta = action.parameters?.get(key)
                                if (meta != null) {
                                    jsonMap[key] = SettingsUtils.stringToJson(strVal, meta.type)
                                }
                            }
                            onConfirm(jsonMap)
                        }
                    ) {
                        Text("Run")
                    }
                }
            }
        }
    }
}
