package org.wip.plugintoolkit.features.flows

import androidx.compose.ui.geometry.Offset
import org.wip.plugintoolkit.api.Capability
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PluginInfo
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.InputPort
import org.wip.plugintoolkit.features.flows.model.Node
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FlowImprovementRegressionTest {
    private val plugin = PluginInfo("example.plugin", "Example", "1.0.0", "Example plugin")
    private val capability = Capability(
        name = "Transform",
        description = "Transforms a value",
        returnType = DataType.Primitive(PrimitiveType.STRING)
    )
    private val stringType = DataType.Primitive(PrimitiveType.STRING)

    private fun node(input: InputPort, isBroken: Boolean = false) = Node.CapabilityNode(
        id = 2,
        position = Offset.Zero,
        pluginInfo = plugin,
        capability = capability,
        inputs = listOf(input),
        outputs = emptyList(),
        isBroken = isBroken
    )

    @Test
    fun `required empty input makes node and flow not ready`() {
        val input = InputPort("value", "Value", stringType, isRequired = true)
        val flow = Flow("Required", nodes = listOf(node(input)))

        assertFalse(flow.nodes.single().isReady(flow.connections))
        assertTrue(flow.isBroken(setOf(capability.name)))
    }

    @Test
    fun `optional empty input remains ready`() {
        val input = InputPort("value", "Value", stringType, isRequired = false)
        val flow = Flow("Optional", nodes = listOf(node(input)))

        assertTrue(flow.nodes.single().isReady(flow.connections))
        assertFalse(flow.isBroken(setOf(capability.name)))
    }

    @Test
    fun `connection or default satisfies a required input`() {
        val required = InputPort("value", "Value", stringType, isRequired = true)
        val connectedFlow = Flow(
            "Connected",
            nodes = listOf(node(required)),
            connections = listOf(Connection(1, "out", 2, "value"))
        )
        val defaultedFlow = Flow(
            "Defaulted",
            nodes = listOf(node(required.copy(defaultValue = "fallback")))
        )

        assertTrue(connectedFlow.nodes.single().isReady(connectedFlow.connections))
        assertTrue(defaultedFlow.nodes.single().isReady(defaultedFlow.connections))
    }

    @Test
    fun `explicitly broken node remains not ready after parameter completion`() {
        val configured = InputPort("value", "Value", stringType, value = "configured", isRequired = true)
        val flow = Flow("Broken", nodes = listOf(node(configured, isBroken = true)))

        assertFalse(flow.nodes.single().isReady(flow.connections))
        assertTrue(flow.isBroken(setOf(capability.name)))
    }
}
