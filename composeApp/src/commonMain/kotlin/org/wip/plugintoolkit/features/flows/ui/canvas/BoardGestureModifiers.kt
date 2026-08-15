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
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.Node

fun Modifier.boardConnectionTapGesture(
    interactionState: BoardInteractionState,
    connections: List<Connection>,
    scale: Float,
    offset: Offset,
    getPortBoardPosition: (Long, String, Boolean) -> Offset?,
    focusRequester: FocusRequester,
    onClearSelection: () -> Unit
): Modifier = this.pointerInput(connections, getPortBoardPosition, scale, offset) {
    detectTapGestures { tapOffset ->
        focusRequester.requestFocus()
        val bestConnection = ConnectionHitTester.findClosestConnection(
            position = tapOffset,
            connections = connections,
            getPortBoardPosition = getPortBoardPosition,
            scale = scale,
            offset = offset
        )
        interactionState.selectedConnection = bestConnection
        if (bestConnection == null) {
            onClearSelection()
        }
    }
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
                if (change == null || change.isConsumed) continue

                focusRequester.requestFocus()
                var lastPoint = change.position
                change.consume()

                while (true) {
                    val dragEvent = awaitPointerEvent()
                    val isStillPanning = dragEvent.buttons.isSecondaryPressed || dragEvent.buttons.isTertiaryPressed
                    if (!isStillPanning) {
                        break
                    }
                    if (dragEvent.type == PointerEventType.Move) {
                        val currentPoint = dragEvent.changes.first().position
                        val delta = currentPoint - lastPoint
                        onPan(delta)
                        lastPoint = currentPoint
                        dragEvent.changes.forEach { it.consume() }
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
    onSelectNodes: (Set<Long>) -> Unit
): Modifier = this.pointerInput(nodes, nodeSizes, density, scale, offset, isDrawingConnection) {
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
                if (!isOverNode) {
                    focusRequester.requestFocus()
                    interactionState.selectionStart = startOffset
                    interactionState.selectionEnd = startOffset
                }
            }
        },
        onDragEnd = {
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

                val selectedIds = mutableSetOf<Long>()
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
                        selectedIds.add(node.id)
                    }
                }
                onSelectNodes(selectedIds)
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
    onConnectionCancel: () -> Unit,
    onDeleteConnection: (Connection) -> Unit,
    onDetachConnection: (Connection, Boolean, Offset) -> Unit
): Modifier {
    val currentIsDrawingConnection by rememberUpdatedState(isDrawingConnection)
    val currentOnConnectionDrag by rememberUpdatedState(onConnectionDrag)
    val currentOnConnectionDrop by rememberUpdatedState(onConnectionDrop)
    val currentOnConnectionCancel by rememberUpdatedState(onConnectionCancel)
    val currentOnDetachConnection by rememberUpdatedState(onDetachConnection)

    return this.pointerInput(connections, nodes, scale, offset) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            val position = event.changes.firstOrNull()?.position ?: Offset.Zero

            if (event.type == PointerEventType.Scroll) {
                val scrollDelta = event.changes.firstOrNull()?.scrollDelta ?: Offset.Zero
                val delta = if (scrollDelta.y != 0f) scrollDelta.y else scrollDelta.x
                if (delta != 0f) {
                    val isShiftPressed = event.keyboardModifiers.isShiftPressed
                    onZoom(-delta, position, isShiftPressed)
                }
            } else if (event.type == PointerEventType.Move) {
                interactionState.lastPointerPosition = position
                if (currentIsDrawingConnection) {
                    val boardPos = (position - offset) / scale
                    currentOnConnectionDrag(boardPos)
                }

                var bestConnection: Connection? = null
                var isHoveringPort = false
                val portHoverRadius = 20f

                nodes.forEach { node ->
                    node.inputs.forEach { port ->
                        val portBoardPos = getPortBoardPosition(node.id, port.id, false) ?: return@forEach
                        val portScreenPos = (portBoardPos * scale) + offset
                        if ((position - portScreenPos).getDistance() < portHoverRadius) {
                            isHoveringPort = true
                        }
                    }
                    node.outputs.forEach { port ->
                        val portBoardPos = getPortBoardPosition(node.id, port.id, true) ?: return@forEach
                        val portScreenPos = (portBoardPos * scale) + offset
                        if ((position - portScreenPos).getDistance() < portHoverRadius) {
                            isHoveringPort = true
                        }
                    }
                }

                if (!isHoveringPort) {
                    bestConnection = ConnectionHitTester.findClosestConnection(
                        position = position,
                        connections = connections,
                        getPortBoardPosition = getPortBoardPosition,
                        scale = scale,
                        offset = offset
                    )
                }
                interactionState.isCtrlModifierPressed = event.keyboardModifiers.isCtrlPressed
                interactionState.lastPointerPosition = position

                if (bestConnection != null && interactionState.isCtrlModifierPressed) {
                    val sourcePortBoardPos =
                        getPortBoardPosition(bestConnection.sourceNodeId, bestConnection.sourcePortId, true)
                    val targetPortBoardPos =
                        getPortBoardPosition(bestConnection.targetNodeId, bestConnection.targetPortId, false)
                    if (sourcePortBoardPos != null && targetPortBoardPos != null) {
                        val startPos = (sourcePortBoardPos * scale) + offset
                        val endPos = (targetPortBoardPos * scale) + offset
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
            } else if (event.type == PointerEventType.Press) {
                if (event.buttons.isSecondaryPressed || event.keyboardModifiers.isShiftPressed) {
                    interactionState.hoveredConnection?.let { conn ->
                        onDeleteConnection(conn)
                        interactionState.clearHoveredConnection()
                        if (interactionState.selectedConnection == conn) {
                            interactionState.selectedConnection = null
                        }
                    }
                } else if (event.keyboardModifiers.isCtrlPressed) {
                    interactionState.hoveredConnection?.let { conn ->
                        val isSrc = interactionState.hoveredConnectionIsSource ?: false
                        currentOnDetachConnection(conn, isSrc, position)

                        event.changes.forEach { it.consume() }

                        var completed = false
                        try {
                            while (true) {
                                val dragEvent = awaitPointerEvent()
                                if (dragEvent.type == PointerEventType.Move) {
                                    val screenPos = dragEvent.changes.firstOrNull()?.position ?: Offset.Zero
                                    interactionState.lastPointerPosition = screenPos
                                    val boardPos = (screenPos - offset) / scale
                                    currentOnConnectionDrag(boardPos)
                                    dragEvent.changes.forEach { it.consume() }
                                } else if (dragEvent.type == PointerEventType.Release) {
                                    currentOnConnectionDrop(dragEvent.keyboardModifiers.isShiftPressed)
                                    completed = true
                                    break
                                }
                            }
                        } finally {
                            if (!completed) currentOnConnectionCancel()
                        }
                    }
                }
            }
        }
    }
}
}
