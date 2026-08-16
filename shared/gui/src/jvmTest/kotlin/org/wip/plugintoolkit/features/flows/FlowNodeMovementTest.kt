package org.wip.plugintoolkit.features.flows

import androidx.compose.ui.geometry.Offset as ComposeOffset
import org.wip.plugintoolkit.features.flows.model.Offset as ModelOffset
import kotlinx.coroutines.test.runTest
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.viewmodel.FlowEditorState
import org.wip.plugintoolkit.features.flows.viewmodel.FlowNodeManager
import kotlin.test.Test
import kotlin.test.assertEquals

class FlowNodeMovementTest {

    private val nodeManager = FlowNodeManager()

    @Test
    fun testNodeMovementWithDeltaScaling() = runTest {
        val node1 = Node.SystemNode(
            id = 1L,
            position = ModelOffset(100f, 100f),
            title = "Node 1",
            systemAction = "save",
            inputs = emptyList(),
            outputs = emptyList()
        )
        val node2 = Node.SystemNode(
            id = 2L,
            position = ModelOffset(150f, 150f),
            title = "Node 2",
            systemAction = "save",
            inputs = emptyList(),
            outputs = emptyList()
        )
        val flow = Flow(name = "Test", nodes = listOf(node1, node2))
        var state = FlowEditorState(flow = flow, scale = 0.4f, selectedNodeIds = setOf(1L))

        // Bring node 1 to front
        state = nodeManager.handleBringToFront(state, 1L)

        // Model delta is 50f
        val modelDelta = ComposeOffset(50f, 50f)

        state = nodeManager.handleMoveNode(state, 1L, modelDelta, snap = false, showGhost = false)
        assertEquals(ModelOffset(50f, 50f), state.currentDragOffset)

        state = nodeManager.handleEndMoveNode(state, 1L, density = 1f)

        val movedNode = state.flow.nodes.find { it.id == 1L }!!
        assertEquals(ModelOffset(150f, 150f), movedNode.position)
    }

    @Test
    fun testBringToFrontOrder() = runTest {
        val node1 = Node.SystemNode(
            id = 1L,
            position = ModelOffset(100f, 100f),
            title = "Node 1",
            systemAction = "save",
            inputs = emptyList(),
            outputs = emptyList()
        )
        val node2 = Node.SystemNode(
            id = 2L,
            position = ModelOffset(100f, 100f),
            title = "Node 2",
            systemAction = "save",
            inputs = emptyList(),
            outputs = emptyList()
        )
        val flow = Flow(name = "Test", nodes = listOf(node1, node2))
        var state = FlowEditorState(flow = flow)

        // Bring node 1 to front so it becomes the last item in the list (rendered on top)
        state = nodeManager.handleBringToFront(state, 1L)
        assertEquals(1L, state.flow.nodes.last().id)
    }

    @Test
    fun testGroupNodeMovement() = runTest {
        val node1 = Node.SystemNode(
            id = 1L,
            position = ModelOffset(100f, 100f),
            title = "Node 1",
            systemAction = "save",
            inputs = emptyList(),
            outputs = emptyList()
        )
        val node2 = Node.SystemNode(
            id = 2L,
            position = ModelOffset(200f, 200f),
            title = "Node 2",
            systemAction = "save",
            inputs = emptyList(),
            outputs = emptyList()
        )
        val flow = Flow(name = "Test", nodes = listOf(node1, node2))
        var state = FlowEditorState(flow = flow, selectedNodeIds = setOf(1L, 2L))

        // Bring node 1 to front while part of selected group
        state = nodeManager.handleBringToFront(state, 1L)
        assertEquals(setOf(1L, 2L), state.selectedNodeIds)

        // Move node 1 with delta
        state = nodeManager.handleMoveNode(state, 1L, ComposeOffset(50f, 50f), snap = false, showGhost = false)
        state = nodeManager.handleEndMoveNode(state, 1L, density = 1f)

        val movedNode1 = state.flow.nodes.find { it.id == 1L }!!
        val movedNode2 = state.flow.nodes.find { it.id == 2L }!!

        assertEquals(ModelOffset(150f, 150f), movedNode1.position)
        assertEquals(ModelOffset(250f, 250f), movedNode2.position)
    }
}
