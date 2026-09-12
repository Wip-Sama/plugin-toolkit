package org.wip.plugintoolkit.features.flows

import androidx.compose.ui.geometry.Offset as ComposeOffset
import kotlinx.coroutines.test.runTest
import org.wip.plugintoolkit.features.flows.history.MoveBoardElementsCommand
import org.wip.plugintoolkit.features.flows.history.UpdateGroupCommand
import org.wip.plugintoolkit.features.flows.history.UpdateLabelCommand
import org.wip.plugintoolkit.features.flows.model.BoardElement
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.FlowGroup
import org.wip.plugintoolkit.features.flows.model.FlowJunction
import org.wip.plugintoolkit.features.flows.model.FlowLabel
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.model.Offset as ModelOffset
import org.wip.plugintoolkit.features.flows.model.ResizableBoardElement
import org.wip.plugintoolkit.features.flows.ui.canvas.ConnectionHitTester
import org.wip.plugintoolkit.features.flows.viewmodel.FlowEditorState
import org.wip.plugintoolkit.features.flows.viewmodel.FlowNodeManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BoardElementArchitectureTest {

    private val nodeManager = FlowNodeManager()

    @Test
    fun testBoardElementPolymorphismAndFlowQueries() {
        val node = Node.SystemNode(
            id = 1L,
            position = ModelOffset(100f, 100f),
            title = "Node 1",
            systemAction = "action",
            inputs = emptyList(),
            outputs = emptyList()
        )
        val group = FlowGroup(
            id = 2L,
            title = "Group A",
            position = ModelOffset(200f, 200f),
            size = ModelOffset(300f, 200f),
            nodeIds = listOf(1L)
        )
        val label = FlowLabel(
            id = 3L,
            text = "Label A",
            position = ModelOffset(50f, 50f)
        )
        val junction = FlowJunction(
            id = 4L,
            position = ModelOffset(150f, 150f)
        )

        // Verify interface inheritance
        assertTrue((node as Any) is BoardElement, "Node must implement BoardElement")
        assertTrue((group as Any) is BoardElement, "FlowGroup must implement BoardElement")
        assertTrue((group as Any) is ResizableBoardElement, "FlowGroup must implement ResizableBoardElement")
        assertTrue((label as Any) is BoardElement, "FlowLabel must implement BoardElement")
        assertTrue((junction as Any) is BoardElement, "FlowJunction must implement BoardElement")

        assertEquals(ModelOffset(300f, 200f), group.size)

        val flow = Flow(
            name = "Test Flow",
            nodes = listOf(node),
            groups = listOf(group),
            labels = listOf(label),
            junctions = listOf(junction)
        )

        val allElements = flow.allBoardElements()
        assertEquals(4, allElements.size)
        assertEquals(setOf(1L, 2L, 3L, 4L), allElements.map { it.id }.toSet())

        assertEquals(node, flow.findBoardElement(1L))
        assertEquals(group, flow.findBoardElement(2L))
        assertEquals(label, flow.findBoardElement(3L))
        assertEquals(junction, flow.findBoardElement(4L))

        // Test updating through withUpdatedBoardElement
        val updatedNode = node.copyWithPosition(ModelOffset(120f, 120f))
        val flowUpdatedNode = flow.withUpdatedBoardElement(updatedNode)
        assertEquals(ModelOffset(120f, 120f), flowUpdatedNode.nodes.first().position)

        val updatedGroup = group.copyWithPosition(ModelOffset(220f, 220f))
        val flowUpdatedGroup = flow.withUpdatedBoardElement(updatedGroup)
        assertEquals(ModelOffset(220f, 220f), flowUpdatedGroup.groups.first().position)

        val updatedLabel = label.copyWithPosition(ModelOffset(70f, 70f))
        val flowUpdatedLabel = flow.withUpdatedBoardElement(updatedLabel)
        assertEquals(ModelOffset(70f, 70f), flowUpdatedLabel.labels.first().position)

        val updatedJunction = junction.copyWithPosition(ModelOffset(170f, 170f))
        val flowUpdatedJunction = flow.withUpdatedBoardElement(updatedJunction)
        assertEquals(ModelOffset(170f, 170f), flowUpdatedJunction.junctions.first().position)
    }

    @Test
    fun testMultiElementSelectionMovementWithGroupAndNode() = runTest {
        val node1 = Node.SystemNode(
            id = 1L,
            position = ModelOffset(100f, 100f),
            title = "Node 1",
            systemAction = "action",
            inputs = emptyList(),
            outputs = emptyList()
        )
        val group1 = FlowGroup(
            id = 10L,
            title = "Group 1",
            position = ModelOffset(100f, 100f),
            size = ModelOffset(200f, 200f),
            nodeIds = listOf(1L),
            isCollapsed = true
        )
        val label1 = FlowLabel(
            id = 20L,
            text = "Title Label",
            position = ModelOffset(50f, 50f)
        )

        val flow = Flow(
            name = "Test Flow",
            nodes = listOf(node1),
            groups = listOf(group1),
            labels = listOf(label1)
        )

        // Select both the collapsed group and the label
        var state = FlowEditorState(
            flow = flow,
            selectedGroupIds = setOf(10L),
            selectedLabelIds = setOf(20L)
        )

        val dragDelta = ComposeOffset(50f, 50f)
        state = nodeManager.handleMoveNode(state, 10L, dragDelta, snap = false, showGhost = false)
        assertEquals(ModelOffset(50f, 50f), state.currentDragOffset)

        // Finalize movement
        state = nodeManager.handleEndMoveNode(state, 10L, density = 1f)

        val movedGroup = state.flow.groups.first { it.id == 10L }
        val movedNode = state.flow.nodes.first { it.id == 1L }
        val movedLabel = state.flow.labels.first { it.id == 20L }

        assertEquals(ModelOffset(150f, 150f), movedGroup.position)
        assertEquals(ModelOffset(150f, 150f), movedNode.position, "Node inside collapsed group moves along with group")
        assertEquals(ModelOffset(100f, 100f), movedLabel.position, "Label in multi-selection also moves by delta")
    }

    @Test
    fun testMoveBoardElementsCommandUndoRedo() = runTest {
        val node = Node.SystemNode(
            id = 1L,
            position = ModelOffset(100f, 100f),
            title = "Node 1",
            systemAction = "action",
            inputs = emptyList(),
            outputs = emptyList()
        )
        val group = FlowGroup(
            id = 2L,
            title = "Group 1",
            position = ModelOffset(200f, 200f),
            size = ModelOffset(200f, 200f)
        )
        val label = FlowLabel(
            id = 3L,
            text = "Test",
            position = ModelOffset(50f, 50f)
        )
        val point = FlowJunction(
            id = 4L,
            position = ModelOffset(300f, 300f)
        )

        val flow = Flow(
            name = "Test",
            nodes = listOf(node),
            groups = listOf(group),
            labels = listOf(label),
            junctions = listOf(point)
        )
        val initialState = FlowEditorState(flow = flow)

        val cmd = MoveBoardElementsCommand(
            nodeMoves = mapOf(1L to Pair(ModelOffset(100f, 100f), ModelOffset(150f, 160f))),
            groupMoves = mapOf(2L to Pair(ModelOffset(200f, 200f), ModelOffset(250f, 260f))),
            labelMoves = mapOf(3L to Pair(ModelOffset(50f, 50f), ModelOffset(100f, 110f))),
            pointMoves = mapOf(4L to Pair(ModelOffset(300f, 300f), ModelOffset(320f, 330f)))
        )

        val executed = cmd.execute(initialState)
        assertEquals(ModelOffset(150f, 160f), executed.flow.nodes.first().position)
        assertEquals(ModelOffset(250f, 260f), executed.flow.groups.first().position)
        assertEquals(ModelOffset(100f, 110f), executed.flow.labels.first().position)
        assertEquals(ModelOffset(320f, 330f), executed.flow.junctions.first().position)

        val undone = cmd.undo(executed)
        assertEquals(ModelOffset(100f, 100f), undone.flow.nodes.first().position)
        assertEquals(ModelOffset(200f, 200f), undone.flow.groups.first().position)
        assertEquals(ModelOffset(50f, 50f), undone.flow.labels.first().position)
        assertEquals(ModelOffset(300f, 300f), undone.flow.junctions.first().position)
    }

    @Test
    fun testCollapsedGroupConnectionScalingAcrossZooms() {
        val group = FlowGroup(
            id = 10L,
            title = "Collapsed Group",
            position = ModelOffset(100f, 200f),
            size = ModelOffset(300f, 300f),
            nodeIds = listOf(1L),
            isCollapsed = true
        )
        val connection = Connection(
            sourceNodeId = 1L,
            sourcePortId = "out",
            targetNodeId = 2L,
            targetPortId = "in"
        )

        // At zoom 1.0x, offset = (0, 0), density = 1f
        val points1x = ConnectionHitTester.getConnectionScreenPoints(
            connection = connection,
            getPortBoardPosition = { _, _, _ -> ComposeOffset(600f, 200f) },
            junctionMap = emptyMap(),
            scale = 1.0f,
            offset = ComposeOffset.Zero,
            groups = listOf(group),
            density = 1.0f
        )
        assertNotNull(points1x)
        val start1x = points1x.first()
        // Expected start: x = 100 + 300 = 400, y = 200 + (44 / 2) = 222
        assertEquals(400f, start1x.x)
        assertEquals(222f, start1x.y)

        // At zoom 2.0x, offset = (50, 50), density = 1f
        val points2x = ConnectionHitTester.getConnectionScreenPoints(
            connection = connection,
            getPortBoardPosition = { _, _, _ -> ComposeOffset(600f, 200f) },
            junctionMap = emptyMap(),
            scale = 2.0f,
            offset = ComposeOffset(50f, 50f),
            groups = listOf(group),
            density = 1.0f
        )
        assertNotNull(points2x)
        val start2x = points2x.first()
        // Expected start: x = (100 * 2 + 50) + (300 * 2) = 250 + 600 = 850
        // Expected start: y = (200 * 2 + 50) + (44 * 1 * 2 / 2) = 450 + 44 = 494
        assertEquals(850f, start2x.x)
        assertEquals(494f, start2x.y)

        // At zoom 0.5x, offset = (10, 20), density = 1f
        val pointsHalfX = ConnectionHitTester.getConnectionScreenPoints(
            connection = connection,
            getPortBoardPosition = { _, _, _ -> ComposeOffset(600f, 200f) },
            junctionMap = emptyMap(),
            scale = 0.5f,
            offset = ComposeOffset(10f, 20f),
            groups = listOf(group),
            density = 1.0f
        )
        assertNotNull(pointsHalfX)
        val startHalfX = pointsHalfX.first()
        // Expected start: x = (100 * 0.5 + 10) + (300 * 0.5) = 60 + 150 = 210
        // Expected start: y = (200 * 0.5 + 20) + (44 * 1 * 0.5 / 2) = 120 + 11 = 131
        assertEquals(210f, startHalfX.x)
        assertEquals(131f, startHalfX.y)
    }

    @Test
    fun testLabelAndGroupTextUpdateOnConfirm() {
        val label = FlowLabel(id = 1L, text = "Original Label", position = ModelOffset(50f, 50f))
        val group = FlowGroup(id = 2L, title = "Original Title", position = ModelOffset(100f, 100f), size = ModelOffset(200f, 200f))
        val flow = Flow(name = "Test", labels = listOf(label), groups = listOf(group))
        val state = FlowEditorState(flow = flow)

        // Label update command
        val updatedLabel = label.copy(text = "Confirmed Label Edit")
        val labelCmd = UpdateLabelCommand(label, updatedLabel)
        val stateAfterLabel = labelCmd.execute(state)
        assertEquals("Confirmed Label Edit", stateAfterLabel.flow.labels.first().text)

        val stateRevertedLabel = labelCmd.undo(stateAfterLabel)
        assertEquals("Original Label", stateRevertedLabel.flow.labels.first().text)

        // Group title update command
        val updatedGroup = group.copy(title = "Confirmed Group Title")
        val groupCmd = UpdateGroupCommand(group, updatedGroup)
        val stateAfterGroup = groupCmd.execute(state)
        assertEquals("Confirmed Group Title", stateAfterGroup.flow.groups.first().title)

        val stateRevertedGroup = groupCmd.undo(stateAfterGroup)
        assertEquals("Original Title", stateRevertedGroup.flow.groups.first().title)
    }
}
