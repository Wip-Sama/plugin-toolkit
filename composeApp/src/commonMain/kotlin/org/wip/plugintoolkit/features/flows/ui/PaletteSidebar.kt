package org.wip.plugintoolkit.features.flows.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.wip.plugintoolkit.api.PluginEntry
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.plugin.logic.PluginManager
import org.wip.plugintoolkit.shared.components.ToolkitTextField
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.palette_search_placeholder
import plugintoolkit.composeapp.generated.resources.palette_tab_flows
import plugintoolkit.composeapp.generated.resources.palette_tab_plugins
import plugintoolkit.composeapp.generated.resources.palette_tab_system

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
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    // Aligns perfectly with standard sidebars
    Surface(
        modifier = modifier.width(280.dp).fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = ToolkitTheme.opacity.textFieldUnfocusedBorder)
        )
    ) {
        Column {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
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

            // Standard sleek search input matching sidebar inputs
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
                modifier = Modifier.fillMaxWidth().padding(ToolkitTheme.spacing.mediumSmall),
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
                        onClick = onClick
                    )

                    1 -> SystemPalette(
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

@Composable
private fun CapabilitiesPalette(
    plugins: List<PluginEntry>,
    searchQuery: String,
    rootLayoutCoordinates: LayoutCoordinates?,
    onDragStart: (PaletteNode, Offset, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onClick: (PaletteNode) -> Unit
) {
    val pluginManager = koinInject<PluginManager>()

    val groupedCaps = remember(searchQuery, plugins) {
        plugins.map { p ->
            val manifest = p.getManifest().getOrThrow()
            manifest.plugin to manifest.capabilities.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }.filter { it.second.isNotEmpty() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(ToolkitTheme.spacing.mediumSmall)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium)
    ) {
        groupedCaps.forEach { (plugin, caps) ->
            val settingsStore = remember(plugin.id, plugins) { pluginManager.loadPluginSettings(plugin.id) }
            val manifest = remember(plugin.id, plugins) {
                plugins.find { it.getManifest().getOrThrow().plugin.id == plugin.id }?.getManifest()?.getOrNull()
            }

            Column(verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall)) {
                // Sleek sidebar headers
                Text(
                    text = plugin.name.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(
                        horizontal = ToolkitTheme.spacing.extraSmall,
                        vertical = ToolkitTheme.spacing.extraSmall
                    )
                )
                caps.forEach { cap ->
                    val isReady = remember(cap, settingsStore.settings, manifest?.settings) {
                        cap.isReady(settingsStore.settings, manifest?.settings)
                    }

                    val paletteNode = PaletteNode.Capability(plugin, cap)
                    PaletteItem(
                        text = cap.name,
                        color = MaterialTheme.colorScheme.primary,
                        enabled = isReady,
                        tooltip = if (!isReady) "Configuration required" else null,
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

@Composable
private fun SystemPalette(
    rootLayoutCoordinates: LayoutCoordinates?,
    onDragStart: (PaletteNode, Offset, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onClick: (PaletteNode) -> Unit
) {
    Column(
        modifier = Modifier
            .padding(ToolkitTheme.spacing.mediumSmall)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall)) {
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

            val flowInput = PaletteNode.FlowInput
            PaletteItem(
                text = "Flow Input",
                color = MaterialTheme.colorScheme.tertiary,
                rootLayoutCoordinates = rootLayoutCoordinates,
                onDragStart = { pos, grabOffset -> onDragStart(flowInput, pos, grabOffset) },
                onDrag = onDrag,
                onDragEnd = onDragEnd,
                onClick = { onClick(flowInput) }
            )

            val flowOutput = PaletteNode.FlowOutput
            PaletteItem(
                text = "Flow Output",
                color = MaterialTheme.colorScheme.tertiary,
                rootLayoutCoordinates = rootLayoutCoordinates,
                onDragStart = { pos, grabOffset -> onDragStart(flowOutput, pos, grabOffset) },
                onDrag = onDrag,
                onDragEnd = onDragEnd,
                onClick = { onClick(flowOutput) }
            )

            val errorNode = PaletteNode.System("Error")
            PaletteItem(
                text = "Error",
                color = MaterialTheme.colorScheme.error,
                rootLayoutCoordinates = rootLayoutCoordinates,
                onDragStart = { pos, grabOffset -> onDragStart(errorNode, pos, grabOffset) },
                onDrag = onDrag,
                onDragEnd = onDragEnd,
                onClick = { onClick(errorNode) }
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall)) {
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

            listOf(
                "Save",
                "save_file",
                "save_folder",
                "Load",
                "Log",
                "Delay",
                "Convert",
                "Merger",
                "Conditional",
                "Comparator",
                "For",
                "While",
                "create_folder"
            ).forEach { action ->
                val systemNode = PaletteNode.System(action)
                PaletteItem(
                    text = action,
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

    Column(
        modifier = Modifier
            .padding(ToolkitTheme.spacing.mediumSmall)
            .verticalScroll(rememberScrollState()),
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
}

@Composable
private fun PaletteItem(
    text: String,
    color: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
    tooltip: String? = null,
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

    // Styled beautifully as premium interactive sidebar items
    org.wip.plugintoolkit.shared.components.TooltipArea(
        tooltip = {
            Text(
                text = tooltip ?: text,
                style = MaterialTheme.typography.bodySmall,
                color = if (!enabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        delayMillis = 1000,
        modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.5f)
    ) {
        Surface(
            onClick = { if (enabled) onClick() },
            modifier = Modifier
                .fillMaxWidth()
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
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = ToolkitTheme.opacity.cardBackground),
            border = BorderStroke(
                1.dp,
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

@Composable
fun PaletteItemPreview(node: PaletteNode) {
    val (color, onColor) = when (node) {
        is PaletteNode.Capability -> Pair(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
        is PaletteNode.System -> {
            if (node.action.lowercase() == "error") {
                Pair(MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.onError)
            } else {
                Pair(ToolkitTheme.colors.success, Color.White)
            }
        }

        is PaletteNode.FlowInput -> Pair(MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.onTertiary)
        is PaletteNode.FlowOutput -> Pair(MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.onTertiary)
        is PaletteNode.SubFlow -> Pair(MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.onSecondary)
    }

    Surface(
        modifier = Modifier.width(300.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = ToolkitTheme.spacing.small,
        shadowElevation = ToolkitTheme.spacing.mediumSmall,
        border = BorderStroke(2.dp, color)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(color)
                    .padding(ToolkitTheme.spacing.mediumSmall)
            ) {
                Text(
                    text = when (node) {
                        is PaletteNode.Capability -> node.capability.name
                        is PaletteNode.System -> node.action
                        is PaletteNode.FlowInput -> "Flow Input"
                        is PaletteNode.FlowOutput -> "Flow Output"
                        is PaletteNode.SubFlow -> node.name
                    },
                    color = onColor,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
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
                    "Drop to add node",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = ToolkitTheme.opacity.secondaryText)
                )
            }
        }
    }
}
