package org.wip.plugintoolkit.features.flows

import org.wip.plugintoolkit.features.flows.model.Offset
import org.wip.plugintoolkit.api.Capability
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.ParameterMetadata
import org.wip.plugintoolkit.api.PluginInfo
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.ConnectionPoint
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.InputPort
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.logic.FlowTypeInference
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

    @Test
    fun testNodeReadinessWithImplicitBooleanDefault() {
        val pluginInfo = PluginInfo("test.plugin", "Test Plugin", "1.0", "Test")
        val boolParam = ParameterMetadata(
            description = "Bool Param",
            type = DataType.Primitive(PrimitiveType.BOOLEAN),
            required = true
        )
        val capability = Capability(
            name = "TestCapBool",
            description = "Desc",
            parameters = mapOf("boolPort" to boolParam),
            returnType = DataType.Primitive(PrimitiveType.UNIT)
        )
        val boolInput = InputPort("boolPort", "Bool Port", DataType.Primitive(PrimitiveType.BOOLEAN), value = null, defaultValue = null)
        val node = Node.CapabilityNode(
            id = 1L,
            position = Offset.Zero,
            pluginInfo = pluginInfo,
            capability = capability,
            inputs = listOf(boolInput),
            outputs = emptyList()
        )

        assertTrue(node.isReady(emptyList()), "Node should be ready even when boolean parameter is untouched (implicit default false)")
    }

    @Test
    fun testNodeNotReadyWhenInputPortConnectedToSourcelessConnection() {
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

        // Connected to an open junction (no effective connection)
        val openConn = Connection(
            sourceNodeId = Connection.FLOATING_NODE_ID,
            sourcePortId = Connection.FLOATING_PORT_ID,
            sourceJunctionId = 10L,
            targetNodeId = 1L,
            targetPortId = "reqPort"
        )
        assertFalse(
            node.isReady(connections = listOf(openConn), effectiveConnections = emptyList()),
            "Node should NOT be ready when input port is connected to an open wire without source"
        )
    }

    @Test
    fun testNodeNotReadyEvenWithDefaultValueWhenConnectedToSourcelessConnection() {
        val pluginInfo = PluginInfo("test.plugin", "Test Plugin", "1.0", "Test")
        val dirParam = ParameterMetadata(
            description = "Workspace Directory",
            type = DataType.Primitive(PrimitiveType.STRING),
            required = true,
            defaultValue = kotlinx.serialization.json.JsonPrimitive(".") // application folder default
        )
        val capability = Capability(
            name = "FileCap",
            description = "Desc",
            parameters = mapOf("dirPort" to dirParam),
            returnType = DataType.Primitive(PrimitiveType.UNIT)
        )
        val dirInput = InputPort(
            id = "dirPort",
            name = "Dir",
            dataType = DataType.Primitive(PrimitiveType.STRING),
            defaultValue = "."
        )
        val node = Node.CapabilityNode(
            id = 1L,
            position = Offset.Zero,
            pluginInfo = pluginInfo,
            capability = capability,
            inputs = listOf(dirInput),
            outputs = emptyList()
        )

        // When not connected, default value "." makes it ready
        assertTrue(node.isReady(emptyList(), effectiveConnections = emptyList()), "Node without wires is ready via default value")

        // But when connected to a wire with no source, it MUST NOT fall back to default value
        val openConn = Connection(
            sourceNodeId = Connection.FLOATING_NODE_ID,
            sourcePortId = Connection.FLOATING_PORT_ID,
            sourceJunctionId = 10L,
            targetNodeId = 1L,
            targetPortId = "dirPort"
        )
        assertFalse(
            node.isReady(connections = listOf(openConn), effectiveConnections = emptyList()),
            "Node MUST NOT be ready when wired to an open connection, even if default value exists!"
        )
    }

    @Test
    fun testFlowBrokenWhenInputPortConnectedToSourcelessConnection() {
        val pluginInfo = PluginInfo("test.plugin", "Test Plugin", "1.0", "Test")
        val dirParam = ParameterMetadata(
            description = "Workspace Directory",
            type = DataType.Primitive(PrimitiveType.STRING),
            required = true,
            defaultValue = kotlinx.serialization.json.JsonPrimitive(".")
        )
        val capability = Capability(
            name = "FileCap",
            description = "Desc",
            parameters = mapOf("dirPort" to dirParam),
            returnType = DataType.Primitive(PrimitiveType.UNIT)
        )
        val dirInput = InputPort(
            id = "dirPort",
            name = "Dir",
            dataType = DataType.Primitive(PrimitiveType.STRING),
            defaultValue = "."
        )
        val node = Node.CapabilityNode(
            id = 1L,
            position = Offset.Zero,
            pluginInfo = pluginInfo,
            capability = capability,
            inputs = listOf(dirInput),
            outputs = emptyList()
        )

        val junction = ConnectionPoint(id = 10L, position = Offset.Zero)
        val openWire = Connection(
            sourceNodeId = Connection.FLOATING_NODE_ID,
            sourcePortId = Connection.FLOATING_PORT_ID,
            sourceJunctionId = 10L,
            targetNodeId = 1L,
            targetPortId = "dirPort"
        )

        val flow = Flow(
            name = "Open Connection Flow",
            nodes = listOf(node),
            junctions = listOf(junction),
            connections = listOf(openWire)
        )

        assertTrue(flow.isBroken(setOf("FileCap")), "Flow should be broken when an input port connects to an open wire")
        assertTrue(flow.getSourcelessInputConnections().isNotEmpty(), "getSourcelessInputConnections should return open wire")

        // Type inference should also report a validation error
        val typeResult = FlowTypeInference.runTypeInference(flow)
        assertTrue(
            typeResult.validationErrors.any { it.targetNodeId == 1L && it.targetPortId == "dirPort" },
            "Type inference must emit a validation error for open wire to input port"
        )
    }
}
