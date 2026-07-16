package org.wip.plugintoolkit.features.plugin.ui

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.ParameterMetadata
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.plugin.utils.SettingsUtils
import org.wip.plugintoolkit.features.plugin.viewmodel.PluginSettingsViewModel
import org.wip.plugintoolkit.shared.components.ToolkitChip
import org.wip.plugintoolkit.shared.components.ToolkitChipStyle
import org.wip.plugintoolkit.shared.components.ToolkitTextField
import org.wip.plugintoolkit.shared.components.TooltipArea
import org.wip.plugintoolkit.shared.components.plugin.DynamicParameterInput
import org.wip.plugintoolkit.shared.components.settings.SettingsGroup
import org.wip.plugintoolkit.shared.components.settings.SettingsItem
import org.wip.plugintoolkit.shared.components.settings.getGroupedShape
import org.wip.plugintoolkit.shared.components.tooltip
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.action_cancel
import plugintoolkit.composeapp.generated.resources.action_save
import plugintoolkit.composeapp.generated.resources.plugin_settings_actions
import plugintoolkit.composeapp.generated.resources.plugin_settings_capability
import plugintoolkit.composeapp.generated.resources.plugin_settings_custom
import plugintoolkit.composeapp.generated.resources.plugin_settings_global_defaults
import plugintoolkit.composeapp.generated.resources.plugin_settings_title
import plugintoolkit.composeapp.generated.resources.settings_locked_capability
import plugintoolkit.composeapp.generated.resources.settings_no_results
import plugintoolkit.composeapp.generated.resources.settings_search_by_section
import plugintoolkit.composeapp.generated.resources.settings_search_placeholder

