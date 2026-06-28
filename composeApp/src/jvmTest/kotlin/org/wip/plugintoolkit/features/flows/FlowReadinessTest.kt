package org.wip.plugintoolkit.features.flows

import androidx.compose.ui.geometry.Offset
import org.wip.plugintoolkit.api.Capability
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.ParameterMetadata
import org.wip.plugintoolkit.api.PluginInfo
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.InputPort
import org.wip.plugintoolkit.features.flows.model.Node
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FlowReadinessTest {

    @Test
    fun testNodeReadiness() {
        val pluginInfo = PluginInfo("test.plugin", "Test Plugin", "1.0", "Test")
        val requiredParam = ParameterMetadata(
            description = "Req",
            type = DataType.Primitive(PrimitiveType.STRING),
            required = true
        )
        val optionalParam = ParameterMetadata(
            description = "Opt",
            type = DataType.Primitive(PrimitiveType.STRING),
            required = false
        )

        val capability = Capability(
            name = "TestCap",
            description = "Desc",
            parameters = mapOf("reqPort" to requiredParam, "optPort" to optionalParam),
            returnType = DataType.Primitive(PrimitiveType.UNIT)
        )

        val reqInput = InputPort("reqPort", "Req", DataType.Primitive(PrimitiveType.STRING))
        val optInput = InputPort("optPort", "Opt", DataType.Primitive(PrimitiveType.STRING))

        val node = Node.CapabilityNode(
            id = 1L,
            position = Offset.Zero,
            pluginInfo = pluginInfo,
            capability = capability,
            inputs = listOf(reqInput, optInput),
            outputs = emptyList()
        )

        // 1. Missing required field -> not ready
        assertFalse(node.isReady(emptyList()), "Node should not be ready when missing required field")

        // 2. Provided via value -> ready
        val valueNode = node.copyWithUpdatedInput("reqPort", kotlinx.serialization.json.JsonPrimitive("value"))
        assertTrue(valueNode.isReady(emptyList()), "Node should be ready when required field has value")

        // 3. Provided via connection -> ready
        val connections =
            listOf(Connection(sourceNodeId = 2L, sourcePortId = "out", targetNodeId = 1L, targetPortId = "reqPort"))
        assertTrue(node.isReady(connections), "Node should be ready when required field has connection")
    }

    @Test
    fun testFlowReadiness() {
        val pluginInfo = PluginInfo("test.plugin", "Test Plugin", "1.0", "Test")
        val requiredParam = ParameterMetadata(
            description = "Req",
            type = DataType.Primitive(PrimitiveType.STRING),
            required = true
        )
        val capability = Capability(
            name = "TestCap",
            description = "Desc",
            parameters = mapOf("reqPort" to requiredParam),
            returnType = DataType.Primitive(PrimitiveType.UNIT)
        )
        val reqInput = InputPort("reqPort", "Req", DataType.Primitive(PrimitiveType.STRING))

        val node = Node.CapabilityNode(
            id = 1L,
            position = Offset.Zero,
            pluginInfo = pluginInfo,
            capability = capability,
            inputs = listOf(reqInput),
            outputs = emptyList()
        )

        val flow = Flow(name = "Test Flow", nodes = listOf(node))

        assertTrue(flow.isBroken(setOf("TestCap")), "Flow should be broken if a node is not ready")

        val valueNode = node.copyWithUpdatedInput("reqPort", kotlinx.serialization.json.JsonPrimitive("value"))
        val readyFlow = Flow(name = "Test Flow", nodes = listOf(valueNode))

        assertFalse(readyFlow.isBroken(setOf("TestCap")), "Flow should not be broken if all nodes are ready")
    }
}
