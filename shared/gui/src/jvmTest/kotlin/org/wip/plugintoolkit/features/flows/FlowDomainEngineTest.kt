package org.wip.plugintoolkit.features.flows

import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.FlowGroup
import org.wip.plugintoolkit.features.flows.model.FlowJunction
import org.wip.plugintoolkit.features.flows.model.FlowLabel
import org.wip.plugintoolkit.features.flows.model.InputPort
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.model.Offset as ModelOffset
import org.wip.plugintoolkit.features.flows.model.OutputPort
import org.wip.plugintoolkit.features.flows.viewmodel.FlowClipboardContent
import org.wip.plugintoolkit.features.flows.viewmodel.FlowConnectionManager
import org.wip.plugintoolkit.features.flows.viewmodel.FlowEditorState
import org.wip.plugintoolkit.features.flows.viewmodel.FlowNodeManager
import org.wip.plugintoolkit.features.flows.viewmodel.InMemoryFlowClipboardService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Headless JVM test suite for Phase 2 domain decoupling.
 * Verifies graph mutations, connections, cycle avoidance, and clipboard isolation
 * completely independently of Compose runtime or Koin context.
 */
class FlowDomainEngineTest {

    private fun createTestNode(id: Long, pos: ModelOffset = ModelOffset(100f, 100f)): Node.SystemNode {
        return Node.SystemNode(
            id = id,
            position = pos,
            title = "Node $id",
            systemAction = "test",
            inputs = listOf(
                InputPort("in", "Input", DataType.Primitive(PrimitiveType.INT))
            ),
            outputs = listOf(
                OutputPort("out", "Output", DataType.Primitive(PrimitiveType.INT))
            )
        )
    }

    @Test
    fun testHeadlessNodeAdditionAndMove() {
        val nodeManager = FlowNodeManager()
        val initialState = FlowEditorState(flow = Flow("HeadlessFlow"))

        val node1 = createTestNode(1L, ModelOffset(50f, 50f))
        val stateWithNode = nodeManager.handleAddNode(initialState, node1, density = 1f)

        assertEquals(1, stateWithNode.flow.nodes.size)
        assertEquals(1L, stateWithNode.flow.nodes.first().id)
        assertTrue(stateWithNode.hasUnsavedChanges)

        // Move node using pure domain ModelOffset
        val delta = ModelOffset(35f, 45f)
        val movedState = nodeManager.handleMoveNode(stateWithNode, 1L, delta, snap = false, showGhost = true)

        assertEquals(1L, movedState.draggedNodeId)
        assertEquals(delta, movedState.currentDragOffset)
        assertNotNull(movedState.ghostPosition)

        // End move with snapping (50f grid: 50+35=85 -> 100, 50+45=95 -> 100)
        val endedState = nodeManager.handleEndMoveNode(movedState, 1L, density = 1f)
        assertNull(endedState.draggedNodeId)
        assertEquals(ModelOffset.Zero, endedState.currentDragOffset)
        assertEquals(ModelOffset(100f, 100f), endedState.flow.nodes.first().position)
    }

    @Test
    fun testHeadlessGroupBoundsCaptureJunctionsWithoutComposeGeometry() {
        val nodeManager = FlowNodeManager()
        val group = FlowGroup(
            id = 10L,
            title = "Container Group",
            position = ModelOffset(100f, 100f),
            size = ModelOffset(200f, 200f) // covers [100..300, 100..300]
        )
        val insideJunc = FlowJunction(id = 20L, position = ModelOffset(150f, 150f))
        val outsideJunc = FlowJunction(id = 21L, position = ModelOffset(500f, 500f))

        val state = FlowEditorState(
            flow = Flow("GroupTest", groups = listOf(group), junctions = listOf(insideJunc, outsideJunc)),
            selectedGroupIds = setOf(10L)
        )

        val moveState = nodeManager.handleMoveNode(state, 10L, ModelOffset(10f, 10f), snap = false, showGhost = false)

        assertTrue(moveState.capturedJunctionIds.contains(20L), "Junction inside group bounding box must be captured")
        assertFalse(moveState.capturedJunctionIds.contains(21L), "Junction outside group must not be captured")
    }