@Composable
fun PluginSettingsDialog(
    pkg: String,
    onDismiss: () -> Unit,
    viewModel: PluginSettingsViewModel = koinInject(parameters = { parametersOf(pkg) })
) {
    val store by viewModel.store.collectAsState()
    val isBusy by viewModel.isBusy.collectAsState()
    val manifest = viewModel.manifest ?: return

    var searchQuery by remember { mutableStateOf("") }
    var searchBySection by remember { mutableStateOf(false) }

    val lazyListState = rememberLazyListState()
    val sidebarListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val actionsTitle = stringResource(Res.string.plugin_settings_actions)
    val customTitle = stringResource(Res.string.plugin_settings_custom)
    val globalTitle = stringResource(Res.string.plugin_settings_global_defaults)

    val capabilityTitles = manifest.capabilities.associate {
        it.name to stringResource(Res.string.plugin_settings_capability, it.name)
    }

    val actions = if (searchBySection) {
        if (searchQuery.isBlank() || actionsTitle.contains(
                searchQuery,
                ignoreCase = true
            )
        ) manifest.actions else emptyList()
    } else {
        manifest.actions.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.description.contains(searchQuery, ignoreCase = true)
        }
    }

    val customSettings = if (searchBySection) {
        if (searchQuery.isBlank() || customTitle.contains(searchQuery, ignoreCase = true)) manifest.settings
            ?: emptyMap() else emptyMap()
    } else {
        manifest.settings?.filter { (key, meta) ->
            key.contains(searchQuery, ignoreCase = true) ||
                    meta.description.contains(searchQuery, ignoreCase = true)
        } ?: emptyMap()
    }

    val globalParams = if (searchBySection) {
        if (searchQuery.isBlank() || globalTitle.contains(searchQuery, ignoreCase = true)) manifest.defaultParameters
            ?: emptyMap() else emptyMap()
    } else {
        manifest.defaultParameters?.filter { (key, meta) ->
            key.contains(searchQuery, ignoreCase = true) ||
                    meta.description.contains(searchQuery, ignoreCase = true)
        } ?: emptyMap()
    }

    val capabilities = manifest.capabilities.mapNotNull { capability ->
        if (searchBySection) {
            val capTitle = capabilityTitles[capability.name] ?: capability.name
            if (searchQuery.isBlank() || capTitle.contains(searchQuery, ignoreCase = true)) capability else null
        } else {
            val filteredParams = capability.parameters?.filter { (key, meta) ->
                key.contains(searchQuery, ignoreCase = true) ||
                        meta.description.contains(searchQuery, ignoreCase = true)
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

    val sectionIndices = remember(
        actions,
        customSettings,
        globalParams,
        capabilities,
        actionsTitle,
        customTitle,
        globalTitle,
        capabilityTitles
    ) {
        val map = mutableMapOf<String, Int>()
        var currentIndex = 0
        if (hasActions) {
            map[actionsTitle] = currentIndex++
        }
        if (hasCustomSettings) {
            map[customTitle] = currentIndex++
        }
        if (hasGlobalParams) {
            map[globalTitle] = currentIndex++
        }
        capabilities.forEach { capability ->
            map[capabilityTitles[capability.name] ?: capability.name] = currentIndex++
        }
        map
    }

    val firstVisibleIndex by remember { derivedStateOf { lazyListState.firstVisibleItemIndex } }

    LaunchedEffect(firstVisibleIndex) {
        if (hasAnyResults) {
            val visibleItems = sidebarListState.layoutInfo.visibleItemsInfo
            if (visibleItems.isNotEmpty()) {
                val isVisible = visibleItems.any { it.index == firstVisibleIndex }
                if (!isVisible) {
                    val firstVisible = visibleItems.first().index
                    val lastVisible = visibleItems.last().index

                    if (firstVisibleIndex > lastVisible) {
                        // It's below the viewport. Scroll so it appears fully at the bottom.
                        val viewportStart = sidebarListState.layoutInfo.viewportStartOffset
                        val viewportEnd = sidebarListState.layoutInfo.viewportEndOffset
                        val fullyVisibleItemsCount = visibleItems.count {
                            it.offset >= viewportStart && (it.offset + it.size) <= viewportEnd
                        }.coerceAtLeast(1)

                        val targetTopIndex = maxOf(0, firstVisibleIndex - fullyVisibleItemsCount + 1)
                        sidebarListState.animateScrollToItem(targetTopIndex)
                    } else if (firstVisibleIndex < firstVisible) {
                        // It's above the viewport. Scroll so it appears at the top.
                        sidebarListState.animateScrollToItem(firstVisibleIndex)
                    }
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .fillMaxHeight(0.85f)
                .widthIn(max = 1200.dp)
                .heightIn(max = 900.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(ToolkitTheme.spacing.large)) {
                // Header row with title and search bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = ToolkitTheme.spacing.large),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(Res.string.plugin_settings_title, manifest.plugin.name),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(end = ToolkitTheme.spacing.large)
                    )

                    ToolkitTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(Res.string.settings_search_placeholder)) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.width(ToolkitTheme.spacing.medium))

                    Text(
                        text = stringResource(Res.string.settings_search_by_section),
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

                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(
                        state = sidebarListState,
                        modifier = Modifier
                            .width(ToolkitTheme.dimensions.sidebarExpandedWidth)
                            .fillMaxHeight()
                            .padding(end = ToolkitTheme.spacing.medium),
                        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
                    ) {
                        items(sectionIndices.toList()) { (name, index) ->
                            val isSelected = firstVisibleIndex == index
                            SidebarItem(
                                text = name,
                                isSelected = isSelected,
                                onClick = {
                                    coroutineScope.launch {
                                        lazyListState.animateScrollToItem(index)
                                    }
                                }
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(start = ToolkitTheme.spacing.medium)
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
                            OutlinedCard(
                                modifier = Modifier.fillMaxSize(),
                                colors = CardDefaults.outlinedCardColors(
                                    containerColor = ToolkitTheme.colors.transparent
                                ),
                                border = CardDefaults.outlinedCardBorder().copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outlineVariant)
                                )
                            ) {
                                LazyColumn(
                                    state = lazyListState,
                                    modifier = Modifier.fillMaxSize().padding(ToolkitTheme.spacing.medium),
                                    verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium)
                                ) {
                                    if (hasActions) {
                                        item {
                                            SettingsGroup(title = actionsTitle) {
                                                actions.forEachIndexed { index, action ->
                                                    SettingsItem(
                                                        title = action.name,
                                                        subtitle = action.description,
                                                        icon = Icons.Default.PlayArrow,
                                                        enabled = !isBusy,
                                                        shape = getGroupedShape(index, actions.size),
                                                        onClick = { viewModel.runAction(action.functionName) }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (hasCustomSettings) {
                                        item {
                                            SettingsGroup(title = customTitle) {
                                                customSettings.forEach { (key, meta) ->
                                                    Column(modifier = Modifier.fillMaxWidth()) {
                                                        val value = store.settings[key] ?: meta.defaultValue
                                                        DynamicParameterInput(
                                                            name = key,
                                                            metadata = ParameterMetadata(
                                                                description = meta.description,
                                                                type = meta.type,
                                                                defaultValue = meta.defaultValue,
                                                                required = meta.required,
                                                                secret = meta.secret
                                                            ),
                                                            value = SettingsUtils.jsonToString(value, meta.type),
                                                            onValueChange = {
                                                                viewModel.updateSetting(
                                                                    key,
                                                                    SettingsUtils.stringToJson(it, meta.type)
                                                                )
                                                            },
                                                            enabled = !isBusy,
                                                            providedSettings = store.settings
                                                        )

                                                        val lockedOptionsForSetting =
                                                            lockedEnumOptions[key]?.distinct() ?: emptyList()

                                                        if (meta.requiredByCapabilities.isNotEmpty() || lockedOptionsForSetting.isNotEmpty()) {
                                                            Row(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .padding(start = ToolkitTheme.spacing.medium, bottom = ToolkitTheme.spacing.mediumSmall, end = ToolkitTheme.spacing.medium)
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
                                                                                modifier = Modifier.size(14.dp)
                                                                            )
                                                                        },
                                                                        style = ToolkitChipStyle.Tinted
                                                                    )
                                                                }
                                                                if (lockedOptionsForSetting.isNotEmpty()) {
                                                                    ToolkitChip(
                                                                        text = "Locked Enum Options",
                                                                        modifier = Modifier.tooltip(
                                                                            text = "Locked values:\n" + lockedOptionsForSetting.joinToString(
                                                                                "\n"
                                                                            ),
                                                                        ),
                                                                        icon = {
                                                                            Icon(
                                                                                Icons.Default.Lock,
                                                                                contentDescription = null,
                                                                                modifier = Modifier.size(14.dp)
                                                                            )
                                                                        },
                                                                        style = ToolkitChipStyle.Tinted
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    if (hasGlobalParams) {
                                        item {
                                            SettingsGroup(title = globalTitle) {
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
                                                        providedSettings = store.settings
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    capabilities.forEach { capability ->
                                        item {
                                            val capTitle = capabilityTitles[capability.name] ?: capability.name
                                            SettingsGroup(title = capTitle) {
                                                capability.parameters!!.forEach { (key, meta) ->
                                                    val value = store.capabilityParams[capability.name]?.get(key)
                                                        ?: meta.defaultValue
                                                    DynamicParameterInput(
                                                        name = key,
                                                        metadata = meta.copy(required = false), // override required
                                                        value = SettingsUtils.jsonToString(value, meta.type),
                                                        onValueChange = {
                                                            viewModel.updateCapabilityParam(
                                                                capability.name,
                                                                key,
                                                                SettingsUtils.stringToJson(it, meta.type)
                                                            )
                                                        },
                                                        enabled = !isBusy,
                                                        providedSettings = store.settings
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

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = ToolkitTheme.spacing.large),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = true,
                        modifier = Modifier.padding(end = ToolkitTheme.spacing.small)
                    ) {
                        Text(if (isBusy) "Close" else stringResource(Res.string.action_cancel))
                    }
                    Button(
                        onClick = { viewModel.save(); onDismiss() },
                        enabled = !isBusy
                    ) {
                        Text(stringResource(Res.string.action_save))
                    }
                }
            }
        }
    }
}

@Composable
private fun SidebarItem(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else ToolkitTheme.colors.transparent
    val contentColor =
        if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(percent = 50))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = ToolkitTheme.spacing.medium, vertical = ToolkitTheme.spacing.mediumSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor
        )
    }
}
