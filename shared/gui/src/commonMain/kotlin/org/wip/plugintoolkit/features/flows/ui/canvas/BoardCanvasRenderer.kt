package org.wip.plugintoolkit.features.flows.ui.canvas

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.utils.BoardMathUtils
import org.wip.plugintoolkit.features.flows.viewmodel.FlowEditorState

private val sharedBezierPath = Path()

fun DrawScope.drawBezierCurve(start: Offset, end: Offset, color: Color, strokeWidth: Float) {
    val controlPointOffset = kotlin.math.abs(end.x - start.x) / 2f
    sharedBezierPath.reset()
    sharedBezierPath.moveTo(start.x, start.y)
    sharedBezierPath.cubicTo(
        start.x + controlPointOffset, start.y,
        end.x - controlPointOffset, end.y,
        end.x, end.y
    )
    drawPath(
        path = sharedBezierPath,
        color = color,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )
}

fun DrawScope.drawBezierCurveSegment(
    curve: BoardMathUtils.CubicBezierCurve,
    color: Color,
    strokeWidth: Float
) {
    sharedBezierPath.reset()
    sharedBezierPath.moveTo(curve.p0.x, curve.p0.y)
    sharedBezierPath.cubicTo(
        curve.p1.x, curve.p1.y,
        curve.p2.x, curve.p2.y,
        curve.p3.x, curve.p3.y
    )
    drawPath(
        path = sharedBezierPath,
        color = color,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )
}

