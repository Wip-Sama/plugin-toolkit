package org.wip.plugintoolkit.features.flows.ui.canvas

import androidx.compose.ui.geometry.Offset
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.FlowGroup
import org.wip.plugintoolkit.features.flows.model.FlowJunction
import org.wip.plugintoolkit.features.flows.ui.toComposeOffset
import org.wip.plugintoolkit.features.flows.utils.SplineMathUtils
import org.wip.plugintoolkit.features.settings.model.ConnectionCurveStyle
import org.wip.plugintoolkit.features.settings.model.OrthogonalStepMode

import kotlin.math.abs

object ConnectionHitTester {

    fun getConnectionOrientations(
        connection: Connection,
        connections: List<Connection> = emptyList(),
        junctionMap: Map<Long, Offset> = emptyMap(),
        getPortBoardPosition: ((Long, String, Boolean) -> Offset?)? = null,
        groups: List<FlowGroup> = emptyList(),
        density: Float = 1f
    ): Pair<Boolean, Boolean> {
        // Determine start orientation:
        val startIsHorizontal = if (connection.sourceJunctionId == null) {
            // Node output ports always exit horizontally to the right
            true
        } else {
            val juncId = connection.sourceJunctionId
            val juncPos = junctionMap[juncId]

            // Find incoming connection entering this junction
            val incoming = connections.find {
                it != connection && (it.targetJunctionId == juncId || juncId in it.junctionIds)
            }

            if (incoming != null && juncPos != null && getPortBoardPosition != null) {
                val inPoints = getConnectionBoardPoints(
                    connection = incoming,
                    getPortBoardPosition = getPortBoardPosition,
                    junctionMap = junctionMap,
                    groups = groups,
                    density = density
                )
                if (inPoints != null && inPoints.size >= 2) {
                    val idx = inPoints.indexOfFirst { (it - juncPos).getDistance() < 1f }
                    val prevPoint = if (idx > 0) inPoints[idx - 1] else inPoints[inPoints.size - 2]
                    val dxIn = juncPos.x - prevPoint.x
                    val dyIn = juncPos.y - prevPoint.y
                    val isIncomingVertical = abs(dyIn) > abs(dxIn)

                    val floating = connection.floatingTarget
                    val targetPos = when {
                        connection.targetJunctionId != null -> junctionMap[connection.targetJunctionId]
                        floating != null -> floating.toComposeOffset()
                        else -> getPortBoardPosition(connection.targetNodeId, connection.targetPortId, false)
                    }

                    if (targetPos != null) {
                        val dxToTarget = targetPos.x - juncPos.x
                        val dyToTarget = targetPos.y - juncPos.y

                        val isAhead = if (isIncomingVertical) {
                            if (dyIn >= 0f) dyToTarget > 1f else dyToTarget < -1f
                        } else {
                            if (dxIn >= 0f) dxToTarget > 1f else dxToTarget < -1f
                        }

                        if (isAhead) {
                            // Preserve incoming tangent through junction (no sharp break)
                            !isIncomingVertical
                        } else {
                            // Target branches off perpendicularly: take perpendicular departure
                            isIncomingVertical
                        }
                    } else {
                        !isIncomingVertical
                    }
                } else {
                    true
                }
            } else {
                true
            }
        }

        // Determine end orientation:
        val endIsHorizontal = if (connection.targetJunctionId == null && !connection.isFloating) {
            // Node input ports always enter horizontally from the left
            true
        } else if (connection.targetJunctionId != null) {
            val juncId = connection.targetJunctionId
            val juncPos = junctionMap[juncId]

            // Check if an outgoing connection continues through this junction
            val outgoing = connections.find {
                it != connection && (it.sourceJunctionId == juncId || juncId in it.junctionIds)
            }

            if (outgoing != null && juncPos != null && getPortBoardPosition != null) {
                val outPoints = getConnectionBoardPoints(
                    connection = outgoing,
                    getPortBoardPosition = getPortBoardPosition,
                    junctionMap = junctionMap,
                    groups = groups,
                    density = density
                )
                if (outPoints != null && outPoints.size >= 2) {
                    val idx = outPoints.indexOfFirst { (it - juncPos).getDistance() < 1f }
                    val nextPoint = if (idx >= 0 && idx < outPoints.size - 1) outPoints[idx + 1] else outPoints[1]
                    val dxOut = nextPoint.x - juncPos.x
                    val dyOut = nextPoint.y - juncPos.y
                    val isOutgoingVertical = abs(dyOut) > abs(dxOut)
                    !isOutgoingVertical
                } else {
                    true
                }
            } else {
                true
            }
        } else {
            true
        }

        return Pair(startIsHorizontal, endIsHorizontal)
    }

