package org.wip.plugintoolkit.features.flows.ui

import androidx.compose.ui.geometry.Offset
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.ui.canvas.ConnectionHitTester
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class ConnectionHitTesterTest {

    @Test
    fun testFindClosestConnectionReturnsNullWhenNoConnections() {
        val result = ConnectionHitTester.findClosestConnection(
            position = Offset(100f, 100f),
            connections = emptyList(),
            getPortBoardPosition = { _, _, _ -> Offset.Zero },
            scale = 1f,
            offset = Offset.Zero
        )
        assertNull(result)
    }

    @Test
    fun testFindClosestConnectionFindsNearbyConnection() {
        val conn = Connection(1L, "out", 2L, "in")
        val getPortBoardPos: (Long, String, Boolean) -> Offset? = { nodeId, _, isOutput ->
            if (nodeId == 1L && isOutput) Offset(0f, 0f)
            else if (nodeId == 2L && !isOutput) Offset(100f, 0f)
            else null
        }

        // Point right on the straight wire between (0,0) and (100,0)
        val result = ConnectionHitTester.findClosestConnection(
            position = Offset(50f, 0f),
            connections = listOf(conn),
            getPortBoardPosition = getPortBoardPos,
            scale = 1f,
            offset = Offset.Zero
        )
        assertEquals(conn, result)
    }

    @Test
    fun testFindClosestConnectionIgnoresDistantWires() {
        val conn = Connection(1L, "out", 2L, "in")
        val getPortBoardPos: (Long, String, Boolean) -> Offset? = { nodeId, _, isOutput ->
            if (nodeId == 1L && isOutput) Offset(0f, 0f)
            else if (nodeId == 2L && !isOutput) Offset(100f, 0f)
            else null
        }

        // Point far away from wire
        val result = ConnectionHitTester.findClosestConnection(
            position = Offset(50f, 500f),
            connections = listOf(conn),
            getPortBoardPosition = getPortBoardPos,
            scale = 1f,
            offset = Offset.Zero
        )
        assertNull(result)
    }

    @Test
    fun testDetermineCloserEndReturnsSourceWhenCloser() {
        val isSource = ConnectionHitTester.determineCloserEnd(
            position = Offset(10f, 10f),
            sourceScreenPos = Offset(0f, 0f),
            targetScreenPos = Offset(100f, 100f)
        )
        assertTrue(isSource)
    }

    @Test
    fun testDetermineCloserEndReturnsTargetWhenCloser() {
        val isSource = ConnectionHitTester.determineCloserEnd(
            position = Offset(90f, 90f),
            sourceScreenPos = Offset(0f, 0f),
            targetScreenPos = Offset(100f, 100f)
        )
        assertFalse(isSource)
    }

    @Test
    fun testFindClosestJunctionFindsNearbyJunction() {
        val junctions = listOf(
            org.wip.plugintoolkit.features.flows.model.FlowJunction(101L, org.wip.plugintoolkit.features.flows.model.Offset(100f, 100f)),
            org.wip.plugintoolkit.features.flows.model.FlowJunction(102L, org.wip.plugintoolkit.features.flows.model.Offset(300f, 300f))
        )

        val hit = ConnectionHitTester.findClosestJunction(
            position = Offset(105f, 98f),
            junctions = junctions,
            scale = 1f,
            offset = Offset.Zero,
            hitRadius = 20f
        )
        assertEquals(101L, hit?.id)

        val miss = ConnectionHitTester.findClosestJunction(
            position = Offset(50f, 50f),
            junctions = junctions,
            scale = 1f,
            offset = Offset.Zero,
            hitRadius = 20f
        )
        assertNull(miss)
    }

    @Test
    fun testFindClosestConnectionWithProjection() {
        val conn = Connection(1L, "out", 2L, "in")
        val getPortBoardPos: (Long, String, Boolean) -> Offset? = { nodeId, _, isOutput ->
            if (nodeId == 1L && isOutput) Offset(0f, 0f)
            else if (nodeId == 2L && !isOutput) Offset(100f, 0f)
            else null
        }

        val result = ConnectionHitTester.findClosestConnectionWithProjection(
            position = Offset(50f, 4f),
            connections = listOf(conn),
            getPortBoardPosition = getPortBoardPos,
            scale = 1f,
            offset = Offset.Zero,
            initialMinDistance = 20f
        )
        kotlin.test.assertNotNull(result)
        assertEquals(conn, result.first)
        assertEquals(50f, result.second.x, 1f)
        assertEquals(0f, result.second.y, 1f)
    }

    @Test
    fun testFindClosestConnectionWithJunctionEndpoint() {
        // Wire from Node 1 to Junction 99
        val conn = Connection(
            sourceNodeId = 1L,
            sourcePortId = "out",
            targetNodeId = -1L,
            targetPortId = "",
            targetJunctionId = 99L
        )
        val junctions = listOf(
            org.wip.plugintoolkit.features.flows.model.FlowJunction(99L, org.wip.plugintoolkit.features.flows.model.Offset(200f, 50f))
        )
        val getPortBoardPos: (Long, String, Boolean) -> Offset? = { nodeId, _, isOutput ->
            if (nodeId == 1L && isOutput) Offset(0f, 50f)
            else null
        }

        val result = ConnectionHitTester.findClosestConnection(
            position = Offset(100f, 50f),
            connections = listOf(conn),
            getPortBoardPosition = getPortBoardPos,
            scale = 1f,
            offset = Offset.Zero,
            junctions = junctions
        )
        assertEquals(conn, result)
    }

    @Test
    fun testFindClosestConnectionWithProjectionSegmentIndex() {
        // Wire: Start (0, 0) -> Waypoint (100, 0) -> End (200, 0)
        val conn = Connection(
            sourceNodeId = 1L,
            sourcePortId = "out",
            targetNodeId = 2L,
            targetPortId = "in",
            waypoints = listOf(org.wip.plugintoolkit.features.flows.model.Offset(100f, 0f))
        )
        val getPortBoardPos: (Long, String, Boolean) -> Offset? = { nodeId, _, isOutput ->
            if (nodeId == 1L && isOutput) Offset(0f, 0f)
            else if (nodeId == 2L && !isOutput) Offset(200f, 0f)
            else null
        }

        // Test click in first half (near 40, 2) -> should be segment 0
        val res0 = ConnectionHitTester.findClosestConnectionWithProjection(
            position = Offset(40f, 2f),
            connections = listOf(conn),
            getPortBoardPosition = getPortBoardPos,
            scale = 1f,
            offset = Offset.Zero
        )
        kotlin.test.assertNotNull(res0)
        assertEquals(0, res0.segmentIndex)

        // Test click in second half (near 160, 2) -> should be segment 1
        val res1 = ConnectionHitTester.findClosestConnectionWithProjection(
            position = Offset(160f, 2f),
            connections = listOf(conn),
            getPortBoardPosition = getPortBoardPos,
            scale = 1f,
            offset = Offset.Zero
        )
        kotlin.test.assertNotNull(res1)
        assertEquals(1, res1.segmentIndex)
    }

    @Test
    fun testVerticalJunctionFeedYieldsVerticalStartAndSingleBendVhCurve() {
        // Vertical trunk wire coming down from top bar (60, 50) to Junction 100 at (60, 115)
        val incomingConn = Connection(
            sourceNodeId = Connection.FLOATING_NODE_ID,
            sourcePortId = Connection.FLOATING_PORT_ID,
            sourceJunctionId = 10L, // Top bar junction at (60, 50)
            targetNodeId = Connection.FLOATING_NODE_ID,
            targetPortId = Connection.FLOATING_PORT_ID,
            targetJunctionId = 100L // Junction 2 at (60, 115)
        )

        // Outgoing connection leaving Junction 100 to target node at (200, 850)
        val outgoingConn = Connection(
            sourceNodeId = Connection.FLOATING_NODE_ID,
            sourcePortId = Connection.FLOATING_PORT_ID,
            sourceJunctionId = 100L,
            targetNodeId = 2L,
            targetPortId = "in"
        )

        val juncMap = mapOf(
            10L to Offset(60f, 50f),
            100L to Offset(60f, 115f)
        )
        val getPortPos: (Long, String, Boolean) -> Offset? = { nodeId, _, isOutput ->
            if (nodeId == 2L && !isOutput) Offset(200f, 850f) else null
        }

        val (startH, endH) = ConnectionHitTester.getConnectionOrientations(
            connection = outgoingConn,
            connections = listOf(incomingConn, outgoingConn),
            junctionMap = juncMap,
            getPortBoardPosition = getPortPos
        )

        // Because incoming wire is vertical moving DOWN and target is ahead (y=850 > y=115),
        // departure preserves vertical tangent (startIsHorizontal = false)
        assertFalse(startH, "Outgoing connection must exit vertically downwards through the junction")
        assertTrue(endH, "Target node input must receive connection horizontally")

        // In Auto orthogonal step mode, (!startH && endH) forms the 1-bend (60, 115) -> (60, 850) -> (200, 850) path (Yellow line)
        val orthoPoints = org.wip.plugintoolkit.features.flows.utils.SplineMathUtils.computeOrthogonalPoints(
            listOf(Offset(60f, 115f), Offset(200f, 850f)),
            startHorizontal = startH,
            endHorizontal = endH,
            stepMode = org.wip.plugintoolkit.features.settings.model.OrthogonalStepMode.Auto
        )
        assertEquals(3, orthoPoints.size, "Must only have 3 points (1 single corner at (60, 850))")
        assertEquals(Offset(60f, 115f), orthoPoints[0])
        assertEquals(Offset(60f, 850f), orthoPoints[1])
        assertEquals(Offset(200f, 850f), orthoPoints[2])
    }

    @Test
    fun testHorizontalJunctionFeedYieldsHorizontalContinuity() {
        // Horizontal trunk wire coming from left (100, 100) to Junction 100 at (300, 100)
        val incomingConn = Connection(
            sourceNodeId = 1L,
            sourcePortId = "out",
            targetNodeId = Connection.FLOATING_NODE_ID,
            targetPortId = Connection.FLOATING_PORT_ID,
            targetJunctionId = 100L
        )

        // Outgoing connection continuing right to Node 2 at (500, 100)
        val outgoingConn = Connection(
            sourceNodeId = Connection.FLOATING_NODE_ID,
            sourcePortId = Connection.FLOATING_PORT_ID,
            sourceJunctionId = 100L,
            targetNodeId = 2L,
            targetPortId = "in"
        )

        val juncMap = mapOf(100L to Offset(300f, 100f))
        val getPortPos: (Long, String, Boolean) -> Offset? = { nodeId, _, isOutput ->
            when {
                nodeId == 1L && isOutput -> Offset(100f, 100f)
                nodeId == 2L && !isOutput -> Offset(500f, 100f)
                else -> null
            }
        }

        val (startH, endH) = ConnectionHitTester.getConnectionOrientations(
            connection = outgoingConn,
            connections = listOf(incomingConn, outgoingConn),
            junctionMap = juncMap,
            getPortBoardPosition = getPortPos
        )

        assertTrue(startH, "Outgoing connection must exit horizontally through horizontal junction")
        assertTrue(endH, "Target node input must receive connection horizontally")
    }

    @Test
    fun testHorizontalJunctionContinuationDoesNotSwitchAxisForVerticalTarget() {
        val incomingConn = Connection(
            sourceNodeId = 1L,
            sourcePortId = "out",
            targetNodeId = Connection.FLOATING_NODE_ID,
            targetPortId = Connection.FLOATING_PORT_ID,
            targetJunctionId = 225L
        )
        val outgoingConn = Connection(
            sourceNodeId = Connection.FLOATING_NODE_ID,
            sourcePortId = Connection.FLOATING_PORT_ID,
            sourceJunctionId = 225L,
            targetNodeId = Connection.FLOATING_NODE_ID,
            targetPortId = Connection.FLOATING_PORT_ID,
            targetJunctionId = 228L
        )

        val juncMap = mapOf(
            225L to Offset(2450f, 1750f),
            228L to Offset(3200f, 50f)
        )
        val getPortPos: (Long, String, Boolean) -> Offset? = { nodeId, _, isOutput ->
            if (nodeId == 1L && isOutput) Offset(330f, 1750f) else null
        }

        val (startH, endH) = ConnectionHitTester.getConnectionOrientations(
            connection = outgoingConn,
            connections = listOf(incomingConn, outgoingConn),
            junctionMap = juncMap,
            getPortBoardPosition = getPortPos
        )

        assertTrue(startH, "Outgoing connection must inherit horizontal arrival at junction 225")
        assertFalse(endH, "Junction-to-junction target should retain its vertical arrival axis")

        val orthoPoints = org.wip.plugintoolkit.features.flows.utils.SplineMathUtils.computeOrthogonalPoints(
            listOf(juncMap.getValue(225L), juncMap.getValue(228L)),
            startHorizontal = startH,
            endHorizontal = endH,
            stepMode = org.wip.plugintoolkit.features.settings.model.OrthogonalStepMode.Auto,
            useMiddleRouteForDirectConnection = false
        )
        assertEquals(
            listOf(
                Offset(2450f, 1750f),
                Offset(3200f, 1750f),
                Offset(3200f, 50f)
            ),
            orthoPoints
        )
    }

    @Test
    fun testDiagonalNodeToJunctionMaintainsHorizontalArrivalAndFillet() {
        // Reproduce the permanently angular connection bug:
        // - Source node output port at (100, 350)
        // - Target junction at (250, 100) — diagonally placed: abs(dy=250) > abs(dx=150)
        // - Without the fix, abs(dx) < abs(dy) would have set endIsHorizontal=false,
        //   causing the wire to enter the junction vertically and collide with the outgoing
        //   vertical branch, preventing any fillet from being calculated.
        val incomingConn = Connection(
            sourceNodeId = 1L,
            sourcePortId = "out",
            targetNodeId = org.wip.plugintoolkit.features.flows.model.Connection.FLOATING_NODE_ID,
            targetPortId = org.wip.plugintoolkit.features.flows.model.Connection.FLOATING_PORT_ID,
            targetJunctionId = 100L
        )
        // Outgoing branch leaves junction vertically upward to node 2 at (250, 0)
        val outgoingConn = Connection(
            sourceNodeId = org.wip.plugintoolkit.features.flows.model.Connection.FLOATING_NODE_ID,
            sourcePortId = org.wip.plugintoolkit.features.flows.model.Connection.FLOATING_PORT_ID,
            sourceJunctionId = 100L,
            targetNodeId = 2L,
            targetPortId = "in"
        )

        val juncMap = mapOf(100L to Offset(250f, 100f))
        val getPortPos: (Long, String, Boolean) -> Offset? = { nodeId, _, isOutput ->
            when {
                nodeId == 1L && isOutput -> Offset(100f, 350f)   // diagonal: dy=250 > dx=150
                nodeId == 2L && !isOutput -> Offset(600f, 100f)  // outgoing goes horizontally right
                else -> null
            }
        }

        // The incoming connection should be classified as arriving HORIZONTALLY regardless of diagonal
        val (inStartH, inEndH) = ConnectionHitTester.getConnectionOrientations(
            connection = incomingConn,
            connections = listOf(incomingConn, outgoingConn),
            junctionMap = juncMap,
            getPortBoardPosition = getPortPos
        )
        assertTrue(inStartH, "Node output port always starts horizontally")
        assertTrue(inEndH, "Node->junction without waypoints must always arrive horizontally, regardless of diagonal placement")

        // The outgoing connection should branch off horizontally (since inEndH is true and target is to the right)
        val (outStartH, outEndH) = ConnectionHitTester.getConnectionOrientations(
            connection = outgoingConn,
            connections = listOf(incomingConn, outgoingConn),
            junctionMap = juncMap,
            getPortBoardPosition = getPortPos
        )
        assertTrue(outStartH, "Outgoing branch should continue or branch horizontally")
        assertTrue(outEndH, "Target node input must receive connection horizontally")

        // Fillet params for the outgoing branch should now receive a valid startFilletLeadIn
        val filletParams = ConnectionHitTester.getJunctionFilletParams(
            connection = outgoingConn,
            connections = listOf(incomingConn, outgoingConn),
            junctionMap = juncMap,
            getPortBoardPosition = getPortPos,
            scale = 1f
        )
        // With horizontal arrival corrected, the dot product will be perpendicular (not collinear),
        // so startFilletLeadIn must be non-null
        assertTrue(
            filletParams.startFilletLeadIn != null || filletParams.endTrimDistance > 0f,
            "With correct horizontal arrival, at least start fillet or end trim must be non-zero for the outgoing branch"
        )
    }

    @Test
    fun testLongSegmentJunctionAlwaysGetsEndTrim() {
        // Regression test for the position-dependent sharp corner bug.
        // Junction 212 sits at the end of a very long horizontal wire (3900 board units)
        // and branches vertically to a distant junction 213 (1500 board units first segment).
        //
        // Old bug: minR = minOf(lenInScreen, lenOutScreen) * 0.01f = 1500*1*0.01 = 15
        //          rBase = 14*1 = 14 → r=14 < minR=15 → rejected at ALL zoom levels.
        //
        // Fix: minR = 1f (fixed floor) → r=14 >= 1 → always accepted.
        val incoming = Connection(
            sourceJunctionId = 193L,
            sourceNodeId = Connection.FLOATING_NODE_ID,
            sourcePortId = Connection.FLOATING_PORT_ID,
            targetNodeId = Connection.FLOATING_NODE_ID,
            targetPortId = Connection.FLOATING_PORT_ID,
            targetJunctionId = 212L
        )
        val outgoing = Connection(
            sourceJunctionId = 212L,
            sourceNodeId = Connection.FLOATING_NODE_ID,
            sourcePortId = Connection.FLOATING_PORT_ID,
            targetNodeId = Connection.FLOATING_NODE_ID,
            targetPortId = Connection.FLOATING_PORT_ID,
            targetJunctionId = 213L
        )

        // Junction 193 at (450, 1850), 212 at (4350, 1850), 213 at (4350, -1150)
        // → incoming horizontal: 3900 board units
        // → outgoing vertical first segment: 1500 board units (to midpoint at y=350)
        val juncMap = mapOf(
            193L to Offset(450f, 1850f),
            212L to Offset(4350f, 1850f),
            213L to Offset(4300f, -1150f)
        )
        val getPortPos: (Long, String, Boolean) -> Offset? = { _, _, _ -> null }

        // scale=1 to match log conditions
        val filletParams = ConnectionHitTester.getJunctionFilletParams(
            connection = incoming,
            connections = listOf(incoming, outgoing),
            junctionMap = juncMap,
            getPortBoardPosition = getPortPos,
            scale = 1f
        )

        assertTrue(
            filletParams.endTrimDistance > 0f,
            "Perpendicular junction with long segments (lenIn=3900, lenOut=1500) must always " +
            "produce a non-zero endTrimDistance (old minR formula = 15 > rBase = 14 caused rejection)"
        )
    }
}
