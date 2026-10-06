package org.wip.plugintoolkit.features.flows

import androidx.compose.ui.geometry.Offset as ComposeOffset
import org.wip.plugintoolkit.features.flows.model.Offset as ModelOffset
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.ui.toComposeOffset
import org.wip.plugintoolkit.features.flows.viewmodel.FlowEditorState
import org.wip.plugintoolkit.features.flows.viewmodel.FlowNodeManager
import kotlinx.serialization.json.JsonPrimitive
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.features.flows.model.InputPort
import org.wip.plugintoolkit.features.flows.model.OutputPort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FlowNodeManagerTest {

    private val manager = FlowNodeManager()

    private fun createTestNode(id: Long, position: ModelOffset = ModelOffset(100f, 100f)): Node.SystemNode {
        return Node.SystemNode(
            id = id,
            position = position,
            title = "Test Node $id",
            systemAction = "test",
            inputs = listOf(
                InputPort(
                    id = "inPort",
                    name = "Input",
                    dataType = DataType.Primitive(PrimitiveType.INT)
                )
            ),
            outputs = listOf(
                OutputPort(
                    id = "outPort",
                    name = "Output",
                    dataType = DataType.Primitive(PrimitiveType.INT)
                )
            )
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
        val node = createTestNode(1L, ModelOffset(100f, 100f))
        val initialState = FlowEditorState(
            flow = Flow("test", nodes = listOf(node))
        )

        val delta1 = ComposeOffset(15f, 25f)
        val state1 = manager.handleMoveNode(initialState, 1L, delta1, snap = false, showGhost = true)

        assertEquals(1L, state1.draggedNodeId)
        assertEquals(ModelOffset(15f, 25f), state1.currentDragOffset)

        val delta2 = ComposeOffset(10f, -5f)
        val state2 = manager.handleMoveNode(state1, 1L, delta2, snap = false, showGhost = true)

        assertEquals(1L, state2.draggedNodeId)
        assertEquals(ModelOffset(25f, 20f), state2.currentDragOffset)
    }

    @Test
    fun testPanningWhileDraggingNodeMaintainsScreenPosition() {
        val node = createTestNode(1L, ModelOffset(100f, 100f))
        val initialState = FlowEditorState(
            flow = Flow("test", nodes = listOf(node)),
            offset = ModelOffset(0f, 0f),
            scale = 1.0f
        )

        // 1. Start moving node 1L by delta = (10, 10)
        val stateAfterMove = manager.handleMoveNode(initialState, 1L, ComposeOffset(10f, 10f), snap = false, showGhost = true)
        val initialScreenPos = (node.position.toComposeOffset() + stateAfterMove.currentDragOffset.toComposeOffset()) * stateAfterMove.scale + stateAfterMove.offset.toComposeOffset()
        assertEquals(ComposeOffset(110f, 110f), initialScreenPos)

        // 2. Pan canvas by panDelta = (50, 50) while node is being dragged (draggedNodeId = 1L)
        val panDelta = ComposeOffset(50f, 50f)
        val stateAfterPan = manager.handlePan(stateAfterMove, panDelta)

        // Rendered position on screen MUST remain (110, 110) under the cursor!
        val screenPosAfterPan = (node.position.toComposeOffset() + stateAfterPan.currentDragOffset.toComposeOffset()) * stateAfterPan.scale + stateAfterPan.offset.toComposeOffset()
        assertEquals(initialScreenPos, screenPosAfterPan)
    }

    @Test
    fun testEndMoveNodeSnapsPositionAndResetsDragState() {
        val node = createTestNode(1L, ModelOffset(100f, 100f))
        val initialState = FlowEditorState(
            flow = Flow("test", nodes = listOf(node))
        )

        val movedState = manager.handleMoveNode(initialState, 1L, ComposeOffset(12f, 18f), snap = false, showGhost = true)
        val endedState = manager.handleEndMoveNode(movedState, 1L, 1f)

        assertNull(endedState.draggedNodeId)
        assertEquals(ModelOffset.Zero, endedState.currentDragOffset)
        assertNull(endedState.ghostPosition)
        assertTrue(endedState.hasUnsavedChanges)

        val updatedNode = endedState.flow.nodes.first()
        // ModelOffset(112, 118) snapped to 50 grid -> ModelOffset(100, 100)
        assertEquals(ModelOffset(100f, 100f), updatedNode.position)
    }

    @Test
    fun testGroupMoveUpdatesAllSelectedNodes() {
        val node1 = createTestNode(1L, ModelOffset(0f, 0f))
        val node2 = createTestNode(2L, ModelOffset(100f, 100f))
        val initialState = FlowEditorState(
            flow = Flow("test", nodes = listOf(node1, node2)),
            selectedNodeIds = setOf(1L, 2L)
        )

        val movedState = manager.handleMoveNode(initialState, 1L, ComposeOffset(50f, 50f), snap = false, showGhost = true)
        val endedState = manager.handleEndMoveNode(movedState, 1L, 1f)

        val updatedNode1 = endedState.flow.nodes.find { it.id == 1L }!!
        val updatedNode2 = endedState.flow.nodes.find { it.id == 2L }!!

        assertEquals(ModelOffset(50f, 50f), updatedNode1.position)
        assertEquals(ModelOffset(150f, 150f), updatedNode2.position)
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
        val node1 = createTestNode(1L, ModelOffset(10f, 10f))
        val node2 = createTestNode(2L, ModelOffset(20f, 20f))
        val initialState = FlowEditorState(
            flow = Flow("test", nodes = listOf(node1, node2)),
            selectedNodeIds = emptySet()
        )

        val newState = manager.handleBringToFront(initialState, 1L)

        assertEquals(listOf(2L, 1L), newState.flow.nodes.map { it.id })
        assertEquals(setOf(1L), newState.selectedNodeIds)
        assertEquals(ModelOffset(10f, 10f), newState.flow.nodes.find { it.id == 1L }!!.position)
    }

    @Test
    fun testUpdateInputPortValueImplicitlySelectsNodeAndClearsOtherSelections() {
        val node1 = createTestNode(1L)
        val node2 = createTestNode(2L)
        val initialState = FlowEditorState(
            flow = Flow("test", nodes = listOf(node1, node2)),
            selectedNodeIds = setOf(2L),
            selectedPointIds = setOf(99L),
            selectedGroupIds = setOf(88L),
            selectedLabelIds = setOf(77L)
        )

        val newState = manager.handleUpdateInputPortValue(
            currentState = initialState,
            nodeId = 1L,
            portId = "inPort",
            value = JsonPrimitive(123)
        )

        assertEquals(setOf(1L), newState.selectedNodeIds)
        assertTrue(newState.selectedPointIds.isEmpty())
        assertTrue(newState.selectedGroupIds.isEmpty())
        assertTrue(newState.selectedLabelIds.isEmpty())
        assertTrue(newState.hasUnsavedChanges)
    }

    @Test
    fun testUpdateInputPortDefaultImplicitlySelectsNodeAndClearsOtherSelections() {
        val node1 = createTestNode(1L)
        val initialState = FlowEditorState(
            flow = Flow("test", nodes = listOf(node1)),
            selectedNodeIds = emptySet(),
            selectedPointIds = setOf(10L)
        )

        val newState = manager.handleUpdateInputPortDefault(
            currentState = initialState,
            nodeId = 1L,
            portId = "inPort",
            defaultValue = JsonPrimitive(55)
        )

        assertEquals(setOf(1L), newState.selectedNodeIds)
        assertTrue(newState.selectedPointIds.isEmpty())
        assertTrue(newState.hasUnsavedChanges)
    }

    @Test
    fun testUpdateBoundaryNodeImplicitlySelectsNodeAndClearsOtherSelections() {
        val boundaryNode = Node.FlowInputNode(
            id = 1L,
            position = ModelOffset(0f, 0f),
            outputs = listOf(
                OutputPort(
                    id = "out",
                    name = "Output",
                    dataType = DataType.Primitive(PrimitiveType.STRING)
                )
            )
        )
        val initialState = FlowEditorState(
            flow = Flow("test", nodes = listOf(boundaryNode)),
            selectedNodeIds = emptySet(),
            selectedPointIds = setOf(5L)
        )

        val newState = manager.handleUpdateBoundaryNode(
            currentState = initialState,
            nodeId = 1L,
            portName = "NewName",
            dataType = DataType.Primitive(PrimitiveType.STRING),
            semanticTypes = emptyList(),
            constraints = null,
            isList = false,
            isRequired = true,
            defaultValue = JsonPrimitive("defaultVal")
        )

        assertEquals(setOf(1L), newState.selectedNodeIds)
        assertTrue(newState.selectedPointIds.isEmpty())
        assertTrue(newState.hasUnsavedChanges)
    }

    @Test
    fun testUpdateSystemNodeSettingsImplicitlySelectsNodeAndClearsOtherSelections() {
        val node = createTestNode(1L)
        val initialState = FlowEditorState(
            flow = Flow("test", nodes = listOf(node)),
            selectedNodeIds = emptySet(),
            selectedGroupIds = setOf(3L)
        )

        val newState = manager.handleUpdateSystemNodeSettings(
            currentState = initialState,
            nodeId = 1L,
            portId = "outPort",
            semanticTypes = emptyList(),
            inputPortId = "inPort",
            extensions = listOf("png")
        )

        assertEquals(setOf(1L), newState.selectedNodeIds)
        assertTrue(newState.selectedGroupIds.isEmpty())
        assertTrue(newState.hasUnsavedChanges)
    }

    @Test
    fun testHandleBringToFrontWithoutCtrlSelectsSingleNodeAndClearsOtherSelections() {
        val node1 = createTestNode(1L)
        val node2 = createTestNode(2L)
        val initialState = FlowEditorState(
            flow = Flow("test", nodes = listOf(node1, node2)),
            selectedNodeIds = setOf(2L),
            selectedPointIds = setOf(10L),
            selectedGroupIds = setOf(20L),
            selectedLabelIds = setOf(30L)
        )

        val newState = manager.handleBringToFront(initialState, nodeId = 1L, isCtrlPressed = false)

        assertEquals(setOf(1L), newState.selectedNodeIds)
        assertTrue(newState.selectedPointIds.isEmpty())
        assertTrue(newState.selectedGroupIds.isEmpty())
        assertTrue(newState.selectedLabelIds.isEmpty())
        assertEquals(1L, newState.flow.nodes.last().id)
    }

    @Test
    fun testHandleBringToFrontWithCtrlAddsNodeToSelectionAndPreservesOtherSelections() {
        val node1 = createTestNode(1L)
        val node2 = createTestNode(2L)
        val initialState = FlowEditorState(
            flow = Flow("test", nodes = listOf(node1, node2)),
            selectedNodeIds = setOf(2L),
            selectedPointIds = setOf(10L),
            selectedGroupIds = setOf(20L),
            selectedLabelIds = setOf(30L)
        )

        val newState = manager.handleBringToFront(initialState, nodeId = 1L, isCtrlPressed = true)

        assertEquals(setOf(2L, 1L), newState.selectedNodeIds)
        assertEquals(setOf(10L), newState.selectedPointIds)
        assertEquals(setOf(20L), newState.selectedGroupIds)
        assertEquals(setOf(30L), newState.selectedLabelIds)
        assertEquals(1L, newState.flow.nodes.last().id)
    }

    @Test
    fun testHandleBringToFrontWithCtrlRemovesNodeFromSelectionAndPreservesOtherSelections() {
        val node1 = createTestNode(1L)
        val node2 = createTestNode(2L)
        val initialState = FlowEditorState(
            flow = Flow("test", nodes = listOf(node1, node2)),
            selectedNodeIds = setOf(1L, 2L),
            selectedPointIds = setOf(10L),
            selectedGroupIds = setOf(20L),
            selectedLabelIds = setOf(30L)
        )

        val newState = manager.handleBringToFront(initialState, nodeId = 1L, isCtrlPressed = true)

        assertEquals(setOf(2L), newState.selectedNodeIds)
        assertEquals(setOf(10L), newState.selectedPointIds)
        assertEquals(setOf(20L), newState.selectedGroupIds)
        assertEquals(setOf(30L), newState.selectedLabelIds)
        assertEquals(1L, newState.flow.nodes.last().id)
    }
}
