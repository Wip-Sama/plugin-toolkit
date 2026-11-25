package org.wip.plugintoolkit.features.plugin.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.wip.plugintoolkit.api.Capability
import org.wip.plugintoolkit.api.CapabilityContext
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PluginEntry
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.core.model.localized
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.core.utils.PlatformVerticalScrollbar
import org.wip.plugintoolkit.features.navigation.GlobalRouter
import org.wip.plugintoolkit.features.navigation.LocalGlobalRouter
import org.wip.plugintoolkit.features.navigation.model.Screen
import org.wip.plugintoolkit.features.plugin.logic.PluginManager
import org.wip.plugintoolkit.features.plugin.utils.CapabilityLockStatus
import org.wip.plugintoolkit.features.plugin.utils.CapabilityLockUtils
import org.wip.plugintoolkit.shared.components.ToolkitTextField
import org.wip.plugintoolkit.shared.components.sidebar.SidebarElement
import org.wip.plugintoolkit.shared.components.sidebar.SidebarSection
import org.wip.plugintoolkit.shared.components.sidebar.SidebarSectionData
import org.wip.plugintoolkit.shared.components.sidebar.ToolkitFloatingSidebar
import org.wip.plugintoolkit.shared.components.sidebar.ToolkitFloatingSidebarScaffold
import org.wip.plugintoolkit.shared.components.sidebar.ToolkitSequelSidebarToggleButton
import org.wip.plugintoolkit.shared.components.verticalFadingEdges
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.action_back
import plugintoolkit.composeapp.generated.resources.plugin_capabilities
import plugintoolkit.composeapp.generated.resources.plugin_selection_hint
import plugintoolkit.composeapp.generated.resources.search_capabilities_placeholder
import plugintoolkit.composeapp.generated.resources.search_plugins_placeholder
import plugintoolkit.composeapp.generated.resources.section_loaded_plugins

