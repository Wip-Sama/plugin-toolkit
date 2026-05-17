package org.wip.plugintoolkit.features.flows.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.plugin.logic.PluginLoader
import org.wip.plugintoolkit.api.PluginEntry

sealed class PaletteNode {
    data class Capability(val pluginInfo: org.wip.plugintoolkit.api.PluginInfo, val capability: org.wip.plugintoolkit.api.Capability) : PaletteNode()
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
    onDragStart: (PaletteNode, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onClick: (PaletteNode) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    
    Surface(
        modifier = modifier.width(300.dp).fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = ToolkitTheme.spacing.extraSmall,
        shadowElevation = ToolkitTheme.spacing.small
    ) {
        Column {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                    Text("Plugins", modifier = Modifier.padding(ToolkitTheme.spacing.mediumSmall), style = MaterialTheme.typography.labelMedium)
                }
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                    Text("System", modifier = Modifier.padding(ToolkitTheme.spacing.mediumSmall), style = MaterialTheme.typography.labelMedium)
                }
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }) {
                    Text("Flows", modifier = Modifier.padding(ToolkitTheme.spacing.mediumSmall), style = MaterialTheme.typography.labelMedium)
                }
            }

            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search nodes...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().padding(ToolkitTheme.spacing.small),
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent
                )
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
    onDragStart: (PaletteNode, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onClick: (PaletteNode) -> Unit
) {
    val groupedCaps = remember(searchQuery, plugins) {
        plugins.map { p ->
            val manifest = p.getManifest()
            manifest.plugin to manifest.capabilities.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }.filter { it.second.isNotEmpty() }
    }

    Column(
        modifier = Modifier
            .padding(ToolkitTheme.spacing.small)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium)
    ) {
        groupedCaps.forEach { (plugin, caps) ->
            Column(verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall)) {
                Text(
                    text = plugin.name.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = ToolkitTheme.spacing.small)
                )
                caps.forEach { cap ->
                    val paletteNode = PaletteNode.Capability(plugin, cap)
                    PaletteItem(
                        text = cap.name,
                        color = MaterialTheme.colorScheme.primary,
                        rootLayoutCoordinates = rootLayoutCoordinates,
                        onDragStart = { pos -> onDragStart(paletteNode, pos) },
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
    onDragStart: (PaletteNode, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onClick: (PaletteNode) -> Unit
) {
    Column(
        modifier = Modifier
            .padding(ToolkitTheme.spacing.mediumSmall)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.mediumSmall)
    ) {
        Text("Standard Nodes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        
        val flowInput = PaletteNode.FlowInput
        PaletteItem(
            text = "Flow Input",
            color = MaterialTheme.colorScheme.tertiary,
            rootLayoutCoordinates = rootLayoutCoordinates,
            onDragStart = { onDragStart(flowInput, it) },
            onDrag = onDrag,
            onDragEnd = onDragEnd,
            onClick = { onClick(flowInput) }
        )
        
        val flowOutput = PaletteNode.FlowOutput
        PaletteItem(
            text = "Flow Output",
            color = MaterialTheme.colorScheme.tertiary,
            rootLayoutCoordinates = rootLayoutCoordinates,
            onDragStart = { onDragStart(flowOutput, it) },
            onDrag = onDrag,
            onDragEnd = onDragEnd,
            onClick = { onClick(flowOutput) }
        )
        
        Spacer(modifier = Modifier.height(ToolkitTheme.spacing.small))
        Text("System Actions", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        
        listOf("Save", "Load", "Log", "Delay").forEach { action ->
            val systemNode = PaletteNode.System(action)
            PaletteItem(
                text = action,
                color = ToolkitTheme.colors.success,
                rootLayoutCoordinates = rootLayoutCoordinates,
                onDragStart = { onDragStart(systemNode, it) },
                onDrag = onDrag,
                onDragEnd = onDragEnd,
                onClick = { onClick(systemNode) }
            )
        }
    }
}

@Composable
private fun FlowsPalette(
    flows: List<Flow>, 
    searchQuery: String, 
    rootLayoutCoordinates: LayoutCoordinates?,
    onDragStart: (PaletteNode, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onClick: (PaletteNode) -> Unit
) {
    val filteredFlows = remember(searchQuery, flows) {
        flows.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .padding(ToolkitTheme.spacing.small)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
    ) {
        filteredFlows.forEach { flow ->
            val subFlow = PaletteNode.SubFlow(flow.name)
            PaletteItem(
                text = flow.name,
                color = MaterialTheme.colorScheme.secondary,
                rootLayoutCoordinates = rootLayoutCoordinates,
                onDragStart = { onDragStart(subFlow, it) },
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
    rootLayoutCoordinates: LayoutCoordinates?,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onClick: () -> Unit
) {
    var lastPosition by remember { mutableStateOf(Offset.Zero) }
    var cumulativeDrag by remember { mutableStateOf(Offset.Zero) }
    
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { localOffset ->
                        cumulativeDrag = Offset.Zero
                        onDragStart(localOffset + lastPosition)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        cumulativeDrag += dragAmount
                        onDrag(cumulativeDrag)
                    },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd
                )
            }
            .onGloballyPositioned {
                val rootCoords = rootLayoutCoordinates
                val localPos = if (rootCoords != null && it.isAttached && rootCoords.isAttached) {
                    rootCoords.localPositionOf(it, Offset.Zero)
                } else {
                    it.positionInWindow()
                }
                lastPosition = localPos
            },
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(ToolkitTheme.spacing.mediumSmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.mediumSmall)
        ) {
            Box(
                modifier = Modifier
                    .size(ToolkitTheme.spacing.small)
                    .background(color, CircleShape)
            )
            Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun PaletteItemPreview(node: PaletteNode) {
    val (color, onColor) = when (node) {
        is PaletteNode.Capability -> Pair(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
        is PaletteNode.System -> Pair(ToolkitTheme.colors.success, Color.White)
        is PaletteNode.FlowInput -> Pair(MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.onTertiary)
        is PaletteNode.FlowOutput -> Pair(MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.onTertiary)
        is PaletteNode.SubFlow -> Pair(MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.onSecondary)
    }

    Surface(
        modifier = Modifier.width(220.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = ToolkitTheme.spacing.small,
        shadowElevation = ToolkitTheme.spacing.mediumSmall,
        border = androidx.compose.foundation.BorderStroke(2.dp, color)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(color)
                    .padding(ToolkitTheme.spacing.small)
            ) {
                Text(
                    text = when(node) {
                        is PaletteNode.Capability -> node.capability.name
                        is PaletteNode.System -> node.action
                        is PaletteNode.FlowInput -> "Flow Input"
                        is PaletteNode.FlowOutput -> "Flow Output"
                        is PaletteNode.SubFlow -> node.name
                    },
                    color = onColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(ToolkitTheme.spacing.small),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Drop to add node",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