    fun findClosestConnection(
        position: Offset,
        connections: List<Connection>,
        getPortBoardPosition: (Long, String, Boolean) -> Offset?,
        scale: Float,
        offset: Offset,
        initialMinDistance: Float = 20f * scale,
        junctions: List<FlowJunction> = emptyList(),
        curveStyle: ConnectionCurveStyle = ConnectionCurveStyle.Bezier,
        roundness: Float = 0.5f,
        groups: List<FlowGroup> = emptyList(),
        density: Float = 1f,
        stepMode: OrthogonalStepMode = OrthogonalStepMode.Middle
    ): Connection? {
        val junctionMap = junctions.associate { it.id to it.position.toComposeOffset() }
        var bestConnection: Connection? = null
        var minDistance = if (initialMinDistance < 15f) 15f else initialMinDistance

        for (connection in connections) {
            val screenPoints = getConnectionScreenPoints(
                connection = connection,
                getPortBoardPosition = getPortBoardPosition,
                junctionMap = junctionMap,
                scale = scale,
                offset = offset,
                groups = groups,
                density = density
            ) ?: continue

            val minX = screenPoints.minOf { it.x } - (30f * scale)
            val maxX = screenPoints.maxOf { it.x } + (30f * scale)
            val minY = screenPoints.minOf { it.y } - (30f * scale)
            val maxY = screenPoints.maxOf { it.y } + (30f * scale)

            if (position.x !in minX..maxX || position.y !in minY..maxY) {
                continue
            }

            val (startIsHorizontal, endIsHorizontal) = getConnectionOrientations(
                connection = connection,
                connections = connections,
                junctionMap = junctionMap,
                getPortBoardPosition = getPortBoardPosition,
                groups = groups,
                density = density
            )
            val effectiveStyle = curveStyle
            val sampledPoints = SplineMathUtils.sampleConnectionPoints(
                points = screenPoints,
                style = effectiveStyle,
                tension = roundness,
                startHorizontal = startIsHorizontal,
                endHorizontal = endIsHorizontal,
                scale = scale,
                stepMode = stepMode
            )
            val dist = SplineMathUtils.distanceToPath(position, sampledPoints)
            if (dist < minDistance) {
                minDistance = dist
                bestConnection = connection
            }
        }
        return bestConnection
    }

    data class ConnectionProjection(
        val connection: Connection,
        val projectedPoint: Offset,
        val segmentIndex: Int
    ) {
        val first: Connection get() = connection
        val second: Offset get() = projectedPoint
        val third: Int get() = segmentIndex
    }

