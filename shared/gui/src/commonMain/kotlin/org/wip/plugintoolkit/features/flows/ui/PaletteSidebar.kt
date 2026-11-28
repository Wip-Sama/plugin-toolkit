package org.wip.plugintoolkit.features.flows.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.wip.plugintoolkit.api.CapabilityContext
import org.wip.plugintoolkit.api.PluginEntry
import org.wip.plugintoolkit.core.theme.LocalCompactPanelsByDefault
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.core.utils.PlatformVerticalScrollbar
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.navigation.GlobalRouter
import org.wip.plugintoolkit.features.navigation.model.Screen
import org.wip.plugintoolkit.features.plugin.logic.PluginManager
import org.wip.plugintoolkit.features.plugin.ui.lockedClickInterceptor
import org.wip.plugintoolkit.features.plugin.utils.CapabilityLockStatus
import org.wip.plugintoolkit.features.plugin.utils.CapabilityLockUtils
import org.wip.plugintoolkit.shared.components.PaneAttachmentEdge
import org.wip.plugintoolkit.shared.components.ResizablePaneDragPhysics
import org.wip.plugintoolkit.shared.components.ResizablePaneDragSnapshot
import org.wip.plugintoolkit.shared.components.ToolkitPaneResizeHandle
import org.wip.plugintoolkit.shared.components.ToolkitTextField
import org.wip.plugintoolkit.shared.components.ToolkitTooltipBox
import org.wip.plugintoolkit.shared.components.ToolkitTooltipPosition
import org.wip.plugintoolkit.shared.components.sidebar.ToolkitFloatingSidebar
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.flow_palette_collapse
import plugintoolkit.composeapp.generated.resources.flow_palette_drag_to_board
import plugintoolkit.composeapp.generated.resources.flow_palette_drop_on_board
import plugintoolkit.composeapp.generated.resources.flow_palette_expand
import plugintoolkit.composeapp.generated.resources.pane_drag_to_resize_or_collapse
import plugintoolkit.composeapp.generated.resources.palette_search_placeholder
import plugintoolkit.composeapp.generated.resources.palette_tab_flows
import plugintoolkit.composeapp.generated.resources.palette_tab_plugins
import plugintoolkit.composeapp.generated.resources.palette_tab_system

private const val PALETTE_PREVIEW_OFF_BOARD_SCALE = 0.94f

sealed class PaletteNode {
    data class Capability(
        val pluginInfo: org.wip.plugintoolkit.api.PluginInfo,
        val capability: org.wip.plugintoolkit.api.Capability
    ) : PaletteNode()

