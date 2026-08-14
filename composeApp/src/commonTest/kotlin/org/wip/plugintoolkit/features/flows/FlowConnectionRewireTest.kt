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
import kotlin.test.assertTrue

class FlowConnectionRewireTest {
    private val stringType = DataType.Primitive(PrimitiveType.STRING)
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

        val result = manager.handleRewireConnection(state, original, 2, "out", 3, "in")

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

        val result = manager.handleRewireConnection(state, original, 2, "out", 3, "in")

        assertEquals(state, result)
        assertEquals(original, result.flow.connections.single())
    }
}
