package org.wip.plugintoolkit.features.flows

import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.FlowGroup
import org.wip.plugintoolkit.features.flows.model.FlowJunction
import org.wip.plugintoolkit.features.flows.model.FlowLabel
import org.wip.plugintoolkit.features.flows.model.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FlowJunctionResolutionTest {

    @Test
    fun testDirectConnectionsIdentity() {
        val conn1 = Connection(sourceNodeId = 1L, sourcePortId = "out", targetNodeId = 2L, targetPortId = "in")
        val conn2 = Connection(sourceNodeId = 2L, sourcePortId = "out", targetNodeId = 3L, targetPortId = "in")
        val flow = Flow(
            name = "direct_flow",
            connections = listOf(conn1, conn2)
        )

        val effective = flow.getEffectiveConnections()
        assertEquals(2, effective.size)
        assertEquals(conn1, effective[0])
        assertEquals(conn2, effective[1])
    }

    @Test
    fun testFloatingConnectionsAreExcluded() {
        val direct = Connection(sourceNodeId = 1L, sourcePortId = "out", targetNodeId = 2L, targetPortId = "in")
        val floating1 = Connection.createFloating(
            sourceNodeId = 1L,
            sourcePortId = "out",
            floatingTarget = Offset(300f, 150f)
        )
        val floating2 = Connection(
            sourceNodeId = 2L,
            sourcePortId = "out",
            targetNodeId = Connection.FLOATING_NODE_ID,
            targetPortId = Connection.FLOATING_PORT_ID
        )

        assertTrue(floating1.isFloating)
        assertTrue(floating2.isFloating)
        assertFalse(direct.isFloating)

        val flow = Flow(
            name = "flow_with_floating",
            connections = listOf(direct, floating1, floating2)
        )

        val effective = flow.getEffectiveConnections()
        assertEquals(1, effective.size)
        assertEquals(direct, effective[0])
    }

    @Test
    fun testSemanticConnectionsWithJunctionIds() {
        val junction1 = FlowJunction(id = 100L, position = Offset(200f, 100f), color = "#FF0000")
        val branch1 = Connection(
            sourceNodeId = 1L,
            sourcePortId = "out",
            targetNodeId = 2L,
            targetPortId = "in",
            junctionIds = listOf(100L),
            color = "#FF0000"
        )
        val branch2 = Connection(
            sourceNodeId = 1L,
            sourcePortId = "out",
            targetNodeId = 3L,
            targetPortId = "in",
            junctionIds = listOf(100L),
            color = "#FF0000"
        )

        val flow = Flow(
            name = "junction_branched_flow",
            junctions = listOf(junction1),
            connections = listOf(branch1, branch2)
        )

        val effective = flow.getEffectiveConnections()
        assertEquals(2, effective.size)
        assertEquals(branch1, effective[0])
        assertEquals(branch2, effective[1])
    }

    @Test
    fun testSegmentedJunctionPathResolution() {
        // Node 1 ("out") -> Junction 100
        val segmentToJunction = Connection(
            sourceNodeId = 1L,
            sourcePortId = "out",
            targetNodeId = -1L,
            targetPortId = "",
            targetJunctionId = 100L
        )

        // Junction 100 -> Node 2 ("in")
        val branchA = Connection(
            sourceNodeId = -1L,
            sourcePortId = "",
            sourceJunctionId = 100L,
            targetNodeId = 2L,
            targetPortId = "in",
            orderIndex = 0
        )

        // Junction 100 -> Node 3 ("in")
        val branchB = Connection(
            sourceNodeId = -1L,
            sourcePortId = "",
            sourceJunctionId = 100L,
            targetNodeId = 3L,
            targetPortId = "in",
            orderIndex = 1
        )

        val flow = Flow(
            name = "segmented_junction_flow",
            junctions = listOf(FlowJunction(100L, Offset(100f, 100f))),
            connections = listOf(segmentToJunction, branchA, branchB)
        )

        val effective = flow.getEffectiveConnections()
        assertEquals(2, effective.size)

        val toNode2 = effective.find { it.targetNodeId == 2L }
        val toNode3 = effective.find { it.targetNodeId == 3L }

        assertEquals(1L, toNode2?.sourceNodeId)
        assertEquals("out", toNode2?.sourcePortId)
        assertEquals("in", toNode2?.targetPortId)
        assertEquals(listOf(100L), toNode2?.junctionIds)

        assertEquals(1L, toNode3?.sourceNodeId)
        assertEquals("out", toNode3?.sourcePortId)
        assertEquals("in", toNode3?.targetPortId)
        assertEquals(listOf(100L), toNode3?.junctionIds)
    }

    @Test
    fun testChainedSegmentedJunctionResolution() {
        // Node 1 ("out") -> J1 -> J2 -> Node 2 ("in")
        val inToJ1 = Connection(
            sourceNodeId = 1L,
            sourcePortId = "out",
            targetNodeId = -1L,
            targetPortId = "",
            targetJunctionId = 101L,
            color = "#00FF00"
        )
        val j1ToJ2 = Connection(
            sourceNodeId = -1L,
            sourcePortId = "",
            sourceJunctionId = 101L,
            targetNodeId = -1L,
            targetPortId = "",
            targetJunctionId = 102L
        )
        val j2ToNode2 = Connection(
            sourceNodeId = -1L,
            sourcePortId = "",
            sourceJunctionId = 102L,
            targetNodeId = 2L,
            targetPortId = "in"
        )

        val flow = Flow(
            name = "chained_flow",
            junctions = listOf(
                FlowJunction(101L, Offset(100f, 100f)),
                FlowJunction(102L, Offset(200f, 200f))
            ),
            connections = listOf(inToJ1, j1ToJ2, j2ToNode2)
        )

        val effective = flow.getEffectiveConnections()
        assertEquals(1, effective.size)
        val conn = effective[0]
        assertEquals(1L, conn.sourceNodeId)
        assertEquals("out", conn.sourcePortId)
        assertEquals(2L, conn.targetNodeId)
        assertEquals("in", conn.targetPortId)
        assertEquals(listOf(101L, 102L), conn.junctionIds)
        assertEquals("#00FF00", conn.color)
    }

    @Test
    fun testJunctionCycleSafety() {
        // Loop: J1 -> J2 -> J1
        val inToJ1 = Connection(
            sourceNodeId = 1L,
            sourcePortId = "out",
            targetNodeId = -1L,
            targetPortId = "",
            targetJunctionId = 201L
        )
        val j1ToJ2 = Connection(
            sourceNodeId = -1L,
            sourcePortId = "",
            sourceJunctionId = 201L,
            targetNodeId = -1L,
            targetPortId = "",
            targetJunctionId = 202L
        )
        val j2ToJ1 = Connection(
            sourceNodeId = -1L,
            sourcePortId = "",
            sourceJunctionId = 202L,
            targetNodeId = -1L,
            targetPortId = "",
            targetJunctionId = 201L
        )

        val flow = Flow(
            name = "cyclic_junction_flow",
            junctions = listOf(
                FlowJunction(201L, Offset(0f, 0f)),
                FlowJunction(202L, Offset(10f, 10f))
            ),
            connections = listOf(inToJ1, j1ToJ2, j2ToJ1)
        )

        // Must terminate safely without infinite recursion and return 0 effective node connections
        val effective = flow.getEffectiveConnections()
        assertEquals(0, effective.size)
    }

    @Test
    fun testPurgeStrayPointsEliminatesDisconnectedJunctions() {
        val isolatedJunction = FlowJunction(100L, Offset(50f, 50f))
        val flow = Flow(
            name = "isolated_flow",
            junctions = listOf(isolatedJunction),
            connections = emptyList()
        )

        val purged = flow.purgeStrayPoints()
        assertTrue(purged.junctions.isEmpty())
        assertTrue(purged.connections.isEmpty())
    }

    @Test
    fun testPurgeStrayPointsEliminatesJunctionWithoutIncomingSource() {
        val junction = FlowJunction(100L, Offset(50f, 50f))
        val deadEndOut = Connection(
            sourceNodeId = -1L,
            sourcePortId = "",
            sourceJunctionId = 100L,
            targetNodeId = 2L,
            targetPortId = "in"
        )
        val flow = Flow(
            name = "no_source_flow",
            junctions = listOf(junction),
            connections = listOf(deadEndOut)
        )

        val purged = flow.purgeStrayPoints()
        assertTrue(purged.junctions.isEmpty())
        assertTrue(purged.connections.isEmpty())
    }

    @Test
    fun testPurgeStrayPointsRetainsValidWireSegmentReachableFromSourceNode() {
        val j1 = FlowJunction(101L, Offset(50f, 50f))
        val j2 = FlowJunction(102L, Offset(100f, 100f))
        val conn1 = Connection(
            sourceNodeId = 1L,
            sourcePortId = "out",
            targetNodeId = -1L,
            targetPortId = "",
            targetJunctionId = 101L
        )
        val conn2 = Connection(
            sourceNodeId = -1L,
            sourcePortId = "",
            sourceJunctionId = 101L,
            targetNodeId = -1L,
            targetPortId = "",
            targetJunctionId = 102L
        )
        val flow = Flow(
            name = "valid_segment_flow",
            junctions = listOf(j1, j2),
            connections = listOf(conn1, conn2)
        )

        val purged = flow.purgeStrayPoints()
        // The left part originating from source node 1 with 2 valid points must remain alive!
        assertEquals(2, purged.junctions.size)
        assertEquals(2, purged.connections.size)
    }

    @Test
    fun testPurgeStrayPointsRetainsValidJunctionChain() {
        val j1 = FlowJunction(101L, Offset(50f, 50f))
        val j2 = FlowJunction(102L, Offset(100f, 100f))
        val conn1 = Connection(
            sourceNodeId = 1L,
            sourcePortId = "out",
            targetNodeId = -1L,
            targetPortId = "",
            targetJunctionId = 101L
        )
        val conn2 = Connection(
            sourceNodeId = -1L,
            sourcePortId = "",
            sourceJunctionId = 101L,
            targetNodeId = -1L,
            targetPortId = "",
            targetJunctionId = 102L
        )
        val conn3 = Connection(
            sourceNodeId = -1L,
            sourcePortId = "",
            sourceJunctionId = 102L,
            targetNodeId = 2L,
            targetPortId = "in"
        )

        val flow = Flow(
            name = "chain_flow",
            junctions = listOf(j1, j2),
            connections = listOf(conn1, conn2, conn3)
        )

        val purged = flow.purgeStrayPoints()
        assertEquals(2, purged.junctions.size)
        assertEquals(3, purged.connections.size)
    }

    @Test
    fun testPurgeStrayPointsRetainsBranchingJunction() {
        val j1 = FlowJunction(101L, Offset(50f, 50f))
        val connIn = Connection(
            sourceNodeId = 1L,
            sourcePortId = "out",
            targetNodeId = -1L,
            targetPortId = "",
            targetJunctionId = 101L
        )
        val connOut1 = Connection(
            sourceNodeId = -1L,
            sourcePortId = "",
            sourceJunctionId = 101L,
            targetNodeId = 2L,
            targetPortId = "in"
        )
        val connOut2 = Connection(
            sourceNodeId = -1L,
            sourcePortId = "",
            sourceJunctionId = 101L,
            targetNodeId = 3L,
            targetPortId = "in"
        )

        val flow = Flow(
            name = "branching_flow",
            junctions = listOf(j1),
            connections = listOf(connIn, connOut1, connOut2)
        )

        val purged = flow.purgeStrayPoints()
        assertEquals(1, purged.junctions.size)
        assertEquals(3, purged.connections.size)
    }

    @Test
    fun testNormalizeWirePointsConvertsWaypointsToConnectionPoints() {
        val conn = Connection(
            sourceNodeId = 1L,
            sourcePortId = "out",
            targetNodeId = 2L,
            targetPortId = "in",
            waypoints = listOf(Offset(100f, 100f), Offset(200f, 200f)),
            color = "#00AAFF",
            isStructured = true
        )

        val flow = Flow(
            name = "waypoints_flow",
            connections = listOf(conn)
        )

        val normalized = flow.normalizeWirePoints()
        assertEquals(2, normalized.junctions.size)
        assertEquals(3, normalized.connections.size)

        // Verify waypoints are cleared on the resulting connections
        assertTrue(normalized.connections.all { it.waypoints.isEmpty() })
        assertTrue(normalized.connections.all { it.isStructured })
        assertTrue(normalized.connections.all { it.color == "#00AAFF" })

        // Verify points can be resolved back to effective connections
        val effective = normalized.getEffectiveConnections()
        assertEquals(1, effective.size)
        assertEquals(1L, effective[0].sourceNodeId)
        assertEquals(2L, effective[0].targetNodeId)
    }
}
