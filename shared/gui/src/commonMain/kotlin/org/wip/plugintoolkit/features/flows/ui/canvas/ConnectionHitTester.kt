package org.wip.plugintoolkit.features.flows.ui.canvas

import androidx.compose.ui.geometry.Offset
import co.touchlab.kermit.Logger
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.FlowGroup
import org.wip.plugintoolkit.features.flows.model.FlowJunction
import org.wip.plugintoolkit.features.flows.model.Node
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

    fun usesMiddleRouteForDirectConnection(connection: Connection, orthogonalPortLead: Boolean = false): Boolean =
        !orthogonalPortLead &&
                connection.sourceJunctionId == null &&
                connection.targetJunctionId == null &&
                connection.junctionIds.isEmpty() &&
                connection.waypoints.isEmpty()

    fun hasStartPortLead(connection: Connection, portLeadEnabled: Boolean): Boolean =
        portLeadEnabled && connection.sourceJunctionId == null && connection.sourceNodeId != Connection.FLOATING_NODE_ID

    fun hasEndPortLead(connection: Connection, portLeadEnabled: Boolean): Boolean =
        portLeadEnabled && connection.targetJunctionId == null && !connection.isFloating && connection.targetNodeId != Connection.FLOATING_NODE_ID

    fun getSourceNodeBorderX(
        connection: Connection,
        nodes: List<Node>,
        defaultNodeWidth: Float = 400f
    ): Float? {
        if (connection.sourceJunctionId != null || connection.sourceNodeId == Connection.FLOATING_NODE_ID) return null
        val node = nodes.find { it.id == connection.sourceNodeId } ?: return null
        return node.position.x + defaultNodeWidth
    }

    fun getTargetNodeBorderX(
        connection: Connection,
        nodes: List<Node>
    ): Float? {
        if (connection.targetJunctionId != null || connection.isFloating || connection.targetNodeId == Connection.FLOATING_NODE_ID) return null
        val node = nodes.find { it.id == connection.targetNodeId } ?: return null
        return node.position.x
    }

    private fun findJunctionLocalPrevPoint(path: List<Offset>, junction: Offset, epsilon: Float = 4f): Offset? {
        if (path.size < 2) return null
        val idx = path.indexOfFirst { (it - junction).getDistance() < epsilon }
        return when {
            idx > 0 -> path[idx - 1]
            idx == 0 -> null
            else -> path[path.size - 2]
        }
    }

    fun getConnectionOrientations(
        connection: Connection,
        connections: List<Connection> = emptyList(),
        junctionMap: Map<Long, Offset> = emptyMap(),
        getPortBoardPosition: ((Long, String, Boolean) -> Offset?)? = null,
        groups: List<FlowGroup> = emptyList(),
        density: Float = 1f,
        stepMode: OrthogonalStepMode = OrthogonalStepMode.Auto,
        orthogonalPortLead: Boolean = false,
        nodes: List<Node> = emptyList()
    ): Pair<Boolean, Boolean> = determineConnectionOrientations(
        connection = connection,
        connections = connections,
        junctionMap = junctionMap,
        getPortBoardPosition = getPortBoardPosition,
        groups = groups,
        density = density,
        stepMode = stepMode,
        orthogonalPortLead = orthogonalPortLead,
        nodes = nodes,
        visited = emptySet()
    )

    private fun determineConnectionOrientations(
        connection: Connection,
        connections: List<Connection>,
        junctionMap: Map<Long, Offset>,
        getPortBoardPosition: ((Long, String, Boolean) -> Offset?)?,
        groups: List<FlowGroup>,
        density: Float,
        stepMode: OrthogonalStepMode,
        orthogonalPortLead: Boolean,
        nodes: List<Node>,
        visited: Set<Connection>
    ): Pair<Boolean, Boolean> {
        val connId = "conn[${connection.sourceNodeId}:${connection.sourcePortId}->${connection.targetNodeId}:${connection.targetPortId}]"
        if (connection in visited) {
            return Pair(true, true)
        }
        val currentVisited = visited + connection

        // 1. Determine start orientation (how this connection leaves its source):
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
                val inBoardPts = if (getPortBoardPosition != null) {
                    getConnectionBoardPoints(
                        connection = incoming,
                        getPortBoardPosition = getPortBoardPosition,
                        junctionMap = junctionMap,
                        groups = groups,
                        density = density
                    )
                } else null

                val (inStartH, inEndH) = determineConnectionOrientations(
                    connection = incoming,
                    connections = connections,
                    junctionMap = junctionMap,
                    getPortBoardPosition = getPortBoardPosition,
                    groups = groups,
                    density = density,
                    stepMode = stepMode,
                    orthogonalPortLead = orthogonalPortLead,
                    nodes = nodes,
                    visited = currentVisited
                )

                val inOrthoPts = if (inBoardPts != null && inBoardPts.size >= 2) {
                    val inStartBorderX = getSourceNodeBorderX(incoming, nodes)
                    val inEndBorderX = getTargetNodeBorderX(incoming, nodes)
                    SplineMathUtils.computeOrthogonalPoints(
                        inBoardPts,
                        startHorizontal = inStartH,
                        endHorizontal = inEndH,
                        stepMode = stepMode,
                        useMiddleRouteForDirectConnection = usesMiddleRouteForDirectConnection(incoming, orthogonalPortLead),
                        startPortLead = hasStartPortLead(incoming, orthogonalPortLead),
                        endPortLead = hasEndPortLead(incoming, orthogonalPortLead),
                        startBorderX = inStartBorderX,
                        endBorderX = inEndBorderX
                    )
                } else null

                val inPrevPoint = if (inOrthoPts != null && inOrthoPts.size >= 2) {
                    findJunctionLocalPrevPoint(inOrthoPts, juncPos)
                } else if (inBoardPts != null && inBoardPts.size >= 2) {
                    findJunctionLocalPrevPoint(inBoardPts, juncPos)
                } else if (incoming.sourceJunctionId != null) {
                    junctionMap[incoming.sourceJunctionId]
                } else {
                    null
                }

                val dxIn = if (inPrevPoint != null) juncPos.x - inPrevPoint.x else 1f
                val dyIn = if (inPrevPoint != null) juncPos.y - inPrevPoint.y else 0f
                val incomingAxisH = if (abs(dxIn) > 0.01f || abs(dyIn) > 0.01f) {
                    abs(dxIn) >= abs(dyIn)
                } else {
                    inEndH
                }
                val incomingDx = if (abs(dxIn) > 0.01f || abs(dyIn) > 0.01f) dxIn else (if (incomingAxisH) 1f else 0f)
                val incomingDy = if (abs(dxIn) > 0.01f || abs(dyIn) > 0.01f) dyIn else (if (incomingAxisH) 0f else 1f)

                val floatingTarget = connection.floatingTarget
                val targetPos = when {
                    connection.targetJunctionId != null -> junctionMap[connection.targetJunctionId]
                    floatingTarget != null -> floatingTarget.toComposeOffset()
                    getPortBoardPosition != null ->
                        getPortBoardPosition(connection.targetNodeId, connection.targetPortId, false)
                    else -> null
                }
                val targetDx = targetPos?.x?.minus(juncPos.x) ?: 0f
                val targetDy = targetPos?.y?.minus(juncPos.y) ?: 0f

                val continuesForward = if (incomingAxisH) {
                    (targetDx > SplineMathUtils.ORTHOGONAL_ALIGNMENT_TOLERANCE && incomingDx >= 0f) ||
                        (targetDx < -SplineMathUtils.ORTHOGONAL_ALIGNMENT_TOLERANCE && incomingDx < 0f)
                } else {
                    (targetDy > SplineMathUtils.ORTHOGONAL_ALIGNMENT_TOLERANCE && incomingDy >= 0f) ||
                        (targetDy < -SplineMathUtils.ORTHOGONAL_ALIGNMENT_TOLERANCE && incomingDy < 0f)
                }
                val result = if (continuesForward) incomingAxisH else !incomingAxisH
                Logger.v(tag = "ConnectionOrientations") {
                    "$connId: startIsHorizontal=$result (incomingAxis=$incomingAxisH, " +
                        "incomingDx=$incomingDx, incomingDy=$incomingDy, targetDx=$targetDx, targetDy=$targetDy, continuesForward=$continuesForward)"
                }
                result
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

        // 2. Determine end orientation (how this connection arrives at its target):
        val endIsHorizontal = if (connection.targetJunctionId == null && !connection.isFloating) {
            // Node input ports always receive connections horizontally from the left
            Logger.v(tag = "ConnectionOrientations") { "$connId: endIsHorizontal=true (target is a node input port, always horizontal)" }
            true
        } else if (connection.targetJunctionId != null) {
            val tgtJuncPos = junctionMap[connection.targetJunctionId]
            if (tgtJuncPos != null) {
                val myBoardPts = if (getPortBoardPosition != null) {
                    getConnectionBoardPoints(
                        connection = connection,
                        getPortBoardPosition = getPortBoardPosition,
                        junctionMap = junctionMap,
                        groups = groups,
                        density = density
                    )
                } else null

                if (myBoardPts != null && myBoardPts.size == 2) {
                    val p0 = myBoardPts[0]
                    val dx = tgtJuncPos.x - p0.x
                    val dy = tgtJuncPos.y - p0.y
                    if (abs(dy) < SplineMathUtils.ORTHOGONAL_ALIGNMENT_TOLERANCE) {
                        true
                    } else if (abs(dx) < SplineMathUtils.ORTHOGONAL_ALIGNMENT_TOLERANCE) {
                        false
                    } else {
                        !startIsHorizontal
                    }
                } else if (myBoardPts != null && myBoardPts.size > 2) {
                    val prevPoint = myBoardPts[myBoardPts.size - 2]
                    val dx = tgtJuncPos.x - prevPoint.x
                    val dy = tgtJuncPos.y - prevPoint.y
                    if (abs(dy) < SplineMathUtils.ORTHOGONAL_ALIGNMENT_TOLERANCE) true
                    else if (abs(dx) < SplineMathUtils.ORTHOGONAL_ALIGNMENT_TOLERANCE) false
                    else abs(dx) >= abs(dy)
                } else if (connection.sourceJunctionId != null && junctionMap[connection.sourceJunctionId] != null) {
                    val p0 = junctionMap[connection.sourceJunctionId]!!
                    val dx = tgtJuncPos.x - p0.x
                    val dy = tgtJuncPos.y - p0.y
                    if (abs(dy) < SplineMathUtils.ORTHOGONAL_ALIGNMENT_TOLERANCE) true
                    else if (abs(dx) < SplineMathUtils.ORTHOGONAL_ALIGNMENT_TOLERANCE) false
                    else !startIsHorizontal
                } else {
                    true
                }
            } else {
                true
            }
        } else if (connection.isFloating && connection.floatingTarget != null) {
            val floatingTargetPos = connection.floatingTarget!!.toComposeOffset()
            val myBoardPts = if (getPortBoardPosition != null) {
                getConnectionBoardPoints(
                    connection = connection,
                    getPortBoardPosition = getPortBoardPosition,
                    junctionMap = junctionMap,
                    groups = groups,
                    density = density
                )
            } else null

            val myOrthoPts = if (myBoardPts != null && myBoardPts.size >= 2) {
                val myStartBorderX = getSourceNodeBorderX(connection, nodes)
                val myEndBorderX = getTargetNodeBorderX(connection, nodes)
                SplineMathUtils.computeOrthogonalPoints(
                    myBoardPts,
                    startHorizontal = startIsHorizontal,
                    endHorizontal = true,
                    stepMode = stepMode,
                    useMiddleRouteForDirectConnection = usesMiddleRouteForDirectConnection(connection, orthogonalPortLead),
                    startPortLead = hasStartPortLead(connection, orthogonalPortLead),
                    endPortLead = hasEndPortLead(connection, orthogonalPortLead),
                    startBorderX = myStartBorderX,
                    endBorderX = myEndBorderX
                )
            } else null

            val prevPoint = if (myOrthoPts != null && myOrthoPts.size >= 2) {
                myOrthoPts[myOrthoPts.size - 2]
            } else null

            if (prevPoint != null) {
                val dx = floatingTargetPos.x - prevPoint.x
                val dy = floatingTargetPos.y - prevPoint.y
                abs(dx) >= abs(dy)
            } else {
                true
            }
        } else {
            Logger.v(tag = "ConnectionOrientations") { "$connId: endIsHorizontal=true (floating/default)" }
            true
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
        stepMode: OrthogonalStepMode = OrthogonalStepMode.Auto,
        orthogonalPortLead: Boolean = false,
        nodes: List<Node> = emptyList()
    ): JunctionFilletParams {
        var startFilletLeadIn: Offset? = null
        var endTrimDistance = 0f

        // 1. Start fillet for connection originating from a junction
        val srcJuncId = connection.sourceJunctionId
        if (srcJuncId != null) {
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
                            density = density,
                            stepMode = stepMode,
                            orthogonalPortLead = orthogonalPortLead,
                            nodes = nodes
                        )
                        val inOrthoPts = SplineMathUtils.computeOrthogonalPoints(
                            inBoardPts,
                            startHorizontal = inStartH,
                            endHorizontal = inEndH,
                            stepMode = stepMode,
                            useMiddleRouteForDirectConnection = usesMiddleRouteForDirectConnection(incoming, orthogonalPortLead),
                            startPortLead = hasStartPortLead(incoming, orthogonalPortLead),
                            endPortLead = hasEndPortLead(incoming, orthogonalPortLead),
                            startBorderX = getSourceNodeBorderX(incoming, nodes),
                            endBorderX = getTargetNodeBorderX(incoming, nodes)
                        )
                        val prevPoint = findJunctionLocalPrevPoint(inOrthoPts, juncPos)

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
        if (tgtJuncId != null) {
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
                            density = density,
                            stepMode = stepMode,
                            orthogonalPortLead = orthogonalPortLead,
                            nodes = nodes
                        )
                        val myOrthoPts = SplineMathUtils.computeOrthogonalPoints(
                            myBoardPts,
                            startHorizontal = myStartH,
                            endHorizontal = myEndH,
                            stepMode = stepMode,
                            useMiddleRouteForDirectConnection = usesMiddleRouteForDirectConnection(connection, orthogonalPortLead),
                            startPortLead = hasStartPortLead(connection, orthogonalPortLead),
                            endPortLead = hasEndPortLead(connection, orthogonalPortLead),
                            startBorderX = getSourceNodeBorderX(connection, nodes),
                            endBorderX = getTargetNodeBorderX(connection, nodes)
                        )
                        val prevPoint = findJunctionLocalPrevPoint(myOrthoPts, juncPos)

                        if (prevPoint != null) {
                            val dIn = juncPos - prevPoint
                            val lenIn = dIn.getDistance()
                            if (lenIn > 0.1f) {
                                val vIn = Offset(dIn.x / lenIn, dIn.y / lenIn)
                                var hasThrough = false
                                var minBranchRadius: Float? = null
                                val rBase = maxOf(
                                    SplineMathUtils.DEFAULT_CORNER_RADIUS * scale,
                                    tension * SplineMathUtils.TENSION_RADIUS_FACTOR * scale,
                                    SplineMathUtils.MIN_RENDER_CORNER_RADIUS
                                )

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
                                        density = density,
                                        stepMode = stepMode,
                                        orthogonalPortLead = orthogonalPortLead,
                                        nodes = nodes
                                    )
                                    val outOrthoPts = SplineMathUtils.computeOrthogonalPoints(
                                        outBoardPts,
                                        startHorizontal = outStartH,
                                        endHorizontal = outEndH,
                                        stepMode = stepMode,
                                        useMiddleRouteForDirectConnection = usesMiddleRouteForDirectConnection(outConn, orthogonalPortLead),
                                        startPortLead = hasStartPortLead(outConn, orthogonalPortLead),
                                        endPortLead = hasEndPortLead(outConn, orthogonalPortLead),
                                        startBorderX = getSourceNodeBorderX(outConn, nodes),
                                        endBorderX = getTargetNodeBorderX(outConn, nodes)
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
                                            val r = minOf(rBase, lenInScreen * SplineMathUtils.TRIM_FACTOR, lenOutScreen * SplineMathUtils.TRIM_FACTOR)
                                            val minR = 0.01f
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
        stepMode: OrthogonalStepMode = OrthogonalStepMode.Auto,
        orthogonalPortLead: Boolean = false,
        nodes: List<Node> = emptyList()
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
                density = density,
                stepMode = stepMode,
                orthogonalPortLead = orthogonalPortLead,
                nodes = nodes
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
                stepMode = stepMode,
                orthogonalPortLead = orthogonalPortLead,
                nodes = nodes
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
                endTrimDistance = filletParams.endTrimDistance,
                useMiddleRouteForDirectConnection = usesMiddleRouteForDirectConnection(connection, orthogonalPortLead),
                startPortLead = hasStartPortLead(connection, orthogonalPortLead),
                endPortLead = hasEndPortLead(connection, orthogonalPortLead),
                startBorderX = getSourceNodeBorderX(connection, nodes),
                endBorderX = getTargetNodeBorderX(connection, nodes)
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
        stepMode: OrthogonalStepMode = OrthogonalStepMode.Middle,
        orthogonalPortLead: Boolean = false,
        nodes: List<Node> = emptyList()
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
                density = density,
                stepMode = stepMode,
                orthogonalPortLead = orthogonalPortLead,
                nodes = nodes
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
                stepMode = stepMode,
                orthogonalPortLead = orthogonalPortLead,
                nodes = nodes
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
                endTrimDistance = filletParams.endTrimDistance,
                useMiddleRouteForDirectConnection = usesMiddleRouteForDirectConnection(connection, orthogonalPortLead),
                startPortLead = hasStartPortLead(connection, orthogonalPortLead),
                endPortLead = hasEndPortLead(connection, orthogonalPortLead),
                startBorderX = getSourceNodeBorderX(connection, nodes),
                endBorderX = getTargetNodeBorderX(connection, nodes)
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
        getPortBoardPosition: ((Long, String, Boolean) -> Offset?)? = null,
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
            else -> getPortBoardPosition?.invoke(connection.sourceNodeId, connection.sourcePortId, true)
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
            else -> getPortBoardPosition?.invoke(connection.targetNodeId, connection.targetPortId, false)
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
        stepMode: OrthogonalStepMode = OrthogonalStepMode.Auto,
        orthogonalPortLead: Boolean = false,
        nodes: List<Node> = emptyList()
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
                density = density,
                stepMode = stepMode,
                orthogonalPortLead = orthogonalPortLead,
                nodes = nodes
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
                stepMode = stepMode,
                orthogonalPortLead = orthogonalPortLead,
                nodes = nodes
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
                endTrimDistance = filletParams.endTrimDistance,
                useMiddleRouteForDirectConnection = usesMiddleRouteForDirectConnection(connection, orthogonalPortLead),
                startPortLead = hasStartPortLead(connection, orthogonalPortLead),
                endPortLead = hasEndPortLead(connection, orthogonalPortLead),
                startBorderX = getSourceNodeBorderX(connection, nodes),
                endBorderX = getTargetNodeBorderX(connection, nodes)
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