    @Test
    fun testHeadlessConnectionManagerCreatesValidConnection() {
        val connectionManager = FlowConnectionManager() // headless: no scope, no notificationService
        val node1 = createTestNode(1L)
        val node2 = createTestNode(2L)

        val state = FlowEditorState(
            flow = Flow("ConnTest", nodes = listOf(node1, node2))
        )

        val connectedState = connectionManager.handleConnectPorts(
            currentState = state,
            sourceNodeId = 1L,
            sourcePortId = "out",
            targetNodeId = 2L,
            targetPortId = "in"
        )

        assertEquals(1, connectedState.flow.connections.size)
        val conn = connectedState.flow.connections.first()
        assertEquals(1L, conn.sourceNodeId)
        assertEquals("out", conn.sourcePortId)
        assertEquals(2L, conn.targetNodeId)
        assertEquals("in", conn.targetPortId)
        assertTrue(connectedState.hasUnsavedChanges)
    }

    @Test
    fun testHeadlessConnectionManagerEnforcesDAGAndRejectsCycles() {
        val connectionManager = FlowConnectionManager()
        val node1 = createTestNode(1L)
        val node2 = createTestNode(2L)
        val existingConn = Connection(1L, "out", 2L, "in")

        val state = FlowEditorState(
            flow = Flow("CycleTest", nodes = listOf(node1, node2), connections = listOf(existingConn))
        )

        // Attempt reverse connection from node 2 to node 1 -> creates a cycle
        val cyclicState = connectionManager.handleConnectPorts(
            currentState = state,
            sourceNodeId = 2L,
            sourcePortId = "out",
            targetNodeId = 1L,
            targetPortId = "in"
        )

        // Connection must be rejected, preserving existing connections
        assertEquals(1, cyclicState.flow.connections.size)
        assertEquals(existingConn, cyclicState.flow.connections.first())
    }

    @Test
    fun testHeadlessConnectionManagerRejectsIncompatibleDataTypes() {
        val connectionManager = FlowConnectionManager()
        val node1 = Node.SystemNode(
            id = 1L,
            position = ModelOffset.Zero,
            title = "N1",
            systemAction = "a",
            inputs = emptyList(),
            outputs = listOf(OutputPort("out", "Out", DataType.Primitive(PrimitiveType.BOOLEAN)))
        )
        val node2 = Node.SystemNode(
            id = 2L,
            position = ModelOffset.Zero,
            title = "N2",
            systemAction = "b",
            inputs = listOf(InputPort("in", "In", DataType.Primitive(PrimitiveType.INT))),
            outputs = emptyList()
        )

        val state = FlowEditorState(flow = Flow("TypeTest", nodes = listOf(node1, node2)))
        val resultState = connectionManager.handleConnectPorts(state, 1L, "out", 2L, "in")

        assertEquals(0, resultState.flow.connections.size, "Incompatible types must not connect")
    }

    @Test
    fun testInMemoryClipboardServiceInstanceIsolationPreventsStaticStateLeakage() {
        val clipboardA = InMemoryFlowClipboardService()
        val clipboardB = InMemoryFlowClipboardService()

        assertFalse(clipboardA.hasContents())
        assertFalse(clipboardB.hasContents())

        val node = createTestNode(1L)
        clipboardA.copy(
            nodes = listOf(node),
            connections = emptyList(),
            groups = emptyList(),
            labels = emptyList(),
            junctions = emptyList()
        )

        assertTrue(clipboardA.hasContents(), "Clipboard A must contain copied elements")
        assertFalse(clipboardB.hasContents(), "Clipboard B must remain completely empty (no static leakage)")
        assertEquals(1, clipboardA.getContents().nodes.size)
        assertEquals(0, clipboardB.getContents().nodes.size)

        clipboardA.clear()
        assertFalse(clipboardA.hasContents())
    }

    @Test
    fun testClipboardContentModel() {
        val content = FlowClipboardContent(
            nodes = listOf(createTestNode(1L)),
            connections = listOf(Connection(1L, "out", 2L, "in")),
            groups = listOf(FlowGroup(10L, "G", ModelOffset.Zero, ModelOffset(100f, 100f))),
            labels = listOf(FlowLabel(20L, "L", ModelOffset.Zero)),
            junctions = listOf(FlowJunction(30L, ModelOffset.Zero))
        )

        assertTrue(content.isNotEmpty)
        assertFalse(content.isEmpty)
        assertEquals(1, content.nodes.size)
        assertEquals(1, content.connections.size)
        assertEquals(1, content.groups.size)
        assertEquals(1, content.labels.size)
        assertEquals(1, content.junctions.size)
    }
}