    data class System(val action: String) : PaletteNode()
    object FlowInput : PaletteNode()
    object FlowOutput : PaletteNode()
    data class SubFlow(val name: String) : PaletteNode()
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PaletteSidebar(
    flows: List<Flow>,
    currentFlowName: String,
    plugins: List<PluginEntry>,
    rootLayoutCoordinates: LayoutCoordinates?,
    onDragStart: (PaletteNode, Offset, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onClick: (PaletteNode) -> Unit,
    hasUnsavedChanges: Boolean = false,
    router: GlobalRouter? = null,
    onNavigateToPluginSetting: ((pluginId: String, settingKey: String) -> Unit)? = null,
    onPaletteBoundsChanged: (Rect?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val compactPanelsByDefault = LocalCompactPanelsByDefault.current
    var selectedTab by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var isExpanded by remember(compactPanelsByDefault) { mutableStateOf(!compactPanelsByDefault) }
    val motion = MaterialTheme.motionScheme
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val defaultPaletteWidth = ToolkitTheme.dimensions.flowPaletteWidth
    val minPaletteWidth = ToolkitTheme.dimensions.flowPaletteMinWidth
    val maxPaletteWidth = ToolkitTheme.dimensions.flowPaletteMaxWidth
    val collapseThreshold = ToolkitTheme.dimensions.paneCollapseDragThreshold
    val expandThreshold = ToolkitTheme.dimensions.paneExpandDragThreshold
    val magneticSnap = ToolkitTheme.dimensions.paneMagneticSnapThreshold
    val collapseAnimSpec = remember {
        spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        )
    }

    var dragSnapshot by remember(compactPanelsByDefault) {
        mutableStateOf(
            ResizablePaneDragSnapshot(
                isExpanded = !compactPanelsByDefault,
                visibleSizeDp = if (!compactPanelsByDefault) defaultPaletteWidth.value else 0f,
                lastExpandedSizeDp = defaultPaletteWidth.value
            )
        )
    }
    var isDragging by remember { mutableStateOf(false) }
    val animatedWidthDp = remember(compactPanelsByDefault) {
        Animatable(if (!compactPanelsByDefault) defaultPaletteWidth.value else 0f)
    }

    LaunchedEffect(isExpanded) {
        if (!isDragging && dragSnapshot.isExpanded != isExpanded) {
            val target = if (isExpanded) {
                dragSnapshot.lastExpandedSizeDp.coerceIn(minPaletteWidth.value, maxPaletteWidth.value)
            } else {
                0f
            }
            dragSnapshot = dragSnapshot.copy(
                isExpanded = isExpanded,
                visibleSizeDp = target,
                overDragCollapseDp = 0f,
                dragOpenProgressDp = 0f
            )
            animatedWidthDp.animateTo(
                targetValue = target,
                animationSpec = if (isExpanded) motion.defaultSpatialSpec() else collapseAnimSpec
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            onPaletteBoundsChanged(null)
        }
    }

    val renderedWidthDp = animatedWidthDp.value.coerceAtLeast(0f).dp
    val showPaletteBody = renderedWidthDp.value > 0.5f

    LaunchedEffect(showPaletteBody) {
        if (!showPaletteBody) {
            onPaletteBoundsChanged(null)
        }
    }

    Row(
        modifier = modifier.fillMaxHeight(),
        verticalAlignment = Alignment.Top
    ) {
        if (showPaletteBody) {
            val safeBodyWidth = renderedWidthDp.coerceAtLeast(minPaletteWidth)
            Box(
                modifier = Modifier
                    .width(renderedWidthDp)
                    .fillMaxHeight()
                    .clipToBounds(),
                contentAlignment = Alignment.CenterEnd
            ) {
                ToolkitFloatingSidebar(
                    navigationKey = selectedTab,
                    animateNavigation = false,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = ToolkitTheme.shapes.large,
                    border = BorderStroke(
                        ToolkitTheme.dimensions.borderUnselected,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = ToolkitTheme.opacity.divider)
                    ),
                    shadowElevation = ToolkitTheme.dimensions.elevationMedium,
                    modifier = Modifier
                        .requiredWidth(safeBodyWidth)
                        .fillMaxHeight()
                        .onGloballyPositioned { coords ->
                            val bounds = if (rootLayoutCoordinates != null && coords.isAttached && rootLayoutCoordinates.isAttached) {
                                rootLayoutCoordinates.localBoundingBoxOf(coords, false)
                            } else {
                                val topLeft = coords.positionInWindow()
                                Rect(
                                    left = topLeft.x,
                                    top = topLeft.y,
                                    right = topLeft.x + coords.size.width,
                                    bottom = topLeft.y + coords.size.height
                                )
                            }
                            onPaletteBoundsChanged(bounds)
                        }
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Final)
                                    if (event.type == PointerEventType.Scroll) {
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            }
                        }
                        .testTag("flow_palette_sidebar")
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = ToolkitTheme.spacing.mediumSmall,
                                    end = ToolkitTheme.spacing.extraSmall,
                                    top = ToolkitTheme.spacing.extraSmall
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PrimaryTabRow(
                                selectedTabIndex = selectedTab,
                                containerColor = Color.Transparent,
                                modifier = Modifier.weight(1f)
                            ) {
                                Tab(
                                    selected = selectedTab == 0,
                                    onClick = { selectedTab = 0 },
                                    icon = { Icon(Icons.Default.Extension, contentDescription = null) },
                                    text = {
                                        Text(
                                            stringResource(Res.string.palette_tab_plugins),
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                )
                                Tab(
                                    selected = selectedTab == 1,
                                    onClick = { selectedTab = 1 },
                                    icon = { Icon(Icons.Default.Build, contentDescription = null) },
                                    text = {
                                        Text(
                                            stringResource(Res.string.palette_tab_system),
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                )
                                Tab(
                                    selected = selectedTab == 2,
                                    onClick = { selectedTab = 2 },
                                    icon = { Icon(Icons.Default.DeviceHub, contentDescription = null) },
                                    text = {
                                        Text(
                                            stringResource(Res.string.palette_tab_flows),
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                )
                            }
                        }

                        ToolkitTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    stringResource(Res.string.palette_search_placeholder),
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(ToolkitTheme.spacing.mediumSmall),
                            singleLine = true
                        )

                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            when (selectedTab) {
                                0 -> CapabilitiesPalette(
                                    plugins = plugins,
                                    searchQuery = searchQuery,
                                    rootLayoutCoordinates = rootLayoutCoordinates,
                                    onDragStart = onDragStart,
                                    onDrag = onDrag,
                                    onDragEnd = onDragEnd,
                                    onClick = onClick,
                                    hasUnsavedChanges = hasUnsavedChanges,
                                    router = router,
                                    onNavigateToPluginSetting = onNavigateToPluginSetting
                                )

                                1 -> SystemPalette(
                                    searchQuery = searchQuery,
                                    rootLayoutCoordinates = rootLayoutCoordinates,
                                    onDragStart = onDragStart,
                                    onDrag = onDrag,
                                    onDragEnd = onDragEnd,
                                    onClick = onClick
                                )

                                2 -> FlowsPalette(
                                    flows = flows.filter { it.name != currentFlowName },
                                    searchQuery = searchQuery,
                                    rootLayoutCoordinates = rootLayoutCoordinates,
                                    onDragStart = onDragStart,
                                    onDrag = onDrag,
                                    onDragEnd = onDragEnd,
                                    onClick = onClick
                                )
                            }
                        }
                    }
                }
            }
        }

        // Single persistent edge handle for click-to-toggle, drag-to-resize, drag-to-collapse, and drag-to-open
        ToolkitPaneResizeHandle(
            edge = PaneAttachmentEdge.Start,
            isExpanded = dragSnapshot.isExpanded,
            isDragging = isDragging,
            collapseIntentProgress = dragSnapshot.collapseIntentProgress(collapseThreshold.value),
            expandIntentProgress = dragSnapshot.expandIntentProgress(expandThreshold.value),
            tooltip = if (dragSnapshot.isExpanded) {
                stringResource(Res.string.pane_drag_to_resize_or_collapse)
            } else {
                stringResource(Res.string.flow_palette_expand)
            },
            testTag = "flow_palette_resize_handle",
            onClick = {
                val next = ResizablePaneDragPhysics.toggle(
                    state = dragSnapshot,
                    minSizeDp = minPaletteWidth.value,
                    maxSizeDp = maxPaletteWidth.value
                )
                dragSnapshot = next
                isExpanded = next.isExpanded
                scope.launch {
                    animatedWidthDp.animateTo(next.visibleSizeDp, animationSpec = motion.defaultSpatialSpec())
                }
            },
            onDragStart = {
                isDragging = true
                dragSnapshot = ResizablePaneDragPhysics.onDragStart(dragSnapshot)
            },
            onDragDelta = { deltaX, _ ->
                val deltaDp = with(density) { deltaX.toDp().value }
                val next = ResizablePaneDragPhysics.onDragDelta(
                    state = dragSnapshot,
                    expandDeltaDp = deltaDp,
                    minSizeDp = minPaletteWidth.value,
                    maxSizeDp = maxPaletteWidth.value,
                    collapseThresholdDp = collapseThreshold.value,
                    expandThresholdDp = expandThreshold.value,
                    collapsible = true
                )
                dragSnapshot = next
                scope.launch { animatedWidthDp.snapTo(next.visibleSizeDp) }
            },
            onDragEnd = { velocityX, _ ->
                isDragging = false
                val velocityDp = with(density) { velocityX.toDp().value }
                val settled = ResizablePaneDragPhysics.onDragEnd(
                    state = dragSnapshot,
                    defaultSizeDp = defaultPaletteWidth.value,
                    minSizeDp = minPaletteWidth.value,
                    maxSizeDp = maxPaletteWidth.value,
                    magneticSnapDp = magneticSnap.value,
                    collapseThresholdDp = collapseThreshold.value,
                    expandThresholdDp = expandThreshold.value,
                    collapsible = true,
                    releaseExpandVelocityDpPerSec = velocityDp
                )
                dragSnapshot = settled
                isExpanded = settled.isExpanded
                scope.launch {
                    animatedWidthDp.animateTo(
                        targetValue = settled.visibleSizeDp,
                        animationSpec = motion.defaultSpatialSpec(),
                        initialVelocity = velocityDp
                    )
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CapabilitiesPalette(
    plugins: List<PluginEntry>,
    searchQuery: String,
    rootLayoutCoordinates: LayoutCoordinates?,
    onDragStart: (PaletteNode, Offset, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onClick: (PaletteNode) -> Unit,
    hasUnsavedChanges: Boolean = false,
    router: GlobalRouter? = null,
    onNavigateToPluginSetting: ((pluginId: String, settingKey: String) -> Unit)? = null
) {
    val pluginManager = koinInject<PluginManager>()
    val pluginLocksState by pluginManager.pluginLocksState.collectAsState()
    val collapsedPlugins = remember { mutableStateMapOf<String, Boolean>() }
    val scrollState = rememberScrollState()
    val scrollbarReserve = if (scrollState.maxValue > 0) {
        ToolkitTheme.dimensions.sduiScrollbarEndPadding
    } else {
        ToolkitTheme.spacing.none
    }

    val groupedCaps = remember(searchQuery, plugins) {
        plugins.map { p ->
            val manifest = p.getManifest().getOrThrow()
            manifest.plugin to manifest.capabilities.filter {
                it.context != CapabilityContext.STANDALONE_ONLY &&
                    !it.isInteractive &&
                    it.context != CapabilityContext.INTERACTIVE_ONLY &&
                    it.name.contains(searchQuery, ignoreCase = true)
            }
        }.filter { it.second.isNotEmpty() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(
                    start = ToolkitTheme.spacing.mediumSmall,
                    top = ToolkitTheme.spacing.mediumSmall,
                    bottom = ToolkitTheme.spacing.mediumSmall,
                    end = ToolkitTheme.spacing.mediumSmall + scrollbarReserve
                ),
            verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium)
        ) {
            groupedCaps.forEach { (plugin, caps) ->
                val settingsStore = remember(plugin.id, plugins) { pluginManager.loadPluginSettings(plugin.id) }
                val manifest = remember(plugin.id, plugins) {
                    plugins.find { it.getManifest().getOrThrow().plugin.id == plugin.id }?.getManifest()?.getOrNull()
                }
                val locks = pluginLocksState[plugin.id] ?: pluginLocksState.values.fold(emptyMap<String, Boolean>()) { acc, map -> acc + map }
                val settings = settingsStore.settings + settingsStore.globalParams
                val isGroupCollapsed = collapsedPlugins[plugin.id] == true && searchQuery.isBlank()
                val chevronRotation by animateFloatAsState(
                    targetValue = if (isGroupCollapsed) 180f else 0f,
                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                    label = "paletteGroupChevron"
                )

                Column(verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ToolkitTheme.shapes.small)
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable {
                                collapsedPlugins[plugin.id] = !(collapsedPlugins[plugin.id] == true)
                            }
                            .padding(
                                horizontal = ToolkitTheme.spacing.extraSmall,
                                vertical = ToolkitTheme.spacing.extraSmall
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = plugin.name.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier
                                .size(ToolkitTheme.dimensions.iconSmall)
                                .graphicsLayer { rotationZ = chevronRotation }
                        )
                    }

                    AnimatedVisibility(
                        visible = !isGroupCollapsed,
                        enter = expandVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
                        exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec())
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall)) {
                            caps.forEach { cap ->
                                val lockStatus = CapabilityLockUtils.checkCapabilityLockStatus(cap, locks, settings)
                                val isLocked = lockStatus is CapabilityLockStatus.Locked
                                val isConfigReady = remember(cap, settingsStore.settings, manifest?.settings) {
                                    cap.isReady(settingsStore.settings, manifest?.settings)
                                }
                                val isReady = !isLocked && isConfigReady

                                val targetSettingKey = when (lockStatus) {
                                    is CapabilityLockStatus.Locked ->
                                        lockStatus.missingLocks.firstOrNull() ?: lockStatus.missingSettings.firstOrNull() ?: ""
                                    else -> cap.requiredLocks.firstOrNull() ?: cap.requiresSettings.firstOrNull() ?: ""
                                }

                                val paletteNode = PaletteNode.Capability(plugin, cap)
                                PaletteItem(
                                    text = cap.name,
                                    color = MaterialTheme.colorScheme.primary,
                                    enabled = isReady,
                                    tooltip = if (!isReady) "Configuration required or capability is locked" else null,
                                    targetScreen = Screen.PluginManager(plugin.id, targetSettingKey),
                                    targetSettingKey = targetSettingKey,
                                    pluginId = plugin.id,
                                    hasUnsavedChanges = hasUnsavedChanges,
                                    router = router,
                                    onNavigateToPluginSetting = onNavigateToPluginSetting,
                                    rootLayoutCoordinates = rootLayoutCoordinates,
                                    onDragStart = { pos, grabOffset -> onDragStart(paletteNode, pos, grabOffset) },
                                    onDrag = onDrag,
                                    onDragEnd = onDragEnd,
                                    onClick = { onClick(paletteNode) }
                                )
                            }
                        }
                    }
                }
            }
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
private fun SystemPalette(
    searchQuery: String,
    rootLayoutCoordinates: LayoutCoordinates?,
    onDragStart: (PaletteNode, Offset, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onClick: (PaletteNode) -> Unit
) {
    val scrollState = rememberScrollState()
    val scrollbarReserve = if (scrollState.maxValue > 0) {
        ToolkitTheme.dimensions.sduiScrollbarEndPadding
    } else {
        ToolkitTheme.spacing.none
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(
                    start = ToolkitTheme.spacing.mediumSmall,
                    top = ToolkitTheme.spacing.mediumSmall,
                    bottom = ToolkitTheme.spacing.mediumSmall,
                    end = ToolkitTheme.spacing.mediumSmall + scrollbarReserve
                ),
            verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall)) {
                val standardNodes = listOf(
                    PaletteNode.FlowInput to "Flow Input",
                    PaletteNode.FlowOutput to "Flow Output",
                    PaletteNode.System("Error") to "Error"
                ).filter { it.second.contains(searchQuery, ignoreCase = true) }

                if (standardNodes.isNotEmpty()) {
                    Text(
                        "STANDARD NODES",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(
                            horizontal = ToolkitTheme.spacing.extraSmall,
                            vertical = ToolkitTheme.spacing.extraSmall
                        )
                    )

                    standardNodes.forEach { (node, text) ->
                        val color = if (node is PaletteNode.System && node.action == "Error") {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.tertiary
                        }
                        PaletteItem(
                            text = text,
                            color = color,
                            rootLayoutCoordinates = rootLayoutCoordinates,
                            onDragStart = { pos, grabOffset -> onDragStart(node, pos, grabOffset) },
                            onDrag = onDrag,
                            onDragEnd = onDragEnd,
                            onClick = { onClick(node) }
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall)) {
                val systemActions = listOf(
                    "Save",
                    "save_file",
                    "save_folder",
                    "Load",
                    "Log",
                    "Delay",
                    "Convert",
                    "Merger",
                    "string_merger",
                    "extract_from_string",
                    "list_filter",
                    "list_check",
                    "Conditional",
                    "Comparator",
                    "For",
                    "While",
                    "create_folder"
                ).filter {
                    it.contains(searchQuery, ignoreCase = true) ||
                        getSystemNodeDisplayName(it).contains(searchQuery, ignoreCase = true)
                }

                if (systemActions.isNotEmpty()) {
                    Text(
                        "SYSTEM ACTIONS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(
                            horizontal = ToolkitTheme.spacing.extraSmall,
                            vertical = ToolkitTheme.spacing.extraSmall
                        )
                    )

                    systemActions.forEach { action ->
                        val systemNode = PaletteNode.System(action)
                        PaletteItem(
                            text = getSystemNodeDisplayName(action),
                            color = ToolkitTheme.colors.success,
                            rootLayoutCoordinates = rootLayoutCoordinates,
                            onDragStart = { pos, grabOffset -> onDragStart(systemNode, pos, grabOffset) },
                            onDrag = onDrag,
                            onDragEnd = onDragEnd,
                            onClick = { onClick(systemNode) }
                        )
                    }
                }
            }
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
private fun FlowsPalette(
    flows: List<Flow>,
    searchQuery: String,
    rootLayoutCoordinates: LayoutCoordinates?,
    onDragStart: (PaletteNode, Offset, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onClick: (PaletteNode) -> Unit
) {
    val filteredFlows = remember(searchQuery, flows) {
        flows.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }
    val scrollState = rememberScrollState()
    val scrollbarReserve = if (scrollState.maxValue > 0) {
        ToolkitTheme.dimensions.sduiScrollbarEndPadding
    } else {
        ToolkitTheme.spacing.none
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(
                    start = ToolkitTheme.spacing.mediumSmall,
                    top = ToolkitTheme.spacing.mediumSmall,
                    bottom = ToolkitTheme.spacing.mediumSmall,
                    end = ToolkitTheme.spacing.mediumSmall + scrollbarReserve
                ),
            verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
        ) {
            filteredFlows.forEach { flow ->
                val subFlow = PaletteNode.SubFlow(flow.name)
                PaletteItem(
                    text = flow.name,
                    color = MaterialTheme.colorScheme.secondary,
                    rootLayoutCoordinates = rootLayoutCoordinates,
                    onDragStart = { pos, grabOffset -> onDragStart(subFlow, pos, grabOffset) },
                    onDrag = onDrag,
                    onDragEnd = onDragEnd,
                    onClick = { onClick(subFlow) }
                )
            }
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
fun PaletteItem(
    text: String,
    color: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
    tooltip: String? = null,
    targetScreen: NavKey? = null,
    targetSettingKey: String = "",
    pluginId: String = "",
    hasUnsavedChanges: Boolean = false,
    router: GlobalRouter? = null,
    onNavigateToPluginSetting: ((pluginId: String, settingKey: String) -> Unit)? = null,
    rootLayoutCoordinates: LayoutCoordinates?,
    onDragStart: (Offset, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onClick: () -> Unit
) {
    var lastPosition by remember { mutableStateOf(Offset.Zero) }
    var cumulativeDrag by remember { mutableStateOf(Offset.Zero) }

    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)

    ToolkitTooltipBox(
        tooltip = tooltip ?: text,
        modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.5f)
    ) {
        Surface(
            onClick = { if (enabled) onClick() },
            modifier = Modifier
                .fillMaxWidth()
                .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
                .then(
                    if (onNavigateToPluginSetting != null) {
                        Modifier.lockedClickInterceptor(
                            isLocked = !enabled,
                            pluginId = pluginId,
                            targetSettingKey = targetSettingKey,
                            hasUnsavedChanges = hasUnsavedChanges,
                            onNavigateToPluginSetting = onNavigateToPluginSetting
                        )
                    } else {
                        val resolvedScreen = targetScreen ?: if (pluginId.isNotEmpty() || targetSettingKey.isNotEmpty()) {
                            Screen.PluginManager(pluginId = pluginId.ifEmpty { null }, scrollToSetting = targetSettingKey.ifEmpty { null })
                        } else null
                        Modifier.lockedClickInterceptor(
                            isLocked = !enabled,
                            targetScreen = resolvedScreen,
                            hasUnsavedChanges = hasUnsavedChanges,
                            router = router
                        )
                    }
                )
                .pointerInput(enabled) {
                    if (enabled) {
                        detectDragGestures(
                            onDragStart = { localOffset ->
                                cumulativeDrag = Offset.Zero
                                currentOnDragStart(localOffset + lastPosition, localOffset)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                cumulativeDrag += dragAmount
                                currentOnDrag(cumulativeDrag)
                            },
                            onDragEnd = currentOnDragEnd,
                            onDragCancel = currentOnDragEnd
                        )
                    }
                }
                .onGloballyPositioned {
                    val localPos =
                        if (rootLayoutCoordinates != null && it.isAttached && rootLayoutCoordinates.isAttached) {
                            rootLayoutCoordinates.localPositionOf(it, Offset.Zero)
                        } else {
                            it.positionInWindow()
                        }
                    lastPosition = localPos
                },
            shape = ToolkitTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(
                ToolkitTheme.dimensions.borderUnselected,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = ToolkitTheme.opacity.textFieldUnfocusedBorder)
            )
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = ToolkitTheme.spacing.medium,
                    vertical = ToolkitTheme.spacing.mediumSmall
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.mediumSmall)
            ) {
                Box(
                    modifier = Modifier
                        .size(ToolkitTheme.spacing.small)
                        .background(color, CircleShape)
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PaletteItemPreview(
    node: PaletteNode,
    isOverBoard: Boolean = true
) {
    val (color, onColor) = when (node) {
        is PaletteNode.Capability -> Pair(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
        is PaletteNode.System -> {
            if (node.action.lowercase() == "error") {
                Pair(MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.onError)
            } else {
                Pair(ToolkitTheme.colors.success, ToolkitTheme.colors.white)
            }
        }

        is PaletteNode.FlowInput -> Pair(MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.onTertiary)
        is PaletteNode.FlowOutput -> Pair(MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.onTertiary)
        is PaletteNode.SubFlow -> Pair(MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.onSecondary)
    }

    val previewScale by animateFloatAsState(
        targetValue = if (isOverBoard) 1f else PALETTE_PREVIEW_OFF_BOARD_SCALE,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "palettePreviewScale"
    )

    val borderColor = if (isOverBoard) {
        color
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = ToolkitTheme.opacity.high)
    }

    val cueText = stringResource(
        if (isOverBoard) Res.string.flow_palette_drop_on_board else Res.string.flow_palette_drag_to_board
    )

    Surface(
        modifier = Modifier
            .width(ToolkitTheme.dimensions.containerWidthLarge)
            .graphicsLayer {
                scaleX = previewScale
                scaleY = previewScale
            },
        shape = ToolkitTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = ToolkitTheme.dimensions.elevationHigh,
        border = BorderStroke(ToolkitTheme.dimensions.progressIndicatorStroke, borderColor)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isOverBoard) color else color.copy(alpha = ToolkitTheme.opacity.secondaryText))
                    .padding(ToolkitTheme.spacing.mediumSmall)
            ) {
                Text(
                    text = when (node) {
                        is PaletteNode.Capability -> node.capability.name
                        is PaletteNode.System -> getSystemNodeDisplayName(node.action)
                        is PaletteNode.FlowInput -> "Flow Input"
                        is PaletteNode.FlowOutput -> "Flow Output"
                        is PaletteNode.SubFlow -> node.name
                    },
                    color = onColor,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ToolkitTheme.dimensions.emptyStateIconSize)
                    .padding(ToolkitTheme.spacing.mediumSmall),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = cueText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isOverBoard) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = ToolkitTheme.opacity.secondaryText)
                    }
                )
            }
        }
    }
}
