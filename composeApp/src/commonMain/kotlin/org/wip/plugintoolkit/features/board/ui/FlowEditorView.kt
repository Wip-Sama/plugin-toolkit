package org.wip.plugintoolkit.features.board.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.wip.plugintoolkit.features.board.viewmodel.FlowEvent
import org.wip.plugintoolkit.features.board.viewmodel.FlowViewModel
import org.wip.plugintoolkit.features.plugin.logic.PluginLoader
import org.wip.plugintoolkit.shared.components.ZoomControls
import kotlin.math.roundToInt

@Composable
fun FlowEditorView(
    viewModel: FlowViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val flow = state.currentFlow ?: return
    val density = androidx.compose.ui.platform.LocalDensity.current
    
    val gridSize = 50f
    var boardSize by remember { mutableStateOf(IntSize.Zero) }
    var boardLayoutCoordinates by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    var rootLayoutCoordinates by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }

    // State for temporary connection drawing
    var isDrawingConnection by remember { mutableStateOf(false) }
    var connectionStartNodeId by remember { mutableStateOf<Long?>(null) }
    var connectionStartPortId by remember { mutableStateOf<String?>(null) }
    var connectionStartIsOutput by remember { mutableStateOf(true) }
    var connectionCurrentPos by remember { mutableStateOf(Offset.Zero) }

    // State for port position tracking
    val portPositions = remember { mutableStateMapOf<Pair<Long, String>, Offset>() } // Stored in Board Space
    var highlightedPortId by remember { mutableStateOf<String?>(null) }
    var highlightedNodeId by remember { mutableStateOf<Long?>(null) }

    var selectedTab by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    // State for drag and drop from palette
    var draggingNodeFromPalette by remember { mutableStateOf<PaletteNode?>(null) }
    var draggingNodePos by remember { mutableStateOf(Offset.Zero) }
    var dragStartPosition by remember { mutableStateOf(Offset.Zero) }

    Box(modifier = modifier.fillMaxSize()
        .onGloballyPositioned { rootLayoutCoordinates = it }
        .pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                val position = event.changes.firstOrNull()?.position ?: Offset.Zero

                if (draggingNodeFromPalette != null) {
                    draggingNodePos = position
                    if (event.type == PointerEventType.Release) {
                        val boardOffset = boardLayoutCoordinates?.positionInParent() ?: Offset.Zero
                        val relativeToBoard = position - boardOffset
                        val isOverBoard = relativeToBoard.x >= 0 && relativeToBoard.y >= 0 && relativeToBoard.x <= boardSize.width && relativeToBoard.y <= boardSize.height
                        
                        val dragDistance = (position - dragStartPosition).getDistance()
                        val isClick = dragDistance < 5f // Small threshold for a click
                        
                        val dropPos = if (isClick) {
                            // Add to center of board
                            (Offset(boardSize.width / 2f, boardSize.height / 2f) - state.offset) / state.scale
                        } else {
                            (relativeToBoard - state.offset) / state.scale
                        }

                        if (isClick || isOverBoard) {
                            draggingNodeFromPalette?.let { paletteNode ->
                                when (paletteNode) {
                                    is PaletteNode.Capability -> viewModel.onEvent(FlowEvent.AddCapabilityNode(paletteNode.pluginInfo, paletteNode.capability, dropPos))
                                    is PaletteNode.System -> viewModel.onEvent(FlowEvent.AddSystemNode(paletteNode.action, dropPos))
                                    is PaletteNode.FlowInput -> viewModel.onEvent(FlowEvent.AddFlowInputNode(dropPos))
                                    is PaletteNode.FlowOutput -> viewModel.onEvent(FlowEvent.AddFlowOutputNode(dropPos))
                                    is PaletteNode.SubFlow -> viewModel.onEvent(FlowEvent.AddSubFlowNode(paletteNode.name, dropPos))
                                }
                            }
                        }
                        draggingNodeFromPalette = null
                    }
                }
            }
        }
    }) {
        // 1. Main Board Area (Background Layer)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { boardLayoutCoordinates = it }
                .onSizeChanged { boardSize = it }
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, _, _ ->
                        if (!isDrawingConnection && draggingNodeFromPalette == null) {
                            viewModel.onEvent(FlowEvent.Pan(pan))
                        }
                    }
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val position = event.changes.firstOrNull()?.position ?: Offset.Zero

                            if (isDrawingConnection) {
                                val boardPosition = (position - state.offset) / state.scale
                                connectionCurrentPos = boardPosition
                                
                                // Proximity Check: Find closest port in Board Space
                                var closestPortId: String? = null
                                var closestNodeId: Long? = null
                                var minDistance = 30f / state.scale // 30dp radius in board space

                                flow.nodes.forEach { node ->
                                    // If dragging from Output, look for Inputs. If dragging from Input, look for Outputs.
                                    val portsToTrack = if (connectionStartIsOutput) node.inputs else node.outputs
                                    portsToTrack.forEach { port ->
                                        val portBoardPos = portPositions[node.id to port.id] ?: return@forEach
                                        val dist = (boardPosition - portBoardPos).getDistance()
                                        if (dist < minDistance) {
                                            minDistance = dist
                                            closestPortId = port.id
                                            closestNodeId = node.id
                                        }
                                    }
                                }
                                
                                highlightedPortId = closestPortId
                                highlightedNodeId = closestNodeId

                                if (event.type == PointerEventType.Release) {
                                    if (highlightedPortId != null && highlightedNodeId != null && connectionStartNodeId != null && connectionStartPortId != null) {
                                        val sourceNodeId = if (connectionStartIsOutput) connectionStartNodeId!! else highlightedNodeId!!
                                        val sourcePortId = if (connectionStartIsOutput) connectionStartPortId!! else highlightedPortId!!
                                        val targetNodeId = if (connectionStartIsOutput) highlightedNodeId!! else connectionStartNodeId!!
                                        val targetPortId = if (connectionStartIsOutput) highlightedPortId!! else connectionStartPortId!!

                                        viewModel.onEvent(FlowEvent.ConnectPorts(
                                            sourceNodeId, 
                                            sourcePortId, 
                                            targetNodeId, 
                                            targetPortId
                                        ))
                                    }
                                    isDrawingConnection = false
                                    connectionStartNodeId = null
                                    connectionStartPortId = null
                                    highlightedPortId = null
                                    highlightedNodeId = null
                                }
                            }

                            if (event.type == PointerEventType.Scroll) {
                                val scrollDeltaY = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                if (scrollDeltaY != 0f) {
                                    viewModel.onEvent(FlowEvent.Zoom(scrollDeltaY, position))
                                }
                            }
                        }
                    }
                }
        ) {
            // 1.1 Grid of Dots
            Canvas(modifier = Modifier.fillMaxSize()) {
                val scaledGridSize = gridSize * state.scale
                val startX = (state.offset.x % scaledGridSize) - scaledGridSize
                val startY = (state.offset.y % scaledGridSize) - scaledGridSize

                val cols = (size.width / scaledGridSize).toInt() + 2
                val rows = (size.height / scaledGridSize).toInt() + 2

                for (i in 0..cols) {
                    for (j in 0..rows) {
                        val x = startX + i * scaledGridSize
                        val y = startY + j * scaledGridSize
                        drawCircle(
                            color = Color.Gray.copy(alpha = 0.4f),
                            radius = 2f * state.scale,
                            center = Offset(x, y)
                        )
                    }
                }

                // Draw connections
                flow.connections.forEach { connection ->
                    val sourcePortBoardPos = portPositions[connection.sourceNodeId to connection.sourcePortId]
                    val targetPortBoardPos = portPositions[connection.targetNodeId to connection.targetPortId]
                    
                    if (sourcePortBoardPos != null && targetPortBoardPos != null) {
                        val startPos = (sourcePortBoardPos * state.scale) + state.offset
                        val endPos = (targetPortBoardPos * state.scale) + state.offset
                        drawBezierCurve(startPos, endPos, Color.White)
                    }
                }

                // Draw temporary connection
                if (isDrawingConnection && connectionStartNodeId != null && connectionStartPortId != null) {
                    val startBoardPos = portPositions[connectionStartNodeId!! to connectionStartPortId!!]
                    if (startBoardPos != null) {
                        val currentPos = if (highlightedPortId != null && highlightedNodeId != null) {
                            portPositions[highlightedNodeId!! to highlightedPortId!!]!!
                        } else {
                            connectionCurrentPos
                        }

                        val (startPos, endPos) = if (connectionStartIsOutput) {
                            startBoardPos to currentPos
                        } else {
                            currentPos to startBoardPos
                        }

                        drawBezierCurve(
                            (startPos * state.scale) + state.offset,
                            (endPos * state.scale) + state.offset,
                            Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            // 1.2 Nodes
            flow.nodes.forEach { node ->
                key(node.id) {
                    val isDragged = state.draggedNodeId == node.id
                    val dragOffset = if (isDragged) state.currentDragOffset else Offset.Zero
                
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (((node.position.x + dragOffset.x) * state.scale) + state.offset.x).roundToInt(),
                                (((node.position.y + dragOffset.y) * state.scale) + state.offset.y).roundToInt()
                            )
                        }
                        .graphicsLayer(
                            scaleX = state.scale,
                            scaleY = state.scale,
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                        )
                ) {
                    NodeComponent(
                        node = node,
                        connectedInputPortIds = flow.connections.filter { it.targetNodeId == node.id }.map { it.targetPortId }.toSet(),
                        onMove = { id, delta, snap, showGhost -> viewModel.onEvent(FlowEvent.MoveNode(id, delta, snap, showGhost)) },
                        onEndMove = { id -> viewModel.onEvent(FlowEvent.EndMoveNode(id)) },
                        onDelete = { id -> viewModel.onEvent(FlowEvent.DeleteNode(id)) },
                        onExpand = { id -> viewModel.onEvent(FlowEvent.ExpandSubFlow(id)) },
                        onUpdateValue = { id, portId, value -> viewModel.onEvent(FlowEvent.UpdateInputPortValue(id, portId, value)) },
                        onStartConnection = { nodeId, portId, isOutput -> 
                            isDrawingConnection = true
                            connectionStartNodeId = nodeId
                            connectionStartPortId = portId
                            connectionStartIsOutput = isOutput
                        },
                        onDropConnection = { nodeId, portId ->
                            // This is still here but the release is now handled by the board's pointerInput for snapping
                        },
                        onPortPositioned = { nodeId, portId, coords -> 
                            boardLayoutCoordinates?.let { boardCoords ->
                                // Using 7.dp offset to get the center of the 14.dp circle
                                val offset = with(density) { Offset(7.dp.toPx(), 7.dp.toPx()) }
                                val viewPos = boardCoords.localPositionOf(coords, offset)
                                val boardPos = (viewPos - state.offset) / state.scale
                                portPositions[nodeId to portId] = boardPos
                            }
                        },
                        onPress = { id -> viewModel.onEvent(FlowEvent.BringToFront(id)) },
                        highlightedPortId = if (highlightedNodeId == node.id) highlightedPortId else null
                    )
                }
            }
        }

            // Ghost Preview for Snapping
            state.ghostPosition?.let { ghostPos ->
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                ((ghostPos.x * state.scale) + state.offset.x).roundToInt(),
                                ((ghostPos.y * state.scale) + state.offset.y).roundToInt()
                            )
                        }
                        .graphicsLayer(
                            scaleX = state.scale,
                            scaleY = state.scale,
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                        )
                        .alpha(0.3f)
                ) {
                    state.currentFlow?.nodes?.find { it.id == state.draggedNodeId }?.let { NodeComponentPlaceholder(it) }
                }
            }

            // 1.3 Zoom Controls UI - Bottom Right
            val centerPosition = Offset(boardSize.width / 2f, boardSize.height / 2f)
            ZoomControls(
                scale = state.scale,
                onZoomIn = { viewModel.onEvent(FlowEvent.Zoom(-1f, centerPosition)) },
                onZoomOut = { viewModel.onEvent(FlowEvent.Zoom(1f, centerPosition)) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            )

        }

        // 2. Left Sidebar (Overlay Layer)
        Surface(
            modifier = Modifier.width(300.dp).fillMaxHeight(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            shadowElevation = 8.dp
        ) {
            Column {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                        Text("Plugins", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.labelMedium)
                    }
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                        Text("System", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.labelMedium)
                    }
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }) {
                        Text("Flows", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.labelMedium)
                    }
                }

                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search nodes...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent
                    )
                )

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (selectedTab) {
                        0 -> CapabilitiesPalette(
                            searchQuery = searchQuery,
                            onDragStart = { info, cap, pos -> 
                                draggingNodeFromPalette = PaletteNode.Capability(info, cap)
                                draggingNodePos = pos
                                dragStartPosition = pos
                            }
                        )
                        1 -> SystemPalette(
                            onDragStart = { node, pos ->
                                draggingNodeFromPalette = node
                                draggingNodePos = pos
                                dragStartPosition = pos
                            }
                        )
                        2 -> FlowsPalette(
                            flows = state.flows.filter { it.name != flow.name },
                            searchQuery = searchQuery,
                            onDragStart = { name, pos ->
                                draggingNodeFromPalette = PaletteNode.SubFlow(name)
                                draggingNodePos = pos
                                dragStartPosition = pos
                            }
                        )
                    }
                }
            }
        }

        // 3. Top Status Bar (Overlay Layer)
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            tonalElevation = 8.dp,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column {
                    Text(
                        text = "Flow Selected",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = flow.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = { viewModel.onEvent(FlowEvent.Save) },
                    enabled = state.hasUnsavedChanges,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("Save Changes")
                }
            }
        }


        // 4. Global Dragging Preview (Highest Layer)
        draggingNodeFromPalette?.let { node ->
            Box(
                modifier = Modifier
                    .offset { IntOffset(draggingNodePos.x.roundToInt(), draggingNodePos.y.roundToInt()) }
                    .alpha(0.7f)
            ) {
                PaletteItemPreview(node)
            }
        }
    }
}

