package org.wip.plugintoolkit.features.flows.ui.canvas

import androidx.compose.ui.geometry.Offset
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.FlowGroup
import org.wip.plugintoolkit.features.flows.model.FlowJunction
import org.wip.plugintoolkit.features.flows.ui.toComposeOffset
import org.wip.plugintoolkit.features.flows.utils.SplineMathUtils
import org.wip.plugintoolkit.features.settings.model.ConnectionCurveStyle

object ConnectionHitTester {

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

            val sampledPoints = SplineMathUtils.sampleConnectionPoints(
                points = screenPoints,
                style = curveStyle,
                tension = roundness
            )
            val dist = SplineMathUtils.distanceToPath(position, sampledPoints)
            if (dist < minDistance) {
                minDistance = dist
                bestConnection = connection
            }
        }
        return bestConnection
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
    ): Pair<Connection, Offset>? {
        val junctionMap = junctions.associate { it.id to it.position.toComposeOffset() }
        var bestConnection: Connection? = null
        var bestProjected: Offset? = null
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

            val sampledPoints = SplineMathUtils.sampleConnectionPoints(
                points = screenPoints,
                style = curveStyle,
                tension = roundness
            )
            val dist = SplineMathUtils.distanceToPath(position, sampledPoints)
            if (dist < minDistance) {
                minDistance = dist
                bestConnection = connection
                val proj = SplineMathUtils.findClosestPointOnPath(position, sampledPoints)
                bestProjected = (proj - offset) / scale
            }
        }
        return if (bestConnection != null && bestProjected != null) {
            Pair(bestConnection, bestProjected)
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
        hitRadius: Float = 16f * scale
    ): FlowJunction? {
        var closest: FlowJunction? = null
        var minDistance = hitRadius
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

    fun getConnectionScreenPoints(
        connection: Connection,
        getPortBoardPosition: (Long, String, Boolean) -> Offset?,
        junctionMap: Map<Long, Offset>,
        scale: Float,
        offset: Offset,
        groups: List<FlowGroup> = emptyList(),
        density: Float = 1f
    ): List<Offset>? {
        val srcCollapsedGroup = groups.find { it.isCollapsed && connection.sourceNodeId in it.nodeIds }
        val tgtCollapsedGroup = groups.find { it.isCollapsed && connection.targetNodeId in it.nodeIds }

        // If both ends are inside the same collapsed group, hide the internal connection
        if (srcCollapsedGroup != null && tgtCollapsedGroup != null && srcCollapsedGroup.id == tgtCollapsedGroup.id) {
            return null
        }

        val startScreenPos = when {
            connection.sourceJunctionId != null -> junctionMap[connection.sourceJunctionId]?.let { (it * scale) + offset }
            srcCollapsedGroup != null -> {
                val collapsedWidthPx = maxOf(srcCollapsedGroup.size.x * scale, 200f * density)
                val collapsedHeightPx = 44f * density
                Offset(
                    (srcCollapsedGroup.position.x * scale + offset.x) + collapsedWidthPx,
                    (srcCollapsedGroup.position.y * scale + offset.y) + (collapsedHeightPx / 2f)
                )
            }
            else -> getPortBoardPosition(connection.sourceNodeId, connection.sourcePortId, true)?.let { (it * scale) + offset }
        } ?: return null

        val floating = connection.floatingTarget
        val endScreenPos = when {
            connection.targetJunctionId != null -> junctionMap[connection.targetJunctionId]?.let { (it * scale) + offset }
            tgtCollapsedGroup != null -> {
                val collapsedHeightPx = 44f * density
                Offset(
                    tgtCollapsedGroup.position.x * scale + offset.x,
                    (tgtCollapsedGroup.position.y * scale + offset.y) + (collapsedHeightPx / 2f)
                )
            }
            floating != null -> (floating.toComposeOffset() * scale) + offset
            else -> getPortBoardPosition(connection.targetNodeId, connection.targetPortId, false)?.let { (it * scale) + offset }
        } ?: return null

        val allPoints = mutableListOf<Offset>()
        allPoints.add(startScreenPos)
        for (jId in connection.junctionIds) {
            junctionMap[jId]?.let { allPoints.add((it * scale) + offset) }
        }
        for (wp in connection.waypoints) {
            allPoints.add((wp.toComposeOffset() * scale) + offset)
        }
        allPoints.add(endScreenPos)
        return allPoints
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
        hitRadius: Float = 16f * scale
    ): Triple<Connection, Int, Offset>? {
        var closest: Triple<Connection, Int, Offset>? = null
        var minDistance = if (hitRadius < 12f) 12f else hitRadius

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
}

