package org.wip.plugintoolkit.features.flows.ui

import androidx.compose.ui.geometry.Offset
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.InputPort
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.model.Offset as ModelOffset
import org.wip.plugintoolkit.features.flows.model.OutputPort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FlowEditorUtilsTest {

    private fun createTestNodeWithInputs(id: Long, inputs: List<InputPort>): Node {
        return Node.FlowOutputNode(
            id = id,
            position = ModelOffset(0f, 0f),
            inputs = inputs
        )
    }

    private fun createTestNodeWithOutputs(id: Long, outputs: List<OutputPort>): Node {
        return Node.FlowInputNode(
            id = id,
            position = ModelOffset(0f, 0f),
            outputs = outputs
        )
    }

    @Test
    fun testFindClosestPortReturnsPortWithinDistance() {
        val inputPort = InputPort(
            id = "batchSize",
            name = "batchSize",
            description = "Batch size",
            dataType = DataType.Primitive(PrimitiveType.INT)
        )
        val node = createTestNodeWithInputs(1L, listOf(inputPort))
        val flow = Flow(name = "Test Flow", nodes = listOf(node))

        val getPortBoardPos: (Long, String, Boolean) -> Offset? = { nodeId, portId, isOutput ->
            if (nodeId == 1L && portId == "batchSize" && !isOutput) Offset(100f, 100f) else null
        }

        val (nodeId, portId) = findClosestPort(
            boardPosition = Offset(105f, 105f),
            flow = flow,
            connectionStartIsOutput = true,
            scale = 1f,
            getPortBoardPosition = getPortBoardPos
        )

        assertEquals(1L, nodeId)
        assertEquals("batchSize", portId)
    }

    @Test
    fun testFindClosestPortSkipsInactiveUnconnectedPort() {
        val inactivePort = InputPort(
            id = "customWeightsPath",
            name = "customWeightsPath",
            description = "Custom weights",
            dataType = DataType.Primitive(PrimitiveType.STRING)
        )
        val activePort = InputPort(
            id = "batchSize",
            name = "batchSize",
            description = "Batch size",
            dataType = DataType.Primitive(PrimitiveType.INT)
        )
        val node = createTestNodeWithInputs(1L, listOf(inactivePort, activePort))
        val flow = Flow(name = "Test Flow", nodes = listOf(node))

        // Suppose both ports return coordinates from getPortBoardPosition
        val getPortBoardPos: (Long, String, Boolean) -> Offset? = { nodeId, portId, isOutput ->
            if (nodeId == 1L && !isOutput) {
                when (portId) {
                    "customWeightsPath" -> Offset(100f, 100f)
                    "batchSize" -> Offset(100f, 105f)
                    else -> null
                }
            } else null
        }

        // Filter specifies customWeightsPath is inactive & unconnected
        val isPortTargetable: (Node, String, Boolean) -> Boolean = { _, portId, _ ->
            portId != "customWeightsPath"
        }

        val (nodeId, portId) = findClosestPort(
            boardPosition = Offset(100f, 101f),
            flow = flow,
            connectionStartIsOutput = true,
            scale = 1f,
            getPortBoardPosition = getPortBoardPos,
            isPortActiveOrConnected = isPortTargetable
        )

        // Must connect to batchSize, NOT customWeightsPath!
        assertEquals(1L, nodeId)
        assertEquals("batchSize", portId)
    }

    @Test
    fun testFindClosestPortIgnoresDisposedPortWithNullCoordinates() {
        val disposedPort = InputPort(
            id = "customWeightsPath",
            name = "customWeightsPath",
            description = "Custom weights",
            dataType = DataType.Primitive(PrimitiveType.STRING)
        )
        val visiblePort = InputPort(
            id = "batchSize",
            name = "batchSize",
            description = "Batch size",
            dataType = DataType.Primitive(PrimitiveType.INT)
        )
        val node = createTestNodeWithInputs(1L, listOf(disposedPort, visiblePort))
        val flow = Flow(name = "Test Flow", nodes = listOf(node))

        // customWeightsPath is uncomposed/disposed, so getPortBoardPosition returns null
        val getPortBoardPos: (Long, String, Boolean) -> Offset? = { nodeId, portId, isOutput ->
            if (nodeId == 1L && portId == "batchSize" && !isOutput) Offset(100f, 100f) else null
        }

        val (nodeId, portId) = findClosestPort(
            boardPosition = Offset(100f, 102f),
            flow = flow,
            connectionStartIsOutput = true,
            scale = 1f,
            getPortBoardPosition = getPortBoardPos
        )

        assertEquals(1L, nodeId)
        assertEquals("batchSize", portId)
    }

    @Test
    fun testFindClosestPortReturnsNullWhenDistant() {
        val inputPort = InputPort(
            id = "batchSize",
            name = "batchSize",
            description = "Batch size",
            dataType = DataType.Primitive(PrimitiveType.INT)
        )
        val node = createTestNodeWithInputs(1L, listOf(inputPort))
        val flow = Flow(name = "Test Flow", nodes = listOf(node))

        val getPortBoardPos: (Long, String, Boolean) -> Offset? = { nodeId, portId, isOutput ->
            if (nodeId == 1L && portId == "batchSize" && !isOutput) Offset(100f, 100f) else null
        }

        val (nodeId, portId) = findClosestPort(
            boardPosition = Offset(250f, 250f),
            flow = flow,
            connectionStartIsOutput = true,
            scale = 1f,
            getPortBoardPosition = getPortBoardPos
        )

        assertNull(nodeId)
        assertNull(portId)
    }

    @Test
    fun testFindClosestPortForOutput() {
        val outputPort = OutputPort(
            id = "result",
            name = "result",
            description = "Result output",
            dataType = DataType.Primitive(PrimitiveType.STRING)
        )
        val node = createTestNodeWithOutputs(2L, listOf(outputPort))
        val flow = Flow(name = "Test Flow", nodes = listOf(node))

        val getPortBoardPos: (Long, String, Boolean) -> Offset? = { nodeId, portId, isOutput ->
            if (nodeId == 2L && portId == "result" && isOutput) Offset(300f, 200f) else null
        }

        val (nodeId, portId) = findClosestPort(
            boardPosition = Offset(302f, 198f),
            flow = flow,
            connectionStartIsOutput = false,
            scale = 1f,
            getPortBoardPosition = getPortBoardPos
        )

        assertEquals(2L, nodeId)
        assertEquals("result", portId)
    }
}