@Composable
private fun NodeComponentPlaceholder(node: org.wip.plugintoolkit.features.board.model.Node) {
    Surface(
        modifier = Modifier.width(300.dp).height(120.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.2f),
        border = androidx.compose.foundation.BorderStroke(2.dp, Color.White.copy(alpha = 0.4f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(node.title, color = Color.White.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun CapabilitiesPalette(
    searchQuery: String, 
    onDragStart: (org.wip.plugintoolkit.api.PluginInfo, org.wip.plugintoolkit.api.Capability, Offset) -> Unit
) {
    val plugins = remember { PluginLoader.getPlugins() }
    val groupedCaps = remember(searchQuery, plugins) {
        plugins.map { p ->
            val manifest = p.getManifest()
            manifest.plugin to manifest.capabilities.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }.filter { it.second.isNotEmpty() }
    }

    Column(modifier = Modifier.padding(8.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        groupedCaps.forEach { (plugin, caps) ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = plugin.name.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                caps.forEach { cap ->
                    PaletteItem(
                        text = cap.name,
                        color = Color(0xFF1E88E5),
                        onDragStart = { pos -> onDragStart(plugin, cap, pos) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SystemPalette(onDragStart: (PaletteNode, Offset) -> Unit) {
    Column(modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Standard Nodes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        
        PaletteItem(text = "Flow Input", color = Color(0xFFE53935), onDragStart = { onDragStart(PaletteNode.FlowInput, it) })
        PaletteItem(text = "Flow Output", color = Color(0xFFE53935), onDragStart = { onDragStart(PaletteNode.FlowOutput, it) })
        
        Spacer(modifier = Modifier.height(8.dp))
        Text("System Actions", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        
        listOf("Save", "Load", "Log", "Delay").forEach { action ->
            PaletteItem(text = action, color = Color(0xFF43A047), onDragStart = { onDragStart(PaletteNode.System(action), it) })
        }
    }
}

@Composable
private fun FlowsPalette(
    flows: List<org.wip.plugintoolkit.features.board.model.Flow>, 
    searchQuery: String, 
    onDragStart: (String, Offset) -> Unit
) {
    val filteredFlows = remember(searchQuery, flows) {
        flows.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Column(modifier = Modifier.padding(8.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        filteredFlows.forEach { flow ->
            PaletteItem(text = flow.name, color = Color(0xFF8E24AA), onDragStart = { onDragStart(flow.name, it) })
        }
    }
}

@Composable
private fun PaletteItem(text: String, color: Color = MaterialTheme.colorScheme.primary, onDragStart: (Offset) -> Unit) {
    var lastPosition by remember { mutableStateOf(Offset.Zero) }
    
    Surface(
        onClick = { /* Dragging handles this */ },
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Press) {
                            onDragStart(event.changes.first().position + lastPosition)
                        }
                    }
                }
            }
            .onGloballyPositioned { lastPosition = it.positionInWindow() },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, CircleShape)
            )
            Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PaletteItemPreview(node: PaletteNode) {
    val color = when (node) {
        is PaletteNode.Capability -> Color(0xFF1E88E5)
        is PaletteNode.System -> Color(0xFF43A047)
        is PaletteNode.FlowInput -> Color(0xFFE53935)
        is PaletteNode.FlowOutput -> Color(0xFFE53935)
        is PaletteNode.SubFlow -> Color(0xFF8E24AA)
    }

    Surface(
        modifier = Modifier.width(220.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shadowElevation = 12.dp,
        border = androidx.compose.foundation.BorderStroke(2.dp, color)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(color)
                    .padding(8.dp)
            ) {
                Text(
                    text = when(node) {
                        is PaletteNode.Capability -> node.capability.name
                        is PaletteNode.System -> node.action
                        is PaletteNode.FlowInput -> "Flow Input"
                        is PaletteNode.FlowOutput -> "Flow Output"
                        is PaletteNode.SubFlow -> node.name
                    },
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(8.dp),
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

sealed class PaletteNode {
    data class Capability(val pluginInfo: org.wip.plugintoolkit.api.PluginInfo, val capability: org.wip.plugintoolkit.api.Capability) : PaletteNode()
    data class System(val action: String) : PaletteNode()
    object FlowInput : PaletteNode()
    object FlowOutput : PaletteNode()
    data class SubFlow(val name: String) : PaletteNode()
}

private fun DrawScope.drawBezierCurve(start: Offset, end: Offset, color: Color) {
    val controlPointOffset = kotlin.math.abs(end.x - start.x) / 2f
    val path = Path().apply {
        moveTo(start.x, start.y)
        cubicTo(
            start.x + controlPointOffset, start.y,
            end.x - controlPointOffset, end.y,
            end.x, end.y
        )
    }
    drawPath(path = path, color = color, style = Stroke(width = 3.dp.toPx()))
}
