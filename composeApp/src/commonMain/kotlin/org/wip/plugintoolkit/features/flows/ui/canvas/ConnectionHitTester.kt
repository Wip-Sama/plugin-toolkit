package org.wip.plugintoolkit.features.flows.ui.canvas

import androidx.compose.ui.geometry.Offset
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.utils.BoardMathUtils
import kotlin.math.abs

object ConnectionHitTester {

    fun findClosestConnection(
        position: Offset,
        connections: List<Connection>,
        getPortBoardPosition: (Long, String, Boolean) -> Offset?,
        scale: Float,
        offset: Offset,
        initialMinDistance: Float = 20f * scale
    ): Connection? {
        var bestConnection: Connection? = null
        var minDistance = if (initialMinDistance < 15f) 15f else initialMinDistance

        connections.forEach { connection ->
            val sourcePortBoardPos = getPortBoardPosition(connection.sourceNodeId, connection.sourcePortId, true)
            val targetPortBoardPos = getPortBoardPosition(connection.targetNodeId, connection.targetPortId, false)
            if (sourcePortBoardPos != null && targetPortBoardPos != null) {
                val startPos = (sourcePortBoardPos * scale) + offset
                val endPos = (targetPortBoardPos * scale) + offset

                val controlPointOffset = abs(endPos.x - startPos.x) / 2f
                val p1x = startPos.x + controlPointOffset
                val p2x = endPos.x - controlPointOffset

                val padding = 30f * scale
                val aabbMinX = minOf(startPos.x, endPos.x, p1x, p2x) - padding
                val aabbMaxX = maxOf(startPos.x, endPos.x, p1x, p2x) + padding
                val aabbMinY = minOf(startPos.y, endPos.y) - padding
                val aabbMaxY = maxOf(startPos.y, endPos.y) + padding

                if (position.x in aabbMinX..aabbMaxX && position.y in aabbMinY..aabbMaxY) {
                    val dist = BoardMathUtils.getDistanceToBezier(position, startPos, endPos)
                    if (dist < minDistance) {
                        minDistance = dist
                        bestConnection = connection
                    }
                }
            }
        }
        return bestConnection
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
}
