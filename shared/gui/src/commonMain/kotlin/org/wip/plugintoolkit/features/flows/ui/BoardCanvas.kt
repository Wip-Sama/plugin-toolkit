package org.wip.plugintoolkit.features.flows.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.clip
import org.wip.plugintoolkit.shared.components.menu.ToolkitDropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.ui.canvas.BoardGridAndConnectionsCanvas
import org.wip.plugintoolkit.features.flows.ui.canvas.BoardInteractionState
import org.wip.plugintoolkit.features.flows.ui.canvas.SelectionBoxCanvas
import org.wip.plugintoolkit.features.flows.ui.canvas.boardConnectionTapGesture
import org.wip.plugintoolkit.features.flows.ui.canvas.boardKeyboardHandler
import org.wip.plugintoolkit.features.flows.ui.canvas.boardPanGesture
import org.wip.plugintoolkit.features.flows.ui.canvas.boardPointerEventGesture
import org.wip.plugintoolkit.features.flows.ui.canvas.boardSelectionBoxGesture
import org.wip.plugintoolkit.features.flows.utils.BoardMathUtils
import org.wip.plugintoolkit.features.flows.viewmodel.FlowEditorState
import org.wip.plugintoolkit.shared.components.LocalOverlayHost
import org.wip.plugintoolkit.shared.components.ZoomControls
import kotlin.math.roundToInt