    /**
     * Finds the closest connection and returns it along with the projected board position on the wire.
     * Useful for wire branching (inserting a junction at the click point).
     */
    fun findClosestConnectionWithProjection(
        position: Offset,
        connections: List<Connection>,
        getPortBoardPosition: (Long, String, Boolean) -> Offset?,
        scale: Float,
        offset: Offset,
        initialMinDistance: Float = 20f * scale,
        junctions: List<FlowJunction> = emptyList(),
        curveStyle: ConnectionCurveStyle = ConnectionCurveStyle.Bezier,
        roundness: Float = 0.5f,
        groups: List<FlowGroup> = emptyList(),
        density: Float = 1f,
        stepMode: OrthogonalStepMode = OrthogonalStepMode.Middle
    ): ConnectionProjection? {
        val junctionMap = junctions.associate { it.id to it.position.toComposeOffset() }
        var bestConnection: Connection? = null
        var bestProjected: Offset? = null
        var bestSegmentIndex = 0
        var minDistance = if (initialMinDistance < 15f) 15f else initialMinDistance

        for (connection in connections) {
            val screenPoints = getConnectionScreenPoints(
                connection = connection,
                getPortBoardPosition = getPortBoardPosition,
                junctionMap = junctionMap,
                scale = scale,
                offset = offset,
                groups = groups,
                density = density
            ) ?: continue

            val minX = screenPoints.minOf { it.x } - (30f * scale)
            val maxX = screenPoints.maxOf { it.x } + (30f * scale)
            val minY = screenPoints.minOf { it.y } - (30f * scale)
            val maxY = screenPoints.maxOf { it.y } + (30f * scale)

            if (position.x !in minX..maxX || position.y !in minY..maxY) {
                continue
            }

            val (startIsHorizontal, endIsHorizontal) = getConnectionOrientations(
                connection = connection,
                connections = connections,
                junctionMap = junctionMap,
                getPortBoardPosition = getPortBoardPosition,
                groups = groups,
                density = density
            )
            val effectiveStyle = curveStyle
            val sampledPoints = SplineMathUtils.sampleConnectionPoints(
                points = screenPoints,
                style = effectiveStyle,
                tension = roundness,
                startHorizontal = startIsHorizontal,
                endHorizontal = endIsHorizontal,
                scale = scale,
                stepMode = stepMode
            )
            val dist = SplineMathUtils.distanceToPath(position, sampledPoints)
            if (dist < minDistance) {
                minDistance = dist
                bestConnection = connection
                val proj = SplineMathUtils.findClosestPointOnPath(position, sampledPoints)
                bestProjected = (proj - offset) / scale

                var closestSegDist = Float.MAX_VALUE
                var bestSeg = 0
                for (i in 0 until screenPoints.size - 1) {
                    val segD = SplineMathUtils.distanceToSegment(proj, screenPoints[i], screenPoints[i + 1])
                    if (segD < closestSegDist) {
                        closestSegDist = segD
                        bestSeg = i
                    }
                }
                bestSegmentIndex = bestSeg
            }
        }
        return if (bestConnection != null && bestProjected != null) {
            ConnectionProjection(bestConnection, bestProjected, bestSegmentIndex)
        } else null
    }

    /**
     * Finds the closest junction handle within [hitRadius] screen distance of [position].
     */
    fun findClosestJunction(
        position: Offset,
        junctions: List<FlowJunction>,
        scale: Float,
        offset: Offset,
        hitRadius: Float = 20f * scale
    ): FlowJunction? {
        var closest: FlowJunction? = null
        var minDistance = if (hitRadius < 20f) 20f else hitRadius
        for (junction in junctions) {
            val screenPos = (junction.position.toComposeOffset() * scale) + offset
            val dist = (position - screenPos).getDistance()
            if (dist < minDistance) {
                minDistance = dist
                closest = junction
            }
        }
        return closest
    }

    fun determineCloserEnd(
        position: Offset,
        sourceScreenPos: Offset,
        targetScreenPos: Offset
    ): Boolean {
        val distToSource = (position - sourceScreenPos).getDistance()
        val distToTarget = (position - targetScreenPos).getDistance()
        return distToSource < distToTarget
    }

    fun getConnectionBoardPoints(
        connection: Connection,
        getPortBoardPosition: (Long, String, Boolean) -> Offset?,
        junctionMap: Map<Long, Offset>,
        groups: List<FlowGroup> = emptyList(),
        density: Float = 1f
    ): List<Offset>? {
        val srcCollapsedGroup = groups.find { it.isCollapsed && connection.sourceNodeId in it.nodeIds }
        val tgtCollapsedGroup = groups.find { it.isCollapsed && connection.targetNodeId in it.nodeIds }

        // If both ends are inside the same collapsed group, hide the internal connection
        if (srcCollapsedGroup != null && tgtCollapsedGroup != null && srcCollapsedGroup.id == tgtCollapsedGroup.id) {
            return null
        }

        val startPos = when {
            connection.sourceJunctionId != null -> junctionMap[connection.sourceJunctionId]
            srcCollapsedGroup != null -> {
                val collapsedWidth = maxOf(srcCollapsedGroup.size.x, 200f * density)
                val collapsedHeight = 44f * density
                Offset(
                    srcCollapsedGroup.position.x + collapsedWidth,
                    srcCollapsedGroup.position.y + (collapsedHeight / 2f)
                )
            }
            else -> getPortBoardPosition(connection.sourceNodeId, connection.sourcePortId, true)
        } ?: return null

        val floating = connection.floatingTarget
        val endPos = when {
            connection.targetJunctionId != null -> junctionMap[connection.targetJunctionId]
            tgtCollapsedGroup != null -> {
                val collapsedHeight = 44f * density
                Offset(
                    tgtCollapsedGroup.position.x,
                    tgtCollapsedGroup.position.y + (collapsedHeight / 2f)
                )
            }
            floating != null -> floating.toComposeOffset()
            else -> getPortBoardPosition(connection.targetNodeId, connection.targetPortId, false)
        } ?: return null

        val allPoints = mutableListOf<Offset>()
        allPoints.add(startPos)
        for (jId in connection.junctionIds) {
            junctionMap[jId]?.let { allPoints.add(it) }
        }
        for (wp in connection.waypoints) {
            allPoints.add(wp.toComposeOffset())
        }
        allPoints.add(endPos)
        return allPoints
    }

