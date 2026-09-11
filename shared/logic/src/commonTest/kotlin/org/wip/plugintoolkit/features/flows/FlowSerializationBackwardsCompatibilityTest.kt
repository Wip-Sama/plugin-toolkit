package org.wip.plugintoolkit.features.flows

import kotlinx.serialization.json.Json
import org.wip.plugintoolkit.api.Capability
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PluginInfo
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.FlowGroup
import org.wip.plugintoolkit.features.flows.model.FlowJunction
import org.wip.plugintoolkit.features.flows.model.FlowLabel
import org.wip.plugintoolkit.features.flows.model.InputPort
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.model.Offset
import org.wip.plugintoolkit.features.flows.model.OutputPort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FlowSerializationBackwardsCompatibilityTest {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun testLegacyFlowJsonDeserialization() {
        val legacyJson = """
            {
                "name": "legacy_flow",
                "version": "1.0.0",
                "nodes": [
                    {
                        "type": "flow_input",
                        "id": 1,
                        "position": { "x": 50.0, "y": 100.0 },
                        "outputs": [
                            {
                                "id": "input_data",
                                "name": "input_data",
                                "dataType": { "type": "primitive", "primitiveType": "STRING" }
                            }
                        ]
                    }
                ],
                "connections": [
                    {
                        "sourceNodeId": 1,
                        "sourcePortId": "input_data",
                        "targetNodeId": 2,
                        "targetPortId": "in_data"
                    }
                ]
            }
        """.trimIndent()

        val decodedFlow = json.decodeFromString<Flow>(legacyJson)
        assertEquals("legacy_flow", decodedFlow.name)
        assertEquals(1, decodedFlow.nodes.size)
        assertEquals(1, decodedFlow.connections.size)

        // New fields must default to empty lists / nulls
        assertTrue(decodedFlow.groups.isEmpty())
        assertTrue(decodedFlow.labels.isEmpty())
        assertTrue(decodedFlow.junctions.isEmpty())

        val firstConn = decodedFlow.connections.first()
        assertNull(firstConn.color)
        assertTrue(firstConn.waypoints.isEmpty())
        assertTrue(firstConn.junctionIds.isEmpty())
        assertNull(firstConn.sourceJunctionId)
        assertNull(firstConn.targetJunctionId)
        assertNull(firstConn.floatingTarget)

        val firstNode = decodedFlow.nodes.first()
        assertNull(firstNode.color)
    }

    @Test
    fun testModernFlowRoundTripSerialization() {
        val group = FlowGroup(
            id = 500L,
            title = "Image Filters Group",
            position = Offset(100f, 200f),
            size = Offset(600f, 400f),
            color = "#336699",
            isCollapsed = false,
            nodeIds = listOf(1L, 2L)
        )

        val label = FlowLabel(
            id = 600L,
            text = "Pre-processing notes",
            position = Offset(150f, 180f),
            color = "#FFCC00",
            fontSize = 16f
        )

        val junction = FlowJunction(
            id = 700L,
            position = Offset(300f, 250f),
            color = "#FF5722"
        )

        val inputNode = Node.FlowInputNode(
            id = 1L,
            position = Offset(50f, 100f),
            outputs = listOf(OutputPort("out", "out", DataType.Primitive(PrimitiveType.STRING))),
            color = "#4CAF50"
        )

        val outputNode = Node.FlowOutputNode(
            id = 2L,
            position = Offset(500f, 100f),
            inputs = listOf(InputPort("in", "in", DataType.Primitive(PrimitiveType.STRING))),
            color = "#9C27B0"
        )

        val connection = Connection(
            sourceNodeId = 1L,
            sourcePortId = "out",
            targetNodeId = 2L,
            targetPortId = "in",
            color = "#FF5722",
            waypoints = listOf(Offset(150f, 120f)),
            junctionIds = listOf(700L)
        )

        val flow = Flow(
            name = "modern_flow",
            nodes = listOf(inputNode, outputNode),
            connections = listOf(connection),
            groups = listOf(group),
            labels = listOf(label),
            junctions = listOf(junction)
        )

        val encoded = json.encodeToString(Flow.serializer(), flow)
        val decoded = json.decodeFromString<Flow>(encoded)

        assertEquals(flow, decoded)
        assertEquals(1, decoded.groups.size)
        assertEquals("Image Filters Group", decoded.groups.first().title)
        assertEquals("#336699", decoded.groups.first().color)
        assertEquals(listOf(1L, 2L), decoded.groups.first().nodeIds)

        assertEquals(1, decoded.labels.size)
        assertEquals("Pre-processing notes", decoded.labels.first().text)

        assertEquals(1, decoded.junctions.size)
        assertEquals(700L, decoded.junctions.first().id)

        assertEquals("#4CAF50", decoded.nodes[0].color)
        assertEquals("#9C27B0", decoded.nodes[1].color)

        val decodedConn = decoded.connections.first()
        assertEquals("#FF5722", decodedConn.color)
        assertEquals(listOf(Offset(150f, 120f)), decodedConn.waypoints)
        assertEquals(listOf(700L), decodedConn.junctionIds)
    }

    @Test
    fun testNodeCopyWithColor() {
        val capNode = Node.CapabilityNode(
            id = 1L,
            position = Offset.Zero,
            pluginInfo = PluginInfo("pkg", "TestPlugin", "1.0", "Desc"),
            capability = Capability(name = "Cap", description = "Desc", returnType = DataType.Primitive(PrimitiveType.STRING)),
            inputs = emptyList(),
            outputs = emptyList()
        )
        assertNull(capNode.color)
        val coloredCapNode = capNode.copyWithColor("#E91E63")
        assertEquals("#E91E63", coloredCapNode.color)

        val sysNode = Node.SystemNode(2L, Offset.Zero, "Sys", "convert", emptyList(), emptyList())
        assertEquals("#00BCD4", sysNode.copyWithColor("#00BCD4").color)

        val inputNode = Node.FlowInputNode(3L, Offset.Zero, emptyList())
        assertEquals("#8BC34A", inputNode.copyWithColor("#8BC34A").color)

        val outputNode = Node.FlowOutputNode(4L, Offset.Zero, emptyList())
        assertEquals("#FF9800", outputNode.copyWithColor("#FF9800").color)

        val subNode = Node.SubFlowNode(5L, Offset.Zero, "Sub", emptyList(), emptyList())
        assertEquals("#673AB7", subNode.copyWithColor("#673AB7").color)
    }
}