@Composable
fun BoardCanvas(
    state: FlowEditorState,
    flow: Flow,
    onPan: (Offset) -> Unit,
    onZoom: (Float, Offset, Boolean) -> Unit,
    isDrawingConnection: Boolean,
    draggingNodeFromPalette: PaletteNode?,
    getPortBoardPosition: (Long, String, Boolean) -> Offset?,
    highlightedPortId: String?,
    highlightedNodeId: Long?,
    connectionStartNodeId: Long?,
    connectionStartPortId: String?,
    connectionStartIsOutput: Boolean,
    connectionCurrentPos: Offset,
    onBoardLayoutCoordinatesChanged: (LayoutCoordinates) -> Unit,
    onBoardSizeChanged: (IntSize) -> Unit,
    onDeleteConnection: (Connection) -> Unit,
    onDetachConnection: (Connection, Boolean, Offset) -> Unit,
    onConnectionDrag: (Offset) -> Unit,
    onConnectionDrop: (Boolean) -> Unit,
    onMoveConnectionFirst: (Connection) -> Unit,
    onMoveConnectionLast: (Connection) -> Unit,
    selectedNodeIds: Set<Long>,
    onSelectNodes: (Set<Long>) -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelectedNodes: () -> Unit,
    onCopy: () -> Unit,
    onPaste: (Offset) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    nodeSizes: Map<Long, IntSize>,
    isReadOnly: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(hoveredConnection: Connection?) -> Unit
) {
    val density = LocalDensity.current
    val focusRequester = remember { FocusRequester() }
    val interactionState = remember { BoardInteractionState() }

    var boardSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(flow.connections) {
        if (interactionState.hoveredConnection != null && !flow.connections.contains(interactionState.hoveredConnection)) {
            interactionState.clearHoveredConnection()
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val dimensions = ToolkitTheme.dimensions
    val spacing = ToolkitTheme.spacing

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("board_canvas")
            .onGloballyPositioned { onBoardLayoutCoordinatesChanged(it) }
            .onSizeChanged {
                boardSize = it
                onBoardSizeChanged(it)
            }
            .background(MaterialTheme.colorScheme.background)
            .focusRequester(focusRequester)
            .focusable()
            .boardKeyboardHandler(
                interactionState = interactionState,
                scale = state.scale,
                offset = state.offset,
                getPortBoardPosition = getPortBoardPosition,
                selectedNodeIds = selectedNodeIds,
                onDeleteSelectedNodes = onDeleteSelectedNodes,
                onUndo = onUndo,
                onRedo = onRedo,
                onCopy = onCopy,
                onPaste = onPaste,
                isReadOnly = isReadOnly
            )
            .boardConnectionTapGesture(
                interactionState = interactionState,
                connections = flow.connections,
                scale = state.scale,
                offset = state.offset,
                getPortBoardPosition = getPortBoardPosition,
                focusRequester = focusRequester,
                onClearSelection = onClearSelection
            )
            .boardPanGesture(
                focusRequester = focusRequester,
                onPan = onPan
            )
            .boardSelectionBoxGesture(
                interactionState = interactionState,
                isDrawingConnection = isDrawingConnection,
                nodes = flow.nodes,
                nodeSizes = nodeSizes,
                density = density,
                scale = state.scale,
                offset = state.offset,
                defaultNodeWidthPx = with(density) { dimensions.nodeWidth.toPx() },
                focusRequester = focusRequester,
                onSelectNodes = onSelectNodes
            )
            .boardPointerEventGesture(
                interactionState = interactionState,
                isDrawingConnection = isDrawingConnection,
                scale = state.scale,
                offset = state.offset,
                nodes = flow.nodes,
                connections = flow.connections,
                getPortBoardPosition = getPortBoardPosition,
                onZoom = onZoom,
                onConnectionDrag = onConnectionDrag,
                onConnectionDrop = onConnectionDrop,
                onDeleteConnection = onDeleteConnection,
                onDetachConnection = onDetachConnection
            )
    ) {
        // 1. Grid and Connections Canvas
        BoardGridAndConnectionsCanvas(
            state = state,
            flow = flow,
            interactionState = interactionState,
            isDrawingConnection = isDrawingConnection,
            connectionStartNodeId = connectionStartNodeId,
            connectionStartPortId = connectionStartPortId,
            connectionStartIsOutput = connectionStartIsOutput,
            highlightedPortId = highlightedPortId,
            highlightedNodeId = highlightedNodeId,
            getPortBoardPosition = getPortBoardPosition
        )

        // 2. Render main children (nodes, preview)
        content(interactionState.hoveredConnection)

        // 3. Selection Box overlay drawing
        SelectionBoxCanvas(interactionState = interactionState)

        // 4. Order Badges
        flow.connections.forEach { conn ->
            if (conn.orderIndex != null) {
                androidx.compose.runtime.key(
                    conn.sourceNodeId,
                    conn.sourcePortId,
                    conn.targetNodeId,
                    conn.targetPortId
                ) {
                    val sourcePortBoardPos = getPortBoardPosition(conn.sourceNodeId, conn.sourcePortId, true)
                    val targetPortBoardPos = getPortBoardPosition(conn.targetNodeId, conn.targetPortId, false)
                    if (sourcePortBoardPos != null && targetPortBoardPos != null) {
                        val startPos = (sourcePortBoardPos * state.scale) + state.offset
                        val endPos = (targetPortBoardPos * state.scale) + state.offset
                        val midPoint = BoardMathUtils.getBezierMidpoint(startPos, endPos)

                        var showMenu by remember { mutableStateOf(false) }

                        Box(
                            modifier = Modifier
                                .testTag("order_badge_${conn.sourceNodeId}_${conn.targetNodeId}")
                                .offset {
                                    val currentSourcePortBoardPos = getPortBoardPosition(conn.sourceNodeId, conn.sourcePortId, true)
                                    val currentTargetPortBoardPos = getPortBoardPosition(conn.targetNodeId, conn.targetPortId, false)
                                    if (currentSourcePortBoardPos != null && currentTargetPortBoardPos != null) {
                                        val currentStartPos = (currentSourcePortBoardPos * state.scale) + state.offset
                                        val currentEndPos = (currentTargetPortBoardPos * state.scale) + state.offset
                                        val currentMidPoint = BoardMathUtils.getBezierMidpoint(currentStartPos, currentEndPos)
                                        IntOffset(
                                            (currentMidPoint.x - spacing.mediumSmall.toPx()).roundToInt(),
                                            (currentMidPoint.y - spacing.mediumSmall.toPx()).roundToInt()
                                        )
                                    } else {
                                        IntOffset.Zero
                                    }
                                }
                                .size(dimensions.iconMedium)
                                .background(MaterialTheme.colorScheme.background, CircleShape)
                                .border(dimensions.borderUnselected, MaterialTheme.colorScheme.primary, CircleShape)
                                .clickable { showMenu = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = conn.orderIndex.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Bold
                            )

                            val overlay = LocalOverlayHost.current
                            LaunchedEffect(showMenu) {
                                if (showMenu) {
                                    val scaledMidPoint = (midPoint * state.scale) + state.offset
                                    val bounds = Rect(
                                        left = scaledMidPoint.x,
                                        top = scaledMidPoint.y,
                                        right = scaledMidPoint.x,
                                        bottom = scaledMidPoint.y
                                    )
                                    overlay?.show(
                                        bounds = bounds,
                                        onDismiss = { showMenu = false }
                                    ) {
                                        Card(
                                            elevation = CardDefaults.cardElevation(defaultElevation = ToolkitTheme.dimensions.menuElevation),
                                            shape = ToolkitTheme.shapes.large,
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                            modifier = Modifier.widthIn(min = ToolkitTheme.dimensions.menuMinWidth).clip(ToolkitTheme.shapes.large)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(
                                                    horizontal = ToolkitTheme.spacing.xs,
                                                    vertical = ToolkitTheme.spacing.xs
                                                ),
                                                verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraExtraSmall)
                                            ) {
                                                ToolkitDropdownMenuItem(
                                                    text = { Text("Move to Start") },
                                                    onClick = {
                                                        onMoveConnectionFirst(conn)
                                                        showMenu = false
                                                    }
                                                )
                                                ToolkitDropdownMenuItem(
                                                    text = { Text("Move to End") },
                                                    onClick = {
                                                        onMoveConnectionLast(conn)
                                                        showMenu = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    overlay?.hide()
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Selected Connection Delete Button Bubble
        interactionState.selectedConnection?.let { conn ->
            val sourcePortBoardPos = getPortBoardPosition(conn.sourceNodeId, conn.sourcePortId, true)
            val targetPortBoardPos = getPortBoardPosition(conn.targetNodeId, conn.targetPortId, false)
            if (sourcePortBoardPos != null && targetPortBoardPos != null) {
                Surface(
                    onClick = {
                        onDeleteConnection(conn)
                        interactionState.selectedConnection = null
                    },
                    modifier = Modifier
                        .testTag("delete_wire_button")
                        .offset {
                            val currentSourcePortBoardPos = getPortBoardPosition(conn.sourceNodeId, conn.sourcePortId, true)
                            val currentTargetPortBoardPos = getPortBoardPosition(conn.targetNodeId, conn.targetPortId, false)
                            if (currentSourcePortBoardPos != null && currentTargetPortBoardPos != null) {
                                val currentStartPos = (currentSourcePortBoardPos * state.scale) + state.offset
                                val currentEndPos = (currentTargetPortBoardPos * state.scale) + state.offset
                                val currentMidPoint = BoardMathUtils.getBezierMidpoint(currentStartPos, currentEndPos)
                                IntOffset(
                                    (currentMidPoint.x - dimensions.offsetLarge.toPx()).roundToInt(),
                                    (currentMidPoint.y - dimensions.offsetLarge.toPx()).roundToInt()
                                )
                            } else {
                                IntOffset(-1000, -1000)
                            }
                        }
                        .size(dimensions.progressBoxSize),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.error,
                    tonalElevation = dimensions.elevationHigh,
                    shadowElevation = dimensions.elevationMedium
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Wire",
                            tint = MaterialTheme.colorScheme.onError,
                            modifier = Modifier.size(dimensions.iconMediumSmall)
                        )
                    }
                }
            }
        }

        // 6. Zoom Controls UI - Bottom Right
        val centerPosition = Offset(boardSize.width / 2f, boardSize.height / 2f)
        ZoomControls(
            scale = state.scale,
            onZoomIn = { onZoom(-1f, centerPosition, false) },
            onZoomOut = { onZoom(1f, centerPosition, false) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(ToolkitTheme.spacing.medium)
                .testTag("zoom_controls")
        )
    }
}
