package org.wip.plugintoolkit.features.flows.ui.canvas

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.FlowGroup
import org.wip.plugintoolkit.features.flows.model.FlowJunction
import org.wip.plugintoolkit.features.flows.model.FlowLabel
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.ui.toModelOffset
import org.wip.plugintoolkit.features.settings.model.ConnectionCurveStyle

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
    onAddJunctionAndBranch: ((Connection, Offset) -> Unit)? = null,
    junctions: List<FlowJunction> = emptyList(),
    curveStyle: ConnectionCurveStyle = ConnectionCurveStyle.Bezier,
    roundness: Float = 0.5f,
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
    groups
) {
    val d = density?.density ?: 1f
    detectTapGestures(
        onDoubleTap = { tapOffset ->
            focusRequester.requestFocus()
            val projection = ConnectionHitTester.findClosestConnectionWithProjection(
                position = tapOffset,
                connections = connections,
                getPortBoardPosition = getPortBoardPosition,
                scale = scale,
                offset = offset,
                junctions = junctions,
                curveStyle = curveStyle,
                roundness = roundness,
                groups = groups,
                density = d
            )
            if (projection != null && onAddJunctionAndBranch != null) {
                onAddJunctionAndBranch(projection.first, projection.second)
            }
        },
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
                density = d
            )
            if (bestConnection != null) {
                if (isEyedropperActive && onSampleColor != null) {
                    onSampleColor(bestConnection.color ?: "#808080")
                } else if (isPaintToolActive && onPaintConnection != null) {
                    onPaintConnection(bestConnection)
                } else if (isWashToolActive && onWashConnection != null) {
                    onWashConnection(bestConnection)
                } else {
                    interactionState.selectedConnection = bestConnection
                }
            } else {
                interactionState.selectedConnection = null
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
    onPan: (Offset) -> Unit
): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            val isPanButtonPressed = event.buttons.isSecondaryPressed || event.buttons.isTertiaryPressed
            if (event.type == PointerEventType.Press && isPanButtonPressed) {
                val change = event.changes.firstOrNull()
                if (change == null) continue

                focusRequester.requestFocus()
                var lastPoint = change.position
                if (!event.buttons.isPrimaryPressed) {
                    change.consume()
                }

                while (true) {
                    val dragEvent = awaitPointerEvent()
                    val isStillPanning = dragEvent.buttons.isSecondaryPressed || dragEvent.buttons.isTertiaryPressed
                    if (!isStillPanning) {
                        break
                    }
                    if (dragEvent.type == PointerEventType.Move) {
                        val currentPoint = dragEvent.changes.firstOrNull()?.position ?: lastPoint
                        val delta = currentPoint - lastPoint
                        onPan(delta)
                        lastPoint = currentPoint
                        if (!dragEvent.buttons.isPrimaryPressed) {
                            dragEvent.changes.forEach { it.consume() }
                        }
                    }
                }
            }
        }
    }
}

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
    onSelectLabels: ((Set<Long>) -> Unit)? = null,
    onSelectGroups: ((Set<Long>) -> Unit)? = null,
    isPaintToolActive: Boolean = false,
    isWashToolActive: Boolean = false,
    onPaintSelection: (() -> Unit)? = null,
    onWashSelection: (() -> Unit)? = null
): Modifier = this.pointerInput(nodes, nodeSizes, labels, groups, density, scale, offset, isDrawingConnection, isPaintToolActive, isWashToolActive) {
    detectDragGestures(
        onDragStart = { startOffset ->
            if (!isDrawingConnection) {
                val modelPoint = (startOffset - offset) / scale
                val isOverNode = nodes.any { node ->
                    val nodeLeft = node.position.x
                    val nodeTop = node.position.y
                    val nodeWidth = nodeSizes[node.id]?.width?.toFloat() ?: defaultNodeWidthPx
                    val nodeHeight = nodeSizes[node.id]?.height?.toFloat() ?: (180f * density.density)
                    modelPoint.x >= nodeLeft && modelPoint.x <= nodeLeft + nodeWidth &&
                            modelPoint.y >= nodeTop && modelPoint.y <= nodeTop + nodeHeight
                }
                val isOverGroupHeader = groups.any { group ->
                    val gLeft = group.position.x
                    val gTop = group.position.y
                    val gWidth = group.size.x
                    val headerHeight = 48f
                    modelPoint.x >= gLeft && modelPoint.x <= gLeft + gWidth &&
                            modelPoint.y >= gTop && modelPoint.y <= gTop + headerHeight
                }
                val isOverLabel = labels.any { label ->
                    val lLeft = label.position.x
                    val lTop = label.position.y
                    val lWidth = maxOf(80f, label.text.length * 9f)
                    val lHeight = 36f
                    modelPoint.x >= lLeft && modelPoint.x <= lLeft + lWidth &&
                            modelPoint.y >= lTop && modelPoint.y <= lTop + lHeight
                }
                if (!isOverNode && !isOverGroupHeader && !isOverLabel) {
                    focusRequester.requestFocus()
                    interactionState.selectionStart = startOffset
                    interactionState.selectionEnd = startOffset
                }
            }
        },
        onDragEnd = {
            if (isPaintToolActive) {
                onPaintSelection?.invoke()
            } else if (isWashToolActive) {
                onWashSelection?.invoke()
            }
            interactionState.clearSelectionBox()
        },
        onDragCancel = {
            interactionState.clearSelectionBox()
        },
        onDrag = { change, _ ->
            if (!isDrawingConnection && interactionState.selectionStart != null) {
                interactionState.selectionEnd = change.position

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
            }
        }
    )
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
    onDeleteJunction: ((Long) -> Unit)? = null,
    onAddWaypoint: ((Connection, Offset) -> Unit)? = null,
    onMoveWaypoint: ((Connection, Int, org.wip.plugintoolkit.features.flows.model.Offset) -> Unit)? = null,
    onDeleteWaypoint: ((Connection, Int) -> Unit)? = null,
    curveStyle: ConnectionCurveStyle = ConnectionCurveStyle.Bezier,
    roundness: Float = 0.5f
): Modifier {
    val currentIsDrawingConnection by rememberUpdatedState(isDrawingConnection)
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
    val currentOnDeleteJunction by rememberUpdatedState(onDeleteJunction)
    val currentOnAddWaypoint by rememberUpdatedState(onAddWaypoint)
    val currentOnMoveWaypoint by rememberUpdatedState(onMoveWaypoint)
    val currentOnDeleteWaypoint by rememberUpdatedState(onDeleteWaypoint)
    val currentCurveStyle by rememberUpdatedState(curveStyle)
    val currentRoundness by rememberUpdatedState(roundness)

    return this.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                val position = event.changes.firstOrNull()?.position ?: Offset.Zero

                if (event.type == PointerEventType.Scroll) {
                    val scrollDelta = event.changes.firstOrNull()?.scrollDelta ?: Offset.Zero
                    val delta = if (scrollDelta.y != 0f) scrollDelta.y else scrollDelta.x
                    if (delta != 0f) {
                        val isShiftPressed = event.keyboardModifiers.isShiftPressed
                        currentOnZoom(-delta, position, isShiftPressed)
                    }
                } else if (event.type == PointerEventType.Move) {
                    val prevPointerPosition = interactionState.lastPointerPosition
                    interactionState.lastPointerPosition = position
                    interactionState.isCtrlModifierPressed = event.keyboardModifiers.isCtrlPressed
                    interactionState.isShiftModifierPressed = event.keyboardModifiers.isShiftPressed
                    interactionState.isAltModifierPressed = event.keyboardModifiers.isAltPressed

                    val draggingJuncId = interactionState.draggingJunctionId
                    if (draggingJuncId != null) {
                        val delta = (position - prevPointerPosition) / currentScale
                        currentOnMoveJunction?.invoke(draggingJuncId, delta.toModelOffset())
                    }

                    val draggingWp = interactionState.draggingWaypoint
                    if (draggingWp != null) {
                        val delta = (position - prevPointerPosition) / currentScale
                        currentOnMoveWaypoint?.invoke(draggingWp.first, draggingWp.second, delta.toModelOffset())
                    }

                    if (currentIsDrawingConnection) {
                        val boardPos = (position - currentOffset) / currentScale
                        currentOnConnectionDrag(boardPos)
                    }

                    val closestJunc = ConnectionHitTester.findClosestJunction(
                        position = position,
                        junctions = currentJunctions,
                        scale = currentScale,
                        offset = currentOffset
                    )
                    interactionState.hoveredJunctionId = closestJunc?.id

                    val closestWp = ConnectionHitTester.findClosestWaypoint(
                        position = position,
                        connections = currentConnections,
                        scale = currentScale,
                        offset = currentOffset
                    )
                    interactionState.hoveredWaypoint = closestWp?.let { Pair(it.first, it.second) }

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
                            roundness = currentRoundness
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
                } else if (event.type == PointerEventType.Press) {
                    if (event.keyboardModifiers.isAltPressed && event.buttons.isPrimaryPressed) {
                        val connProj = ConnectionHitTester.findClosestConnectionWithProjection(
                            position = position,
                            connections = currentConnections,
                            getPortBoardPosition = currentGetPortBoardPosition,
                            scale = currentScale,
                            offset = currentOffset,
                            junctions = currentJunctions,
                            curveStyle = currentCurveStyle,
                            roundness = currentRoundness
                        )
                        if (connProj != null && currentOnAddWaypoint != null) {
                            currentOnAddWaypoint?.invoke(connProj.first, connProj.second)
                            event.changes.forEach { it.consume() }
                        }
                    } else if (event.keyboardModifiers.isShiftPressed && event.buttons.isPrimaryPressed) {
                        val wp = interactionState.hoveredWaypoint
                        if (wp != null && currentOnDeleteWaypoint != null) {
                            currentOnDeleteWaypoint?.invoke(wp.first, wp.second)
                            interactionState.hoveredWaypoint = null
                            event.changes.forEach { it.consume() }
                        } else {
                            val juncId = interactionState.hoveredJunctionId
                            if (juncId != null && currentOnDeleteJunction != null) {
                                currentOnDeleteJunction?.invoke(juncId)
                                interactionState.hoveredJunctionId = null
                            } else {
                                interactionState.hoveredConnection?.let { conn ->
                                    currentOnDeleteConnection(conn)
                                    interactionState.clearHoveredConnection()
                                    if (interactionState.selectedConnection == conn) {
                                        interactionState.selectedConnection = null
                                    }
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
                    } else if (event.buttons.isPrimaryPressed) {
                        if (interactionState.hoveredWaypoint != null) {
                            interactionState.draggingWaypoint = interactionState.hoveredWaypoint
                            event.changes.forEach { it.consume() }
                        } else if (interactionState.hoveredJunctionId != null) {
                            interactionState.draggingJunctionId = interactionState.hoveredJunctionId
                            event.changes.forEach { it.consume() }
                        }
                    }
                } else if (event.type == PointerEventType.Release) {
                    interactionState.draggingJunctionId = null
                    interactionState.draggingWaypoint = null
                    if (currentIsDrawingConnection && !event.buttons.isPrimaryPressed) {
                        currentOnConnectionDrop(event.keyboardModifiers.isShiftPressed)
                    }
                }
            }
        }
    }
}

