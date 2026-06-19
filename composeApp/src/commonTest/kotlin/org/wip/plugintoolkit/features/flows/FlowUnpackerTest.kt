package org.wip.plugintoolkit.features.flows

import androidx.compose.ui.geometry.Offset
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.FlowUnpacker
import org.wip.plugintoolkit.features.flows.model.InputPort
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.model.OutputPort
import org.wip.plugintoolkit.features.flows.model.SubflowPortMapping
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FlowUnpackerTest {

    @Test
    fun testHasCycleDetectsSimpleCycle() {
        val connections = listOf(
            Connection(1L, "out", 2L, "in"),
            Connection(2L, "out", 1L, "in")
        )
        assertTrue(FlowUnpacker.hasCycle(connections), "Should detect a direct loop cycle")
    }

    @Test
    fun testHasCycleDetectsIndirectCycle() {
        val connections = listOf(
            Connection(1L, "out", 2L, "in"),
            Connection(2L, "out", 3L, "in"),
            Connection(3L, "out", 1L, "in")
        )
        assertTrue(FlowUnpacker.hasCycle(connections), "Should detect a 3-node cycle")
    }

    @Test
    fun testHasCycleAllowsDAG() {
        val connections = listOf(
            Connection(1L, "out", 2L, "in1"),
            Connection(1L, "out", 3L, "in"),
            Connection(2L, "out", 3L, "in2")
        )
        assertFalse(FlowUnpacker.hasCycle(connections), "Should allow a valid Directed Acyclic Graph")
    }

    @Test
    fun testUnpackSubflowInFlow() {
        // Define DataType
        val anyType = DataType.Primitive(PrimitiveType.ANY)

        // 1. Build Subflow (Subflow "A")
        // Node 100: Input Node
        // Node 101: System Node doing some work
        // Node 102: Output Node
        val subflowInputNode = Node.FlowInputNode(
            id = 100L,
            position = Offset(0f, 0f),
            outputs = listOf(OutputPort("input_data", "Input Data", anyType))
        )
        val subflowWorkNode = Node.SystemNode(
            id = 101L,
            position = Offset(100f, 50f),
            title = "Work",
            systemAction = "save",
            inputs = listOf(InputPort("in_port", "In", anyType)),
            outputs = listOf(OutputPort("out_port", "Out", anyType))
        )
        val subflowOutputNode = Node.FlowOutputNode(
            id = 102L,
            position = Offset(200f, 100f),
            inputs = listOf(InputPort("output_data", "Output Data", anyType))
        )

        val subflow = Flow(
            name = "SubflowA",
            nodes = listOf(subflowInputNode, subflowWorkNode, subflowOutputNode),
            connections = listOf(
                Connection(100L, "input_data", 101L, "in_port"),
                Connection(101L, "out_port", 102L, "output_data")
            )
        )

        // 2. Build Parent Flow
        // Node 1: Source System Node
        // Node 2: Subflow Node (representing "SubflowA")
        // Node 3: Target System Node
        val parentSourceNode = Node.SystemNode(
            id = 1L,
            position = Offset(0f, 0f),
            title = "Source",
            systemAction = "load",
            inputs = emptyList(),
            outputs = listOf(OutputPort("src_out", "Source Out", anyType))
        )

        val subflowNode = Node.SubFlowNode(
            id = 2L,
            position = Offset(200f, 0f),
            flowName = "SubflowA",
            inputs = listOf(InputPort("port_input_100", "Input Data", anyType)),
            outputs = listOf(OutputPort("port_output_102", "Output Data", anyType)),
            inputMappings = listOf(SubflowPortMapping("port_input_100", 100L)),
            outputMappings = listOf(SubflowPortMapping("port_output_102", 102L))
        )

        val parentTargetNode = Node.SystemNode(
            id = 3L,
            position = Offset(400f, 0f),
            title = "Target",
            systemAction = "save",
            inputs = listOf(InputPort("tgt_in", "Target In", anyType)),
            outputs = emptyList()
        )

        val parentFlow = Flow(
            name = "ParentFlow",
            nodes = listOf(parentSourceNode, subflowNode, parentTargetNode),
            connections = listOf(
                Connection(1L, "src_out", 2L, "port_input_100"),
                Connection(2L, "port_output_102", 3L, "tgt_in")
            )
        )

        // 3. Unpack Subflow Node 2L in parentFlow
        val unpackedFlow = FlowUnpacker.unpackSubflowInFlow(
            parentFlow = parentFlow,
            nodeId = 2L,
            subflow = subflow
        )

        // 4. Verification Assertions
        // SubflowNode 2L should be deleted
        assertFalse(unpackedFlow.nodes.any { it.id == 2L }, "Subflow node 2 should be removed")

        // Input and Output boundary nodes from Subflow (100L, 102L) should NOT be added
        assertFalse(
            unpackedFlow.nodes.any { it is Node.FlowInputNode },
            "Boundary Input node should not be in final unpacked flow"
        )
        assertFalse(
            unpackedFlow.nodes.any { it is Node.FlowOutputNode },
            "Boundary Output node should not be in final unpacked flow"
        )

        // Source node (1L) and Target node (3L) should remain
        assertTrue(unpackedFlow.nodes.any { it.id == 1L }, "Source node 1 should remain")
        assertTrue(unpackedFlow.nodes.any { it.id == 3L }, "Target node 3 should remain")

        // The work node (101L) should be copied and remapped with a new ID
        // The ID should be based on parent maximum ID + 1 (max of {1, 2, 3} + 1 = 4L)
        val copiedWorkNode = unpackedFlow.nodes.find { it.title == "Work" }
        assertNotNull(copiedWorkNode, "Copied work node should exist")
        assertEquals(5L, copiedWorkNode!!.id, "Copied work node should be assigned ID 5")
        assertEquals(
            Offset(300f, 50f),
            copiedWorkNode.position,
            "Position of copied node should be shifted by subflowNode position Offset(200,0)"
        )

        // Verify Connections
        // The two connections of parentFlow and two connections of subflow should resolve to:
        // Connection 1: 1L ("src_out") -> 5L ("in_port")
        // Connection 2: 5L ("out_port") -> 3L ("tgt_in")
        assertEquals(2, unpackedFlow.connections.size, "There should be exactly 2 final connections")

        val conn1 = unpackedFlow.connections.find { it.sourceNodeId == 1L }
        assertNotNull(conn1, "Should find connection from source node 1")
        assertEquals("src_out", conn1!!.sourcePortId)
        assertEquals(5L, conn1.targetNodeId)
        assertEquals("in_port", conn1.targetPortId)

        val conn2 = unpackedFlow.connections.find { it.targetNodeId == 3L }
        assertNotNull(conn2, "Should find connection to target node 3")
        assertEquals(5L, conn2!!.sourceNodeId)
        assertEquals("out_port", conn2.sourcePortId)
        assertEquals("tgt_in", conn2.targetPortId)
    }
}
