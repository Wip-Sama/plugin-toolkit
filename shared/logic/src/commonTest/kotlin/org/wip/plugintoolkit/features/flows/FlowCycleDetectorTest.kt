package org.wip.plugintoolkit.features.flows

import org.wip.plugintoolkit.features.flows.logic.FlowCycleDetector
import org.wip.plugintoolkit.features.flows.logic.GraphVertex
import org.wip.plugintoolkit.features.flows.model.Connection
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FlowCycleDetectorTest {

    @Test
    fun testWouldCreateCycleAllowsIndependentJunctionBranches() {
        // Reproduces the exact scenario from user issue 1 / Image 1:
        // Branch 1: Node 1 -> Junction 101 -> Node 2
        // Branch 2: Node 3 -> Junction 102 -> Node 4
        // Adding connection from Node 2 to Node 3 should NOT create a cycle!
        val connections = listOf(
            Connection(
                sourceNodeId = 1L,
                sourcePortId = "out",
                targetNodeId = -1L,
                targetPortId = "",
                targetJunctionId = 101L
            ),
            Connection(
                sourceNodeId = -1L,
                sourcePortId = "",
                sourceJunctionId = 101L,
                targetNodeId = 2L,
                targetPortId = "in"
            ),
            Connection(
                sourceNodeId = 3L,
                sourcePortId = "out",
                targetNodeId = -1L,
                targetPortId = "",
                targetJunctionId = 102L
            ),
            Connection(
                sourceNodeId = -1L,
                sourcePortId = "",
                sourceJunctionId = 102L,
                targetNodeId = 4L,
                targetPortId = "in"
            )
        )

        // Pre-condition: current graph has no cycle
        assertFalse(FlowCycleDetector.hasCycle(connections))

        // Connecting Node 2 -> Node 3 forms: 1 -> J101 -> 2 -> 3 -> J102 -> 4 (Valid DAG)
        val wouldCycle = FlowCycleDetector.wouldCreateCycle(
            sourceNodeId = 2L,
            targetNodeId = 3L,
            connections = connections
        )
        assertFalse(wouldCycle, "Connecting Node 2 to Node 3 across junction branches must NOT be detected as cyclic")
    }

    @Test
    fun testWouldCreateCycleDetectsRealCycleThroughJunctions() {
        // Node 1 -> Junction 101 -> Node 2 -> Junction 102 -> Node 3
        // Connecting Node 3 -> Node 1 creates a real cycle
        val connections = listOf(
            Connection(
                sourceNodeId = 1L,
                sourcePortId = "out",
                targetNodeId = -1L,
                targetPortId = "",
                targetJunctionId = 101L
            ),
            Connection(
                sourceNodeId = -1L,
                sourcePortId = "",
                sourceJunctionId = 101L,
                targetNodeId = 2L,
                targetPortId = "in"
            ),
            Connection(
                sourceNodeId = 2L,
                sourcePortId = "out",
                targetNodeId = -1L,
                targetPortId = "",
                targetJunctionId = 102L
            ),
            Connection(
                sourceNodeId = -1L,
                sourcePortId = "",
                sourceJunctionId = 102L,
                targetNodeId = 3L,
                targetPortId = "in"
            )
        )

        val wouldCycle = FlowCycleDetector.wouldCreateCycle(
            sourceNodeId = 3L,
            targetNodeId = 1L,
            connections = connections
        )
        assertTrue(wouldCycle, "Connecting Node 3 to Node 1 must detect genuine cycle through junctions")
    }

    @Test
    fun testWouldCreateCycleConnectingToJunctionDirectly() {
        // Node 1 -> Junction 101 -> Node 2
        // If we try to connect Node 2 -> Junction 101, it must be detected as cycle
        val connections = listOf(
            Connection(
                sourceNodeId = 1L,
                sourcePortId = "out",
                targetNodeId = -1L,
                targetPortId = "",
                targetJunctionId = 101L
            ),
            Connection(
                sourceNodeId = -1L,
                sourcePortId = "",
                sourceJunctionId = 101L,
                targetNodeId = 2L,
                targetPortId = "in"
            )
        )

        val wouldCycle = FlowCycleDetector.wouldCreateCycle(
            sourceNodeId = 2L,
            sourceJunctionId = null,
            targetNodeId = null,
            targetJunctionId = 101L,
            connections = connections
        )
        assertTrue(wouldCycle, "Connecting Node 2 back to Junction 101 must create a cycle")
    }

    @Test
    fun testSelfCycleDirectlyRejected() {
        assertTrue(
            FlowCycleDetector.wouldCreateCycle(sourceNodeId = 1L, targetNodeId = 1L, connections = emptyList()),
            "Connecting a node to itself must be rejected"
        )
        assertTrue(
            FlowCycleDetector.wouldCreateCycle(
                sourceNodeId = null,
                sourceJunctionId = 101L,
                targetNodeId = null,
                targetJunctionId = 101L,
                connections = emptyList()
            ),
            "Connecting a junction to itself must be rejected"
        )
    }

    @Test
    fun testIntermediateJunctionChainCycleDetection() {
        // Connection with intermediate junctionIds: Node 1 -> J101 -> J102 -> Node 2
        val connections = listOf(
            Connection(
                sourceNodeId = 1L,
                sourcePortId = "out",
                targetNodeId = 2L,
                targetPortId = "in",
                junctionIds = listOf(101L, 102L)
            )
        )

        assertFalse(FlowCycleDetector.hasCycle(connections))

        // Connecting Node 2 back to J101 must be detected as a cycle
        val cycleToJ101 = FlowCycleDetector.wouldCreateCycle(
            sourceNodeId = 2L,
            sourceJunctionId = null,
            targetNodeId = null,
            targetJunctionId = 101L,
            connections = connections
        )
        assertTrue(cycleToJ101, "Connecting Node 2 back to intermediate junction 101 must create cycle")

        // Connecting Node 2 back to J102 must be detected as a cycle
        val cycleToJ102 = FlowCycleDetector.wouldCreateCycle(
            sourceNodeId = 2L,
            sourceJunctionId = null,
            targetNodeId = null,
            targetJunctionId = 102L,
            connections = connections
        )
        assertTrue(cycleToJ102, "Connecting Node 2 back to intermediate junction 102 must create cycle")

        // Connecting Node 2 back to Node 1 must be detected as a cycle
        val cycleToNode1 = FlowCycleDetector.wouldCreateCycle(
            sourceNodeId = 2L,
            targetNodeId = 1L,
            connections = connections
        )
        assertTrue(cycleToNode1, "Connecting Node 2 back to Node 1 must create cycle")
    }

    @Test
    fun testDiamondDagAllowedAndCycleBackRejected() {
        // Diamond DAG:
        // Node 1 -> Node 2 -> Node 4
        // Node 1 -> Node 3 -> Node 4
        val connections = listOf(
            Connection(sourceNodeId = 1L, sourcePortId = "out1", targetNodeId = 2L, targetPortId = "in"),
            Connection(sourceNodeId = 1L, sourcePortId = "out2", targetNodeId = 3L, targetPortId = "in"),
            Connection(sourceNodeId = 2L, sourcePortId = "out", targetNodeId = 4L, targetPortId = "in1"),
            Connection(sourceNodeId = 3L, sourcePortId = "out", targetNodeId = 4L, targetPortId = "in2")
        )

        assertFalse(FlowCycleDetector.hasCycle(connections), "Diamond DAG must not have a cycle")

        // Adding connection from Node 2 -> Node 3 is valid (still a DAG, 1 -> 2 -> 3 -> 4)
        val validCross = FlowCycleDetector.wouldCreateCycle(
            sourceNodeId = 2L,
            targetNodeId = 3L,
            connections = connections
        )
        assertFalse(validCross, "Cross-branch edge that maintains acyclicity must be permitted")

        // Connecting sink Node 4 back to source Node 1 creates cycle
        val cycleSinkToSource = FlowCycleDetector.wouldCreateCycle(
            sourceNodeId = 4L,
            targetNodeId = 1L,
            connections = connections
        )
        assertTrue(cycleSinkToSource, "Connecting sink Node 4 back to root Node 1 must create cycle")
    }
}