@Composable
fun BoardGridAndConnectionsCanvas(
    state: FlowEditorState,
    flow: Flow,
    interactionState: BoardInteractionState,
    isDrawingConnection: Boolean,
    connectionStartNodeId: Long?,
    connectionStartPortId: String?,
    connectionStartIsOutput: Boolean,
    highlightedPortId: String?,
    highlightedNodeId: Long?,
    getPortBoardPosition: (Long, String, Boolean) -> Offset?,
    problematicConnections: Set<Connection> = emptySet(),
    portLayoutVersion: Int = 0,
    modifier: Modifier = Modifier
) {
    val gridSize = 50f
    val dimensions = ToolkitTheme.dimensions
    val opacity = ToolkitTheme.opacity
    val customColors = ToolkitTheme.colors

    val connectionColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    val connectionAlphas = flow.connections.associateWith { connection ->
        val isDimmedByConnectionHover = interactionState.hoveredConnection != null && interactionState.hoveredConnection != connection
        val isDimmedByNodeHover = interactionState.hoveredNodeId != null &&
                connection.sourceNodeId != interactionState.hoveredNodeId &&
                connection.targetNodeId != interactionState.hoveredNodeId
        val isDimmed = isDimmedByConnectionHover || isDimmedByNodeHover
        val targetAlpha = if (isDimmed) 0.6f else 1f
        animateFloatAsState(
            targetValue = targetAlpha,
            animationSpec = tween(
                durationMillis = 200,
                delayMillis = if (isDimmed) 500 else 0
            ),
            label = "ConnectionAlpha"
        ).value
    }

    Canvas(modifier = modifier.fillMaxSize().testTag("board_grid")) {
        // Read portLayoutVersion to ensure canvas redraws when node expansion or port layout completes
        if (portLayoutVersion < 0) return@Canvas
        val scaledGridSize = gridSize * state.scale
        val startX = (state.offset.x % scaledGridSize) - scaledGridSize
        val startY = (state.offset.y % scaledGridSize) - scaledGridSize

        val cols = (size.width / scaledGridSize).toInt() + 2
        val rows = (size.height / scaledGridSize).toInt() + 2

        if (state.scale >= 0.4f) {
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
        }

        // Draw connections
        flow.connections.forEach { connection ->
            val sourcePortBoardPos = getPortBoardPosition(connection.sourceNodeId, connection.sourcePortId, true)
            val targetPortBoardPos = getPortBoardPosition(connection.targetNodeId, connection.targetPortId, false)

            if (sourcePortBoardPos != null && targetPortBoardPos != null) {
                val startPos = (sourcePortBoardPos * state.scale) + state.offset
                val endPos = (targetPortBoardPos * state.scale) + state.offset
                val isInvalid = state.validationErrors.any {
                    it.sourceNodeId == connection.sourceNodeId &&
                            it.sourcePortId == connection.sourcePortId &&
                            it.targetNodeId == connection.targetNodeId &&
                            it.targetPortId == connection.targetPortId
                }
                val hasProblem = problematicConnections.contains(connection)
                val isSelected = interactionState.selectedConnection == connection
                val isHovered = interactionState.hoveredConnection == connection
                val baseColor = when {
                    isInvalid -> customColors.red
                    hasProblem -> customColors.warning
                    else -> connectionColor
                }
                val color = when {
                    isSelected -> { Color(0xFFFF9800) }
                    isHovered && interactionState.hoveredConnectionIsSource == null -> { Color(0xFFFF2D55) }
                    else -> { baseColor }
                }.copy(alpha = connectionAlphas[connection] ?: 1f)

                if (isHovered && interactionState.hoveredConnectionIsSource == null) {
                    drawBezierCurve(startPos, endPos, color.copy(alpha = opacity.settingsItemDefault), strokeWidth = dimensions.strokeWidthMedium.toPx())
                }

                if (isHovered && interactionState.hoveredConnectionIsSource != null) {
                    // Custom drawing for split bezier
                    val (sourceHalf, targetHalf) = BoardMathUtils.splitCubicBezierInHalf(startPos, endPos)

                    val highlightColor = Color(0xFFFF2D55)
                    val sourceColor =
                        if (interactionState.hoveredConnectionIsSource == true) highlightColor else baseColor.copy(alpha = opacity.sidebarBackground)
                    val sourceStroke = if (interactionState.hoveredConnectionIsSource == true) dimensions.strokeWidthMedium.toPx() else dimensions.borderSelected.toPx()

                    val targetColor =
                        if (interactionState.hoveredConnectionIsSource == false) highlightColor else baseColor.copy(alpha = opacity.sidebarBackground)
                    val targetStroke = if (interactionState.hoveredConnectionIsSource == false) dimensions.strokeWidthMediumSmall.toPx() else dimensions.borderSelected.toPx()

                    if (interactionState.hoveredConnectionIsSource == true) {
                        drawBezierCurveSegment(
                            sourceHalf,
                            sourceColor.copy(alpha = opacity.settingsItemDefault),
                            strokeWidth = dimensions.strokeWidthMedium.toPx()
                        )
                    } else {
                        drawBezierCurveSegment(
                            targetHalf,
                            targetColor.copy(alpha = opacity.settingsItemDefault),
                            strokeWidth = dimensions.strokeWidthThick.toPx()
                        )
                    }

                    drawBezierCurveSegment(sourceHalf, sourceColor, strokeWidth = sourceStroke)
                    drawBezierCurveSegment(targetHalf, targetColor, strokeWidth = targetStroke)
                } else {
                    val strokeWidth = if (isSelected || isHovered) dimensions.strokeWidthMedium.toPx() else dimensions.strokeWidthThin.toPx()
                    drawBezierCurve(startPos, endPos, color, strokeWidth)
                }
            }
        }

        // Draw temporary connection line
        if (isDrawingConnection && connectionStartNodeId != null && connectionStartPortId != null) {
            val startBoardPos = getPortBoardPosition(connectionStartNodeId, connectionStartPortId, connectionStartIsOutput)
            if (startBoardPos != null) {
                val currentPos = if (highlightedPortId != null && highlightedNodeId != null) {
                    getPortBoardPosition(highlightedNodeId, highlightedPortId, !connectionStartIsOutput)!!
                } else {
                    (interactionState.lastPointerPosition - state.offset) / state.scale
                }

                val (startPos, endPos) = if (connectionStartIsOutput) {
                    startBoardPos to currentPos
                } else {
                    currentPos to startBoardPos
                }

                drawBezierCurve(
                    (startPos * state.scale) + state.offset,
                    (endPos * state.scale) + state.offset,
                    connectionColor.copy(alpha = opacity.disabled),
                    dimensions.strokeWidthThick.toPx()
                )
            }
        }
    }
}

@Composable
fun SelectionBoxCanvas(
    interactionState: BoardInteractionState,
    modifier: Modifier = Modifier
) {
    val start = interactionState.selectionStart
    val end = interactionState.selectionEnd
    if (start != null && end != null) {
        val dimensions = ToolkitTheme.dimensions
        val opacity = ToolkitTheme.opacity
        val connectionColor = MaterialTheme.colorScheme.primary

        Canvas(modifier = modifier.fillMaxSize().testTag("selection_box")) {
            val rectLeft = minOf(start.x, end.x)
            val rectRight = maxOf(start.x, end.x)
            val rectTop = minOf(start.y, end.y)
            val rectBottom = maxOf(start.y, end.y)

            drawRect(
                color = connectionColor.copy(alpha = opacity.buttonBackground),
                topLeft = Offset(rectLeft, rectTop),
                size = Size(rectRight - rectLeft, rectBottom - rectTop)
            )
            drawRect(
                color = connectionColor,
                topLeft = Offset(rectLeft, rectTop),
                size = Size(rectRight - rectLeft, rectBottom - rectTop),
                style = Stroke(width = dimensions.progressIndicatorStroke.toPx())
            )
        }
    }
}
