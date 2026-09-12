package org.wip.plugintoolkit.features.flows.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.flow_info_button_tooltip
import plugintoolkit.composeapp.generated.resources.node_port_inactive_connected_warning
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.FlowGroup
import org.wip.plugintoolkit.features.flows.model.FlowLabel
import org.wip.plugintoolkit.features.flows.ui.toModelOffset
import org.wip.plugintoolkit.features.flows.ui.toComposeOffset
import org.wip.plugintoolkit.features.flows.ui.canvas.BoardGridAndConnectionsCanvas
import org.wip.plugintoolkit.features.flows.ui.canvas.BoardInteractionState
import org.wip.plugintoolkit.features.flows.ui.canvas.StructuredConnectionStartInfo
import org.wip.plugintoolkit.features.flows.ui.canvas.SelectionBoxCanvas
import org.wip.plugintoolkit.features.flows.ui.canvas.boardConnectionTapGesture
import org.wip.plugintoolkit.features.flows.ui.canvas.boardKeyboardHandler
import org.wip.plugintoolkit.features.flows.ui.canvas.boardPanGesture
import org.wip.plugintoolkit.features.flows.ui.canvas.boardPointerEventGesture
import org.wip.plugintoolkit.features.flows.ui.canvas.boardSelectionBoxGesture
import org.wip.plugintoolkit.features.flows.utils.BoardMathUtils
import org.wip.plugintoolkit.features.flows.viewmodel.FlowEditorState
import org.wip.plugintoolkit.features.settings.model.ConnectionCurveStyle
import org.wip.plugintoolkit.shared.components.LocalOverlayHost
import org.wip.plugintoolkit.shared.components.LocalTooltipState
import org.wip.plugintoolkit.shared.components.menu.ToolkitDropdownMenuItem
import org.wip.plugintoolkit.shared.components.tooltip
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
    problematicConnections: Set<Connection> = emptySet(),
    portLayoutVersion: Int = 0,
    onTogglePaintTool: () -> Unit = {},
    onToggleWashTool: () -> Unit = {},
    onSelectPaintColor: (String) -> Unit = {},
    onAddGroup: () -> Unit = {},
    onAddLabel: () -> Unit = {},
    onUpdateGroup: (FlowGroup) -> Unit = {},
    onDeleteGroup: (FlowGroup) -> Unit = {},
    onMoveGroup: (Long, org.wip.plugintoolkit.features.flows.model.Offset) -> Unit = { _, _ -> },
    onUpdateLabel: (FlowLabel) -> Unit = {},
    onDeleteLabel: (FlowLabel) -> Unit = {},
    onMoveLabel: (Long, org.wip.plugintoolkit.features.flows.model.Offset) -> Unit = { _, _ -> },
    onChangeConnectionStyle: (ConnectionCurveStyle) -> Unit = {},
    onChangeConnectionRoundness: (Float) -> Unit = {},
    onPaintConnection: ((Connection) -> Unit)? = null,
    onWashConnection: ((Connection) -> Unit)? = null,
    onPaintGroup: ((Long) -> Unit)? = null,
    onWashGroup: ((Long) -> Unit)? = null,
    onPaintLabel: ((Long) -> Unit)? = null,
    onWashLabel: ((Long) -> Unit)? = null,
    onMoveJunction: ((Long, org.wip.plugintoolkit.features.flows.model.Offset) -> Unit)? = null,
    onDeleteJunction: ((Long) -> Unit)? = null,
    onSampleColor: (String) -> Unit = {},
    onResizeGroup: (Long, Offset) -> Unit = { _, _ -> },
    onSelectLabels: (Set<Long>) -> Unit = {},
    onSelectGroups: (Set<Long>) -> Unit = {},
    onAddWaypoint: (Connection, Offset) -> Unit = { _, _ -> },
    onMoveWaypoint: (Connection, Int, org.wip.plugintoolkit.features.flows.model.Offset) -> Unit = { _, _, _ -> },
    onDeleteWaypoint: (Connection, Int) -> Unit = { _, _ -> },
    onDeleteConnectionSegment: (Connection, Int) -> Unit = { _, _ -> },
    onInsertWaypoint: (Connection, Int, org.wip.plugintoolkit.features.flows.model.Offset) -> Unit = { _, _, _ -> },
    onFinalizeStructuredConnection: (Long?, String?, Long?, Long, String, List<org.wip.plugintoolkit.features.flows.model.Offset>, Long?) -> Unit = { _, _, _, _, _, _, _ -> },
    onLeaveStructuredConnectionAtLastPoint: (Long?, String?, Long?, List<org.wip.plugintoolkit.features.flows.model.Offset>) -> Unit = { _, _, _, _ -> },
    onToggleStructuredConnectionMode: () -> Unit = {},
    onAddJunctionAndBranch: (Connection, Offset, Int) -> Unit = { _, _, _ -> },
    onToggleEyedropper: () -> Unit = {},
    onToggleAdvancedConnectionMode: () -> Unit = {},
    onPaintSelection: () -> Unit = {},
    onWashSelection: () -> Unit = {},
    structuredConnectionStartInfo: StructuredConnectionStartInfo? = null,
    onClearStructuredConnectionStartInfo: () -> Unit = {},
    interactionState: BoardInteractionState = remember { BoardInteractionState() },
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(hoveredConnection: Connection?, hoveredNodeId: Long?, onHoverNode: (Long?) -> Unit) -> Unit
) {
    val density = LocalDensity.current
    val focusRequester = remember { FocusRequester() }

    var boardSize by remember { mutableStateOf(IntSize.Zero) }

    var hoveredConnectionTooltipVisible by remember { mutableStateOf(false) }
    LaunchedEffect(interactionState.hoveredConnection) {
        hoveredConnectionTooltipVisible = false
        if (interactionState.hoveredConnection != null) {
            delay(1000)
            hoveredConnectionTooltipVisible = true
        }
    }

    LaunchedEffect(flow.connections) {
        if (interactionState.hoveredConnection != null && !flow.connections.contains(interactionState.hoveredConnection)) {
            interactionState.clearHoveredConnection()
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(structuredConnectionStartInfo) {
        structuredConnectionStartInfo?.let { info ->
            interactionState.isDrawingStructuredConnection = true
            interactionState.structuredConnectionStartNodeId = info.nodeId
            interactionState.structuredConnectionStartPortId = info.portId
            interactionState.structuredConnectionStartIsOutput = info.isOutput
            interactionState.structuredConnectionSourceJunctionId = info.sourceJunctionId
            interactionState.structuredConnectionPoints = info.initialWaypoints.toMutableList()
            val portPos = if (info.nodeId != null && info.portId != null) {
                getPortBoardPosition(info.nodeId, info.portId, info.isOutput) ?: Offset.Zero
            } else Offset.Zero
            interactionState.structuredConnectionLivePos = info.livePos ?: portPos
            onClearStructuredConnectionStartInfo()
        }
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
                isReadOnly = isReadOnly,
                onTogglePaintTool = onTogglePaintTool,
                onToggleWashTool = onToggleWashTool,
                onToggleStructuredConnectionMode = onToggleStructuredConnectionMode,
                onLeaveStructuredConnectionAtLastPoint = { sNodeId, sPortId, sJuncId, pts ->
                    onLeaveStructuredConnectionAtLastPoint(sNodeId, sPortId, sJuncId, pts.map { it.toModelOffset() })
                }
            )
            .boardConnectionTapGesture(
                interactionState = interactionState,
                connections = flow.connections,
                scale = state.scale,
                offset = state.offset,
                getPortBoardPosition = getPortBoardPosition,
                focusRequester = focusRequester,
                nodes = flow.nodes,
                nodeSizes = nodeSizes,
                density = density,
                defaultNodeWidthPx = with(density) { dimensions.nodeWidth.toPx() },
                onClearSelection = onClearSelection,
                isPaintToolActive = state.isPaintToolActive,
                isWashToolActive = state.isWashToolActive,
                isEyedropperActive = state.isEyedropperActive,
                onPaintConnection = onPaintConnection,
                onWashConnection = onWashConnection,
                onSampleColor = onSampleColor,
                junctions = flow.junctions,
                curveStyle = state.connectionCurveStyle,
                roundness = state.connectionRoundness,
                groups = flow.groups
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
                onSelectNodes = onSelectNodes,
                labels = flow.labels,
                groups = flow.groups,
                onSelectLabels = onSelectLabels,
                onSelectGroups = onSelectGroups,
                isPaintToolActive = state.isPaintToolActive,
                isWashToolActive = state.isWashToolActive,
                onPaintSelection = onPaintSelection,
                onWashSelection = onWashSelection
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
                onDetachConnection = onDetachConnection,
                junctions = flow.junctions,
                onMoveJunction = onMoveJunction,
                onDeleteJunction = onDeleteJunction,
                onAddWaypoint = onAddWaypoint,
                onMoveWaypoint = onMoveWaypoint,
                onDeleteWaypoint = onDeleteWaypoint,
                onDeleteConnectionSegment = onDeleteConnectionSegment,
                onInsertWaypoint = onInsertWaypoint,
                onFinalizeStructuredConnection = onFinalizeStructuredConnection,
                connectionStartNodeId = connectionStartNodeId,
                connectionStartPortId = connectionStartPortId,
                connectionStartIsOutput = connectionStartIsOutput,
                curveStyle = state.connectionCurveStyle,
                roundness = state.connectionRoundness,
                isAdvancedConnectionMode = state.isAdvancedConnectionMode,
                onAddJunctionAndBranch = onAddJunctionAndBranch
            )
    ) {
        val isDraggedInSelection = state.draggedNodeId != null && (
            state.selectedNodeIds.contains(state.draggedNodeId) ||
            state.selectedGroupIds.contains(state.draggedNodeId) ||
            state.selectedLabelIds.contains(state.draggedNodeId)
        )

        val movingGroupIds = remember(state.draggedNodeId, state.selectedGroupIds, isDraggedInSelection, flow.groups) {
            if (state.draggedNodeId == null) emptySet()
            else if (isDraggedInSelection) state.selectedGroupIds + (if (flow.groups.any { it.id == state.draggedNodeId }) setOf(state.draggedNodeId) else emptySet())
            else if (flow.groups.any { it.id == state.draggedNodeId }) setOf(state.draggedNodeId)
            else emptySet()
        }

        val movingLabelIds = remember(state.draggedNodeId, state.selectedLabelIds, isDraggedInSelection, flow.labels) {
            if (state.draggedNodeId == null) emptySet()
            else if (isDraggedInSelection) state.selectedLabelIds + (if (flow.labels.any { it.id == state.draggedNodeId }) setOf(state.draggedNodeId) else emptySet())
            else if (flow.labels.any { it.id == state.draggedNodeId }) setOf(state.draggedNodeId)
            else emptySet()
        }

        val renderedGroups = remember(flow.groups, movingGroupIds, state.currentDragOffset) {
            if (state.currentDragOffset == org.wip.plugintoolkit.features.flows.model.Offset.Zero || movingGroupIds.isEmpty()) {
                flow.groups
            } else {
                flow.groups.map { grp ->
                    if (movingGroupIds.contains(grp.id)) {
                        grp.copy(position = grp.position + state.currentDragOffset)
                    } else grp
                }
            }
        }

        // 1. Grid and Connections Canvas
        BoardGridAndConnectionsCanvas(
            state = state,
            flow = flow.copy(groups = renderedGroups),
            interactionState = interactionState,
            isDrawingConnection = isDrawingConnection,
            connectionStartNodeId = connectionStartNodeId,
            connectionStartPortId = connectionStartPortId,
            connectionStartIsOutput = connectionStartIsOutput,
            highlightedPortId = highlightedPortId,
            highlightedNodeId = highlightedNodeId,
            getPortBoardPosition = getPortBoardPosition,
            problematicConnections = problematicConnections,
            portLayoutVersion = portLayoutVersion,
            curveStyle = state.connectionCurveStyle,
            roundness = state.connectionRoundness
        )

        // 1.1 Groups Layer (Behind nodes and labels)
        flow.groups.forEach { group ->
            androidx.compose.runtime.key(group.id) {
                val hasIncoming = flow.connections.any { it.targetNodeId in group.nodeIds && it.sourceNodeId !in group.nodeIds }
                val hasOutgoing = flow.connections.any { it.sourceNodeId in group.nodeIds && it.targetNodeId !in group.nodeIds }
                val isDropTarget = state.draggedNodeId != null && state.flow.nodes.find { it.id == state.draggedNodeId }?.let { dNode ->
                    val currPos = dNode.position + state.currentDragOffset
                    currPos.x >= group.position.x && currPos.x <= group.position.x + group.size.x &&
                    currPos.y >= group.position.y && currPos.y <= group.position.y + group.size.y
                } ?: false

                val isGroupSelected = state.selectedGroupIds.contains(group.id)
                val isMoving = movingGroupIds.contains(group.id)
                val groupDragOffset = if (isMoving) state.currentDragOffset.toComposeOffset() else Offset.Zero

                BoardElementContainer(
                    position = group.position.toComposeOffset(),
                    dragOffset = groupDragOffset,
                    scale = state.scale,
                    boardOffset = state.offset
                ) {
                    FlowGroupComponent(
                        group = group,
                        stateScale = state.scale,
                        stateOffset = state.offset,
                        isReadOnly = isReadOnly,
                        onUpdateGroup = onUpdateGroup,
                        onDeleteGroup = onDeleteGroup,
                        onDragDelta = { delta -> onMoveGroup(group.id, delta.toModelOffset()) },
                        isSelected = isGroupSelected,
                        isDropTarget = isDropTarget,
                        hasIncomingConnections = hasIncoming,
                        hasOutgoingConnections = hasOutgoing,
                        isPaintToolActive = state.isPaintToolActive,
                        isWashToolActive = state.isWashToolActive,
                        isEyedropperActive = state.isEyedropperActive,
                        onPaintGroup = onPaintGroup,
                        onWashGroup = onWashGroup,
                        onSampleColor = onSampleColor,
                        onResizeGroup = onResizeGroup
                    )
                }
            }
        }

        // 1.2 Labels Layer
        flow.labels.forEach { label ->
            androidx.compose.runtime.key(label.id) {
                val isLabelSelected = state.selectedLabelIds.contains(label.id)
                val isMoving = movingLabelIds.contains(label.id)
                val labelDragOffset = if (isMoving) state.currentDragOffset.toComposeOffset() else Offset.Zero

                BoardElementContainer(
                    position = label.position.toComposeOffset(),
                    dragOffset = labelDragOffset,
                    scale = state.scale,
                    boardOffset = state.offset
                ) {
                    FlowLabelComponent(
                        label = label,
                        stateScale = state.scale,
                        stateOffset = state.offset,
                        isReadOnly = isReadOnly,
                        onUpdateLabel = onUpdateLabel,
                        onDeleteLabel = onDeleteLabel,
                        onDragDelta = { delta -> onMoveLabel(label.id, delta.toModelOffset()) },
                        isSelected = isLabelSelected,
                        isPaintToolActive = state.isPaintToolActive,
                        isWashToolActive = state.isWashToolActive,
                        isEyedropperActive = state.isEyedropperActive,
                        onPaintLabel = onPaintLabel,
                        onWashLabel = onWashLabel,
                        onSampleColor = onSampleColor
                    )
                }
            }
        }

        // Connection Hover Tooltip
        if (hoveredConnectionTooltipVisible && interactionState.hoveredConnection != null) {
            val conn = interactionState.hoveredConnection!!
            val isProblem = problematicConnections.contains(conn)
            val validationError = state.validationErrors.firstOrNull {
                it.sourceNodeId == conn.sourceNodeId && it.sourcePortId == conn.sourcePortId &&
                        it.targetNodeId == conn.targetNodeId && it.targetPortId == conn.targetPortId
            }
            val tooltipMsg = when {
                validationError != null -> validationError.message
                isProblem -> stringResource(Res.string.node_port_inactive_connected_warning)
                else -> null
            }
            if (tooltipMsg != null) {
                Popup(
                    offset = IntOffset(
                        interactionState.lastPointerPosition.x.toInt() + 16,
                        interactionState.lastPointerPosition.y.toInt() + 16
                    ),
                    properties = PopupProperties(focusable = false)
                ) {
                    Box(
                        modifier = Modifier
                            .shadow(ToolkitTheme.dimensions.elevationHigh, ToolkitTheme.shapes.extraSmall)
                            .background(MaterialTheme.colorScheme.surfaceVariant, ToolkitTheme.shapes.extraSmall)
                            .border(ToolkitTheme.dimensions.borderUnselected, MaterialTheme.colorScheme.outlineVariant, ToolkitTheme.shapes.extraSmall)
                            .padding(horizontal = ToolkitTheme.spacing.small, vertical = ToolkitTheme.spacing.extraSmall)
                    ) {
                        Text(
                            text = tooltipMsg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 2. Render main children (nodes, preview)
        content(interactionState.hoveredConnection, interactionState.hoveredNodeId) { interactionState.hoveredNodeId = it }

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

        // 6. Floating App Bar (M3 Toolbar + Zoom Controls)
        val centerPosition = Offset(boardSize.width / 2f, boardSize.height / 2f)
        val colorsInFlow = remember(flow) {
            val nodeColors = flow.nodes.mapNotNull { it.color }.filter { it.isNotBlank() }
            val connColors = flow.connections.mapNotNull { it.color }.filter { it.isNotBlank() }
            val groupColors = flow.groups.mapNotNull { it.color }.filter { it.isNotBlank() }
            val labelColors = flow.labels.mapNotNull { it.color }.filter { it.isNotBlank() }
            (nodeColors + connColors + groupColors + labelColors).distinct()
        }

        FlowFloatingAppBar(
            scale = state.scale,
            isReadOnly = isReadOnly,
            isPaintToolActive = state.isPaintToolActive,
            isWashToolActive = state.isWashToolActive,
            isEyedropperActive = state.isEyedropperActive,
            isAdvancedConnectionMode = state.isAdvancedConnectionMode,
            activePaintColor = state.activePaintColor,
            colorsInFlow = colorsInFlow,
            connectionStyle = state.connectionCurveStyle,
            connectionRoundness = state.connectionRoundness,
            onTogglePaintTool = onTogglePaintTool,
            onToggleWashTool = onToggleWashTool,
            onToggleEyedropper = onToggleEyedropper,
            onToggleAdvancedConnectionMode = onToggleAdvancedConnectionMode,
            onSelectPaintColor = onSelectPaintColor,
            onAddGroup = onAddGroup,
            onAddLabel = onAddLabel,
            onChangeConnectionStyle = onChangeConnectionStyle,
            onChangeConnectionRoundness = onChangeConnectionRoundness,
            onZoomIn = { onZoom(-1f, centerPosition, false) },
            onZoomOut = { onZoom(1f, centerPosition, false) },
            modifier = Modifier.align(Alignment.BottomEnd)
        )
    }
}
