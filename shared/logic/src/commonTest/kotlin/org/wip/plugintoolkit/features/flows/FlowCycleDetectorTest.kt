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
}
