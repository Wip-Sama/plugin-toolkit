package org.wip.plugintoolkit.features.flows.ui.canvas

import androidx.compose.ui.geometry.Offset
import co.touchlab.kermit.Logger
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.FlowGroup
import org.wip.plugintoolkit.features.flows.model.FlowJunction
import org.wip.plugintoolkit.features.flows.ui.toComposeOffset
import org.wip.plugintoolkit.features.flows.utils.SplineMathUtils
import org.wip.plugintoolkit.features.settings.model.ConnectionCurveStyle
import org.wip.plugintoolkit.features.settings.model.OrthogonalStepMode

import kotlin.math.abs

data class JunctionFilletParams(
    val startFilletLeadIn: Offset? = null,
    val endTrimDistance: Float = 0f
)

object ConnectionHitTester {

    fun getConnectionOrientations(
        connection: Connection,
        connections: List<Connection> = emptyList(),
        junctionMap: Map<Long, Offset> = emptyMap(),
        getPortBoardPosition: ((Long, String, Boolean) -> Offset?)? = null,
        groups: List<FlowGroup> = emptyList(),
        density: Float = 1f
    ): Pair<Boolean, Boolean> {
        val connId = "conn[${connection.sourceNodeId}:${connection.sourcePortId}->${connection.targetNodeId}:${connection.targetPortId}]"
        // 1. Determine end orientation (how this connection arrives at its target):
        val endIsHorizontal = if (connection.targetJunctionId == null && !connection.isFloating) {
            // Node input ports always receive connections horizontally from the left
            Logger.v(tag = "ConnectionOrientations") { "$connId: endIsHorizontal=true (target is a node input port, always horizontal)" }
            true
        } else if (connection.targetJunctionId != null) {
            val tgtJuncPos = junctionMap[connection.targetJunctionId]
            if (tgtJuncPos != null) {
                if (connection.sourceJunctionId == null && connection.waypoints.isEmpty()) {
                    Logger.v(tag = "ConnectionOrientations") {
                        "$connId: endIsHorizontal=true (node output port -> junction, no waypoints: always arrives horizontally)"
                    }
                    true
                } else {
                    val inBoardPts = if (getPortBoardPosition != null) {
                        getConnectionBoardPoints(
                            connection = connection,
                            getPortBoardPosition = getPortBoardPosition,
                            junctionMap = junctionMap,
                            groups = groups,
                            density = density
                        )
                    } else null

                    val inPrevPoint = if (inBoardPts != null && inBoardPts.size >= 2) {
                        val idx = inBoardPts.indexOfFirst { (it - tgtJuncPos).getDistance() < 1f }
                        if (idx > 0) inBoardPts[idx - 1] else inBoardPts[inBoardPts.size - 2]
                    } else if (connection.sourceJunctionId != null) {
                        junctionMap[connection.sourceJunctionId]
                    } else {
                        null
                    }

                    if (inPrevPoint != null) {
                        val dx = tgtJuncPos.x - inPrevPoint.x
                        val dy = tgtJuncPos.y - inPrevPoint.y
                        val result = abs(dx) >= abs(dy)
                        Logger.v(tag = "ConnectionOrientations") {
                            "$connId: endIsHorizontal=$result from prev-segment (dx=$dx, dy=$dy, abs(dx)>=abs(dy)=$result)"
                        }
                        result
                    } else {
                        Logger.v(tag = "ConnectionOrientations") { "$connId: endIsHorizontal=true (no prev point found, defaulting)" }
                        true
                    }
                }
            } else {
                Logger.v(tag = "ConnectionOrientations") { "$connId: endIsHorizontal=true (target junction pos not in map, defaulting)" }
                true
            }
        } else {
            Logger.v(tag = "ConnectionOrientations") { "$connId: endIsHorizontal=true (floating/default)" }
            true
        }

        // 2. Determine start orientation (how this connection leaves its source):
        val startIsHorizontal = if (connection.sourceJunctionId == null) {
            // Node output ports always exit horizontally to the right
            Logger.v(tag = "ConnectionOrientations") { "$connId: startIsHorizontal=true (node output port, always horizontal)" }
            true
        } else {
            val juncId = connection.sourceJunctionId
            val juncPos = junctionMap[juncId]

            // Find incoming connection entering this junction to determine its through-axis
            val incoming = connections.find {
                it != connection && (it.targetJunctionId == juncId || juncId in it.junctionIds)
            }

            if (incoming != null && juncPos != null) {
                val inEndH: Boolean
                if (incoming.sourceJunctionId == null && incoming.waypoints.isEmpty()) {
                    // Incoming comes directly from a node output port: always horizontal arrival
                    inEndH = true
                    Logger.v(tag = "ConnectionOrientations") {
                        "$connId: incoming=${
                            "conn[${incoming.sourceNodeId}:${incoming.sourcePortId}]->junc[$juncId]"
                        } inEndH=true (node output -> junction, no waypoints)"
                    }
                } else {
                    // Derive arrival axis from actual orthogonal segment preceding the junction
                    val inBoardPts = if (getPortBoardPosition != null) {
                        getConnectionBoardPoints(
                            connection = incoming,
                            getPortBoardPosition = getPortBoardPosition,
                            junctionMap = junctionMap,
                            groups = groups,
                            density = density
                        )
                    } else null

                    val inPrevPoint = if (inBoardPts != null && inBoardPts.size >= 2) {
                        val idx = inBoardPts.indexOfFirst { (it - juncPos).getDistance() < 1f }
                        if (idx > 0) inBoardPts[idx - 1] else inBoardPts[inBoardPts.size - 2]
                    } else if (incoming.sourceJunctionId != null) {
                        junctionMap[incoming.sourceJunctionId]
                    } else {
                        null
                    }

                    val dxIn = if (inPrevPoint != null) juncPos.x - inPrevPoint.x else 1f
                    val dyIn = if (inPrevPoint != null) juncPos.y - inPrevPoint.y else 0f
                    inEndH = abs(dxIn) >= abs(dyIn)
                    Logger.v(tag = "ConnectionOrientations") {
                        "$connId: incoming junc[${
                            incoming.sourceJunctionId ?: incoming.sourceNodeId
                        }]->junc[$juncId] inEndH=$inEndH (dxIn=$dxIn, dyIn=$dyIn)"
                    }
                }

