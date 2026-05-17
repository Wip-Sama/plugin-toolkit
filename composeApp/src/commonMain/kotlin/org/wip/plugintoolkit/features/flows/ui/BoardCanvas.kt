package org.wip.plugintoolkit.features.flows.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.viewmodel.FlowState
import org.wip.plugintoolkit.shared.components.ZoomControls

@Composable
fun BoardCanvas(
    state: FlowState,
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
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val gridSize = 50f
    var boardSize by remember { mutableStateOf(IntSize.Zero) }

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
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, _, _ ->
                    if (!isDrawingConnection && draggingNodeFromPalette == null) {
                        onPan(pan)
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
                    drawBezierCurve(startPos, endPos, connectionColor)
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
