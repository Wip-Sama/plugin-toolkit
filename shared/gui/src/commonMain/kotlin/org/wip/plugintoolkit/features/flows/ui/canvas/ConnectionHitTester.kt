package org.wip.plugintoolkit.features.flows.ui.canvas

import androidx.compose.ui.geometry.Offset
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.FlowGroup
import org.wip.plugintoolkit.features.flows.model.FlowJunction
import org.wip.plugintoolkit.features.flows.ui.toComposeOffset
import org.wip.plugintoolkit.features.flows.utils.SplineMathUtils
import org.wip.plugintoolkit.features.settings.model.ConnectionCurveStyle

object ConnectionHitTester {

    fun getConnectionOrientations(
        connection: Connection,
        connections: List<Connection> = emptyList()
    ): Pair<Boolean, Boolean> {
        val startIsHorizontal = if (connection.sourceJunctionId == null) {
            true
        } else {
            // Connection leaves a junction. Check incoming connection to this junction
            val incoming = connections.find { it.targetJunctionId == connection.sourceJunctionId }
            if (incoming != null) {
                false
            } else {
                false
            }
        }

        val endIsHorizontal = if (connection.targetJunctionId == null && !connection.isFloating) {
            true
        } else {
            false
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
        density: Float = 1f
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

            val (startIsHorizontal, endIsHorizontal) = getConnectionOrientations(connection, connections)
            val effectiveStyle = curveStyle
            val sampledPoints = SplineMathUtils.sampleConnectionPoints(
                points = screenPoints,
                style = effectiveStyle,
                tension = roundness,
                startHorizontal = startIsHorizontal,
                endHorizontal = endIsHorizontal,
                scale = scale
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
        density: Float = 1f
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

            val (startIsHorizontal, endIsHorizontal) = getConnectionOrientations(connection, connections)
            val effectiveStyle = curveStyle
            val sampledPoints = SplineMathUtils.sampleConnectionPoints(
                points = screenPoints,
                style = effectiveStyle,
                tension = roundness,
                startHorizontal = startIsHorizontal,
                endHorizontal = endIsHorizontal,
                scale = scale
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
        hitRadius: Float = 12f * scale
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

            val (startIsHorizontal, endIsHorizontal) = getConnectionOrientations(connection, connections)
            val effectiveStyle = curveStyle
            val midpoints = SplineMathUtils.computeSegmentMidpoints(
                points = screenPoints,
                style = effectiveStyle,
                tension = roundness,
                startHorizontal = startIsHorizontal,
                endHorizontal = endIsHorizontal,
                scale = scale
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

