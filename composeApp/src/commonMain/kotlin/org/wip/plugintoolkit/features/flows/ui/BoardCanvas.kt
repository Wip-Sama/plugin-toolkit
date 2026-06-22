package org.wip.plugintoolkit.features.flows.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.utils.BoardMathUtils
import org.wip.plugintoolkit.features.flows.viewmodel.FlowEditorState
import org.wip.plugintoolkit.shared.components.ZoomControls
import kotlin.math.roundToInt

@Composable
fun BoardCanvas(
    state: FlowEditorState,
    flow: Flow,
    onPan: (Offset) -> Unit,
    onZoom: (Float, Offset) -> Unit,
    isDrawingConnection: Boolean,
    draggingNodeFromPalette: PaletteNode?,
    getPortBoardPosition: (Long, String) -> Offset?,
    highlightedPortId: String?,
    highlightedNodeId: Long?,
    connectionStartNodeId: Long?,
    connectionStartPortId: String?,
    connectionStartIsOutput: Boolean,
    connectionCurrentPos: Offset,
    onBoardLayoutCoordinatesChanged: (LayoutCoordinates) -> Unit,
    onBoardSizeChanged: (IntSize) -> Unit,
    onDeleteConnection: (org.wip.plugintoolkit.features.flows.model.Connection) -> Unit,
    onDetachConnection: (org.wip.plugintoolkit.features.flows.model.Connection, Boolean, Offset) -> Unit,
    onConnectionDrag: (Offset) -> Unit,
    onConnectionDrop: (Boolean) -> Unit,
    onMoveConnectionFirst: (org.wip.plugintoolkit.features.flows.model.Connection) -> Unit,
    onMoveConnectionLast: (org.wip.plugintoolkit.features.flows.model.Connection) -> Unit,
    selectedNodeIds: Set<Long>,
    onSelectNodes: (Set<Long>) -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelectedNodes: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    nodeSizes: Map<Long, IntSize>,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val gridSize = 50f
    val density = LocalDensity.current
    val focusRequester = remember { FocusRequester() }

    var boardSize by remember { mutableStateOf(IntSize.Zero) }
    var selectedConnection by remember { mutableStateOf<org.wip.plugintoolkit.features.flows.model.Connection?>(null) }
    var hoveredConnection by remember { mutableStateOf<org.wip.plugintoolkit.features.flows.model.Connection?>(null) }
    var hoveredConnectionIsSource by remember { mutableStateOf<Boolean?>(null) }
    var isCtrlModifierPressed by remember { mutableStateOf(false) }

    var selectionStart by remember { mutableStateOf<Offset?>(null) }
    var selectionEnd by remember { mutableStateOf<Offset?>(null) }

    val currentConnections by rememberUpdatedState(flow.connections)
    val currentNodes by rememberUpdatedState(flow.nodes)
    val currentScale by rememberUpdatedState(state.scale)
    val currentOffset by rememberUpdatedState(state.offset)

    LaunchedEffect(flow.connections) {
        if (hoveredConnection != null && !flow.connections.contains(hoveredConnection)) {
            hoveredConnection = null
            hoveredConnectionIsSource = null
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val connectionColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { onBoardLayoutCoordinatesChanged(it) }
            .onSizeChanged {
                boardSize = it
                onBoardSizeChanged(it)
            }
            .background(MaterialTheme.colorScheme.background)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when {
                        keyEvent.key == Key.Delete || keyEvent.key == Key.Backspace -> {
                            onDeleteSelectedNodes()
                            true
                        }

                        keyEvent.isCtrlPressed && keyEvent.key == Key.Z -> {
                            onUndo()
                            true
                        }

                        keyEvent.isCtrlPressed && keyEvent.key == Key.Y -> {
                            onRedo()
                            true
                        }

                        else -> false
                    }
                } else {
                    false
                }
            }
            .pointerInput(isDrawingConnection, draggingNodeFromPalette) {
                detectTransformGestures { _, pan, _, _ ->
                    if (!isDrawingConnection && draggingNodeFromPalette == null) {
                        onPan(pan)
                    }
                }
            }
            .pointerInput(flow.connections, state.scale, state.offset, getPortBoardPosition) {
                detectTapGestures { tapOffset ->
                    focusRequester.requestFocus()
                    var bestConnection: org.wip.plugintoolkit.features.flows.model.Connection? = null
                    var minDistance = 20f * state.scale
                    if (minDistance < 15f) minDistance = 15f

                    flow.connections.forEach { connection ->
                        val sourcePortBoardPos = getPortBoardPosition(connection.sourceNodeId, connection.sourcePortId)
                        val targetPortBoardPos = getPortBoardPosition(connection.targetNodeId, connection.targetPortId)
                        if (sourcePortBoardPos != null && targetPortBoardPos != null) {
                            val startPos = (sourcePortBoardPos * state.scale) + state.offset
                            val endPos = (targetPortBoardPos * state.scale) + state.offset
                            val dist = BoardMathUtils.getDistanceToBezier(tapOffset, startPos, endPos)
                            if (dist < minDistance) {
                                minDistance = dist
                                bestConnection = connection
                            }
                        }
                    }
                    selectedConnection = bestConnection
                    if (bestConnection == null) {
                        onClearSelection()
                    }
                }
            }
            .pointerInput(state.scale, state.offset, flow.nodes, nodeSizes) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                            focusRequester.requestFocus()
                            val startPoint = event.changes.first().position
                            selectionStart = startPoint
                            selectionEnd = startPoint

                            event.changes.forEach { it.consume() }

                            while (true) {
                                val dragEvent = awaitPointerEvent()
                                if (dragEvent.type == PointerEventType.Move) {
                                    selectionEnd = dragEvent.changes.first().position
                                    dragEvent.changes.forEach { it.consume() }

                                    val modelStart = (selectionStart!! - state.offset) / state.scale
                                    val modelEnd = (selectionEnd!! - state.offset) / state.scale
                                    val selectLeft = minOf(modelStart.x, modelEnd.x)
                                    val selectRight = maxOf(modelStart.x, modelEnd.x)
                                    val selectTop = minOf(modelStart.y, modelEnd.y)
                                    val selectBottom = maxOf(modelStart.y, modelEnd.y)

                                    val selectedIds = mutableSetOf<Long>()
                                    flow.nodes.forEach { node ->
                                        val nodeLeft = node.position.x
                                        val nodeTop = node.position.y
                                        val nodeWidth = nodeSizes[node.id]?.width?.toFloat() ?: (300f * density.density)
                                        val nodeHeight =
                                            nodeSizes[node.id]?.height?.toFloat() ?: (180f * density.density)
                                        val nodeRight = nodeLeft + nodeWidth
                                        val nodeBottom = nodeTop + nodeHeight

                                        if (selectLeft < nodeRight && selectRight > nodeLeft &&
                                            selectTop < nodeBottom && selectBottom > nodeTop
                                        ) {
                                            selectedIds.add(node.id)
                                        }
                                    }
                                    onSelectNodes(selectedIds)
                                } else if (dragEvent.type == PointerEventType.Release) {
                                    selectionStart = null
                                    selectionEnd = null
                                    break
                                }
                            }
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val position = event.changes.firstOrNull()?.position ?: Offset.Zero

                        if (event.type == PointerEventType.Scroll) {
                            val scrollDeltaY = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                            if (scrollDeltaY != 0f) {
                                onZoom(scrollDeltaY, position)
                            }
                        } else if (event.type == PointerEventType.Move) {
                            var bestConnection: org.wip.plugintoolkit.features.flows.model.Connection? = null
                            var minDistance = 20f * currentScale
                            if (minDistance < 15f) minDistance = 15f

                            var isHoveringPort = false
                            val portHoverRadius = 20f

                            currentNodes.forEach { node ->
                                val portsToTrack = node.inputs + node.outputs
                                portsToTrack.forEach { port ->
                                    val portBoardPos = getPortBoardPosition(node.id, port.id) ?: return@forEach
                                    val portScreenPos = (portBoardPos * currentScale) + currentOffset
                                    if ((position - portScreenPos).getDistance() < portHoverRadius) {
                                        isHoveringPort = true
                                    }
                                }
                            }

                            if (!isHoveringPort) {
                                currentConnections.forEach { connection ->
                                    val sourcePortBoardPos =
                                        getPortBoardPosition(connection.sourceNodeId, connection.sourcePortId)
                                    val targetPortBoardPos =
                                        getPortBoardPosition(connection.targetNodeId, connection.targetPortId)
                                    if (sourcePortBoardPos != null && targetPortBoardPos != null) {
                                        val startPos = (sourcePortBoardPos * currentScale) + currentOffset
                                        val endPos = (targetPortBoardPos * currentScale) + currentOffset
                                        val dist = BoardMathUtils.getDistanceToBezier(position, startPos, endPos)
                                        if (dist < minDistance) {
                                            minDistance = dist
                                            bestConnection = connection
                                        }
                                    }
                                }
                            }
                            isCtrlModifierPressed = event.keyboardModifiers.isCtrlPressed
                            if (bestConnection != null && isCtrlModifierPressed) {
                                val sourcePortBoardPos =
                                    getPortBoardPosition(bestConnection.sourceNodeId, bestConnection.sourcePortId)
                                val targetPortBoardPos =
                                    getPortBoardPosition(bestConnection.targetNodeId, bestConnection.targetPortId)
                                if (sourcePortBoardPos != null && targetPortBoardPos != null) {
                                    val startPos = (sourcePortBoardPos * currentScale) + currentOffset
                                    val endPos = (targetPortBoardPos * currentScale) + currentOffset
                                    val distToSource = (position - startPos).getDistance()
                                    val distToTarget = (position - endPos).getDistance()
                                    hoveredConnectionIsSource = distToSource < distToTarget
                                } else {
                                    hoveredConnectionIsSource = null
                                }
                            } else {
                                hoveredConnectionIsSource = null
                            }
                            hoveredConnection = bestConnection
                        } else if (event.type == PointerEventType.Exit) {
                            hoveredConnection = null
                            hoveredConnectionIsSource = null
                        } else if (event.type == PointerEventType.Press) {
                            if (event.buttons.isSecondaryPressed || event.keyboardModifiers.isShiftPressed) {
                                hoveredConnection?.let { conn ->
                                    onDeleteConnection(conn)
                                    hoveredConnection = null
                                    hoveredConnectionIsSource = null
                                    if (selectedConnection == conn) {
                                        selectedConnection = null
                                    }
                                }
                            } else if (event.keyboardModifiers.isCtrlPressed) {
                                hoveredConnection?.let { conn ->
                                    val isSrc = hoveredConnectionIsSource ?: false
                                    onDetachConnection(conn, isSrc, position)
                                    
                                    // Consume to prevent panning
                                    event.changes.forEach { it.consume() }
                                    
                                    while (true) {
                                        val dragEvent = awaitPointerEvent()
                                        if (dragEvent.type == PointerEventType.Move) {
                                            val screenPos = dragEvent.changes.firstOrNull()?.position ?: Offset.Zero
                                            val boardPos = (screenPos - currentOffset) / currentScale
                                            onConnectionDrag(boardPos)
                                            dragEvent.changes.forEach { it.consume() }
                                        } else if (dragEvent.type == PointerEventType.Release) {
                                            onConnectionDrop(dragEvent.keyboardModifiers.isShiftPressed)
                                            break
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
    ) {
        // 1.1 Grid and Connections Canvas
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
                        color = gridColor,
                        radius = 1.5f * state.scale,
                        center = Offset(x, y)
                    )
                }
            }

            // Draw connections
            flow.connections.forEach { connection ->
                val sourcePortBoardPos = getPortBoardPosition(connection.sourceNodeId, connection.sourcePortId)
                val targetPortBoardPos = getPortBoardPosition(connection.targetNodeId, connection.targetPortId)

                if (sourcePortBoardPos != null && targetPortBoardPos != null) {
                    val startPos = (sourcePortBoardPos * state.scale) + state.offset
                    val endPos = (targetPortBoardPos * state.scale) + state.offset
                    val isInvalid = state.validationErrors.any {
                        it.sourceNodeId == connection.sourceNodeId &&
                                it.sourcePortId == connection.sourcePortId &&
                                it.targetNodeId == connection.targetNodeId &&
                                it.targetPortId == connection.targetPortId
                    }
                    val isSelected = selectedConnection == connection
                    val isHovered = hoveredConnection == connection
                    val color = if (isSelected) {
                        Color(0xFFFF9800)
                    } else if (isHovered && hoveredConnectionIsSource == null) {
                        Color(0xFFFF2D55)
                    } else if (isInvalid) {
                        Color.Red
                    } else {
                        connectionColor
                    }

                    if (isHovered && hoveredConnectionIsSource == null) {
                        drawBezierCurve(startPos, endPos, color.copy(alpha = 0.25f), strokeWidth = 9.dp.toPx())
                    }

                    if (isHovered && hoveredConnectionIsSource != null) {
                        // Custom drawing for split bezier
                        val (sourceHalf, targetHalf) = BoardMathUtils.splitCubicBezierInHalf(startPos, endPos)

                        val pathSource = Path().apply {
                            moveTo(sourceHalf.p0.x, sourceHalf.p0.y)
                            cubicTo(sourceHalf.p1.x, sourceHalf.p1.y, sourceHalf.p2.x, sourceHalf.p2.y, sourceHalf.p3.x, sourceHalf.p3.y)
                        }
                        val pathTarget = Path().apply {
                            moveTo(targetHalf.p0.x, targetHalf.p0.y)
                            cubicTo(targetHalf.p1.x, targetHalf.p1.y, targetHalf.p2.x, targetHalf.p2.y, targetHalf.p3.x, targetHalf.p3.y)
                        }

                        val highlightColor = Color(0xFFFF2D55)
                        val sourceColor =
                            if (hoveredConnectionIsSource == true) highlightColor else connectionColor.copy(alpha = 0.4f)
                        val sourceStroke = if (hoveredConnectionIsSource == true) 5.dp.toPx() else 3.dp.toPx()

                        val targetColor =
                            if (hoveredConnectionIsSource == false) highlightColor else connectionColor.copy(alpha = 0.4f)
                        val targetStroke = if (hoveredConnectionIsSource == false) 5.dp.toPx() else 3.dp.toPx()

                        if (hoveredConnectionIsSource == true) {
                            drawPath(
                                pathSource,
                                sourceColor.copy(alpha = 0.25f),
                                style = Stroke(
                                    width = 9.dp.toPx(),
                                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                                )
                            )
                        } else {
                            drawPath(
                                pathTarget,
                                targetColor.copy(alpha = 0.25f),
                                style = Stroke(
                                    width = 9.dp.toPx(),
                                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                                )
                            )
                        }

                        drawPath(
                            pathSource,
                            sourceColor,
                            style = Stroke(
                                width = sourceStroke,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                        )
                        drawPath(
                            pathTarget,
                            targetColor,
                            style = Stroke(
                                width = targetStroke,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                        )
                    } else {
                        val strokeWidth = if (isSelected || isHovered) 5.dp.toPx() else 3.dp.toPx()
                        drawBezierCurve(startPos, endPos, color, strokeWidth)
                    }
                }
            }

            // Draw temporary connection
            if (isDrawingConnection && connectionStartNodeId != null && connectionStartPortId != null) {
                val startBoardPos = getPortBoardPosition(connectionStartNodeId, connectionStartPortId)
                if (startBoardPos != null) {
                    val currentPos = if (highlightedPortId != null && highlightedNodeId != null) {
                        getPortBoardPosition(highlightedNodeId, highlightedPortId)!!
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
                        connectionColor.copy(alpha = 0.6f)
                    )
                }
            }
        }

        // Render main children (nodes, preview)
        content()

        // Selection Box overlay drawing
        if (selectionStart != null && selectionEnd != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val rectLeft = minOf(selectionStart!!.x, selectionEnd!!.x)
                val rectRight = maxOf(selectionStart!!.x, selectionEnd!!.x)
                val rectTop = minOf(selectionStart!!.y, selectionEnd!!.y)
                val rectBottom = maxOf(selectionStart!!.y, selectionEnd!!.y)

                drawRect(
                    color = connectionColor.copy(alpha = 0.15f),
                    topLeft = Offset(rectLeft, rectTop),
                    size = androidx.compose.ui.geometry.Size(rectRight - rectLeft, rectBottom - rectTop)
                )
                drawRect(
                    color = connectionColor,
                    topLeft = Offset(rectLeft, rectTop),
                    size = androidx.compose.ui.geometry.Size(rectRight - rectLeft, rectBottom - rectTop),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }

        // Order Badges
        flow.connections.forEach { conn ->
            if (conn.orderIndex != null) {
                androidx.compose.runtime.key(
                    conn.sourceNodeId,
                    conn.sourcePortId,
                    conn.targetNodeId,
                    conn.targetPortId
                ) {
                    val sourcePortBoardPos = getPortBoardPosition(conn.sourceNodeId, conn.sourcePortId)
                    val targetPortBoardPos = getPortBoardPosition(conn.targetNodeId, conn.targetPortId)
                    if (sourcePortBoardPos != null && targetPortBoardPos != null) {
                        val startPos = (sourcePortBoardPos * state.scale) + state.offset
                        val endPos = (targetPortBoardPos * state.scale) + state.offset
                        val midPoint = BoardMathUtils.getBezierMidpoint(startPos, endPos)

                        var showMenu by remember { mutableStateOf(false) }

                        Box(
                            modifier = Modifier
                                .offset {
                                    IntOffset(
                                        (midPoint.x - 12.dp.toPx()).roundToInt(),
                                        (midPoint.y - 12.dp.toPx()).roundToInt()
                                    )
                                }
                                .size(24.dp)
                                .background(MaterialTheme.colorScheme.background, CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                .clickable { showMenu = true },
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.material3.Text(
                                text = conn.orderIndex.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Bold
                            )

                            androidx.compose.material3.DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { androidx.compose.material3.Text("Move First") },
                                    onClick = {
                                        onMoveConnectionFirst(conn)
                                        showMenu = false
                                    }
                                )
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { androidx.compose.material3.Text("Move Last") },
                                    onClick = {
                                        onMoveConnectionLast(conn)
                                        showMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Selected Connection Delete Button Bubble
        selectedConnection?.let { conn ->
            val sourcePortBoardPos = getPortBoardPosition(conn.sourceNodeId, conn.sourcePortId)
            val targetPortBoardPos = getPortBoardPosition(conn.targetNodeId, conn.targetPortId)
            if (sourcePortBoardPos != null && targetPortBoardPos != null) {
                val startPos = (sourcePortBoardPos * state.scale) + state.offset
                val endPos = (targetPortBoardPos * state.scale) + state.offset
                val midPoint = BoardMathUtils.getBezierMidpoint(startPos, endPos)

                Surface(
                    onClick = {
                        onDeleteConnection(conn)
                        selectedConnection = null
                    },
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (midPoint.x - 20.dp.toPx()).roundToInt(),
                                (midPoint.y - 20.dp.toPx()).roundToInt()
                            )
                        }
                        .size(40.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.error,
                    tonalElevation = 6.dp,
                    shadowElevation = 6.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Wire",
                            tint = MaterialTheme.colorScheme.onError,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // 1.3 Zoom Controls UI - Bottom Right
        val centerPosition = Offset(boardSize.width / 2f, boardSize.height / 2f)
        ZoomControls(
            scale = state.scale,
            onZoomIn = { onZoom(-1f, centerPosition) },
            onZoomOut = { onZoom(1f, centerPosition) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(ToolkitTheme.spacing.medium)
        )
    }
}

private fun DrawScope.drawBezierCurve(start: Offset, end: Offset, color: Color, strokeWidth: Float = 3.dp.toPx()) {
    val controlPointOffset = kotlin.math.abs(end.x - start.x) / 2f
    val path = Path().apply {
        moveTo(start.x, start.y)
        cubicTo(
            start.x + controlPointOffset, start.y,
            end.x - controlPointOffset, end.y,
            end.x, end.y
        )
    }
    drawPath(path = path, color = color, style = Stroke(width = strokeWidth))
}
