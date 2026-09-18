package org.wip.plugintoolkit.features.flows.ui.canvas

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import org.wip.plugintoolkit.features.shortcuts.logic.ShortcutManager
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutActionId
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutGesture
import org.wip.plugintoolkit.features.shortcuts.ui.shortcutDrag
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.FlowGroup
import org.wip.plugintoolkit.features.flows.model.FlowJunction
import org.wip.plugintoolkit.features.flows.model.FlowLabel
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.ui.snapToGrid
import org.wip.plugintoolkit.features.flows.ui.toComposeOffset
import org.wip.plugintoolkit.features.flows.ui.toModelOffset
import org.wip.plugintoolkit.features.flows.utils.SplineMathUtils
import org.wip.plugintoolkit.features.settings.model.ConnectionCurveStyle
import org.wip.plugintoolkit.features.settings.model.OrthogonalStepMode

fun Modifier.boardConnectionTapGesture(
    interactionState: BoardInteractionState,
    connections: List<Connection>,
    scale: Float,
    offset: Offset,
    getPortBoardPosition: (Long, String, Boolean) -> Offset?,
    focusRequester: FocusRequester,
    nodes: List<Node> = emptyList(),
    nodeSizes: Map<Long, IntSize> = emptyMap(),
    density: Density? = null,
    defaultNodeWidthPx: Float = 0f,
    onClearSelection: () -> Unit,
    isPaintToolActive: Boolean = false,
    isWashToolActive: Boolean = false,
    isEyedropperActive: Boolean = false,
    onPaintConnection: ((Connection) -> Unit)? = null,
    onWashConnection: ((Connection) -> Unit)? = null,
    onSampleColor: ((String) -> Unit)? = null,
    junctions: List<FlowJunction> = emptyList(),
    curveStyle: ConnectionCurveStyle = ConnectionCurveStyle.Bezier,
    roundness: Float = 0.5f,
    orthogonalStepMode: OrthogonalStepMode = OrthogonalStepMode.Middle,
    groups: List<FlowGroup> = emptyList()
): Modifier = this.pointerInput(
    connections,
    getPortBoardPosition,
    scale,
    offset,
    nodes,
    nodeSizes,
    isPaintToolActive,
    isWashToolActive,
    isEyedropperActive,
    junctions,
    curveStyle,
    roundness,
    orthogonalStepMode,
    groups
) {
    val d = density?.density ?: 1f
    detectTapGestures(
        onTap = { tapOffset ->
            focusRequester.requestFocus()
            val bestConnection = ConnectionHitTester.findClosestConnection(
                position = tapOffset,
                connections = connections,
                getPortBoardPosition = getPortBoardPosition,
                scale = scale,
                offset = offset,
                junctions = junctions,
                curveStyle = curveStyle,
                roundness = roundness,
                groups = groups,
                density = d,
                stepMode = orthogonalStepMode
            )
            if (bestConnection != null) {
                if (isEyedropperActive && onSampleColor != null) {
                    onSampleColor(bestConnection.color ?: "#808080")
                } else if (isPaintToolActive && onPaintConnection != null) {
                    onPaintConnection(bestConnection)
                } else if (isWashToolActive && onWashConnection != null) {
                    onWashConnection(bestConnection)
                }
            } else {
                interactionState.selectedConnection = null
                interactionState.selectedJunctionId = null
                val d = density?.density ?: 1f
                val modelPoint = (tapOffset - offset) / scale
                val isOverNode = nodes.any { node ->
                    val nodeLeft = node.position.x
                    val nodeTop = node.position.y
                    val nodeWidth = nodeSizes[node.id]?.width?.toFloat() ?: defaultNodeWidthPx
                    val nodeHeight = nodeSizes[node.id]?.height?.toFloat() ?: (180f * d)
                    modelPoint.x >= nodeLeft && modelPoint.x <= nodeLeft + nodeWidth &&
                            modelPoint.y >= nodeTop && modelPoint.y <= nodeTop + nodeHeight
                }
                if (!isOverNode) {
                    onClearSelection()
                }
            }
        }
    )
}

fun Modifier.boardPanGesture(
    focusRequester: FocusRequester,
    shortcutManager: ShortcutManager? = null,
    onPan: (Offset) -> Unit
): Modifier = this.shortcutDrag(
    shortcutManager = shortcutManager,
    actionId = ShortcutActionId.FLOW_PAN_CANVAS,
    onDragStart = { focusRequester.requestFocus() },
    onDrag = onPan
)