/**
 * Unified floating sidebar for Capabilities and Interactive Tools.
 *
 * Supports both:
 * - Single-column animated drill-down (`Loaded Plugins -> Plugin Capabilities`)
 * - Two-column side-by-side sequel mode inside the exact same rounded floating block when
 *   [isSequelExpanded] is `true`, with an optional header toggle button ([onToggleSequelMode]).
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DirectExecutionSidebar(
    loadedPlugins: List<PluginEntry>,
    selectedPluginId: String?,
    onPluginSelected: (String) -> Unit,
    onBackToPlugins: () -> Unit,
    selectedCapability: Capability?,
    onCapabilitySelected: (Capability) -> Unit,
    router: GlobalRouter? = null,
    onNavigateToPluginSetting: ((pluginId: String, settingKey: String) -> Unit)? = null,
    isInteractiveOnly: Boolean = false,
    isSequelExpanded: Boolean = false,
    onToggleSequelMode: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    fillPaneWidth: Boolean = true
) {
    val currentRouter = router ?: LocalGlobalRouter.current
    var pluginSearchQuery by remember { mutableStateOf("") }
    var capabilitySearchQuery by remember { mutableStateOf("") }
    var isShiftPressed by remember { mutableStateOf(false) }
    val pluginSearchFocusRequester = remember { FocusRequester() }
    val capabilitySearchFocusRequester = remember { FocusRequester() }
    val motion = MaterialTheme.motionScheme

    LaunchedEffect(selectedPluginId, isSequelExpanded) {
        delay(50)
        runCatching {
            if (selectedPluginId == null || isSequelExpanded) {
                pluginSearchFocusRequester.requestFocus()
            } else {
                capabilitySearchFocusRequester.requestFocus()
            }
        }
    }

    val widthModifier = if (fillPaneWidth) {
        Modifier.fillMaxWidth()
    } else {
        Modifier.width(ToolkitTheme.dimensions.floatingSidebarDefaultWidth)
    }

    val navigationKey = if (isSequelExpanded) null else selectedPluginId

    ToolkitFloatingSidebar(
        navigationKey = navigationKey,
        modifier = modifier
            .then(widthModifier)
            .fillMaxHeight()
            .onPreviewKeyEvent { event ->
                if (event.isCtrlPressed && event.key == Key.F && event.type == KeyEventType.KeyDown) {
                    if (selectedPluginId == null || isSequelExpanded) {
                        pluginSearchFocusRequester.requestFocus()
                    } else {
                        capabilitySearchFocusRequester.requestFocus()
                    }
                    true
                } else false
            }
            .onPointerEvent(PointerEventType.Press) { event ->
                isShiftPressed = event.keyboardModifiers.isShiftPressed
            },
        navigationTransitionSpec = { initial, target ->
            if (target != null && initial == null) {
                slideInHorizontally(motion.defaultSpatialSpec()) { it } + fadeIn(motion.fastEffectsSpec()) togetherWith
                    slideOutHorizontally(motion.defaultSpatialSpec()) { -it } + fadeOut(motion.fastEffectsSpec())
            } else {
                slideInHorizontally(motion.defaultSpatialSpec()) { -it } + fadeIn(motion.fastEffectsSpec()) togetherWith
                    slideOutHorizontally(motion.defaultSpatialSpec()) { it } + fadeOut(motion.fastEffectsSpec())
            }
        },
        sequelVisible = isSequelExpanded,
        sequelContent = {
            if (selectedPluginId != null) {
                PluginCapabilitiesSidebarPane(
                    loadedPlugins = loadedPlugins,
                    pluginId = selectedPluginId,
                    isInteractiveOnly = isInteractiveOnly,
                    capabilitySearchQuery = capabilitySearchQuery,
                    onCapabilitySearchQueryChange = { capabilitySearchQuery = it },
                    capabilitySearchFocusRequester = capabilitySearchFocusRequester,
                    selectedCapability = selectedCapability,
                    onCapabilitySelected = onCapabilitySelected,
                    showBackRow = false,
                    onBackToPlugins = onBackToPlugins,
                    onNavigateToPluginSetting = onNavigateToPluginSetting,
                    currentRouter = currentRouter,
                    headerActions = null
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(ToolkitTheme.spacing.medium),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(Res.string.plugin_selection_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    ) { activeKey ->
        if (activeKey == null) {
            LoadedPluginsSidebarPane(
                loadedPlugins = loadedPlugins,
                selectedPluginId = selectedPluginId,
                isInteractiveOnly = isInteractiveOnly,
                pluginSearchQuery = pluginSearchQuery,
                onPluginSearchQueryChange = { pluginSearchQuery = it },
                pluginSearchFocusRequester = pluginSearchFocusRequester,
                onPluginSelected = onPluginSelected,
                headerActions = onToggleSequelMode?.let { toggle ->
                    {
                        ToolkitSequelSidebarToggleButton(
                            isExpanded = isSequelExpanded,
                            onToggle = toggle
                        )
                    }
                }
            )
        } else {
            PluginCapabilitiesSidebarPane(
                loadedPlugins = loadedPlugins,
                pluginId = activeKey,
                isInteractiveOnly = isInteractiveOnly,
                capabilitySearchQuery = capabilitySearchQuery,
                onCapabilitySearchQueryChange = { capabilitySearchQuery = it },
                capabilitySearchFocusRequester = capabilitySearchFocusRequester,
                selectedCapability = selectedCapability,
                onCapabilitySelected = onCapabilitySelected,
                showBackRow = true,
                onBackToPlugins = {
                    capabilitySearchQuery = ""
                    onBackToPlugins()
                },
                onNavigateToPluginSetting = onNavigateToPluginSetting,
                currentRouter = currentRouter,
                headerActions = onToggleSequelMode?.let { toggle ->
                    {
                        ToolkitSequelSidebarToggleButton(
                            isExpanded = isSequelExpanded,
                            onToggle = toggle
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun LoadedPluginsSidebarPane(
    loadedPlugins: List<PluginEntry>,
    selectedPluginId: String?,
    isInteractiveOnly: Boolean,
    pluginSearchQuery: String,
    onPluginSearchQueryChange: (String) -> Unit,
    pluginSearchFocusRequester: FocusRequester,
    onPluginSelected: (String) -> Unit,
    headerActions: (@Composable androidx.compose.foundation.layout.RowScope.() -> Unit)?
) {
    val filteredPlugins = remember(loadedPlugins, isInteractiveOnly, pluginSearchQuery) {
        loadedPlugins.filter { plugin ->
            val manifest = plugin.getManifest().getOrNull() ?: return@filter false
            val hasMatchingCaps = if (isInteractiveOnly) {
                manifest.capabilities.any {
                    it.isInteractive || it.context == CapabilityContext.INTERACTIVE_ONLY
                } || plugin.getInteractiveProcessor() != null
            } else {
                manifest.capabilities.any {
                    it.context != CapabilityContext.FLOW_ONLY &&
                        !it.isInteractive &&
                        it.context != CapabilityContext.INTERACTIVE_ONLY
                }
            }
            if (!hasMatchingCaps) return@filter false
            val name = manifest.plugin.name
            val id = manifest.plugin.id
            name.contains(pluginSearchQuery, ignoreCase = true) || id.contains(pluginSearchQuery, ignoreCase = true)
        }
    }

    val pluginsElements = filteredPlugins.map { plugin ->
        val manifest = plugin.getManifest().getOrThrow()
        SidebarElement(
            id = manifest.plugin.id,
            icon = Icons.Default.Extension,
            title = manifest.plugin.name.localized
        )
    }

    val scrollState = rememberScrollState()
    ToolkitFloatingSidebarScaffold(
        title = stringResource(Res.string.section_loaded_plugins),
        headerActions = headerActions,
        headerExtraContent = {
            ToolkitTextField(
                value = pluginSearchQuery,
                onValueChange = onPluginSearchQueryChange,
                placeholder = {
                    Text(
                        stringResource(Res.string.search_plugins_placeholder),
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(ToolkitTheme.dimensions.iconMediumSmall)
                    )
                },
                trailingIcon = if (pluginSearchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { onPluginSearchQueryChange("") }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear",
                                modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                            )
                        }
                    }
                } else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = ToolkitTheme.spacing.small)
                    .focusRequester(pluginSearchFocusRequester),
                singleLine = true
            )
        }
    ) {
        val scrollbarReserve = if (scrollState.maxValue > 0) {
            ToolkitTheme.dimensions.sduiScrollbarEndPadding
        } else {
            ToolkitTheme.spacing.none
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalFadingEdges(
                    scrollState = scrollState,
                    topFadeLength = ToolkitTheme.spacing.large,
                    bottomFadeLength = ToolkitTheme.spacing.large,
                    almostOpaque = ToolkitTheme.opacity.almostOpaque
                )
                .verticalScroll(scrollState)
                .padding(end = scrollbarReserve)
        ) {
            SidebarSection(
                section = SidebarSectionData(title = null, elements = pluginsElements),
                currentSelection = selectedPluginId ?: "",
                onItemSelected = onPluginSelected,
                isExpanded = true
            )
        }
        if (scrollState.maxValue > 0) {
            PlatformVerticalScrollbar(
                scrollState = scrollState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
            )
        }
    }
}

@Composable
private fun PluginCapabilitiesSidebarPane(
    loadedPlugins: List<PluginEntry>,
    pluginId: String,
    isInteractiveOnly: Boolean,
    capabilitySearchQuery: String,
    onCapabilitySearchQueryChange: (String) -> Unit,
    capabilitySearchFocusRequester: FocusRequester,
    selectedCapability: Capability?,
    onCapabilitySelected: (Capability) -> Unit,
    showBackRow: Boolean,
    onBackToPlugins: () -> Unit,
    onNavigateToPluginSetting: ((pluginId: String, settingKey: String) -> Unit)?,
    currentRouter: GlobalRouter,
    headerActions: (@Composable androidx.compose.foundation.layout.RowScope.() -> Unit)?
) {
    val plugin = loadedPlugins.find { it.getManifest().getOrNull()?.plugin?.id == pluginId } ?: return
    val manifest = plugin.getManifest().getOrThrow()
    val pluginManager: PluginManager = koinInject()
    val settingsStore = pluginManager.loadPluginSettings(pluginId)
    val settings = settingsStore.settings + settingsStore.globalParams
    val pluginLocksState by pluginManager.pluginLocksState.collectAsState()
    val locks = pluginLocksState[pluginId] ?: pluginLocksState.values.fold(emptyMap<String, Boolean>()) { acc, map -> acc + map }

    val filteredCapabilities = if (isInteractiveOnly) {
        val interactiveCaps = manifest.capabilities.filter { capability ->
            (capability.isInteractive || capability.context == CapabilityContext.INTERACTIVE_ONLY) &&
                (capability.name.contains(capabilitySearchQuery, ignoreCase = true) ||
                    (capability.description != null && capability.description.contains(capabilitySearchQuery, ignoreCase = true)))
        }
        if (interactiveCaps.isEmpty() && plugin.getInteractiveProcessor() != null) {
            listOf(
                Capability(
                    name = manifest.plugin.id,
                    description = manifest.plugin.description,
                    returnType = DataType.Primitive(PrimitiveType.STRING),
                    isInteractive = true,
                    context = CapabilityContext.INTERACTIVE_ONLY
                )
            )
        } else {
            interactiveCaps
        }
    } else {
        manifest.capabilities.filter { capability ->
            capability.context != CapabilityContext.FLOW_ONLY &&
                !capability.isInteractive &&
                capability.context != CapabilityContext.INTERACTIVE_ONLY &&
                (capability.name.contains(capabilitySearchQuery, ignoreCase = true) ||
                    (capability.description != null && capability.description.contains(capabilitySearchQuery, ignoreCase = true)))
        }
    }

    val capabilityElements = filteredCapabilities.map { capability ->
        val lockStatus = CapabilityLockUtils.checkCapabilityLockStatus(capability, locks, settings)
        val isLocked = lockStatus is CapabilityLockStatus.Locked
        SidebarElement(
            id = capability,
            icon = if (isLocked) Icons.Default.Lock else Icons.Default.Bolt,
            title = capability.name.localized,
            description = capability.description?.localized
        )
    }

    val scrollState = rememberScrollState()
    ToolkitFloatingSidebarScaffold(
        title = manifest.plugin.name,
        headerActions = {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = ToolkitTheme.opacity.divider),
                shape = ToolkitTheme.shapes.extraSmall
            ) {
                Text(
                    text = "v${manifest.plugin.version}",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(
                        horizontal = ToolkitTheme.spacing.xs,
                        vertical = ToolkitTheme.spacing.xxs
                    ),
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            headerActions?.invoke(this)
        },
        headerExtraContent = {
            Column {
                if (showBackRow) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = ToolkitTheme.spacing.small)
                    ) {
                        TextButton(
                            onClick = onBackToPlugins,
                            contentPadding = PaddingValues(
                                horizontal = ToolkitTheme.spacing.small,
                                vertical = ToolkitTheme.spacing.extraSmall
                            )
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(Res.string.action_back),
                                modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                            )
                            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                            Text(stringResource(Res.string.action_back), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                ToolkitTextField(
                    value = capabilitySearchQuery,
                    onValueChange = onCapabilitySearchQueryChange,
                    placeholder = {
                        Text(
                            stringResource(Res.string.search_capabilities_placeholder),
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(ToolkitTheme.dimensions.iconMediumSmall)
                        )
                    },
                    trailingIcon = if (capabilitySearchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { onCapabilitySearchQueryChange("") }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Clear",
                                    modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                                )
                            }
                        }
                    } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = ToolkitTheme.spacing.small)
                        .focusRequester(capabilitySearchFocusRequester),
                    singleLine = true
                )
            }
        }
    ) {
        val scrollbarReserve = if (scrollState.maxValue > 0) {
            ToolkitTheme.dimensions.sduiScrollbarEndPadding
        } else {
            ToolkitTheme.spacing.none
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalFadingEdges(
                    scrollState = scrollState,
                    topFadeLength = ToolkitTheme.spacing.large,
                    bottomFadeLength = ToolkitTheme.spacing.large,
                    almostOpaque = ToolkitTheme.opacity.almostOpaque
                )
                .verticalScroll(scrollState)
                .padding(end = scrollbarReserve)
        ) {
            SidebarSection(
                section = SidebarSectionData(
                    title = Res.string.plugin_capabilities.localized,
                    elements = capabilityElements
                ),
                currentSelection = selectedCapability,
                onItemSelected = { capability ->
                    capability?.let { cap ->
                        val lockStatus = CapabilityLockUtils.checkCapabilityLockStatus(cap, locks, settings)
                        val isLocked = lockStatus is CapabilityLockStatus.Locked

                        if (isLocked) {
                            val targetSettingKey = when (lockStatus) {
                                is CapabilityLockStatus.Locked -> lockStatus.missingLocks.firstOrNull()
                                    ?: lockStatus.missingSettings.firstOrNull()
                                    ?: ""
                                else -> cap.requiredLocks.firstOrNull() ?: cap.requiresSettings.firstOrNull() ?: ""
                            }
                            if (onNavigateToPluginSetting != null) {
                                onNavigateToPluginSetting(manifest.plugin.id, targetSettingKey)
                            } else {
                                currentRouter.navigateTo(Screen.PluginManager(manifest.plugin.id, targetSettingKey))
                            }
                        } else {
                            onCapabilitySelected(cap)
                        }
                    }
                },
                isExpanded = true
            )
        }
        if (scrollState.maxValue > 0) {
            PlatformVerticalScrollbar(
                scrollState = scrollState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
            )
        }
    }
}
