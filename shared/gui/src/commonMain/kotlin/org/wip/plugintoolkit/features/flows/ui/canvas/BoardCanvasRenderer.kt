package org.wip.plugintoolkit.features.flows.ui.canvas

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.ui.toComposeOffset
import org.wip.plugintoolkit.features.flows.ui.snapToGrid
import org.wip.plugintoolkit.features.flows.utils.BoardMathUtils
import org.wip.plugintoolkit.features.flows.utils.SplineMathUtils
import org.wip.plugintoolkit.features.flows.viewmodel.FlowEditorState
import org.wip.plugintoolkit.features.settings.model.ConnectionCurveStyle
import org.wip.plugintoolkit.features.settings.model.OrthogonalStepMode
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
    connectionCurrentPos: Offset = Offset.Zero,
    problematicConnections: Set<Connection> = emptySet(),
    portLayoutVersion: Int = 0,
    curveStyle: ConnectionCurveStyle = ConnectionCurveStyle.CardinalSpline,
    roundness: Float = 0.5f,
    stepMode: OrthogonalStepMode = state.orthogonalStepMode ?: flow.orthogonalStepMode ?: OrthogonalStepMode.Auto,
    orthogonalPortLead: Boolean = state.orthogonalPortLead ?: flow.orthogonalPortLead ?: false,
    modifier: Modifier = Modifier
) {
    val dimensions = ToolkitTheme.dimensions
    val density = LocalDensity.current
    val gridSize = with(density) { dimensions.gridStep.toPx() }
    val opacity = ToolkitTheme.opacity
    val customColors = ToolkitTheme.colors

    val connectionColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val surfaceColor = MaterialTheme.colorScheme.surface
    val junctionMap = flow.junctions.associate { it.id to it.position.toComposeOffset() }

    val hoveredWireTree = remember(interactionState.hoveredConnection, flow.connections) {
        interactionState.hoveredConnection?.let { flow.findConnectedWireTree(it) }
    }
    val nodeHoveredWireTree = remember(interactionState.hoveredNodeId, flow.connections) {
        interactionState.hoveredNodeId?.let { flow.findAllConnectionsForNode(it) }
    }

    val connectionAlphas = flow.connections.associateWith { connection ->
        val isDimmedByConnectionHover = hoveredWireTree != null && connection !in hoveredWireTree
        val isDimmedByNodeHover = nodeHoveredWireTree != null && connection !in nodeHoveredWireTree
        val isDimmed = isDimmedByConnectionHover || isDimmedByNodeHover
        if (isDimmed) opacity.disabled else 1f
    }

    Canvas(modifier = modifier.fillMaxSize().testTag("board_grid")) {
        // Read portLayoutVersion and currentDragOffset to ensure canvas redraws reactively during node dragging
        if (portLayoutVersion < 0) return@Canvas
        val _activeDragOffset = state.currentDragOffset
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
            val isJuncDimmedByConn = hoveredWireTree != null && hoveredWireTree.none { it.sourceJunctionId == junction.id || it.targetJunctionId == junction.id }
            val isJuncDimmedByNode = nodeHoveredWireTree != null && nodeHoveredWireTree.none { it.sourceJunctionId == junction.id || it.targetJunctionId == junction.id }
            val juncAlpha = if (isJuncDimmedByConn || isJuncDimmedByNode) opacity.disabled else 1f

            val center = (junction.position.toComposeOffset() * state.scale) + state.offset
            val isHovered = interactionState.hoveredJunctionId == junction.id
            val juncColor = junction.color
            val baseColor = if (!juncColor.isNullOrBlank()) {
                parseColorString(juncColor)
            } else {
                connectionColor
            }
            val color = if (isHovered) Color(0xFFFF2D55) else baseColor
            val isSelected = interactionState.selectedJunctionId == junction.id || junction.id in state.selectedPointIds

            val isJunctionVisible = !state.hideConnectionPointsUnlessHovered ||
                    isHovered || isSelected ||
                    isDrawingConnection || interactionState.isDrawingStructuredConnection ||
                    (hoveredWireTree != null && !isJuncDimmedByConn) ||
                    (nodeHoveredWireTree != null && !isJuncDimmedByNode)

            if (isJunctionVisible) {
                drawCircle(
                    color = color.copy(alpha = juncAlpha),
                    radius = 5.5f * state.scale,
                    center = center
                )
                drawCircle(
                    color = surfaceColor.copy(alpha = juncAlpha),
                    radius = 5.5f * state.scale,
                    center = center,
                    style = Stroke(width = 1.5f * state.scale)
                )

                // Prominent selection highlight ring
                if (isSelected) {
                    drawCircle(
                        color = Color(0xFFFF9800),
                        radius = 9f * state.scale,
                        center = center,
                        style = Stroke(width = 2f * state.scale)
                    )
                }
            }
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
                val (startIsHorizontal, endIsHorizontal) = ConnectionHitTester.getConnectionOrientations(
                    connection = connection,
                    connections = flow.connections,
                    junctionMap = junctionMap,
                    getPortBoardPosition = getPortBoardPosition,
                    groups = flow.groups,
                    density = this.density,
                    stepMode = stepMode,
                    orthogonalPortLead = orthogonalPortLead,
                    nodes = flow.nodes
                )

                val filletParams = ConnectionHitTester.getJunctionFilletParams(
                    connection = connection,
                    connections = flow.connections,
                    junctionMap = junctionMap,
                    getPortBoardPosition = getPortBoardPosition,
                    groups = flow.groups,
                    density = this.density,
                    scale = state.scale,
                    offset = state.offset,
                    tension = roundness,
                    stepMode = stepMode,
                    orthogonalPortLead = orthogonalPortLead,
                    nodes = flow.nodes
                )
                val effectiveStyle = curveStyle
                val startBorderX = ConnectionHitTester.getSourceNodeBorderX(connection, flow.nodes)
                val endBorderX = ConnectionHitTester.getTargetNodeBorderX(connection, flow.nodes)
                val path = SplineMathUtils.buildConnectionPath(
                    points = screenPoints,
                    style = effectiveStyle,
                    tension = roundness,
                    startHorizontal = startIsHorizontal,
                    endHorizontal = endIsHorizontal,
                    scale = state.scale,
                    canvasOffset = state.offset,
                    stepMode = stepMode,
                    startFilletLeadIn = filletParams.startFilletLeadIn,
                    endTrimDistance = filletParams.endTrimDistance,
                    useMiddleRouteForDirectConnection =
                        ConnectionHitTester.usesMiddleRouteForDirectConnection(connection, orthogonalPortLead),
                    startPortLead = ConnectionHitTester.hasStartPortLead(connection, orthogonalPortLead),
                    endPortLead = ConnectionHitTester.hasEndPortLead(connection, orthogonalPortLead),
                    startBorderX = startBorderX,
                    endBorderX = endBorderX
                )
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
                            
                    val isWpVisible = !state.hideConnectionPointsUnlessHovered ||
                            isWpHovered || isWpDragging ||
                            isDrawingConnection || interactionState.isDrawingStructuredConnection ||
                            isHovered || isSelected || isGroupHighlighted ||
                            (hoveredWireTree != null && connection in hoveredWireTree) ||
                            (nodeHoveredWireTree != null && connection in nodeHoveredWireTree)

                    if (isWpVisible) {
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
                }
            }
        }

        // Draw snapping indicator on wire
        interactionState.snappedWirePoint?.let { snapPt ->
            val center = (snapPt * state.scale) + state.offset
            drawCircle(
                color = Color(0xFF00E676).copy(alpha = 0.35f),
                radius = 13f * state.scale,
                center = center
            )
            drawCircle(
                color = Color(0xFF00E676),
                radius = 8.5f * state.scale,
                center = center,
                style = Stroke(width = 2.5f * state.scale)
            )
            drawCircle(
                color = surfaceColor,
                radius = 3.5f * state.scale,
                center = center
            )
        }

        // Draw temporary drag connection line
        if (isDrawingConnection && connectionStartNodeId != null && connectionStartPortId != null) {
            val startBoardPos = getPortBoardPosition(connectionStartNodeId, connectionStartPortId, connectionStartIsOutput)
            if (startBoardPos != null) {
                val fallbackPos = if (connectionCurrentPos != Offset.Zero) {
                    connectionCurrentPos
                } else if (interactionState.lastPointerPosition != Offset.Zero) {
                    (interactionState.lastPointerPosition - state.offset) / state.scale
                } else {
                    startBoardPos
                }

                var currentPos = if (highlightedPortId != null && highlightedNodeId != null) {
                    getPortBoardPosition(highlightedNodeId, highlightedPortId, !connectionStartIsOutput) ?: fallbackPos
                } else if (interactionState.hoveredJunctionId != null) {
                    junctionMap[interactionState.hoveredJunctionId] ?: fallbackPos
                } else if (interactionState.snappedWirePoint != null) {
                    interactionState.snappedWirePoint!!
                } else {
                    fallbackPos
                }

                if (interactionState.snappedWirePoint == null) {
                    if (interactionState.isShiftModifierPressed) {
                        currentPos = SplineMathUtils.snapToStraightAngle(startBoardPos, currentPos)
                    } else if (interactionState.isCtrlModifierPressed) {
                        currentPos = currentPos.snapToGrid()
                    }
                }

                val (startPos, endPos) = if (connectionStartIsOutput) {
                    startBoardPos to currentPos
                } else {
                    currentPos to startBoardPos
                }

                val pts = listOf((startPos * state.scale) + state.offset, (endPos * state.scale) + state.offset)
                val isTargetNodePort = highlightedPortId != null && highlightedNodeId != null
                val leadStart = if (connectionStartIsOutput) orthogonalPortLead else (isTargetNodePort && orthogonalPortLead)
                val leadEnd = if (!connectionStartIsOutput) orthogonalPortLead else (isTargetNodePort && orthogonalPortLead)

                val previewSourceNodeId = if (connectionStartIsOutput) connectionStartNodeId else (if (isTargetNodePort) highlightedNodeId else null)
                val previewTargetNodeId = if (!connectionStartIsOutput) connectionStartNodeId else (if (isTargetNodePort) highlightedNodeId else null)
                val previewStartBorderX = previewSourceNodeId?.let { id -> flow.nodes.find { it.id == id } }?.let { it.position.x + 400f }
                val previewEndBorderX = previewTargetNodeId?.let { id -> flow.nodes.find { it.id == id } }?.let { it.position.x }

                val path = SplineMathUtils.buildConnectionPath(
                    pts,
                    curveStyle,
                    roundness,
                    scale = state.scale,
                    canvasOffset = state.offset,
                    stepMode = stepMode,
                    useMiddleRouteForDirectConnection = !orthogonalPortLead,
                    startPortLead = leadStart,
                    endPortLead = leadEnd,
                    startBorderX = previewStartBorderX,
                    endBorderX = previewEndBorderX
                )
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

                val fallbackStructured = if (interactionState.structuredConnectionLivePos != Offset.Zero) {
                    interactionState.structuredConnectionLivePos
                } else {
                    allBoardPts.lastOrNull() ?: startBoardPos
                }

                var liveBoardPos = if (highlightedPortId != null && highlightedNodeId != null) {
                    getPortBoardPosition(highlightedNodeId, highlightedPortId, !interactionState.structuredConnectionStartIsOutput)
                        ?: fallbackStructured
                } else if (interactionState.hoveredJunctionId != null) {
                    junctionMap[interactionState.hoveredJunctionId] ?: fallbackStructured
                } else if (interactionState.snappedWirePoint != null) {
                    interactionState.snappedWirePoint!!
                } else {
                    fallbackStructured
                }

                val lastCommitted = allBoardPts.last()
                if (interactionState.snappedWirePoint == null) {
                    if (interactionState.isShiftModifierPressed) {
                        liveBoardPos = SplineMathUtils.snapToStraightAngle(lastCommitted, liveBoardPos)
                    } else if (interactionState.isCtrlModifierPressed) {
                        liveBoardPos = liveBoardPos.snapToGrid()
                    }
                }
                allBoardPts.add(liveBoardPos)

                val previewScreenPts = allBoardPts.map { (it * state.scale) + state.offset }
                val previewLeadIn = if (interactionState.structuredConnectionSourceJunctionId != null) {
                    val dummyConn = Connection(
                        sourceNodeId = -1L,
                        sourcePortId = "",
                        targetNodeId = -1L,
                        targetPortId = "",
                        sourceJunctionId = interactionState.structuredConnectionSourceJunctionId
                    )
                    ConnectionHitTester.getJunctionFilletParams(
                        connection = dummyConn,
                        connections = flow.connections,
                        junctionMap = junctionMap,
                        getPortBoardPosition = getPortBoardPosition,
                        groups = flow.groups,
                        density = this.density,
                        scale = state.scale,
                        offset = state.offset,
                        tension = roundness,
                        stepMode = stepMode,
                        orthogonalPortLead = orthogonalPortLead,
                        nodes = flow.nodes
                    ).startFilletLeadIn
                } else null

                val isStartNodePort = interactionState.structuredConnectionStartNodeId != null && interactionState.structuredConnectionSourceJunctionId == null
                val isEndNodePort = highlightedNodeId != null && highlightedPortId != null
                val previewStartBorderX = if (isStartNodePort) flow.nodes.find { it.id == interactionState.structuredConnectionStartNodeId }?.let { it.position.x + 400f } else null
                val previewEndBorderX = if (isEndNodePort) flow.nodes.find { it.id == highlightedNodeId }?.let { it.position.x } else null

                val previewPath = SplineMathUtils.buildConnectionPath(
                    previewScreenPts,
                    curveStyle,
                    roundness,
                    scale = state.scale,
                    canvasOffset = state.offset,
                    stepMode = stepMode,
                    startFilletLeadIn = previewLeadIn,
                    useMiddleRouteForDirectConnection = !orthogonalPortLead,
                    startPortLead = isStartNodePort && orthogonalPortLead,
                    endPortLead = isEndNodePort && orthogonalPortLead,
                    startBorderX = previewStartBorderX,
                    endBorderX = previewEndBorderX
                )
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
    scale: Float = 1f,
    offset: Offset = Offset.Zero,
    modifier: Modifier = Modifier
) {
    val boardStart = interactionState.selectionStart
    val boardEnd = interactionState.selectionEnd
    if (boardStart != null && boardEnd != null) {
        val start = (boardStart * scale) + offset
        val end = (boardEnd * scale) + offset
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
