package org.wip.plugintoolkit.features.flows

import org.wip.plugintoolkit.api.Capability
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.ParameterMetadata
import org.wip.plugintoolkit.api.PluginInfo
import org.wip.plugintoolkit.api.PluginManifest
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.api.canConvert
import org.wip.plugintoolkit.api.isCompatibleWith
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.InputPort
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.model.Offset as ModelOffset
import org.wip.plugintoolkit.features.flows.model.OutputPort
import org.wip.plugintoolkit.features.flows.viewmodel.FlowEditorState
import org.wip.plugintoolkit.features.flows.viewmodel.FlowNodeManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FlowNodeReplaceAndRefreshTest {

    private val pluginInfo = PluginInfo(
        id = "test.plugin",
        name = "Test Plugin",
        version = "1.0",
        description = "Test plugin"
    )

    private val capA = Capability(
        name = "Capability A",
        description = "Cap A",
        returnType = DataType.Primitive(PrimitiveType.STRING),
        parameters = mapOf(
            "input_text" to ParameterMetadata(
                description = "Input text",
                type = DataType.Primitive(PrimitiveType.STRING),
                required = true
            )
        ),
        outputs = listOf(
            org.wip.plugintoolkit.api.OutputMetadata(
                name = "output_text",
                description = "Output text",
                type = DataType.Primitive(PrimitiveType.STRING)
            )
        )
    )

    private val capB = Capability(
        name = "Capability B",
        description = "Cap B with convertible inputs",
        returnType = DataType.Array(DataType.Primitive(PrimitiveType.STRING)),
        parameters = mapOf(
            "input_list" to ParameterMetadata(
                description = "Input list or convertible string",
                type = DataType.Array(DataType.Primitive(PrimitiveType.STRING)),
                required = true
            )
        ),
        outputs = listOf(
            org.wip.plugintoolkit.api.OutputMetadata(
                name = "result_list",
                description = "Result list",
                type = DataType.Array(DataType.Primitive(PrimitiveType.STRING))
            )
        )
    )

    private val manifest = PluginManifest(
        manifestVersion = "1.0",
        plugin = pluginInfo,
        requirements = org.wip.plugintoolkit.api.Requirements(minMemoryMb = 256, minExecutionTimeMs = 1000),
        capabilities = listOf(capA, capB)
    )

    private val nodeManager = FlowNodeManager()

    @Test
    fun testHandleRefreshNodeHealsBrokenNode() {
        val brokenNode = Node.CapabilityNode(
            id = 10L,
            position = ModelOffset.Zero,
            pluginInfo = pluginInfo,
            capability = capA,
            inputs = listOf(InputPort("input_text", "Input Text", DataType.Primitive(PrimitiveType.STRING))),
            outputs = listOf(OutputPort("output_text", "Output Text", DataType.Primitive(PrimitiveType.STRING))),
            isBroken = true
        )

        val state = FlowEditorState(
            flow = Flow("TestFlow", nodes = listOf(brokenNode))
        )

        val (newState, refreshed) = nodeManager.handleRefreshNode(
            currentState = state,
            nodeId = 10L,
            manifests = mapOf(pluginInfo.id to manifest)
        )

        assertNotNull(refreshed)
        assertFalse(refreshed.isBroken, "Refreshed capability node should heal to isBroken = false")
        val updatedInFlow = newState.flow.nodes.find { it.id == 10L } as? Node.CapabilityNode
        assertNotNull(updatedInFlow)
        assertFalse(updatedInFlow.isBroken)
    }

    @Test
    fun testHandleRefreshBrokenNodesHealsAllAvailable() {
        val brokenNode1 = Node.CapabilityNode(
            id = 1L,
            position = ModelOffset.Zero,
            pluginInfo = pluginInfo,
            capability = capA,
            inputs = listOf(InputPort("input_text", "Input Text", DataType.Primitive(PrimitiveType.STRING))),
            outputs = emptyList(),
            isBroken = true
        )

        val brokenNode2 = Node.CapabilityNode(
            id = 2L,
            position = ModelOffset.Zero,
            pluginInfo = pluginInfo,
            capability = capB,
            inputs = listOf(InputPort("input_list", "Input List", DataType.Array(DataType.Primitive(PrimitiveType.STRING)))),
            outputs = emptyList(),
            isBroken = true
        )

        val state = FlowEditorState(
            flow = Flow("TestFlow", nodes = listOf(brokenNode1, brokenNode2))
        )

        val (newState, healedCount) = nodeManager.handleRefreshBrokenNodes(
            currentState = state,
            manifests = mapOf(pluginInfo.id to manifest)
        )

        assertEquals(2, healedCount, "Both broken nodes should be healed")
        assertTrue(newState.flow.nodes.filterIsInstance<Node.CapabilityNode>().none { it.isBroken })
    }

    @Test
    fun testHandleReplaceNodeWithConnectionMigrationAndDisconnection() {
        val originalNode = Node.CapabilityNode(
            id = 100L,
            position = ModelOffset(50f, 50f),
            pluginInfo = pluginInfo,
            capability = capA,
            inputs = listOf(
                InputPort("input_text", "Input Text", DataType.Primitive(PrimitiveType.STRING)),
                InputPort("unused_input", "Unused", DataType.Primitive(PrimitiveType.INT))
            ),
            outputs = listOf(
                OutputPort("output_text", "Output Text", DataType.Primitive(PrimitiveType.STRING)),
                OutputPort("extra_out", "Extra", DataType.Primitive(PrimitiveType.BOOLEAN))
            )
        )

        val sourceNode = Node.FlowInputNode(
            id = 1L,
            position = ModelOffset(0f, 0f),
            outputs = listOf(OutputPort("source_out", "Source Out", DataType.Primitive(PrimitiveType.STRING)))
        )

        val targetNode = Node.FlowOutputNode(
            id = 2L,
            position = ModelOffset(200f, 200f),
            inputs = listOf(InputPort("target_in", "Target In", DataType.Primitive(PrimitiveType.STRING)))
        )

        val targetNode2 = Node.FlowOutputNode(
            id = 3L,
            position = ModelOffset(300f, 300f),
            inputs = listOf(InputPort("target_in2", "Target In 2", DataType.Primitive(PrimitiveType.BOOLEAN)))
        )

        // Incoming connection: 1L:source_out -> 100L:input_text
        val connIn = Connection(1L, "source_out", 100L, "input_text")
        // Outgoing connection 1: 100L:output_text -> 2L:target_in
        val connOut1 = Connection(100L, "output_text", 2L, "target_in")
        // Outgoing connection 2: 100L:extra_out -> 3L:target_in2
        val connOut2 = Connection(100L, "extra_out", 3L, "target_in2")
        // Unrelated connection: 1L:source_out -> 2L:target_in
        val unrelatedConn = Connection(1L, "source_out", 2L, "target_in")

        val state = FlowEditorState(
            flow = Flow(
                name = "FlowWithConnections",
                nodes = listOf(sourceNode, originalNode, targetNode, targetNode2),
                connections = listOf(connIn, connOut1, connOut2, unrelatedConn)
            )
        )

        // Replacement Node (Node B) has input "input_list" and output "result_list"
        val replacementNode = Node.CapabilityNode(
            id = 100L,
            position = ModelOffset(50f, 50f),
            pluginInfo = pluginInfo,
            capability = capB,
            inputs = listOf(InputPort("input_list", "Input List", DataType.Array(DataType.Primitive(PrimitiveType.STRING)))),
            outputs = listOf(OutputPort("result_list", "Result List", DataType.Array(DataType.Primitive(PrimitiveType.STRING))))
        )

        // Input migration: "input_text" -> "input_list"
        val inputMappings = mapOf("input_text" to "input_list")
        // Output migration: "output_text" -> "result_list", "extra_out" -> null (Disconnect)
        val outputMappings = mapOf(
            "output_text" to "result_list",
            "extra_out" to null
        )

        val newState = nodeManager.handleReplaceNode(
            currentState = state,
            nodeId = 100L,
            targetNode = replacementNode,
            inputMappings = inputMappings,
            outputMappings = outputMappings
        )

        assertEquals(4, newState.flow.nodes.size)
        val replaced = newState.flow.nodes.find { it.id == 100L } as? Node.CapabilityNode
        assertNotNull(replaced)
        assertEquals("Capability B", replaced.capability.name)

        // Verify connections:
        // connIn should be rewired to targetPortId = "input_list"
        val migratedIn = newState.flow.connections.find { it.sourceNodeId == 1L && it.targetNodeId == 100L }
        assertNotNull(migratedIn)
        assertEquals("input_list", migratedIn.targetPortId)

        // connOut1 should be rewired to sourcePortId = "result_list"
        val migratedOut1 = newState.flow.connections.find { it.sourceNodeId == 100L && it.targetNodeId == 2L }
        assertNotNull(migratedOut1)
        assertEquals("result_list", migratedOut1.sourcePortId)

        // connOut2 was mapped to Disconnect (null), so it must be removed!
        val disconnectedOut = newState.flow.connections.find { it.sourceNodeId == 100L && it.targetNodeId == 3L }
        assertNull(disconnectedOut, "Connection mapped to Disconnect must be removed")

        // Unrelated connection must be untouched
        val preservedUnrelated = newState.flow.connections.find { it.sourceNodeId == 1L && it.targetNodeId == 2L }
        assertNotNull(preservedUnrelated, "Unrelated connection must remain untouched")
    }

    @Test
    fun testConvertibleTypesAllowedInMigration() {
        // Primitive String to Array of String is convertible
        val stringType = DataType.Primitive(PrimitiveType.STRING)
        val listType = DataType.Array(DataType.Primitive(PrimitiveType.STRING))

        assertTrue(stringType.canConvert(listType), "String should convert to Array<String>")

        // Primitive Int to Primitive String is convertible
        val intType = DataType.Primitive(PrimitiveType.INT)
        assertTrue(intType.canConvert(stringType), "Int should convert to String")
    }
}
