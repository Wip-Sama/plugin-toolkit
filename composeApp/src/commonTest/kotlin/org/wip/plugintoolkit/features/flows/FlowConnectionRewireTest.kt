package org.wip.plugintoolkit.features.flows

import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.InputPort
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.model.OutputPort
import org.wip.plugintoolkit.features.flows.viewmodel.FlowConnectionManager
import org.wip.plugintoolkit.features.flows.viewmodel.FlowEditorState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FlowConnectionRewireTest {
    private val stringType = DataType.Primitive(PrimitiveType.STRING)
    private val intType = DataType.Primitive(PrimitiveType.INT)
    private val anyType = DataType.Primitive(PrimitiveType.ANY)
    private val incompatibleType = DataType.Object("example.Payload")

    private fun node(id: Long, outputType: DataType? = null, inputType: DataType? = null) = Node.SystemNode(
        id = id,
        position = Offset.Zero,
        title = "Node $id",
        systemAction = "test",
        inputs = inputType?.let { listOf(InputPort("in", "Input", it)) } ?: emptyList(),
        outputs = outputType?.let { listOf(OutputPort("out", "Output", it)) } ?: emptyList()
    )

    private val manager = FlowConnectionManager(null, CoroutineScope(Dispatchers.Unconfined)) {}

    @Test
    fun `rewire replaces a connection atomically when compatible`() {
        val original = Connection(1, "out", 3, "in")
        val state = FlowEditorState(
            flow = Flow(
                "Flow",
                nodes = listOf(node(1, outputType = stringType), node(2, outputType = stringType), node(3, inputType = stringType)),
                connections = listOf(original)
            )
        )

        val result = manager.handleRewireConnection(state, original, 2, "out", 3, "in", false)

        assertEquals(1, result.flow.connections.size)
        assertEquals(2, result.flow.connections.single().sourceNodeId)
        assertTrue(result.hasUnsavedChanges)
    }

    @Test
    fun `invalid rewire preserves the original connection`() {
        val original = Connection(1, "out", 3, "in")
        val state = FlowEditorState(
            flow = Flow(
                "Flow",
                nodes = listOf(node(1, outputType = stringType), node(2, outputType = incompatibleType), node(3, inputType = stringType)),
                connections = listOf(original)
            )
        )

        val result = manager.handleRewireConnection(state, original, 2, "out", 3, "in", false)

        assertEquals(state, result)
        assertEquals(original, result.flow.connections.single())
    }

    @Test
    fun `rewire recalculates wildcard inference without the original edge`() {
        val original = Connection(1, "out", 3, "in")
        val state = FlowEditorState(
            flow = Flow(
                "Flow",
                nodes = listOf(
                    node(1, outputType = stringType),
                    node(2, outputType = incompatibleType),
                    node(3, inputType = anyType)
                ),
                connections = listOf(original)
            ),
            inferredTypes = mapOf((3L to "in") to stringType)
        )

        val result = manager.handleRewireConnection(state, original, 2, "out", 3, "in", false)

        assertEquals(listOf(Connection(2, "out", 3, "in")), result.flow.connections)
    }

    @Test
    fun `rewire can replace the target endpoint`() {
        val original = Connection(1, "out", 2, "in")
        val state = FlowEditorState(
            flow = Flow(
                "Flow",
                nodes = listOf(
                    node(1, outputType = stringType),
                    node(2, inputType = stringType),
                    node(3, inputType = stringType)
                ),
                connections = listOf(original)
            )
        )

        val result = manager.handleRewireConnection(state, original, 1, "out", 3, "in", false)

        assertEquals(Connection(1, "out", 3, "in"), result.flow.connections.single())
    }

    @Test
    fun `rewire onto occupied scalar input replaces its previous edge`() {
        val original = Connection(1, "out", 3, "in")
        val occupied = Connection(2, "out", 4, "in")
        val state = FlowEditorState(
            flow = Flow(
                "Flow",
                nodes = listOf(
                    node(1, outputType = stringType),
                    node(2, outputType = stringType),
                    node(3, inputType = stringType),
                    node(4, inputType = stringType)
                ),
                connections = listOf(original, occupied)
            )
        )

        val result = manager.handleRewireConnection(state, original, 1, "out", 4, "in", false)

        assertEquals(listOf(Connection(1, "out", 4, "in")), result.flow.connections)
    }

    @Test
    fun `rewire preserves array order and keeps indices contiguous`() {
        val arrayType = DataType.Array(stringType)
        val original = Connection(1, "out", 4, "in", orderIndex = 0)
        val state = FlowEditorState(
            flow = Flow(
                "Flow",
                nodes = listOf(
                    node(1, outputType = arrayType),
                    node(2, outputType = arrayType),
                    node(3, outputType = arrayType),
                    node(4, inputType = arrayType),
                    node(5, outputType = arrayType)
                ),
                connections = listOf(
                    original,
                    Connection(2, "out", 4, "in", orderIndex = 1),
                    Connection(3, "out", 4, "in", orderIndex = 2)
                )
            )
        )

        val result = manager.handleRewireConnection(state, original, 5, "out", 4, "in", false)
        val orderedSources = result.flow.connections.sortedBy { it.orderIndex }.map { it.sourceNodeId }

        assertEquals(listOf(5L, 2L, 3L), orderedSources)
        assertEquals(listOf(0, 1, 2), result.flow.connections.sortedBy { it.orderIndex }.map { it.orderIndex })
    }

    @Test
    fun `rewire to duplicate array edge preserves the original`() {
        val arrayType = DataType.Array(stringType)
        val original = Connection(1, "out", 3, "in", orderIndex = 0)
        val duplicate = Connection(2, "out", 3, "in", orderIndex = 1)
        val state = FlowEditorState(
            flow = Flow(
                "Flow",
                nodes = listOf(
                    node(1, outputType = arrayType),
                    node(2, outputType = arrayType),
                    node(3, inputType = arrayType)
                ),
                connections = listOf(original, duplicate)
            )
        )

        val result = manager.handleRewireConnection(state, original, 2, "out", 3, "in", false)

        assertEquals(state, result)
    }

    @Test
    fun `convertible rewire remains atomic until conversion is confirmed`() {
        val original = Connection(1, "out", 3, "in")
        val state = FlowEditorState(
            flow = Flow(
                "Flow",
                nodes = listOf(
                    node(1, outputType = intType),
                    node(2, outputType = stringType),
                    node(3, inputType = intType)
                ),
                connections = listOf(original)
            ),
            nextId = 10
        )

        val pending = manager.handleRewireConnection(state, original, 2, "out", 3, "in", false)
        assertEquals(listOf(original), pending.flow.connections)
        assertEquals(original, assertNotNull(pending.pendingConnection).originalConnection)

        val converted = manager.handleAutoConvertAndConnect(
            pending.copy(pendingConnection = null),
            2,
            "out",
            3,
            "in",
            original
        )
        assertTrue(original !in converted.flow.connections)
        assertTrue(converted.flow.nodes.any { it.id == 10L && it is Node.SystemNode && it.systemAction == "convert" })
        assertEquals(2, converted.flow.connections.size)
    }

    @Test
    fun `direct connect rejects a convertible pair without a converter`() {
        val state = FlowEditorState(
            flow = Flow(
                "Flow",
                nodes = listOf(node(1, outputType = stringType), node(2, inputType = intType))
            )
        )

        assertEquals(state, manager.handleConnectPorts(state, 1, "out", 2, "in"))
    }

    @Test
    fun `direct connect uses inferred array target when preserving existing edges`() {
        val arrayType = DataType.Array(stringType)
        val existing = Connection(1, "out", 3, "in", orderIndex = 0)
        val state = FlowEditorState(
            flow = Flow(
                "Flow",
                nodes = listOf(
                    node(1, outputType = arrayType),
                    node(2, outputType = arrayType),
                    node(3, inputType = anyType)
                ),
                connections = listOf(existing)
            ),
            inferredTypes = mapOf((3L to "in") to arrayType)
        )

        val result = manager.handleConnectPorts(state, 2, "out", 3, "in")

        assertEquals(2, result.flow.connections.size)
        assertEquals(setOf(1L, 2L), result.flow.connections.map { it.sourceNodeId }.toSet())
        assertEquals(listOf(0, 1), result.flow.connections.sortedBy { it.orderIndex }.map { it.orderIndex })
    }

    @Test
    fun `cyclic rewire preserves the original`() {
        val original = Connection(4, "out", 1, "in")
        val state = FlowEditorState(
            flow = Flow(
                "Flow",
                nodes = listOf(
                    node(1, outputType = stringType, inputType = stringType),
                    node(2, outputType = stringType, inputType = stringType),
                    node(3, outputType = stringType, inputType = stringType),
                    node(4, outputType = stringType)
                ),
                connections = listOf(
                    original,
                    Connection(1, "out", 2, "in"),
                    Connection(2, "out", 3, "in")
                )
            )
        )

        val result = manager.handleRewireConnection(state, original, 3, "out", 1, "in", false)

        assertEquals(state, result)
    }
}