fun Modifier.boardSelectionBoxGesture(
    interactionState: BoardInteractionState,
    isDrawingConnection: Boolean,
    nodes: List<Node>,
    nodeSizes: Map<Long, IntSize>,
    density: Density,
    scale: Float,
    offset: Offset,
    defaultNodeWidthPx: Float,
    focusRequester: FocusRequester,
    onSelectNodes: (Set<Long>) -> Unit,
    labels: List<FlowLabel> = emptyList(),
    groups: List<FlowGroup> = emptyList(),
    junctions: List<FlowJunction> = emptyList(),
    onSelectLabels: ((Set<Long>) -> Unit)? = null,
    onSelectGroups: ((Set<Long>) -> Unit)? = null,
    onSelectPoints: ((Set<Long>) -> Unit)? = null,
    isPaintToolActive: Boolean = false,
    isWashToolActive: Boolean = false,
    onPaintSelection: (() -> Unit)? = null,
    onWashSelection: (() -> Unit)? = null,
    shortcutManager: ShortcutManager? = null
): Modifier = this.pointerInput(nodes, nodeSizes, labels, groups, junctions, density, scale, offset, isDrawingConnection, isPaintToolActive, isWashToolActive, shortcutManager) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            val isBoxSelectTriggered = if (shortcutManager != null) {
                shortcutManager.matchesPointer(ShortcutActionId.FLOW_BOX_SELECT, event, ShortcutGesture.Drag)
            } else {
                !event.keyboardModifiers.isCtrlPressed &&
                !event.keyboardModifiers.isShiftPressed &&
                !event.keyboardModifiers.isAltPressed &&
                !event.keyboardModifiers.isMetaPressed &&
                event.buttons.isPrimaryPressed &&
                !event.buttons.isSecondaryPressed &&
                !event.buttons.isTertiaryPressed
            }

            if (event.type == PointerEventType.Press && isBoxSelectTriggered) {
                val startChange = event.changes.firstOrNull() ?: continue
                if (startChange.isConsumed) continue

                val isOverElement = interactionState.hoveredNodeId != null ||
                    interactionState.hoveredJunctionId != null ||
                    interactionState.hoveredWaypoint != null ||
                    interactionState.hoveredMidpoint != null ||
                    interactionState.hoveredConnection != null ||
                    interactionState.draggingJunctionId != null ||
                    interactionState.draggingWaypoint != null ||
                    interactionState.pendingMidpoint != null
                if (isOverElement) continue

                if (isDrawingConnection || interactionState.isDrawingStructuredConnection) continue

                val startOffset = startChange.position
                var dragStarted = false

                while (true) {
                    val dragEvent = awaitPointerEvent()
                    val isPrimaryDown = dragEvent.buttons.isPrimaryPressed && !dragEvent.buttons.isSecondaryPressed && !dragEvent.buttons.isTertiaryPressed

                    if (!isPrimaryDown || dragEvent.type == PointerEventType.Release) {
                        if (dragStarted) {
                            if (isPaintToolActive) {
                                onPaintSelection?.invoke()
                            } else if (isWashToolActive) {
                                onWashSelection?.invoke()
                            }
                            interactionState.clearSelectionBox()
                        } else {
                            // Tapped empty canvas without dragging: clear selection and reset focus
                            focusRequester.requestFocus()
                            onSelectNodes(emptySet())
                            onSelectLabels?.invoke(emptySet())
                            onSelectGroups?.invoke(emptySet())
                            onSelectPoints?.invoke(emptySet())
                            interactionState.selectedConnection = null
                            interactionState.selectedJunctionId = null
                        }
                        break
                    }

                    if (dragEvent.type == PointerEventType.Move) {
                        val currentChange = dragEvent.changes.firstOrNull() ?: continue
                        val currentPos = currentChange.position
                        if (!dragStarted) {
                            val dist = (currentPos - startOffset).getDistance()
                            if (dist >= 6f) {
                                dragStarted = true
                                focusRequester.requestFocus()
                                interactionState.selectionStart = startOffset
                                interactionState.selectionEnd = currentPos
                                shortcutManager?.eat(dragEvent, ShortcutActionId.FLOW_BOX_SELECT) ?: currentChange.consume()
                            }
                        } else {
                            interactionState.selectionEnd = currentPos
                            shortcutManager?.eat(dragEvent, ShortcutActionId.FLOW_BOX_SELECT) ?: currentChange.consume()

                            val modelStart = (interactionState.selectionStart!! - offset) / scale
                            val modelEnd = (interactionState.selectionEnd!! - offset) / scale
                            val selectLeft = minOf(modelStart.x, modelEnd.x)
                            val selectRight = maxOf(modelStart.x, modelEnd.x)
                            val selectTop = minOf(modelStart.y, modelEnd.y)
                            val selectBottom = maxOf(modelStart.y, modelEnd.y)

                            val selectedNodeIds = mutableSetOf<Long>()
                            nodes.forEach { node ->
                                val nodeLeft = node.position.x
                                val nodeTop = node.position.y
                                val nodeWidth = nodeSizes[node.id]?.width?.toFloat() ?: defaultNodeWidthPx
                                val nodeHeight = nodeSizes[node.id]?.height?.toFloat() ?: (180f * density.density)
                                val nodeRight = nodeLeft + nodeWidth
                                val nodeBottom = nodeTop + nodeHeight

                                if (selectLeft < nodeRight && selectRight > nodeLeft &&
                                    selectTop < nodeBottom && selectBottom > nodeTop
                                ) {
                                    selectedNodeIds.add(node.id)
                                }
                            }
                            onSelectNodes(selectedNodeIds)

                            if (onSelectLabels != null) {
                                val selectedLabelIds = mutableSetOf<Long>()
                                labels.forEach { label ->
                                    val lLeft = label.position.x
                                    val lTop = label.position.y
                                    val lWidth = maxOf(80f, label.text.length * 9f)
                                    val lHeight = 36f
                                    val lRight = lLeft + lWidth
                                    val lBottom = lTop + lHeight

                                    if (selectLeft < lRight && selectRight > lLeft &&
                                        selectTop < lBottom && selectBottom > lTop
                                    ) {
                                        selectedLabelIds.add(label.id)
                                    }
                                }
                                onSelectLabels(selectedLabelIds)
                            }

                            if (onSelectGroups != null) {
                                val selectedGroupIds = mutableSetOf<Long>()
                                groups.forEach { group ->
                                    val gLeft = group.position.x
                                    val gTop = group.position.y
                                    val gWidth = group.size.x
                                    val gHeight = if (group.isCollapsed) 48f else group.size.y
                                    val gRight = gLeft + gWidth
                                    val gBottom = gTop + gHeight

                                    if (selectLeft < gRight && selectRight > gLeft &&
                                        selectTop < gBottom && selectBottom > gTop
                                    ) {
                                        selectedGroupIds.add(group.id)
                                    }
                                }
                                onSelectGroups(selectedGroupIds)
                            }

                            if (onSelectPoints != null) {
                                val selectedPtIds = mutableSetOf<Long>()
                                junctions.forEach { junc ->
                                    val jx = junc.position.x
                                    val jy = junc.position.y
                                    if (selectLeft < jx + 8f && selectRight > jx - 8f &&
                                        selectTop < jy + 8f && selectBottom > jy - 8f
                                    ) {
                                        selectedPtIds.add(junc.id)
                                    }
                                }
                                onSelectPoints(selectedPtIds)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Modifier.boardPointerEventGesture(
    interactionState: BoardInteractionState,
    isDrawingConnection: Boolean,
    scale: Float,
    offset: Offset,
    nodes: List<Node>,
    connections: List<Connection>,
    getPortBoardPosition: (Long, String, Boolean) -> Offset?,
    onZoom: (Float, Offset, Boolean) -> Unit,
    onConnectionDrag: (Offset) -> Unit,
    onConnectionDrop: (Boolean) -> Unit,
    onDeleteConnection: (Connection) -> Unit,
    onDetachConnection: (Connection, Boolean, Offset) -> Unit,
    junctions: List<FlowJunction> = emptyList(),
    onMoveJunction: ((Long, org.wip.plugintoolkit.features.flows.model.Offset) -> Unit)? = null,
    onEndMoveJunction: ((Map<Long, Pair<org.wip.plugintoolkit.features.flows.model.Offset, org.wip.plugintoolkit.features.flows.model.Offset>>, Map<Long, Pair<org.wip.plugintoolkit.features.flows.model.Offset, org.wip.plugintoolkit.features.flows.model.Offset>>, Map<Long, Pair<org.wip.plugintoolkit.features.flows.model.Offset, org.wip.plugintoolkit.features.flows.model.Offset>>, Map<Long, Pair<org.wip.plugintoolkit.features.flows.model.Offset, org.wip.plugintoolkit.features.flows.model.Offset>>) -> Unit)? = null,
    onDeleteJunction: ((Long) -> Unit)? = null,
    onAddWaypoint: ((Connection, Offset) -> Unit)? = null,
    onMoveWaypoint: ((Connection, Int, org.wip.plugintoolkit.features.flows.model.Offset) -> Unit)? = null,
    onDeleteWaypoint: ((Connection, Int) -> Unit)? = null,
    onInsertWaypoint: ((Connection, Int, org.wip.plugintoolkit.features.flows.model.Offset) -> Unit)? = null,
    onFinalizeStructuredConnection: ((Long?, String?, Long?, Long, String, List<org.wip.plugintoolkit.features.flows.model.Offset>, Long?) -> Unit)? = null,
    connectionStartNodeId: Long? = null,
    connectionStartPortId: String? = null,
    connectionStartIsOutput: Boolean = true,
    curveStyle: ConnectionCurveStyle = ConnectionCurveStyle.Bezier,
    roundness: Float = 0.5f,
    orthogonalStepMode: OrthogonalStepMode = OrthogonalStepMode.Middle,
    isAdvancedConnectionMode: Boolean = false,
    onAddJunctionAndBranch: ((Connection, Offset, Int) -> Unit)? = null,
    onDeleteConnectionSegment: ((Connection, Int) -> Unit)? = null,
    selectedPointIds: Set<Long> = emptySet(),
    selectedNodeIds: Set<Long> = emptySet(),
    selectedGroupIds: Set<Long> = emptySet(),
    selectedLabelIds: Set<Long> = emptySet(),
    groups: List<FlowGroup> = emptyList(),
    labels: List<FlowLabel> = emptyList(),
    onSelectPoints: ((Set<Long>) -> Unit)? = null,
    onSplitConnectionAndConnect: ((Connection, org.wip.plugintoolkit.features.flows.model.Offset, Long?, String?, Long?, Long?, String?, Long?, List<org.wip.plugintoolkit.features.flows.model.Offset>) -> Unit)? = null,
    onResetDrawingConnection: (() -> Unit)? = null,
    shortcutManager: ShortcutManager? = null,
    highlightedNodeId: Long? = null,
    highlightedPortId: String? = null,
    onSelectNodes: ((Set<Long>) -> Unit)? = null,
    onSelectGroups: ((Set<Long>) -> Unit)? = null,
    onSelectLabels: ((Set<Long>) -> Unit)? = null,
    isPaintToolActive: Boolean = false,
    isWashToolActive: Boolean = false,
    isEyedropperActive: Boolean = false,
    onPaintConnection: ((Connection) -> Unit)? = null,
    onWashConnection: ((Connection) -> Unit)? = null,
    onSampleColor: ((String) -> Unit)? = null,
    onMoveSegment: ((Connection, Int, org.wip.plugintoolkit.features.flows.model.Offset) -> Unit)? = null,
    onEndMoveSegment: ((Connection, Int, org.wip.plugintoolkit.features.flows.model.Offset) -> Unit)? = null
): Modifier {
    val currentOnSelectNodes by rememberUpdatedState(onSelectNodes)
    val currentOnSelectGroups by rememberUpdatedState(onSelectGroups)
    val currentOnSelectLabels by rememberUpdatedState(onSelectLabels)
    val currentIsPaintToolActive by rememberUpdatedState(isPaintToolActive)
    val currentIsWashToolActive by rememberUpdatedState(isWashToolActive)
    val currentIsEyedropperActive by rememberUpdatedState(isEyedropperActive)
    val currentOnPaintConnection by rememberUpdatedState(onPaintConnection)
    val currentOnWashConnection by rememberUpdatedState(onWashConnection)
    val currentOnSampleColor by rememberUpdatedState(onSampleColor)
    val currentOnMoveSegment by rememberUpdatedState(onMoveSegment)
    val currentOnEndMoveSegment by rememberUpdatedState(onEndMoveSegment)
    val currentIsDrawingConnection by rememberUpdatedState(isDrawingConnection)
    val currentHighlightedNodeId by rememberUpdatedState(highlightedNodeId)
    val currentHighlightedPortId by rememberUpdatedState(highlightedPortId)
    val currentScale by rememberUpdatedState(scale)
    val currentOffset by rememberUpdatedState(offset)
    val currentNodes by rememberUpdatedState(nodes)
    val currentConnections by rememberUpdatedState(connections)
    val currentGetPortBoardPosition by rememberUpdatedState(getPortBoardPosition)
    val currentOnZoom by rememberUpdatedState(onZoom)
    val currentOnConnectionDrag by rememberUpdatedState(onConnectionDrag)
    val currentOnConnectionDrop by rememberUpdatedState(onConnectionDrop)
    val currentOnDeleteConnection by rememberUpdatedState(onDeleteConnection)
    val currentOnDetachConnection by rememberUpdatedState(onDetachConnection)
    val currentJunctions by rememberUpdatedState(junctions)
    val currentOnMoveJunction by rememberUpdatedState(onMoveJunction)
    val currentOnEndMoveJunction by rememberUpdatedState(onEndMoveJunction)
    val currentOnDeleteJunction by rememberUpdatedState(onDeleteJunction)
    val currentOnAddWaypoint by rememberUpdatedState(onAddWaypoint)
    val currentOnMoveWaypoint by rememberUpdatedState(onMoveWaypoint)
    val currentOnDeleteWaypoint by rememberUpdatedState(onDeleteWaypoint)
    val currentOnDeleteConnectionSegment by rememberUpdatedState(onDeleteConnectionSegment)
    val currentOnInsertWaypoint by rememberUpdatedState(onInsertWaypoint)
    val currentOnFinalizeStructuredConnection by rememberUpdatedState(onFinalizeStructuredConnection)
    val currentConnectionStartNodeId by rememberUpdatedState(connectionStartNodeId)
    val currentConnectionStartPortId by rememberUpdatedState(connectionStartPortId)
    val currentConnectionStartIsOutput by rememberUpdatedState(connectionStartIsOutput)
    val currentCurveStyle by rememberUpdatedState(curveStyle)
    val currentRoundness by rememberUpdatedState(roundness)
    val currentOrthogonalStepMode by rememberUpdatedState(orthogonalStepMode)
    val currentIsAdvancedConnectionMode by rememberUpdatedState(isAdvancedConnectionMode)
    val currentOnAddJunctionAndBranch by rememberUpdatedState(onAddJunctionAndBranch)
    val currentSelectedPointIds by rememberUpdatedState(selectedPointIds)
    val currentSelectedNodeIds by rememberUpdatedState(selectedNodeIds)
    val currentSelectedGroupIds by rememberUpdatedState(selectedGroupIds)
    val currentSelectedLabelIds by rememberUpdatedState(selectedLabelIds)
    val currentGroups by rememberUpdatedState(groups)
    val currentLabels by rememberUpdatedState(labels)
    val currentOnSelectPoints by rememberUpdatedState(onSelectPoints)
    val currentOnSplitConnectionAndConnect by rememberUpdatedState(onSplitConnectionAndConnect)
    val currentOnResetDrawingConnection by rememberUpdatedState(onResetDrawingConnection)
    val currentShortcutManager by rememberUpdatedState(shortcutManager)

    var junctionDragStartPointPositions by remember { mutableStateOf<Map<Long, org.wip.plugintoolkit.features.flows.model.Offset>>(emptyMap()) }
    var junctionDragStartNodePositions by remember { mutableStateOf<Map<Long, org.wip.plugintoolkit.features.flows.model.Offset>>(emptyMap()) }
    var junctionDragStartGroupPositions by remember { mutableStateOf<Map<Long, org.wip.plugintoolkit.features.flows.model.Offset>>(emptyMap()) }
    var junctionDragStartLabelPositions by remember { mutableStateOf<Map<Long, org.wip.plugintoolkit.features.flows.model.Offset>>(emptyMap()) }
    var junctionDragStartPointerPosition by remember { mutableStateOf<Offset?>(null) }
    var junctionLastDispatchedBoardPos by remember { mutableStateOf<Offset?>(null) }
    var segmentDragStartPos by remember { mutableStateOf<Offset?>(null) }

    var rightClickStartPos by remember { mutableStateOf<Offset?>(null) }
    var rightClickLastPos by remember { mutableStateOf<Offset?>(null) }
    var rightClickDidDrag by remember { mutableStateOf(false) }
    var middleClickLastPos by remember { mutableStateOf<Offset?>(null) }

    return this.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull() ?: continue
                val position = change.position

                if (event.type == PointerEventType.Scroll) {
                    val scrollDelta = change.scrollDelta
                    val delta = if (scrollDelta.y != 0f) scrollDelta.y else scrollDelta.x
                    if (delta != 0f) {
                        val isShiftPressed = event.keyboardModifiers.isShiftPressed
                        currentOnZoom(-delta, position, isShiftPressed)
                    }
                } else if (event.type == PointerEventType.Move) {
                    val prevPointerPosition = if (interactionState.lastPointerPosition == Offset.Zero) position else interactionState.lastPointerPosition
                    interactionState.lastPointerPosition = position
                    interactionState.isCtrlModifierPressed = event.keyboardModifiers.isCtrlPressed
                    interactionState.isShiftModifierPressed = event.keyboardModifiers.isShiftPressed
                    interactionState.isAltModifierPressed = event.keyboardModifiers.isAltPressed

                    if (event.buttons.isSecondaryPressed && rightClickStartPos != null) {
                        val curPos = position
                        rightClickLastPos = curPos
                        if (!rightClickDidDrag && (curPos - rightClickStartPos!!).getDistance() > 6f) {
                            rightClickDidDrag = true
                        }
                    } else {
                        middleClickLastPos = null
                    }

                    val draggingJuncId = interactionState.draggingJunctionId
                    if (draggingJuncId != null) {
                        val isCtrl = event.keyboardModifiers.isCtrlPressed || interactionState.isCtrlModifierPressed
                        val startPos = junctionDragStartPointPositions[draggingJuncId]
                        if (startPos != null && junctionDragStartPointerPosition != null) {
                            val totalDelta = (position - junctionDragStartPointerPosition!!) / currentScale
                            val targetBoardPos = if (isCtrl) {
                                (startPos.toComposeOffset() + totalDelta).snapToGrid()
                            } else {
                                startPos.toComposeOffset() + totalDelta
                            }
                            val prevBoardPos = junctionLastDispatchedBoardPos ?: startPos.toComposeOffset()
                            val deltaToApply = targetBoardPos - prevBoardPos
                            if (deltaToApply.x != 0f || deltaToApply.y != 0f) {
                                currentOnMoveJunction?.invoke(draggingJuncId, deltaToApply.toModelOffset())
                                junctionLastDispatchedBoardPos = targetBoardPos
                            }
                        } else {
                            val delta = (position - prevPointerPosition) / currentScale
                            currentOnMoveJunction?.invoke(draggingJuncId, delta.toModelOffset())
                        }
                        currentShortcutManager?.eat(event, ShortcutActionId.FLOW_MOVE_POINT) ?: event.changes.forEach { it.consume() }
                    }

                    val draggingWp = interactionState.draggingWaypoint
                    if (draggingWp != null) {
                        var boardPos = (position - currentOffset) / currentScale
                        val isCtrl = event.keyboardModifiers.isCtrlPressed || interactionState.isCtrlModifierPressed
                        if (isCtrl) {
                            boardPos = boardPos.snapToGrid()
                        }
                        currentOnMoveWaypoint?.invoke(draggingWp.first, draggingWp.second, boardPos.toModelOffset())
                        currentShortcutManager?.eat(event, ShortcutActionId.FLOW_MOVE_POINT) ?: event.changes.forEach { it.consume() }
                    }

                    val draggingSeg = interactionState.draggingSegment
                    if (draggingSeg != null) {
                        val delta = (position - prevPointerPosition) / currentScale
                        currentOnMoveSegment?.invoke(draggingSeg.connection, draggingSeg.segmentIndex, delta.toModelOffset())
                        currentShortcutManager?.eat(event, ShortcutActionId.FLOW_MOVE_POINT) ?: event.changes.forEach { it.consume() }
                    }

                    if (currentIsDrawingConnection || interactionState.isDrawingStructuredConnection) {
                        val closestJuncForSnap = ConnectionHitTester.findClosestJunction(
                            position = position,
                            junctions = currentJunctions,
                            scale = currentScale,
                            offset = currentOffset,
                            hitRadius = 24f * currentScale
                        )
                        if (closestJuncForSnap != null) {
                            interactionState.hoveredJunctionId = closestJuncForSnap.id
                            interactionState.snappedWirePoint = closestJuncForSnap.position.toComposeOffset()
                            interactionState.snappedWireConnection = null
                            interactionState.snappedWireSegmentIndex = null
                        } else {
                            val connProj = ConnectionHitTester.findClosestConnectionWithProjection(
                                position = position,
                                connections = currentConnections,
                                getPortBoardPosition = currentGetPortBoardPosition,
                                scale = currentScale,
                                offset = currentOffset,
                                initialMinDistance = 24f * currentScale,
                                junctions = currentJunctions,
                                curveStyle = currentCurveStyle,
                                roundness = currentRoundness,
                                stepMode = currentOrthogonalStepMode
                            )
                            if (connProj != null) {
                                interactionState.snappedWirePoint = connProj.projectedPoint
                                interactionState.snappedWireConnection = connProj.connection
                                interactionState.snappedWireSegmentIndex = connProj.segmentIndex
                                interactionState.hoveredJunctionId = null
                            } else {
                                interactionState.clearSnapping()
                            }
                        }
                    } else {
                        interactionState.clearSnapping()
                    }

                    if (currentIsDrawingConnection) {
                        var boardPos = interactionState.snappedWirePoint ?: ((position - currentOffset) / currentScale)
                        val startBoardPos = if (currentConnectionStartNodeId != null && currentConnectionStartPortId != null) {
                            currentGetPortBoardPosition(currentConnectionStartNodeId!!, currentConnectionStartPortId!!, currentConnectionStartIsOutput)
                        } else null
                        if (startBoardPos != null && interactionState.snappedWirePoint == null) {
                            if (event.keyboardModifiers.isShiftPressed || interactionState.isShiftModifierPressed) {
                                boardPos = SplineMathUtils.snapToStraightAngle(startBoardPos, boardPos)
                            } else if (event.keyboardModifiers.isCtrlPressed || interactionState.isCtrlModifierPressed) {
                                boardPos = boardPos.snapToGrid()
                            }
                        }
                        currentOnConnectionDrag(boardPos)
                    }

                    if (interactionState.isDrawingStructuredConnection) {
                        var livePos = interactionState.snappedWirePoint ?: ((position - currentOffset) / currentScale)
                        val lastPoint = if (interactionState.structuredConnectionPoints.isNotEmpty()) {
                            interactionState.structuredConnectionPoints.last()
                        } else {
                            val startBoardPos = if (interactionState.structuredConnectionSourceJunctionId != null) {
                                currentJunctions.find { it.id == interactionState.structuredConnectionSourceJunctionId }?.position?.toComposeOffset()
                            } else if (interactionState.structuredConnectionStartNodeId != null && interactionState.structuredConnectionStartPortId != null) {
                                currentGetPortBoardPosition(interactionState.structuredConnectionStartNodeId!!, interactionState.structuredConnectionStartPortId!!, interactionState.structuredConnectionStartIsOutput)
                            } else null
                            startBoardPos ?: livePos
                        }
                        if (interactionState.snappedWirePoint == null) {
                            if (event.keyboardModifiers.isShiftPressed || interactionState.isShiftModifierPressed) {
                                livePos = SplineMathUtils.snapToStraightAngle(lastPoint, livePos)
                            } else if (event.keyboardModifiers.isCtrlPressed || interactionState.isCtrlModifierPressed) {
                                livePos = livePos.snapToGrid()
                            }
                        }
                        currentOnConnectionDrag(livePos)
                        interactionState.structuredConnectionLivePos = livePos
                        if (!event.buttons.isSecondaryPressed && !event.buttons.isTertiaryPressed) {
                            event.changes.forEach { it.consume() }
                        }
                    }

                    val closestJunc = ConnectionHitTester.findClosestJunction(
                        position = position,
                        junctions = currentJunctions,
                        scale = currentScale,
                        offset = currentOffset,
                        hitRadius = maxOf(20f, 20f * currentScale)
                    )
                    interactionState.hoveredJunctionId = closestJunc?.id

                    val closestWp = ConnectionHitTester.findClosestWaypoint(
                        position = position,
                        connections = currentConnections,
                        scale = currentScale,
                        offset = currentOffset,
                        hitRadius = maxOf(20f, 20f * currentScale)
                    )
                    interactionState.hoveredWaypoint = closestWp?.let { Pair(it.first, it.second) }

                    val junctionMap = currentJunctions.associate { it.id to it.position.toComposeOffset() }
                    val closestMid = ConnectionHitTester.findClosestMidpoint(
                        position = position,
                        connections = currentConnections,
                        getPortBoardPosition = currentGetPortBoardPosition,
                        junctionMap = junctionMap,
                        scale = currentScale,
                        offset = currentOffset,
                        curveStyle = currentCurveStyle,
                        roundness = currentRoundness,
                        stepMode = currentOrthogonalStepMode
                    )
                    interactionState.hoveredMidpoint = closestMid?.let { Pair(it.first, it.second) }

                    var bestConnection: Connection? = null
                    var isHoveringPort = false
                    val portHoverRadius = 20f

                    currentNodes.forEach { node ->
                        node.inputs.forEach { port ->
                            val portBoardPos = currentGetPortBoardPosition(node.id, port.id, false) ?: return@forEach
                            val portScreenPos = (portBoardPos * currentScale) + currentOffset
                            if ((position - portScreenPos).getDistance() < portHoverRadius) {
                                isHoveringPort = true
                            }
                        }
                        node.outputs.forEach { port ->
                            val portBoardPos = currentGetPortBoardPosition(node.id, port.id, true) ?: return@forEach
                            val portScreenPos = (portBoardPos * currentScale) + currentOffset
                            if ((position - portScreenPos).getDistance() < portHoverRadius) {
                                isHoveringPort = true
                            }
                        }
                    }

                    if (!isHoveringPort && closestJunc == null && closestWp == null) {
                        bestConnection = ConnectionHitTester.findClosestConnection(
                            position = position,
                            connections = currentConnections,
                            getPortBoardPosition = currentGetPortBoardPosition,
                            scale = currentScale,
                            offset = currentOffset,
                            junctions = currentJunctions,
                            curveStyle = currentCurveStyle,
                            roundness = currentRoundness,
                            stepMode = currentOrthogonalStepMode
                        )
                    }

                    if (bestConnection != null && interactionState.isCtrlModifierPressed) {
                        val sourcePortBoardPos =
                            currentGetPortBoardPosition(bestConnection.sourceNodeId, bestConnection.sourcePortId, true)
                        val targetPortBoardPos =
                            currentGetPortBoardPosition(bestConnection.targetNodeId, bestConnection.targetPortId, false)
                        if (sourcePortBoardPos != null && targetPortBoardPos != null) {
                            val startPos = (sourcePortBoardPos * currentScale) + currentOffset
                            val endPos = (targetPortBoardPos * currentScale) + currentOffset
                            interactionState.hoveredConnectionIsSource = ConnectionHitTester.determineCloserEnd(position, startPos, endPos)
                        } else {
                            interactionState.hoveredConnectionIsSource = null
                        }
                    } else {
                        interactionState.hoveredConnectionIsSource = null
                    }
                    interactionState.hoveredConnection = bestConnection
                } else if (event.type == PointerEventType.Exit) {
                    interactionState.clearHoveredConnection()
                    interactionState.hoveredJunctionId = null
                    interactionState.clearHoveredWaypoint()
                    interactionState.clearHoveredMidpoint()
                    interactionState.draggingWaypoint = null
                    interactionState.draggingJunctionId = null
                    junctionLastDispatchedBoardPos = null
                    interactionState.pendingMidpoint = null
                    interactionState.pendingMidpointWasAltPressed = false
                    interactionState.clearSnapping()
                    rightClickStartPos = null
                    rightClickLastPos = null
                    rightClickDidDrag = false
                    middleClickLastPos = null
                } else if (event.type == PointerEventType.Press) {
                    if (event.buttons.isSecondaryPressed) {
                        val pos = event.changes.firstOrNull()?.position ?: position
                        rightClickStartPos = pos
                        rightClickLastPos = pos
                        rightClickDidDrag = false
                    } else if (event.buttons.isTertiaryPressed) {
                        val pos = event.changes.firstOrNull()?.position ?: position
                        middleClickLastPos = pos
                    } else if (interactionState.isDrawingStructuredConnection && event.buttons.isPrimaryPressed) {
                        // Check if snapped to wire segment to split and finalize
                        if (interactionState.snappedWirePoint != null && interactionState.snappedWireConnection != null) {
                            val snappedConn = interactionState.snappedWireConnection!!
                            val snappedPt = interactionState.snappedWirePoint!!.toModelOffset()
                            val isOutput = interactionState.structuredConnectionStartIsOutput
                            val srcNodeId = if (isOutput) interactionState.structuredConnectionStartNodeId else null
                            val srcPortId = if (isOutput) interactionState.structuredConnectionStartPortId else null
                            val srcJuncId = if (isOutput) interactionState.structuredConnectionSourceJunctionId else null
                            val tgtNodeId = if (!isOutput) interactionState.structuredConnectionStartNodeId else null
                            val tgtPortId = if (!isOutput) interactionState.structuredConnectionStartPortId else null
                            val tgtJuncId = if (!isOutput) interactionState.structuredConnectionSourceJunctionId else null
                            val interPts = interactionState.structuredConnectionPoints.map { it.toModelOffset() }

                            currentOnSplitConnectionAndConnect?.invoke(
                                snappedConn,
                                snappedPt,
                                srcNodeId,
                                srcPortId,
                                srcJuncId,
                                tgtNodeId,
                                tgtPortId,
                                tgtJuncId,
                                interPts
                            )
                            interactionState.resetStructuredConnection()
                            interactionState.clearSnapping()
                            event.changes.forEach { it.consume() }
                        } else {
                            // Check if over an existing junction to finalize
                            val targetJuncId = interactionState.hoveredJunctionId
                            if (targetJuncId != null && targetJuncId != interactionState.structuredConnectionSourceJunctionId) {
                                val finalSourceNodeId = if (interactionState.structuredConnectionStartIsOutput) (interactionState.structuredConnectionStartNodeId ?: -1L) else -1L
                                val finalSourcePortId = if (interactionState.structuredConnectionStartIsOutput) (interactionState.structuredConnectionStartPortId ?: "") else ""
                                val finalTargetNodeId = if (!interactionState.structuredConnectionStartIsOutput) (interactionState.structuredConnectionStartNodeId ?: -1L) else -1L
                                val finalTargetPortId = if (!interactionState.structuredConnectionStartIsOutput) (interactionState.structuredConnectionStartPortId ?: "") else ""
                                val finalSrcJuncId = if (!interactionState.structuredConnectionStartIsOutput) targetJuncId else interactionState.structuredConnectionSourceJunctionId
                                val finalTgtJuncId = if (interactionState.structuredConnectionStartIsOutput) targetJuncId else null

                                currentOnFinalizeStructuredConnection?.invoke(
                                    finalSourceNodeId,
                                    finalSourcePortId,
                                    finalSrcJuncId,
                                    finalTargetNodeId,
                                    finalTargetPortId,
                                    interactionState.structuredConnectionPoints.map { it.toModelOffset() },
                                    finalTgtJuncId
                                )
                                interactionState.resetStructuredConnection()
                                event.changes.forEach { it.consume() }
                            } else {
                                // Check if over a port to finalize
                                var clickedPortNodeId: Long? = null
                                var clickedPortId: String? = null
                                currentNodes.forEach { node ->
                                    val portsToCheck = if (interactionState.structuredConnectionStartIsOutput) node.inputs else node.outputs
                                    portsToCheck.forEach { port ->
                                        val portBoardPos = currentGetPortBoardPosition(node.id, port.id, !interactionState.structuredConnectionStartIsOutput) ?: return@forEach
                                        val portScreenPos = (portBoardPos * currentScale) + currentOffset
                                        if ((position - portScreenPos).getDistance() < 32f) {
                                            clickedPortNodeId = node.id
                                            clickedPortId = port.id
                                        }
                                    }
                                }

                                val resolvedPortNodeId = clickedPortNodeId ?: currentHighlightedNodeId
                                val resolvedPortId = clickedPortId ?: currentHighlightedPortId

                                if (resolvedPortNodeId != null && resolvedPortId != null) {
                                    val finalTargetNodeId = if (interactionState.structuredConnectionStartIsOutput) resolvedPortNodeId else (interactionState.structuredConnectionStartNodeId ?: -1L)
                                    val finalTargetPortId = if (interactionState.structuredConnectionStartIsOutput) resolvedPortId else (interactionState.structuredConnectionStartPortId ?: "")
                                    val finalSourceNodeId = if (interactionState.structuredConnectionStartIsOutput) (interactionState.structuredConnectionStartNodeId ?: -1L) else resolvedPortNodeId
                                    val finalSourcePortId = if (interactionState.structuredConnectionStartIsOutput) (interactionState.structuredConnectionStartPortId ?: "") else resolvedPortId

                                    currentOnFinalizeStructuredConnection?.invoke(
                                        finalSourceNodeId,
                                        finalSourcePortId,
                                        interactionState.structuredConnectionSourceJunctionId,
                                        finalTargetNodeId,
                                        finalTargetPortId,
                                        interactionState.structuredConnectionPoints.map { it.toModelOffset() },
                                        null
                                    )
                                    interactionState.resetStructuredConnection()
                                    currentOnResetDrawingConnection?.invoke()
                                    event.changes.forEach { it.consume() }
                                } else {
                                    // Commit point to structured connection
                                    val startBoardPos = if (interactionState.structuredConnectionSourceJunctionId != null) {
                                        currentJunctions.find { it.id == interactionState.structuredConnectionSourceJunctionId }?.position?.toComposeOffset()
                                    } else if (interactionState.structuredConnectionStartNodeId != null && interactionState.structuredConnectionStartPortId != null) {
                                        currentGetPortBoardPosition(interactionState.structuredConnectionStartNodeId!!, interactionState.structuredConnectionStartPortId!!, interactionState.structuredConnectionStartIsOutput)
                                    } else null

                                    val lastPoint = if (interactionState.structuredConnectionPoints.isNotEmpty()) {
                                        interactionState.structuredConnectionPoints.last()
                                    } else {
                                        startBoardPos ?: ((position - currentOffset) / currentScale)
                                    }

                                    var committedPos = (position - currentOffset) / currentScale
                                    if (interactionState.isShiftModifierPressed || event.keyboardModifiers.isShiftPressed) {
                                        committedPos = SplineMathUtils.snapToStraightAngle(lastPoint, committedPos)
                                    } else if (interactionState.isCtrlModifierPressed || event.keyboardModifiers.isCtrlPressed) {
                                        committedPos = committedPos.snapToGrid()
                                    }
                                    interactionState.structuredConnectionPoints = (interactionState.structuredConnectionPoints + committedPos).toMutableList()
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                    } else if (event.buttons.isPrimaryPressed) {
                        interactionState.lastPointerPosition = position
                        val isAlt = event.keyboardModifiers.isAltPressed || interactionState.isAltModifierPressed
                        val isShift = event.keyboardModifiers.isShiftPressed || interactionState.isShiftModifierPressed

                        // Direct hit-test existing points first: Priority over wire clicks
                        val hitJunc = ConnectionHitTester.findClosestJunction(
                            position = position,
                            junctions = currentJunctions,
                            scale = currentScale,
                            offset = currentOffset,
                            hitRadius = maxOf(20f, 20f * currentScale)
                        )
                        val hitWp = ConnectionHitTester.findClosestWaypoint(
                            position = position,
                            connections = currentConnections,
                            scale = currentScale,
                            offset = currentOffset,
                            hitRadius = maxOf(20f, 20f * currentScale)
                        )

                        val distJunc = hitJunc?.let {
                            val screenPos = (it.position.toComposeOffset() * currentScale) + currentOffset
                            (position - screenPos).getDistance()
                        } ?: Float.MAX_VALUE

                        val distWp = hitWp?.let {
                            (position - it.third).getDistance()
                        } ?: Float.MAX_VALUE

                        if (distWp < distJunc && hitWp != null) {
                            val wp = hitWp
                            if (currentIsEyedropperActive && currentOnSampleColor != null) {
                                currentOnSampleColor?.invoke(wp.first.color ?: "#808080")
                                event.changes.forEach { it.consume() }
                            } else if (currentIsPaintToolActive && currentOnPaintConnection != null) {
                                currentOnPaintConnection?.invoke(wp.first)
                                event.changes.forEach { it.consume() }
                            } else if (currentIsWashToolActive && currentOnWashConnection != null) {
                                currentOnWashConnection?.invoke(wp.first)
                                event.changes.forEach { it.consume() }
                            } else {
                                val actId = if (isShift) ShortcutActionId.FLOW_DELETE_SELECTED else ShortcutActionId.FLOW_MOVE_POINT
                                if (isShift) {
                                    currentOnDeleteWaypoint?.invoke(wp.first, wp.second)
                                    interactionState.hoveredWaypoint = null
                                } else {
                                    interactionState.draggingWaypoint = Pair(wp.first, wp.second)
                                    interactionState.lastPointerPosition = position
                                }
                                currentShortcutManager?.eat(event, actId) ?: event.changes.forEach { it.consume() }
                            }
                        } else if (hitJunc != null) {
                            val juncId = hitJunc.id
                            if (currentIsEyedropperActive || currentIsPaintToolActive || currentIsWashToolActive) {
                                val connectedConns = currentConnections.filter { it.sourceJunctionId == juncId || it.targetJunctionId == juncId }
                                if (currentIsEyedropperActive && currentOnSampleColor != null) {
                                    val sample = connectedConns.firstOrNull { it.color != null }?.color ?: "#808080"
                                    currentOnSampleColor?.invoke(sample)
                                    event.changes.forEach { it.consume() }
                                } else if (currentIsPaintToolActive && currentOnPaintConnection != null) {
                                    connectedConns.forEach { currentOnPaintConnection?.invoke(it) }
                                    event.changes.forEach { it.consume() }
                                } else if (currentIsWashToolActive && currentOnWashConnection != null) {
                                    connectedConns.forEach { currentOnWashConnection?.invoke(it) }
                                    event.changes.forEach { it.consume() }
                                }
                            } else if (isAlt) {
                                // Ramification / branching directly from junction
                                interactionState.isDrawingStructuredConnection = true
                                interactionState.structuredConnectionSourceJunctionId = juncId
                                interactionState.structuredConnectionStartNodeId = null
                                interactionState.structuredConnectionStartPortId = null
                                interactionState.structuredConnectionStartIsOutput = true
                                interactionState.structuredConnectionPoints = mutableListOf()
                                interactionState.structuredConnectionLivePos = (position - currentOffset) / currentScale
                                currentShortcutManager?.eat(event, ShortcutActionId.FLOW_CREATE_RAMIFICATION) ?: event.changes.forEach { it.consume() }
                            } else if (isShift) {
                                currentOnDeleteJunction?.invoke(juncId)
                                interactionState.hoveredJunctionId = null
                                currentShortcutManager?.eat(event, ShortcutActionId.FLOW_DELETE_SELECTED) ?: event.changes.forEach { it.consume() }
                            } else {
                                val isSelected = juncId in currentSelectedPointIds
                                if (!isSelected) {
                                    currentOnSelectPoints?.invoke(setOf(juncId))
                                    currentOnSelectNodes?.invoke(emptySet())
                                    currentOnSelectGroups?.invoke(emptySet())
                                    currentOnSelectLabels?.invoke(emptySet())
                                    junctionDragStartPointPositions = mapOf(juncId to hitJunc.position)
                                    junctionDragStartNodePositions = emptyMap()
                                    junctionDragStartGroupPositions = emptyMap()
                                    junctionDragStartLabelPositions = emptyMap()
                                } else {
                                    junctionDragStartPointPositions = currentJunctions.filter { it.id in currentSelectedPointIds }.associate { it.id to it.position }
                                    junctionDragStartNodePositions = currentNodes.filter { it.id in currentSelectedNodeIds }.associate { it.id to it.position }
                                    junctionDragStartGroupPositions = currentGroups.filter { it.id in currentSelectedGroupIds }.associate { it.id to it.position }
                                    junctionDragStartLabelPositions = currentLabels.filter { it.id in currentSelectedLabelIds }.associate { it.id to it.position }
                                }
                                interactionState.selectedJunctionId = juncId
                                interactionState.draggingJunctionId = juncId
                                interactionState.lastPointerPosition = position
                                junctionDragStartPointerPosition = position
                                junctionLastDispatchedBoardPos = hitJunc.position.toComposeOffset()
                                currentShortcutManager?.eat(event, ShortcutActionId.FLOW_MOVE_POINT) ?: event.changes.forEach { it.consume() }
                            }
                        } else {
                            val connProj = ConnectionHitTester.findClosestConnectionWithProjection(
                                position = position,
                                connections = currentConnections,
                                getPortBoardPosition = currentGetPortBoardPosition,
                                scale = currentScale,
                                offset = currentOffset,
                                junctions = currentJunctions,
                                curveStyle = currentCurveStyle,
                                roundness = currentRoundness,
                                stepMode = currentOrthogonalStepMode
                            )
                            if (connProj != null) {
                                if (currentIsEyedropperActive && currentOnSampleColor != null) {
                                    currentOnSampleColor?.invoke(connProj.connection.color ?: "#808080")
                                    event.changes.forEach { it.consume() }
                                } else if (currentIsPaintToolActive && currentOnPaintConnection != null) {
                                    currentOnPaintConnection?.invoke(connProj.connection)
                                    event.changes.forEach { it.consume() }
                                } else if (currentIsWashToolActive && currentOnWashConnection != null) {
                                    currentOnWashConnection?.invoke(connProj.connection)
                                    event.changes.forEach { it.consume() }
                                } else if (isShift) {
                                    if (connProj.connection.waypoints.isNotEmpty() && currentOnDeleteConnectionSegment != null) {
                                        currentOnDeleteConnectionSegment?.invoke(connProj.connection, connProj.segmentIndex)
                                    } else {
                                        currentOnDeleteConnection(connProj.connection)
                                    }
                                    interactionState.clearHoveredConnection()
                                    if (interactionState.selectedConnection == connProj.connection) {
                                        interactionState.selectedConnection = null
                                    }
                                    event.changes.forEach { it.consume() }
                                } else if (isAlt) {
                                    // Alt + drag on wire segment: move the entire segment!
                                    interactionState.draggingSegment = DraggingSegmentInfo(
                                        connection = connProj.connection,
                                        segmentIndex = connProj.segmentIndex
                                    )
                                    segmentDragStartPos = position
                                    interactionState.lastPointerPosition = position
                                    currentShortcutManager?.eat(event, ShortcutActionId.FLOW_MOVE_POINT) ?: event.changes.forEach { it.consume() }
                                } else {
                                    // Normal click anywhere on wire: spawn new point (junction) and immediately begin dragging it!
                                    val newJuncId = (currentJunctions.maxOfOrNull { it.id } ?: 0L) + 1L
                                    currentOnAddJunctionAndBranch?.invoke(connProj.connection, connProj.projectedPoint, connProj.segmentIndex)
                                    interactionState.selectedJunctionId = newJuncId
                                    currentOnSelectPoints?.invoke(setOf(newJuncId))
                                    currentOnSelectNodes?.invoke(emptySet())
                                    currentOnSelectGroups?.invoke(emptySet())
                                    currentOnSelectLabels?.invoke(emptySet())
                                    interactionState.draggingJunctionId = newJuncId
                                    interactionState.lastPointerPosition = position
                                    junctionDragStartPointerPosition = position
                                    junctionDragStartPointPositions = mapOf(newJuncId to connProj.projectedPoint.toModelOffset())
                                    junctionDragStartNodePositions = emptyMap()
                                    junctionDragStartGroupPositions = emptyMap()
                                    junctionDragStartLabelPositions = emptyMap()
                                    junctionLastDispatchedBoardPos = connProj.projectedPoint
                                    currentShortcutManager?.eat(event, ShortcutActionId.FLOW_MOVE_POINT) ?: event.changes.forEach { it.consume() }
                                }
                            }
                        }
                    } else if (event.keyboardModifiers.isCtrlPressed) {
                        interactionState.hoveredConnection?.let { conn ->
                            val isSrc = interactionState.hoveredConnectionIsSource ?: false
                            val boardPos = (position - currentOffset) / currentScale
                            currentOnDetachConnection(conn, isSrc, boardPos)
                            event.changes.forEach { it.consume() }
                        }
                    }
                } else if (event.type == PointerEventType.Release) {
                    if (rightClickStartPos != null) {
                        if (!rightClickDidDrag) {
                            // Right-click tap without drag: cancel ongoing drawing sessions cleanly
                            if (interactionState.isDrawingStructuredConnection) {
                                interactionState.resetStructuredConnection()
                                interactionState.clearSnapping()
                                event.changes.forEach { it.consume() }
                            } else if (currentIsDrawingConnection) {
                                interactionState.clearSnapping()
                                currentOnConnectionDrop(false)
                                event.changes.forEach { it.consume() }
                            }
                        }
                        rightClickStartPos = null
                        rightClickLastPos = null
                        rightClickDidDrag = false
                    }
                    middleClickLastPos = null
                    if (interactionState.draggingSegment != null) {
                        val seg = interactionState.draggingSegment!!
                        val totalDelta = if (segmentDragStartPos != null) {
                            (position - segmentDragStartPos!!) / currentScale
                        } else Offset.Zero
                        currentOnEndMoveSegment?.invoke(seg.connection, seg.segmentIndex, totalDelta.toModelOffset())
                        interactionState.draggingSegment = null
                        segmentDragStartPos = null
                    }
                    if (interactionState.draggingJunctionId != null) {
                        val isCtrl = event.keyboardModifiers.isCtrlPressed || interactionState.isCtrlModifierPressed
                        val pointMoves = junctionDragStartPointPositions.mapValues { (id, startPos) ->
                            val currentPos = currentJunctions.find { it.id == id }?.position ?: startPos
                            startPos to (if (isCtrl) currentPos.snapToGrid() else currentPos)
                        }.filter { it.value.first != it.value.second }

                        val nodeMoves = junctionDragStartNodePositions.mapValues { (id, startPos) ->
                            startPos to (currentNodes.find { it.id == id }?.position ?: startPos)
                        }.filter { it.value.first != it.value.second }

                        val groupMoves = junctionDragStartGroupPositions.mapValues { (id, startPos) ->
                            startPos to (currentGroups.find { it.id == id }?.position ?: startPos)
                        }.filter { it.value.first != it.value.second }

                        val labelMoves = junctionDragStartLabelPositions.mapValues { (id, startPos) ->
                            startPos to (currentLabels.find { it.id == id }?.position ?: startPos)
                        }.filter { it.value.first != it.value.second }

                        if (pointMoves.isNotEmpty() || nodeMoves.isNotEmpty() || groupMoves.isNotEmpty() || labelMoves.isNotEmpty()) {
                            currentOnEndMoveJunction?.invoke(pointMoves, nodeMoves, groupMoves, labelMoves)
                        }

                        junctionDragStartPointPositions = emptyMap()
                        junctionDragStartNodePositions = emptyMap()
                        junctionDragStartGroupPositions = emptyMap()
                        junctionDragStartLabelPositions = emptyMap()
                        junctionDragStartPointerPosition = null
                        junctionLastDispatchedBoardPos = null
                        interactionState.draggingJunctionId = null
                    }
                    interactionState.draggingWaypoint = null
                    if (currentIsDrawingConnection && !event.buttons.isPrimaryPressed) {
                        if (interactionState.snappedWirePoint != null && interactionState.snappedWireConnection != null) {
                            val snappedConn = interactionState.snappedWireConnection!!
                            val snappedPt = interactionState.snappedWirePoint!!.toModelOffset()
                            val isOutput = currentConnectionStartIsOutput
                            val srcNodeId = if (isOutput) currentConnectionStartNodeId else null
                            val srcPortId = if (isOutput) currentConnectionStartPortId else null
                            val tgtNodeId = if (!isOutput) currentConnectionStartNodeId else null
                            val tgtPortId = if (!isOutput) currentConnectionStartPortId else null

                            currentOnSplitConnectionAndConnect?.invoke(
                                snappedConn,
                                snappedPt,
                                srcNodeId,
                                srcPortId,
                                null,
                                tgtNodeId,
                                tgtPortId,
                                null,
                                emptyList()
                            )
                            interactionState.clearSnapping()
                            currentOnResetDrawingConnection?.invoke()
                        } else {
                            currentOnConnectionDrop(event.keyboardModifiers.isShiftPressed)
                        }
                    }
                    interactionState.clearSnapping()
                }
            }
        }
    }
}

