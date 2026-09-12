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
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.ui.toComposeOffset
import org.wip.plugintoolkit.features.flows.utils.BoardMathUtils
import org.wip.plugintoolkit.features.flows.utils.SplineMathUtils
import org.wip.plugintoolkit.features.flows.viewmodel.FlowEditorState
import org.wip.plugintoolkit.features.settings.model.ConnectionCurveStyle
import org.wip.plugintoolkit.shared.components.plugin.inputs.parseColorString

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
    curveStyle: ConnectionCurveStyle = ConnectionCurveStyle.CardinalSpline,
    roundness: Float = 0.5f,
    modifier: Modifier = Modifier
) {
    val gridSize = 50f
    val dimensions = ToolkitTheme.dimensions
    val opacity = ToolkitTheme.opacity
    val customColors = ToolkitTheme.colors

    val connectionColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val surfaceColor = MaterialTheme.colorScheme.surface
    val junctionMap = flow.junctions.associate { it.id to it.position.toComposeOffset() }

    val connectionAlphas = flow.connections.associateWith { connection ->
        val isDimmedByConnectionHover = interactionState.hoveredConnection != null && interactionState.hoveredConnection != connection
        val isDimmedByNodeHover = interactionState.hoveredNodeId != null &&
                connection.sourceNodeId != interactionState.hoveredNodeId &&
                connection.targetNodeId != interactionState.hoveredNodeId
        val isDimmed = isDimmedByConnectionHover || isDimmedByNodeHover
        if (isDimmed) opacity.disabled else 1f
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

        // Draw junctions (Unified connection points)
        flow.junctions.forEach { junction ->
            val center = (junction.position.toComposeOffset() * state.scale) + state.offset
            val isHovered = interactionState.hoveredJunctionId == junction.id
            val juncColor = junction.color
            val jColor = if (!juncColor.isNullOrBlank()) {
                parseColorString(juncColor)
            } else {
                connectionColor
            }
            val isSelected = interactionState.selectedJunctionId == junction.id
            val radius = (if (isHovered || isSelected) 7.5f else 5.5f) * state.scale

            drawCircle(
                color = jColor,
                radius = radius,
                center = center
            )
            drawCircle(
                color = surfaceColor,
                radius = radius,
                center = center,
                style = Stroke(width = 1.5f * state.scale)
            )
        }

        // Draw connections, waypoints, and midpoints
        flow.connections.forEach { connection ->
            val screenPoints = ConnectionHitTester.getConnectionScreenPoints(
                connection = connection,
                getPortBoardPosition = getPortBoardPosition,
                junctionMap = junctionMap,
                scale = state.scale,
                offset = state.offset,
                groups = flow.groups,
                density = this.density
            )

            if (screenPoints != null && screenPoints.size >= 2) {
                val isInvalid = state.validationErrors.any {
                    it.sourceNodeId == connection.sourceNodeId &&
                            it.sourcePortId == connection.sourcePortId &&
                            it.targetNodeId == connection.targetNodeId &&
                            it.targetPortId == connection.targetPortId
                }
                val hasProblem = problematicConnections.contains(connection)
                val isSelected = interactionState.selectedConnection == connection
                val isHovered = interactionState.hoveredConnection == connection
                val isGroupHighlighted = flow.groups.any { group ->
                    state.selectedGroupIds.contains(group.id) &&
                            (connection.sourceNodeId in group.nodeIds || connection.targetNodeId in group.nodeIds)
                }
                val connColor = connection.color
                val baseColor = when {
                    isInvalid -> customColors.red
                    hasProblem -> customColors.warning
                    !connColor.isNullOrBlank() -> parseColorString(connColor)
                    else -> connectionColor
                }
                val color = when {
                    isSelected -> { Color(0xFFFF9800) }
                    isHovered && interactionState.hoveredConnectionIsSource == null -> { Color(0xFFFF2D55) }
                    isGroupHighlighted -> { baseColor }
                    else -> { baseColor }
                }.copy(alpha = connectionAlphas[connection] ?: 1f)

                val strokeWidth = if (isSelected || isHovered || isGroupHighlighted) {
                    dimensions.strokeWidthMedium.toPx()
                } else {
                    dimensions.strokeWidthThin.toPx()
                }
                val effectiveStyle = if (connection.isStructured) ConnectionCurveStyle.Orthogonal else curveStyle
                val path = SplineMathUtils.buildConnectionPath(screenPoints, effectiveStyle, roundness)
                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // When holding Ctrl to reattach, indicate which end will detach with a prominent ring cap
                if (isHovered && interactionState.hoveredConnectionIsSource != null) {
                    val detachEndPos = if (interactionState.hoveredConnectionIsSource == true) screenPoints.first() else screenPoints.last()
                    drawCircle(
                        color = Color(0xFFFF2D55),
                        radius = 9f * state.scale,
                        center = detachEndPos
                    )
                    drawCircle(
                        color = surfaceColor,
                        radius = 5.5f * state.scale,
                        center = detachEndPos
                    )
                }

                // If floating connection, draw an open ring cap at the floating target end
                if (connection.isFloating) {
                    val endPos = screenPoints.last()
                    drawCircle(
                        color = surfaceColor,
                        radius = 6f * state.scale,
                        center = endPos
                    )
                    drawCircle(
                        color = color,
                        radius = 6f * state.scale,
                        center = endPos,
                        style = Stroke(width = 2.5f * state.scale)
                    )
                }

                // Draw Waypoints (Unified connection points)
                connection.waypoints.forEachIndexed { index, wp ->
                    val center = (wp.toComposeOffset() * state.scale) + state.offset
                    val isWpHovered = interactionState.hoveredWaypoint?.first == connection &&
                            interactionState.hoveredWaypoint?.second == index
                    val isWpDragging = interactionState.draggingWaypoint?.first == connection &&
                            interactionState.draggingWaypoint?.second == index
                    val wpRadius = (if (isWpHovered || isWpDragging) 7.5f else 5.5f) * state.scale
                    val wpColor = if (!connColor.isNullOrBlank()) parseColorString(connColor) else connectionColor
                    drawCircle(
                        color = wpColor,
                        radius = wpRadius,
                        center = center
                    )
                    drawCircle(
                        color = surfaceColor,
                        radius = wpRadius,
                        center = center,
                        style = Stroke(width = 1.5f * state.scale)
                    )
                }

                // Draw Midpoints (Interactive splitting handles - 3x bigger)
                val isConnHovered = interactionState.hoveredConnection == connection
                val midpoints = SplineMathUtils.computeSegmentMidpoints(screenPoints, effectiveStyle, roundness)
                midpoints.forEachIndexed { segIndex, midPt ->
                    val isMidpointHovered = interactionState.hoveredMidpoint?.first == connection &&
                            interactionState.hoveredMidpoint?.second == segIndex
                    if (isConnHovered || isMidpointHovered) {
                        val midRadius = (if (isMidpointHovered) 18f else 12f) * state.scale
                        val midColor = (if (!connColor.isNullOrBlank()) parseColorString(connColor) else connectionColor)
                            .copy(alpha = if (isMidpointHovered) 1f else 0.85f)
                        drawCircle(
                            color = midColor,
                            radius = midRadius,
                            center = midPt
                        )
                        drawCircle(
                            color = surfaceColor,
                            radius = midRadius,
                            center = midPt,
                            style = Stroke(width = 2.5f * state.scale)
                        )
                    }
                }
            }
        }

        // Draw temporary drag connection line
        if (isDrawingConnection && connectionStartNodeId != null && connectionStartPortId != null) {
            val startBoardPos = getPortBoardPosition(connectionStartNodeId, connectionStartPortId, connectionStartIsOutput)
            if (startBoardPos != null) {
                var currentPos = if (highlightedPortId != null && highlightedNodeId != null) {
                    getPortBoardPosition(highlightedNodeId, highlightedPortId, !connectionStartIsOutput) ?: ((interactionState.lastPointerPosition - state.offset) / state.scale)
                } else {
                    (interactionState.lastPointerPosition - state.offset) / state.scale
                }

                if (interactionState.isShiftModifierPressed) {
                    currentPos = SplineMathUtils.snapToStraightAngle(startBoardPos, currentPos)
                }

                val (startPos, endPos) = if (connectionStartIsOutput) {
                    startBoardPos to currentPos
                } else {
                    currentPos to startBoardPos
                }

                val pts = listOf((startPos * state.scale) + state.offset, (endPos * state.scale) + state.offset)
                val path = SplineMathUtils.buildConnectionPath(pts, curveStyle, roundness)
                drawPath(
                    path = path,
                    color = connectionColor.copy(alpha = opacity.disabled),
                    style = Stroke(width = dimensions.strokeWidthThick.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        // Draw structured connection drawing preview (point-by-point creation)
        if (interactionState.isDrawingStructuredConnection) {
            val startBoardPos = when {
                interactionState.structuredConnectionSourceJunctionId != null ->
                    junctionMap[interactionState.structuredConnectionSourceJunctionId]
                interactionState.structuredConnectionStartNodeId != null && interactionState.structuredConnectionStartPortId != null ->
                    getPortBoardPosition(
                        interactionState.structuredConnectionStartNodeId!!,
                        interactionState.structuredConnectionStartPortId!!,
                        interactionState.structuredConnectionStartIsOutput
                    )
                else -> null
            }
            if (startBoardPos != null) {
                val allBoardPts = mutableListOf<Offset>()
                allBoardPts.add(startBoardPos)
                allBoardPts.addAll(interactionState.structuredConnectionPoints)

                var liveBoardPos = if (highlightedPortId != null && highlightedNodeId != null) {
                    getPortBoardPosition(highlightedNodeId, highlightedPortId, !interactionState.structuredConnectionStartIsOutput)
                        ?: interactionState.structuredConnectionLivePos
                } else {
                    interactionState.structuredConnectionLivePos
                }

                val lastCommitted = allBoardPts.last()
                if (interactionState.isShiftModifierPressed) {
                    liveBoardPos = SplineMathUtils.snapToOrthogonal(lastCommitted, liveBoardPos)
                } else if (interactionState.isCtrlModifierPressed) {
                    liveBoardPos = SplineMathUtils.snapToStraightAngle(lastCommitted, liveBoardPos)
                }
                allBoardPts.add(liveBoardPos)

                val previewScreenPts = allBoardPts.map { (it * state.scale) + state.offset }
                val previewPath = SplineMathUtils.buildRoundedPolylinePath(previewScreenPts, cornerRadius = 8f * state.scale)
                drawPath(
                    path = previewPath,
                    color = connectionColor.copy(alpha = 0.9f),
                    style = Stroke(width = dimensions.strokeWidthThick.toPx(), cap = StrokeCap.Round)
                )

                // Draw dots at committed intermediate points
                interactionState.structuredConnectionPoints.forEach { pt ->
                    val scrPt = (pt * state.scale) + state.offset
                    drawCircle(
                        color = connectionColor,
                        radius = 5.5f * state.scale,
                        center = scrPt
                    )
                    drawCircle(
                        color = surfaceColor,
                        radius = 5.5f * state.scale,
                        center = scrPt,
                        style = Stroke(width = 1.5f * state.scale)
                    )
                }
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