                val floating = connection.floatingTarget
                val targetPos = when {
                    connection.targetJunctionId != null -> junctionMap[connection.targetJunctionId]
                    floating != null -> floating.toComposeOffset()
                    getPortBoardPosition != null -> getPortBoardPosition(connection.targetNodeId, connection.targetPortId, false)
                    else -> null
                }

                if (targetPos != null) {
                    val dxToTarget = targetPos.x - juncPos.x
                    val dyToTarget = targetPos.y - juncPos.y

                    if (inEndH) {
                        // Incoming arrived horizontally: continue through only if target is predominantly forward horizontal
                        // Determine the incoming horizontal direction by looking at the last segment of the incoming connection
                        val incomingPrevBoard = if (getPortBoardPosition != null) {
                            getConnectionBoardPoints(
                                connection = incoming,
                                getPortBoardPosition = getPortBoardPosition,
                                junctionMap = junctionMap,
                                groups = groups,
                                density = density
                            )
                        } else null
                        val incomingPrevPt = if (incomingPrevBoard != null && incomingPrevBoard.size >= 2) {
                            val idx = incomingPrevBoard.indexOfFirst { (it - juncPos).getDistance() < 1f }
                            if (idx > 0) incomingPrevBoard[idx - 1] else incomingPrevBoard[incomingPrevBoard.size - 2]
                        } else null
                        val dxInSign = if (incomingPrevPt != null) juncPos.x - incomingPrevPt.x else 1f

                        val isAhead = abs(dxToTarget) >= abs(dyToTarget) && (if (dxInSign >= 0f) dxToTarget > 10f else dxToTarget < -10f)
                        val result = isAhead // true = continue horizontal, false = branch vertical
                        Logger.v(tag = "ConnectionOrientations") {
                            "$connId: startIsHorizontal=$result (inEndH=true, dxToTarget=$dxToTarget, dyToTarget=$dyToTarget, isAhead=$isAhead -> ${if (result) "continue horizontal" else "branch vertical"})"
                        }
                        result
                    } else {
                        // Incoming arrived vertically: continue through only if target is predominantly forward vertical
                        val incomingPrevBoard = if (getPortBoardPosition != null) {
                            getConnectionBoardPoints(
                                connection = incoming,
                                getPortBoardPosition = getPortBoardPosition,
                                junctionMap = junctionMap,
                                groups = groups,
                                density = density
                            )
                        } else null
                        val incomingPrevPt = if (incomingPrevBoard != null && incomingPrevBoard.size >= 2) {
                            val idx = incomingPrevBoard.indexOfFirst { (it - juncPos).getDistance() < 1f }
                            if (idx > 0) incomingPrevBoard[idx - 1] else incomingPrevBoard[incomingPrevBoard.size - 2]
                        } else null
                        val dyInSign = if (incomingPrevPt != null) juncPos.y - incomingPrevPt.y else 1f

                        val isAhead = abs(dyToTarget) >= abs(dxToTarget) && (if (dyInSign >= 0f) dyToTarget > 10f else dyToTarget < -10f)
                        val result = !isAhead // false = continue vertical, true = branch horizontal
                        Logger.v(tag = "ConnectionOrientations") {
                            "$connId: startIsHorizontal=$result (inEndH=false, dxToTarget=$dxToTarget, dyToTarget=$dyToTarget, isAhead=$isAhead -> ${if (result) "branch horizontal" else "continue vertical"})"
                        }
                        result
                    }
                } else {
                    Logger.v(tag = "ConnectionOrientations") { "$connId: startIsHorizontal=$inEndH (no target pos, mirrors inEndH)" }
                    inEndH
                }
            } else {
                // No incoming connection: determine departure axis from target displacement
                val floating = connection.floatingTarget
                val targetPos = when {
                    connection.targetJunctionId != null -> junctionMap[connection.targetJunctionId]
                    floating != null -> floating.toComposeOffset()
                    getPortBoardPosition != null -> getPortBoardPosition(connection.targetNodeId, connection.targetPortId, false)
                    else -> null
                }
                if (targetPos != null && juncPos != null) {
                    val dx = targetPos.x - juncPos.x
                    val dy = targetPos.y - juncPos.y
                    val result = abs(dx) >= abs(dy)
                    Logger.v(tag = "ConnectionOrientations") {
                        "$connId: startIsHorizontal=$result (no incoming, junc->target dx=$dx, dy=$dy)"
                    }
                    result
                } else {
                    Logger.v(tag = "ConnectionOrientations") { "$connId: startIsHorizontal=true (no incoming, no target pos, defaulting)" }
                    true
                }
            }
        }

        Logger.v(tag = "ConnectionOrientations") { "$connId: FINAL startIsHorizontal=$startIsHorizontal, endIsHorizontal=$endIsHorizontal" }
        return Pair(startIsHorizontal, endIsHorizontal)
    }

    fun getJunctionFilletParams(
        connection: Connection,
        connections: List<Connection> = emptyList(),
        junctionMap: Map<Long, Offset> = emptyMap(),
        getPortBoardPosition: ((Long, String, Boolean) -> Offset?)? = null,
        groups: List<FlowGroup> = emptyList(),
        density: Float = 1f,
        scale: Float = 1f,
        offset: Offset = Offset.Zero,
        tension: Float = 0.5f,
        stepMode: OrthogonalStepMode = OrthogonalStepMode.Auto
    ): JunctionFilletParams {
        var startFilletLeadIn: Offset? = null
        var endTrimDistance = 0f

        // 1. Start fillet for connection originating from a junction
        val srcJuncId = connection.sourceJunctionId
        if (srcJuncId != null && getPortBoardPosition != null) {
            val juncPos = junctionMap[srcJuncId]
            if (juncPos != null) {
                val incoming = connections.find {
                    it != connection && (it.targetJunctionId == srcJuncId || srcJuncId in it.junctionIds)
                }
                if (incoming != null) {
                    val inBoardPts = getConnectionBoardPoints(
                        connection = incoming,
                        getPortBoardPosition = getPortBoardPosition,
                        junctionMap = junctionMap,
                        groups = groups,
                        density = density
                    )
                    if (inBoardPts != null && inBoardPts.size >= 2) {
                        val (inStartH, inEndH) = getConnectionOrientations(
                            connection = incoming,
                            connections = connections,
                            junctionMap = junctionMap,
                            getPortBoardPosition = getPortBoardPosition,
                            groups = groups,
                            density = density
                        )
                        val inOrthoPts = SplineMathUtils.computeOrthogonalPoints(
                            inBoardPts,
                            startHorizontal = inStartH,
                            endHorizontal = inEndH,
                            stepMode = stepMode
                        )
                        val idx = inOrthoPts.indexOfFirst { (it - juncPos).getDistance() < 1f }
                        val prevPoint = if (idx > 0) {
                            inOrthoPts[idx - 1]
                        } else if (inOrthoPts.size >= 2) {
                            inOrthoPts[inOrthoPts.size - 2]
                        } else null

                        if (prevPoint != null) {
                            startFilletLeadIn = (prevPoint * scale) + offset
                            Logger.v(tag = "JunctionFillet") {
                                "startFillet conn[${connection.sourceNodeId}:${connection.sourcePortId}] " +
                                "src-junc[${connection.sourceJunctionId}]: lead-in=$prevPoint (screen=${startFilletLeadIn})"
                            }
                        }
                    }
                }
            }
        }

        // 2. End trim for connection terminating at a junction with turning outgoing branches
        val tgtJuncId = connection.targetJunctionId
        if (tgtJuncId != null && getPortBoardPosition != null) {
            val juncPos = junctionMap[tgtJuncId]
            if (juncPos != null) {
                val outgoingList = connections.filter {
                    it != connection && (it.sourceJunctionId == tgtJuncId || tgtJuncId in it.junctionIds)
                }
                if (outgoingList.isNotEmpty()) {
                    val myBoardPts = getConnectionBoardPoints(
                        connection = connection,
                        getPortBoardPosition = getPortBoardPosition,
                        junctionMap = junctionMap,
                        groups = groups,
                        density = density
                    )
                    if (myBoardPts != null && myBoardPts.size >= 2) {
                        val (myStartH, myEndH) = getConnectionOrientations(
                            connection = connection,
                            connections = connections,
                            junctionMap = junctionMap,
                            getPortBoardPosition = getPortBoardPosition,
                            groups = groups,
                            density = density
                        )
                        val myOrthoPts = SplineMathUtils.computeOrthogonalPoints(
                            myBoardPts,
                            startHorizontal = myStartH,
                            endHorizontal = myEndH,
                            stepMode = stepMode
                        )
                        val idx = myOrthoPts.indexOfFirst { (it - juncPos).getDistance() < 1f }
                        val prevPoint = if (idx > 0) {
                            myOrthoPts[idx - 1]
                        } else if (myOrthoPts.size >= 2) {
                            myOrthoPts[myOrthoPts.size - 2]
                        } else null

                        if (prevPoint != null) {
                            val dIn = juncPos - prevPoint
                            val lenIn = dIn.getDistance()
                            if (lenIn > 0.1f) {
                                val vIn = Offset(dIn.x / lenIn, dIn.y / lenIn)
                                var hasThrough = false
                                var minBranchRadius: Float? = null
                                val rBase = maxOf(14f * scale, tension * 28f * scale)

                                for (outConn in outgoingList) {
                                    val outBoardPts = getConnectionBoardPoints(
                                        connection = outConn,
                                        getPortBoardPosition = getPortBoardPosition,
                                        junctionMap = junctionMap,
                                        groups = groups,
                                        density = density
                                    ) ?: continue
                                    if (outBoardPts.size < 2) continue

                                    val (outStartH, outEndH) = getConnectionOrientations(
                                        connection = outConn,
                                        connections = connections,
                                        junctionMap = junctionMap,
                                        getPortBoardPosition = getPortBoardPosition,
                                        groups = groups,
                                        density = density
                                    )
                                    val outOrthoPts = SplineMathUtils.computeOrthogonalPoints(
                                        outBoardPts,
                                        startHorizontal = outStartH,
                                        endHorizontal = outEndH,
                                        stepMode = stepMode
                                    )
                                    val outIdx = outOrthoPts.indexOfFirst { (it - juncPos).getDistance() < 1f }
                                    val nextPoint = if (outIdx >= 0 && outIdx < outOrthoPts.size - 1) {
                                        outOrthoPts[outIdx + 1]
                                    } else if (outOrthoPts.size >= 2) {
                                        outOrthoPts[1]
                                    } else null ?: continue

                                    val dOut = nextPoint - juncPos
                                    val lenOut = dOut.getDistance()
                                    if (lenOut > 0.1f) {
                                        val vOut = Offset(dOut.x / lenOut, dOut.y / lenOut)
                                        val dot = vIn.x * vOut.x + vIn.y * vOut.y
                                        if (dot > 0.9f) {
                                            // Through-connection: no fillet needed on incoming wire
                                            Logger.v(tag = "JunctionFillet") {
                                                "endTrim junc[$tgtJuncId]: outgoing conn[${outConn.sourceNodeId}] is through-connection (dot=$dot > 0.9), skipping trim"
                                            }
                                            hasThrough = true
                                            break
                                        } else if (abs(dot) < 0.1f) {
                                            val lenInScreen = lenIn * scale
                                            val lenOutScreen = lenOut * scale
                                            val r = minOf(rBase, lenInScreen * 0.45f, lenOutScreen * 0.45f)
                                            val minR = minOf(lenInScreen, lenOutScreen) * 0.01f
                                            if (r >= minR && r > 0f) {
                                                Logger.v(tag = "JunctionFillet") {
                                                    "endTrim junc[$tgtJuncId]: perpendicular branch (dot=$dot), r=$r accepted " +
                                                    "(rBase=$rBase, lenInScreen=$lenInScreen, lenOutScreen=$lenOutScreen)"
                                                }
                                                minBranchRadius = if (minBranchRadius == null) r else minOf(minBranchRadius, r)
                                            } else {
                                                Logger.v(tag = "JunctionFillet") {
                                                    "endTrim junc[$tgtJuncId]: perpendicular branch (dot=$dot), r=$r REJECTED " +
                                                    "(r < minR=$minR or r <= 0)"
                                                }
                                            }
                                        } else {
                                            Logger.v(tag = "JunctionFillet") {
                                                "endTrim junc[$tgtJuncId]: outgoing conn[${outConn.sourceNodeId}] neither through nor perpendicular (dot=$dot), skipping"
                                            }
                                        }
                                    }
                                }

                                if (!hasThrough && minBranchRadius != null) {
                                    endTrimDistance = minBranchRadius
                                    Logger.v(tag = "JunctionFillet") {
                                        "endTrim junc[$tgtJuncId]: final endTrimDistance=$endTrimDistance (screen px)"
                                    }
                                } else if (hasThrough) {
                                    Logger.v(tag = "JunctionFillet") {
                                        "endTrim junc[$tgtJuncId]: no trim applied (through-connection detected)"
                                    }
                                } else {
                                    Logger.v(tag = "JunctionFillet") {
                                        "endTrim junc[$tgtJuncId]: no trim applied (no valid perpendicular branch)"
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return JunctionFilletParams(startFilletLeadIn = startFilletLeadIn, endTrimDistance = endTrimDistance)
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
        stepMode: OrthogonalStepMode = OrthogonalStepMode.Auto
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
            val filletParams = getJunctionFilletParams(
                connection = connection,
                connections = connections,
                junctionMap = junctionMap,
                getPortBoardPosition = getPortBoardPosition,
                groups = groups,
                density = density,
                scale = scale,
                offset = offset,
                tension = roundness,
                stepMode = stepMode
            )
            val effectiveStyle = curveStyle
            val sampledPoints = SplineMathUtils.sampleConnectionPoints(
                points = screenPoints,
                style = effectiveStyle,
                tension = roundness,
                startHorizontal = startIsHorizontal,
                endHorizontal = endIsHorizontal,
                scale = scale,
                canvasOffset = offset,
                stepMode = stepMode,
                startFilletLeadIn = filletParams.startFilletLeadIn,
                endTrimDistance = filletParams.endTrimDistance
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
            val filletParams = getJunctionFilletParams(
                connection = connection,
                connections = connections,
                junctionMap = junctionMap,
                getPortBoardPosition = getPortBoardPosition,
                groups = groups,
                density = density,
                scale = scale,
                offset = offset,
                tension = roundness,
                stepMode = stepMode
            )
            val effectiveStyle = curveStyle
            val sampledPoints = SplineMathUtils.sampleConnectionPoints(
                points = screenPoints,
                style = effectiveStyle,
                tension = roundness,
                startHorizontal = startIsHorizontal,
                endHorizontal = endIsHorizontal,
                scale = scale,
                canvasOffset = offset,
                stepMode = stepMode,
                startFilletLeadIn = filletParams.startFilletLeadIn,
                endTrimDistance = filletParams.endTrimDistance
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
        stepMode: OrthogonalStepMode = OrthogonalStepMode.Auto
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
            val filletParams = getJunctionFilletParams(
                connection = connection,
                connections = connections,
                junctionMap = junctionMap,
                getPortBoardPosition = getPortBoardPosition,
                groups = groups,
                density = density,
                scale = scale,
                offset = offset,
                tension = roundness,
                stepMode = stepMode
            )
            val effectiveStyle = curveStyle
            val midpoints = SplineMathUtils.computeSegmentMidpoints(
                points = screenPoints,
                style = effectiveStyle,
                tension = roundness,
                startHorizontal = startIsHorizontal,
                endHorizontal = endIsHorizontal,
                scale = scale,
                canvasOffset = offset,
                stepMode = stepMode,
                startFilletLeadIn = filletParams.startFilletLeadIn,
                endTrimDistance = filletParams.endTrimDistance
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