    fun getConnectionScreenPoints(
        connection: Connection,
        getPortBoardPosition: (Long, String, Boolean) -> Offset?,
        junctionMap: Map<Long, Offset>,
        scale: Float,
        offset: Offset,
        groups: List<FlowGroup> = emptyList(),
        density: Float = 1f
    ): List<Offset>? {
        val boardPoints = getConnectionBoardPoints(
            connection = connection,
            getPortBoardPosition = getPortBoardPosition,
            junctionMap = junctionMap,
            groups = groups,
            density = density
        ) ?: return null
        return boardPoints.map { (it * scale) + offset }
    }

    /**
     * Finds the closest waypoint on any connection within [hitRadius] screen distance of [position].
     * Returns Triple(Connection, waypointIndex, screenPosition).
     */
    fun findClosestWaypoint(
        position: Offset,
        connections: List<Connection>,
        scale: Float,
        offset: Offset,
        hitRadius: Float = 20f * scale
    ): Triple<Connection, Int, Offset>? {
        var closest: Triple<Connection, Int, Offset>? = null
        var minDistance = if (hitRadius < 20f) 20f else hitRadius

        for (connection in connections) {
            connection.waypoints.forEachIndexed { index, wp ->
                val screenPos = (wp.toComposeOffset() * scale) + offset
                val dist = (position - screenPos).getDistance()
                if (dist < minDistance) {
                    minDistance = dist
                    closest = Triple(connection, index, screenPos)
                }
            }
        }
        return closest
    }

    /**
     * Finds the closest segment midpoint on any visible connection within [hitRadius] screen distance of [position].
     * Returns Triple(Connection, segmentIndex, screenPosition).
     */
    fun findClosestMidpoint(
        position: Offset,
        connections: List<Connection>,
        getPortBoardPosition: (Long, String, Boolean) -> Offset?,
        junctionMap: Map<Long, Offset>,
        scale: Float,
        offset: Offset,
        curveStyle: ConnectionCurveStyle = ConnectionCurveStyle.CardinalSpline,
        roundness: Float = 0.5f,
        groups: List<FlowGroup> = emptyList(),
        density: Float = 1f,
        hitRadius: Float = 12f * scale,
        stepMode: OrthogonalStepMode = OrthogonalStepMode.Middle
    ): Triple<Connection, Int, Offset>? {
        var closest: Triple<Connection, Int, Offset>? = null
        var minDistance = if (hitRadius < 8f) 8f else hitRadius

        for (connection in connections) {
            val screenPoints = getConnectionScreenPoints(
                connection = connection,
                getPortBoardPosition = getPortBoardPosition,
                junctionMap = junctionMap,
                scale = scale,
                offset = offset,
                groups = groups,
                density = density
            ) ?: continue

            if (screenPoints.size < 2) continue

            val (startIsHorizontal, endIsHorizontal) = getConnectionOrientations(
                connection = connection,
                connections = connections,
                junctionMap = junctionMap,
                getPortBoardPosition = getPortBoardPosition,
                groups = groups,
                density = density
            )
            val effectiveStyle = curveStyle
            val midpoints = SplineMathUtils.computeSegmentMidpoints(
                points = screenPoints,
                style = effectiveStyle,
                tension = roundness,
                startHorizontal = startIsHorizontal,
                endHorizontal = endIsHorizontal,
                scale = scale,
                stepMode = stepMode
            )

            midpoints.forEachIndexed { segIdx, midPt ->
                val dist = (position - midPt).getDistance()
                if (dist < minDistance) {
                    minDistance = dist
                    closest = Triple(connection, segIdx, midPt)
                }
            }
        }
        return closest
    }
}

