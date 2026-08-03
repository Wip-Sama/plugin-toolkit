package org.wip.plugintoolkit.features.flows

import androidx.compose.ui.geometry.Offset
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.viewmodel.FlowEditorState
import org.wip.plugintoolkit.features.flows.viewmodel.FlowNodeManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FlowNodeManagerTest {

    private val manager = FlowNodeManager()

    private fun createTestNode(id: Long, position: Offset = Offset(100f, 100f)): Node.SystemNode {
        return Node.SystemNode(
            id = id,
            position = position,
            title = "Test Node $id",
            systemAction = "test",
            inputs = emptyList(),
            outputs = emptyList()
        )
    }

    @Test
    fun testAddNode() {
        val initialState = FlowEditorState()
        val node = createTestNode(1L)

        val newState = manager.handleAddNode(initialState, node, 1f)

        assertEquals(1, newState.flow.nodes.size)
        assertEquals(1L, newState.flow.nodes.first().id)
        assertEquals(1L, newState.nextId)
        assertTrue(newState.hasUnsavedChanges)
    }

    @Test
    fun testMoveNodeAccumulatesDelta() {
        val node = createTestNode(1L, Offset(100f, 100f))
        val initialState = FlowEditorState(
            flow = Flow("test", nodes = listOf(node))
        )

        val delta1 = Offset(15f, 25f)
        val state1 = manager.handleMoveNode(initialState, 1L, delta1, snap = false, showGhost = true)

        assertEquals(1L, state1.draggedNodeId)
        assertEquals(Offset(15f, 25f), state1.currentDragOffset)

        val delta2 = Offset(10f, -5f)
        val state2 = manager.handleMoveNode(state1, 1L, delta2, snap = false, showGhost = true)

        assertEquals(1L, state2.draggedNodeId)
        assertEquals(Offset(25f, 20f), state2.currentDragOffset)
    }

    @Test
    fun testEndMoveNodeSnapsPositionAndResetsDragState() {
        val node = createTestNode(1L, Offset(100f, 100f))
        val initialState = FlowEditorState(
            flow = Flow("test", nodes = listOf(node))
        )

        val movedState = manager.handleMoveNode(initialState, 1L, Offset(12f, 18f), snap = false, showGhost = true)
        val endedState = manager.handleEndMoveNode(movedState, 1L, 1f)

        assertNull(endedState.draggedNodeId)
        assertEquals(Offset.Zero, endedState.currentDragOffset)
        assertNull(endedState.ghostPosition)
        assertTrue(endedState.hasUnsavedChanges)

        val updatedNode = endedState.flow.nodes.first()
        // Offset(112, 118) snapped to 50 grid -> Offset(100, 100)
        assertEquals(Offset(100f, 100f), updatedNode.position)
    }

    @Test
    fun testGroupMoveUpdatesAllSelectedNodes() {
        val node1 = createTestNode(1L, Offset(0f, 0f))
        val node2 = createTestNode(2L, Offset(100f, 100f))
        val initialState = FlowEditorState(
            flow = Flow("test", nodes = listOf(node1, node2)),
            selectedNodeIds = setOf(1L, 2L)
        )

        val movedState = manager.handleMoveNode(initialState, 1L, Offset(50f, 50f), snap = false, showGhost = true)
        val endedState = manager.handleEndMoveNode(movedState, 1L, 1f)

        val updatedNode1 = endedState.flow.nodes.find { it.id == 1L }!!
        val updatedNode2 = endedState.flow.nodes.find { it.id == 2L }!!

        assertEquals(Offset(50f, 50f), updatedNode1.position)
        assertEquals(Offset(150f, 150f), updatedNode2.position)
    }

    @Test
    fun testDeleteNodeRemovesNodeAndConnections() {
        val node1 = createTestNode(1L)
        val node2 = createTestNode(2L)
        val connection = Connection(1L, "out", 2L, "in")
        val initialState = FlowEditorState(
            flow = Flow("test", nodes = listOf(node1, node2), connections = listOf(connection)),
            selectedNodeIds = setOf(1L)
        )

        val newState = manager.handleDeleteNode(initialState, 1L)

        assertEquals(1, newState.flow.nodes.size)
        assertEquals(2L, newState.flow.nodes.first().id)
        assertTrue(newState.flow.connections.isEmpty())
        assertTrue(newState.selectedNodeIds.isEmpty())
        assertTrue(newState.hasUnsavedChanges)
    }

    @Test
    fun testBringToFrontMovesNodeToEndOfListWithoutAlteringPositions() {
        val node1 = createTestNode(1L, Offset(10f, 10f))
        val node2 = createTestNode(2L, Offset(20f, 20f))
        val initialState = FlowEditorState(
            flow = Flow("test", nodes = listOf(node1, node2)),
            selectedNodeIds = emptySet()
        )

        val newState = manager.handleBringToFront(initialState, 1L)

        assertEquals(listOf(2L, 1L), newState.flow.nodes.map { it.id })
        assertEquals(setOf(1L), newState.selectedNodeIds)
        assertEquals(Offset(10f, 10f), newState.flow.nodes.find { it.id == 1L }!!.position)
    }
}
