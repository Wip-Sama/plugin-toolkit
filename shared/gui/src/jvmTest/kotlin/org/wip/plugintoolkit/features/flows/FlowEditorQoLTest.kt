package org.wip.plugintoolkit.features.flows

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.features.flows.history.AddGroupCommand
import org.wip.plugintoolkit.features.flows.history.AddJunctionCommand
import org.wip.plugintoolkit.features.flows.history.AddLabelCommand
import org.wip.plugintoolkit.features.flows.history.DeleteGroupCommand
import org.wip.plugintoolkit.features.flows.history.DeleteJunctionCommand
import org.wip.plugintoolkit.features.flows.history.DeleteLabelCommand
import org.wip.plugintoolkit.features.flows.history.MoveGroupCommand
import org.wip.plugintoolkit.features.flows.history.MoveJunctionCommand
import org.wip.plugintoolkit.features.flows.history.MoveLabelCommand
import org.wip.plugintoolkit.features.flows.history.PaintElementsCommand
import org.wip.plugintoolkit.features.flows.history.ResizeGroupCommand
import org.wip.plugintoolkit.features.flows.history.UpdateGroupCommand
import org.wip.plugintoolkit.features.flows.history.UpdateLabelCommand
import org.wip.plugintoolkit.features.flows.history.UpdateWaypointsCommand
import org.wip.plugintoolkit.features.flows.logic.FlowRepository
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.FlowGroup
import org.wip.plugintoolkit.features.flows.model.FlowJunction
import org.wip.plugintoolkit.features.flows.model.FlowLabel
import org.wip.plugintoolkit.features.flows.model.InputPort
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.model.Offset as ModelOffset
import org.wip.plugintoolkit.features.flows.model.OutputPort
import org.wip.plugintoolkit.features.flows.ui.canvas.BoardInteractionState
import org.wip.plugintoolkit.features.flows.ui.canvas.ConnectionHitTester
import org.wip.plugintoolkit.features.flows.viewmodel.ActiveFlowEditorTracker
import org.wip.plugintoolkit.features.flows.viewmodel.FlowEditorState
import org.wip.plugintoolkit.features.flows.viewmodel.FlowEditorViewModel
import org.wip.plugintoolkit.features.flows.viewmodel.FlowEvent
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository
import org.wip.plugintoolkit.features.settings.model.AppSettings
import org.wip.plugintoolkit.features.settings.model.ConnectionCurveStyle
import org.wip.plugintoolkit.features.settings.model.FlowSettings
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FlowEditorQoLTest {

    private val mockFlowRepo = mockk<FlowRepository>(relaxed = true)
    private val mockJobManager = mockk<JobManager>(relaxed = true)
    private val mockSettingsRepo = mockk<SettingsRepository>(relaxed = true)

    @BeforeTest
    fun setUp() {
        stopKoin()
        every { mockJobManager.jobs } returns MutableStateFlow(emptyList<BackgroundJob>())
        every { mockSettingsRepo.settings } returns MutableStateFlow(AppSettings(flows = FlowSettings(autosave = false)))
        every { mockSettingsRepo.isLoaded } returns MutableStateFlow(true)
        every { mockFlowRepo.flows } returns MutableStateFlow(emptyList())

        startKoin {
            modules(
                module {
                    single<JobManager> { mockJobManager }
                    single<SettingsRepository> { mockSettingsRepo }
                    single<ActiveFlowEditorTracker> { ActiveFlowEditorTracker() }
                }
            )
        }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    private fun createViewModel(initialFlow: Flow = Flow("TestFlow")): FlowEditorViewModel = kotlinx.coroutines.runBlocking {
        every { mockFlowRepo.flows } returns MutableStateFlow(listOf(initialFlow))
        val vm = FlowEditorViewModel(
            initialFlowName = initialFlow.name,
            flowRepository = mockFlowRepo
        ).apply {
            bypassReadOnlyForTesting = true
        }
        for (i in 1..100) {
            if (vm.state.value.flow.name == initialFlow.name &&
                vm.state.value.flow.nodes.size == initialFlow.nodes.size &&
                vm.state.value.flow.connections.size == initialFlow.connections.size &&
                vm.state.value.flow.groups.size == initialFlow.groups.size &&
                vm.state.value.flow.labels.size == initialFlow.labels.size
            ) break
            kotlinx.coroutines.delay(10)
        }
        vm
    }

    @Test
    fun testAddUpdateMoveDeleteGroupCommands() {
        val group = FlowGroup(id = 1L, title = "Section A", position = ModelOffset(100f, 100f), size = ModelOffset(300f, 200f))
        val initial = FlowEditorState(flow = Flow("Test"))

        // Add
        val addCmd = AddGroupCommand(group)
        val addedState = addCmd.execute(initial)
        assertEquals(1, addedState.flow.groups.size)
        assertEquals("Section A", addedState.flow.groups.first().title)
        assertTrue(addedState.hasUnsavedChanges)

        val undoneAdd = addCmd.undo(addedState)
        assertEquals(0, undoneAdd.flow.groups.size)

        // Update
        val updatedGroup = group.copy(title = "Renamed Section", isCollapsed = true, color = "#FF0000")
        val updateCmd = UpdateGroupCommand(group, updatedGroup)
        val updatedState = updateCmd.execute(addedState)
        assertEquals("Renamed Section", updatedState.flow.groups.first().title)
        assertTrue(updatedState.flow.groups.first().isCollapsed)
        assertEquals("#FF0000", updatedState.flow.groups.first().color)

        val undoneUpdate = updateCmd.undo(updatedState)
        assertEquals("Section A", undoneUpdate.flow.groups.first().title)
        assertFalse(undoneUpdate.flow.groups.first().isCollapsed)

        // Move
        val moveCmd = MoveGroupCommand(1L, ModelOffset(100f, 100f), ModelOffset(250f, 300f))
        val movedState = moveCmd.execute(addedState)
        assertEquals(ModelOffset(250f, 300f), movedState.flow.groups.first().position)

        val undoneMove = moveCmd.undo(movedState)
        assertEquals(ModelOffset(100f, 100f), undoneMove.flow.groups.first().position)

        // Delete
        val deleteCmd = DeleteGroupCommand(group)
        val deletedState = deleteCmd.execute(addedState)
        assertEquals(0, deletedState.flow.groups.size)

        val undoneDelete = deleteCmd.undo(deletedState)
        assertEquals(1, undoneDelete.flow.groups.size)
    }

    @Test
    fun testAddUpdateMoveDeleteLabelCommands() {
        val label = FlowLabel(id = 1L, text = "Important Note", position = ModelOffset(50f, 50f))
        val initial = FlowEditorState(flow = Flow("Test"))

        // Add
        val addCmd = AddLabelCommand(label)
        val addedState = addCmd.execute(initial)
        assertEquals(1, addedState.flow.labels.size)
        assertEquals("Important Note", addedState.flow.labels.first().text)

        val undoneAdd = addCmd.undo(addedState)
        assertEquals(0, undoneAdd.flow.labels.size)

        // Update
        val updatedLabel = label.copy(text = "Updated Note", fontSize = 20f, color = "#0000FF")
        val updateCmd = UpdateLabelCommand(label, updatedLabel)
        val updatedState = updateCmd.execute(addedState)
        assertEquals("Updated Note", updatedState.flow.labels.first().text)
        assertEquals(20f, updatedState.flow.labels.first().fontSize)
        assertEquals("#0000FF", updatedState.flow.labels.first().color)

        val undoneUpdate = updateCmd.undo(updatedState)
        assertEquals("Important Note", undoneUpdate.flow.labels.first().text)

        // Move
        val moveCmd = MoveLabelCommand(1L, ModelOffset(50f, 50f), ModelOffset(120f, 80f))
        val movedState = moveCmd.execute(addedState)
        assertEquals(ModelOffset(120f, 80f), movedState.flow.labels.first().position)

        val undoneMove = moveCmd.undo(movedState)
        assertEquals(ModelOffset(50f, 50f), undoneMove.flow.labels.first().position)

        // Delete
        val deleteCmd = DeleteLabelCommand(label)
        val deletedState = deleteCmd.execute(addedState)
        assertEquals(0, deletedState.flow.labels.size)

        val undoneDelete = deleteCmd.undo(deletedState)
        assertEquals(1, undoneDelete.flow.labels.size)
    }

    @Test
    fun testJunctionCommandsAndCascade() {
        val junction = FlowJunction(id = 10L, position = ModelOffset(150f, 150f), color = "#4CAF50")
        val conn1 = Connection(sourceNodeId = 1L, sourcePortId = "out", targetNodeId = -1L, targetPortId = "", targetJunctionId = 10L)
        val conn2 = Connection(sourceNodeId = -1L, sourcePortId = "", sourceJunctionId = 10L, targetNodeId = 2L, targetPortId = "in")
        val initial = FlowEditorState(
            flow = Flow("Test", junctions = listOf(junction), connections = listOf(conn1, conn2))
        )

        // Move Junction
        val moveCmd = MoveJunctionCommand(10L, ModelOffset(150f, 150f), ModelOffset(200f, 220f))
        val movedState = moveCmd.execute(initial)
        assertEquals(ModelOffset(200f, 220f), movedState.flow.junctions.first().position)

        val undoneMove = moveCmd.undo(movedState)
        assertEquals(ModelOffset(150f, 150f), undoneMove.flow.junctions.first().position)

        // Delete Junction with cascading connections
        val deleteCmd = DeleteJunctionCommand(junction, listOf(conn1, conn2))
        val deletedState = deleteCmd.execute(initial)
        assertEquals(0, deletedState.flow.junctions.size)
        assertEquals(0, deletedState.flow.connections.size)

        val undoneDelete = deleteCmd.undo(deletedState)
        assertEquals(1, undoneDelete.flow.junctions.size)
        assertEquals(2, undoneDelete.flow.connections.size)
    }

    @Test
    fun testPaintElementsCommandUndoRedo() {
        val oldFlow = Flow("Test")
        val newFlow = Flow("Test", groups = listOf(FlowGroup(1L, "G", ModelOffset.Zero, ModelOffset.Zero, color = "#FF00FF")))
        val initial = FlowEditorState(flow = oldFlow)

        val cmd = PaintElementsCommand(oldFlow, newFlow)
        val executed = cmd.execute(initial)
        assertEquals(1, executed.flow.groups.size)
        assertEquals("#FF00FF", executed.flow.groups.first().color)

        val undone = cmd.undo(executed)
        assertEquals(0, undone.flow.groups.size)
    }

    @Test
    fun testViewModelGroupEvents() {
        val vm = createViewModel()

        vm.onEvent(FlowEvent.AddGroup(ModelOffset(100f, 100f)))
        assertEquals(1, vm.state.value.flow.groups.size)
        val group = vm.state.value.flow.groups.first()
        assertEquals(ModelOffset(100f, 100f), group.position)

        vm.onEvent(FlowEvent.MoveGroup(group.id, ModelOffset(20f, 30f)))
        assertEquals(ModelOffset(120f, 130f), vm.state.value.flow.groups.first().position)

        val latestGroup = vm.state.value.flow.groups.first()
        vm.onEvent(FlowEvent.UpdateGroup(latestGroup.copy(title = "Custom Title", color = "#123456")))
        assertEquals("Custom Title", vm.state.value.flow.groups.first().title)

        vm.undo()
        assertEquals("New Group", vm.state.value.flow.groups.first().title)

        vm.redo()
        assertEquals("Custom Title", vm.state.value.flow.groups.first().title)

        vm.onEvent(FlowEvent.DeleteGroup(vm.state.value.flow.groups.first()))
        assertEquals(0, vm.state.value.flow.groups.size)

        vm.undo()
        assertEquals(1, vm.state.value.flow.groups.size)
    }

    @Test
    fun testViewModelLabelEvents() {
        val vm = createViewModel()

        vm.onEvent(FlowEvent.AddLabel(ModelOffset(200f, 200f)))
        assertEquals(1, vm.state.value.flow.labels.size)
        val label = vm.state.value.flow.labels.first()
        assertEquals(ModelOffset(200f, 200f), label.position)

        vm.onEvent(FlowEvent.MoveLabel(label.id, ModelOffset(15f, -10f)))
        assertEquals(ModelOffset(215f, 190f), vm.state.value.flow.labels.first().position)

        val latestLabel = vm.state.value.flow.labels.first()
        vm.onEvent(FlowEvent.UpdateLabel(latestLabel.copy(text = "Hello Flow", fontSize = 18f)))
        assertEquals("Hello Flow", vm.state.value.flow.labels.first().text)

        vm.undo()
        assertEquals("Text Note", vm.state.value.flow.labels.first().text)

        vm.onEvent(FlowEvent.DeleteLabel(vm.state.value.flow.labels.first()))
        assertEquals(0, vm.state.value.flow.labels.size)

        vm.undo()
        assertEquals(1, vm.state.value.flow.labels.size)
    }

    @Test
    fun testViewModelPaintAndWashNode() {
        val node1 = Node.FlowInputNode(1L, ModelOffset(0f, 0f), listOf(OutputPort("out", "Out", DataType.Primitive(PrimitiveType.STRING))))
        val node2 = Node.FlowOutputNode(2L, ModelOffset(200f, 0f), listOf(InputPort("in", "In", DataType.Primitive(PrimitiveType.STRING))))
        val node3 = Node.FlowOutputNode(3L, ModelOffset(200f, 100f), listOf(InputPort("in", "In", DataType.Primitive(PrimitiveType.STRING))))

        // Connection 1 is uncolored, Connection 2 is already colored
        val conn1 = Connection(1L, "out", 2L, "in", color = null)
        val conn2 = Connection(1L, "out", 3L, "in", color = "#FF0000")

        val initialFlow = Flow("PaintTest", nodes = listOf(node1, node2, node3), connections = listOf(conn1, conn2))
        val vm = createViewModel(initialFlow)

        vm.onEvent(FlowEvent.SetActivePaintColor("#00FF00"))

        // 1. Normal paint on node1: colors node1 and conn1 (uncolored), but NOT conn2 (already colored)
        vm.onEvent(FlowEvent.PaintNode(node1.id, isForce = false))

        val paintedNode = vm.state.value.flow.nodes.find { it.id == node1.id }
        assertEquals("#00FF00", paintedNode?.color)

        val updatedConn1 = vm.state.value.flow.connections.find { it.targetNodeId == 2L }
        assertEquals("#00FF00", updatedConn1?.color)

        val updatedConn2 = vm.state.value.flow.connections.find { it.targetNodeId == 3L }
        assertEquals("#FF0000", updatedConn2?.color) // Preserved!

        // 2. Force paint on node1: overrides conn2 as well
        vm.onEvent(FlowEvent.SetActivePaintColor("#0000FF"))
        vm.onEvent(FlowEvent.PaintNode(node1.id, isForce = true))

        val forcePaintedNode = vm.state.value.flow.nodes.find { it.id == node1.id }
        assertEquals("#0000FF", forcePaintedNode?.color)

        val forceConn2 = vm.state.value.flow.connections.find { it.targetNodeId == 3L }
        assertEquals("#0000FF", forceConn2?.color) // Overwritten!

        // 3. Wash node1: resets color to null
        vm.onEvent(FlowEvent.WashNode(node1.id))
        val washedNode = vm.state.value.flow.nodes.find { it.id == node1.id }
        assertNull(washedNode?.color)

        // 4. Undo restores color
        vm.undo()
        val restoredNode = vm.state.value.flow.nodes.find { it.id == node1.id }
        assertEquals("#0000FF", restoredNode?.color)
    }

    @Test
    fun testViewModelPaintAndWashConnectionsGroupsLabels() {
        val group = FlowGroup(id = 1L, title = "Group", position = ModelOffset.Zero, size = ModelOffset(100f, 100f))
        val label = FlowLabel(id = 2L, text = "Label", position = ModelOffset.Zero)
        val conn = Connection(sourceNodeId = 10L, sourcePortId = "out", targetNodeId = 20L, targetPortId = "in")

        val initialFlow = Flow("Test", groups = listOf(group), labels = listOf(label), connections = listOf(conn))
        val vm = createViewModel(initialFlow)

        vm.onEvent(FlowEvent.SetActivePaintColor("#FFAA00"))

        // Paint Connection
        vm.onEvent(FlowEvent.PaintConnection(conn))
        assertEquals("#FFAA00", vm.state.value.flow.connections.first().color)

        // Wash Connection
        vm.onEvent(FlowEvent.WashConnection(vm.state.value.flow.connections.first()))
        assertNull(vm.state.value.flow.connections.first().color)

        // Paint Group
        vm.onEvent(FlowEvent.PaintGroup(1L))
        assertEquals("#FFAA00", vm.state.value.flow.groups.first().color)

        // Wash Group
        vm.onEvent(FlowEvent.WashGroup(1L))
        assertNull(vm.state.value.flow.groups.first().color)

        // Paint Label
        vm.onEvent(FlowEvent.PaintLabel(2L))
        assertEquals("#FFAA00", vm.state.value.flow.labels.first().color)

        // Wash Label
        vm.onEvent(FlowEvent.WashLabel(2L))
        assertNull(vm.state.value.flow.labels.first().color)
    }

    @Test
    fun testViewModelJunctionBranchingAndFloatingConnections() {
        val conn = Connection(sourceNodeId = 1L, sourcePortId = "out", targetNodeId = 2L, targetPortId = "in", color = "#E91E63")
        val initialFlow = Flow("BranchTest", connections = listOf(conn))
        val vm = createViewModel(initialFlow)

        // Branching: split connection at (150, 150)
        vm.onEvent(FlowEvent.AddJunctionAndBranch(conn, ModelOffset(150f, 150f)))
        assertEquals(1, vm.state.value.flow.junctions.size)
        val junc = vm.state.value.flow.junctions.first()
        assertEquals(ModelOffset(150f, 150f), junc.position)
        assertEquals("#E91E63", junc.color)

        // Two connections now: 1 -> junction, and junction -> 2
        assertEquals(2, vm.state.value.flow.connections.size)
        val c1 = vm.state.value.flow.connections.find { it.targetJunctionId == junc.id }
        val c2 = vm.state.value.flow.connections.find { it.sourceJunctionId == junc.id }
        assertNotNull(c1)
        assertNotNull(c2)
        assertEquals(1L, c1.sourceNodeId)
        assertEquals(2L, c2.targetNodeId)

        // Move Junction
        vm.onEvent(FlowEvent.MoveJunction(junc.id, ModelOffset(10f, -20f)))
        assertEquals(ModelOffset(160f, 130f), vm.state.value.flow.junctions.first().position)

        // Create Floating connection
        vm.onEvent(FlowEvent.CreateFloatingConnection(sourceNodeId = 1L, sourcePortId = "out", floatingTarget = ModelOffset(300f, 400f)))
        assertEquals(3, vm.state.value.flow.connections.size)
        val floating = vm.state.value.flow.connections.find { it.isFloating }
        assertNotNull(floating)
        assertEquals(ModelOffset(300f, 400f), floating.floatingTarget)

        // Delete Junction cascades to both c1 and c2
        vm.onEvent(FlowEvent.DeleteJunction(junc.id))
        assertEquals(0, vm.state.value.flow.junctions.size)
        assertEquals(1, vm.state.value.flow.connections.size) // Only floating remains

        // Undo restores junction and its 2 connections
        vm.undo()
        assertEquals(1, vm.state.value.flow.junctions.size)
        assertEquals(3, vm.state.value.flow.connections.size)
    }

    @Test
    fun testViewModelSplineStyleAndRoundness() {
        val vm = createViewModel()

        assertEquals(ConnectionCurveStyle.CardinalSpline, vm.state.value.connectionCurveStyle)
        assertEquals(0.5f, vm.state.value.connectionRoundness)

        vm.onEvent(FlowEvent.UpdateConnectionCurveStyle(ConnectionCurveStyle.Straight))
        assertEquals(ConnectionCurveStyle.Straight, vm.state.value.connectionCurveStyle)

        vm.onEvent(FlowEvent.UpdateConnectionRoundness(0.85f))
        assertEquals(0.85f, vm.state.value.connectionRoundness)
    }

    @Test
    fun testBoardInteractionStateJunctionTracking() {
        val state = BoardInteractionState()
        assertNull(state.hoveredJunctionId)
        assertNull(state.selectedJunctionId)
        assertNull(state.draggingJunctionId)

        state.hoveredJunctionId = 5L
        state.selectedJunctionId = 5L
        state.draggingJunctionId = 5L

        assertEquals(5L, state.hoveredJunctionId)
        assertEquals(5L, state.selectedJunctionId)
        assertEquals(5L, state.draggingJunctionId)

        state.clearHoveredJunction()
        assertNull(state.hoveredJunctionId)
        assertEquals(5L, state.selectedJunctionId)

        state.clearAllInteractions()
        assertNull(state.selectedJunctionId)
        assertNull(state.draggingJunctionId)
    }

    @Test
    fun testEyedropperToolSampleColor() {
        val vm = createViewModel()

        assertFalse(vm.state.value.isEyedropperActive)

        vm.onEvent(FlowEvent.ToggleEyedropper)
        assertTrue(vm.state.value.isEyedropperActive)

        // Sampling color sets activePaintColor and deactivates eyedropper
        vm.onEvent(FlowEvent.SampleColor("#9C27B0"))
        assertFalse(vm.state.value.isEyedropperActive)
        assertEquals("#9C27B0", vm.state.value.activePaintColor)
    }

    @Test
    fun testSelectionBoxAndBatchDeletion() {
        val n1 = Node.FlowInputNode(id = 1L, position = ModelOffset(10f, 10f), outputs = emptyList())
        val n2 = Node.FlowOutputNode(id = 2L, position = ModelOffset(200f, 200f), inputs = emptyList())
        val grp = FlowGroup(id = 100L, title = "Group 1", position = ModelOffset(50f, 50f), size = ModelOffset(200f, 150f))
        val lbl = FlowLabel(id = 200L, text = "Label 1", position = ModelOffset(80f, 80f))
        val initialFlow = Flow(name = "TestBatch", nodes = listOf(n1, n2), groups = listOf(grp), labels = listOf(lbl))

        val vm = createViewModel(initialFlow)

        vm.onEvent(FlowEvent.SelectNodes(setOf(1L)))
        vm.onEvent(FlowEvent.SelectGroups(setOf(100L)))
        vm.onEvent(FlowEvent.SelectLabels(setOf(200L)))

        assertEquals(setOf(1L), vm.state.value.selectedNodeIds)
        assertEquals(setOf(100L), vm.state.value.selectedGroupIds)
        assertEquals(setOf(200L), vm.state.value.selectedLabelIds)

        // Batch delete selected nodes, groups, and labels
        vm.onEvent(FlowEvent.DeleteSelectedNodes)

        assertEquals(1, vm.state.value.flow.nodes.size)
        assertEquals(2L, vm.state.value.flow.nodes.first().id)
        assertEquals(0, vm.state.value.flow.groups.size)
        assertEquals(0, vm.state.value.flow.labels.size)
        assertTrue(vm.state.value.selectedNodeIds.isEmpty())
        assertTrue(vm.state.value.selectedGroupIds.isEmpty())
        assertTrue(vm.state.value.selectedLabelIds.isEmpty())

        // Undo restores all of them
        vm.undo()
        assertEquals(2, vm.state.value.flow.nodes.size)
        assertEquals(1, vm.state.value.flow.groups.size)
        assertEquals(1, vm.state.value.flow.labels.size)
    }

    @Test
    fun testResizeGroupCommandAndViewModel() {
        val grp = FlowGroup(id = 10L, title = "G", position = ModelOffset(0f, 0f), size = ModelOffset(200f, 150f))
        val initialFlow = Flow(name = "TestResize", groups = listOf(grp))
        val vm = createViewModel(initialFlow)

        vm.onEvent(FlowEvent.ResizeGroup(10L, ModelOffset(50f, 30f)))
        val resized = vm.state.value.flow.groups.first()
        assertEquals(250f, resized.size.x)
        assertEquals(180f, resized.size.y)

        // Undo restores original size
        vm.undo()
        val restored = vm.state.value.flow.groups.first()
        assertEquals(200f, restored.size.x)
        assertEquals(150f, restored.size.y)
    }

    @Test
    fun testGroupContainmentMove() {
        // Node 1 is explicitly bound to group. Node 2 is not bound.
        val n1 = Node.FlowInputNode(id = 1L, position = ModelOffset(50f, 50f), outputs = emptyList())
        val n2 = Node.FlowOutputNode(id = 2L, position = ModelOffset(500f, 500f), inputs = emptyList())
        val grp = FlowGroup(id = 10L, title = "Container", position = ModelOffset(0f, 0f), size = ModelOffset(300f, 200f), nodeIds = listOf(1L))
        val initialFlow = Flow(name = "TestContainment", nodes = listOf(n1, n2), groups = listOf(grp))
        val vm = createViewModel(initialFlow)

        // Move group by (40, 60)
        vm.onEvent(FlowEvent.MoveGroup(10L, ModelOffset(40f, 60f)))

        val movedGrp = vm.state.value.flow.groups.first()
        val movedN1 = vm.state.value.flow.nodes.find { it.id == 1L }!!
        val unmovedN2 = vm.state.value.flow.nodes.find { it.id == 2L }!!

        assertEquals(ModelOffset(40f, 60f), movedGrp.position)
        assertEquals(ModelOffset(90f, 110f), movedN1.position) // (50+40, 50+60)
        assertEquals(ModelOffset(500f, 500f), unmovedN2.position) // unchanged

        // Undo restores both group and contained node positions
        vm.undo()
        assertEquals(ModelOffset(0f, 0f), vm.state.value.flow.groups.first().position)
        assertEquals(ModelOffset(50f, 50f), vm.state.value.flow.nodes.find { it.id == 1L }!!.position)
    }

    @Test
    fun testNodeDropBindingAndUnbindingInGroup() {
        val n1 = Node.FlowInputNode(id = 1L, position = ModelOffset(500f, 500f), outputs = emptyList())
        val grp = FlowGroup(id = 10L, title = "Container", position = ModelOffset(0f, 0f), size = ModelOffset(300f, 200f), nodeIds = emptyList())
        val initialFlow = Flow(name = "TestBinding", nodes = listOf(n1), groups = listOf(grp))
        val vm = createViewModel(initialFlow)

        // 1. Initially node is not bound
        assertEquals(emptyList(), vm.state.value.flow.groups.first().nodeIds)

        // 2. Drag node into group (move from 500,500 by -400,-400 to 100,100)
        vm.onEvent(FlowEvent.MoveNode(1L, androidx.compose.ui.geometry.Offset(-400f, -400f), snap = false, showGhost = false))
        vm.onEvent(FlowEvent.EndMoveNode(1L, density = 1f))

        // Node is now bound to group!
        assertEquals(listOf(1L), vm.state.value.flow.groups.first().nodeIds)

        // 3. Drag node outside group (move from 100,100 by 600,600 to 700,700)
        vm.onEvent(FlowEvent.MoveNode(1L, androidx.compose.ui.geometry.Offset(600f, 600f), snap = false, showGhost = false))
        vm.onEvent(FlowEvent.EndMoveNode(1L, density = 1f))

        // Node is now unbound from group!
        assertTrue(vm.state.value.flow.groups.first().nodeIds.isEmpty())
    }

    @Test
    fun testCollapsedGroupGenericPortsConnectionHitTester() {
        val nSource = Node.FlowInputNode(id = 1L, position = ModelOffset(50f, 50f), outputs = listOf(OutputPort("out", "Out", DataType.Primitive(PrimitiveType.STRING))))
        val nInternal = Node.FlowOutputNode(id = 2L, position = ModelOffset(150f, 50f), inputs = listOf(InputPort("in1", "In1", DataType.Primitive(PrimitiveType.STRING))))
        val nTarget = Node.FlowOutputNode(id = 3L, position = ModelOffset(600f, 50f), inputs = listOf(InputPort("in2", "In2", DataType.Primitive(PrimitiveType.STRING))))

        // Group 10 contains nodes 1 and 2 and is collapsed
        val grp = FlowGroup(id = 10L, title = "CollapsedGrp", position = ModelOffset(0f, 0f), size = ModelOffset(200f, 100f), isCollapsed = true, nodeIds = listOf(1L, 2L))

        val internalConn = Connection(sourceNodeId = 1L, sourcePortId = "out", targetNodeId = 2L, targetPortId = "in1")
        val externalOutConn = Connection(sourceNodeId = 1L, sourcePortId = "out", targetNodeId = 3L, targetPortId = "in2")
        val externalInConn = Connection(sourceNodeId = 3L, sourcePortId = "out", targetNodeId = 2L, targetPortId = "in1")

        val portLookup: (Long, String, Boolean) -> androidx.compose.ui.geometry.Offset? = { nodeId, _, _ ->
            when (nodeId) {
                1L -> androidx.compose.ui.geometry.Offset(50f, 50f)
                2L -> androidx.compose.ui.geometry.Offset(150f, 50f)
                3L -> androidx.compose.ui.geometry.Offset(600f, 50f)
                else -> null
            }
        }

        // Internal connection points should be null when collapsed
        val internalPoints = org.wip.plugintoolkit.features.flows.ui.canvas.ConnectionHitTester.getConnectionScreenPoints(
            connection = internalConn,
            getPortBoardPosition = portLookup,
            junctionMap = emptyMap(),
            scale = 1f,
            offset = androidx.compose.ui.geometry.Offset.Zero,
            groups = listOf(grp)
        )
        assertNull(internalPoints, "Internal wire in collapsed group should be hidden")

        // External outgoing connection should start at group's right edge (x = 200, y = 22)
        val outPoints = org.wip.plugintoolkit.features.flows.ui.canvas.ConnectionHitTester.getConnectionScreenPoints(
            connection = externalOutConn,
            getPortBoardPosition = portLookup,
            junctionMap = emptyMap(),
            scale = 1f,
            offset = androidx.compose.ui.geometry.Offset.Zero,
            groups = listOf(grp)
        )
        assertNotNull(outPoints)
        assertEquals(2, outPoints.size)
        assertEquals(200f, outPoints.first().x)
        assertEquals(22f, outPoints.first().y)

        // External incoming connection should end at group's left edge (x = 0, y = 22)
        val inPoints = org.wip.plugintoolkit.features.flows.ui.canvas.ConnectionHitTester.getConnectionScreenPoints(
            connection = externalInConn,
            getPortBoardPosition = portLookup,
            junctionMap = emptyMap(),
            scale = 1f,
            offset = androidx.compose.ui.geometry.Offset.Zero,
            groups = listOf(grp)
        )
        assertNotNull(inPoints)
        assertEquals(2, inPoints.size)
        assertEquals(0f, inPoints.last().x)
        assertEquals(22f, inPoints.last().y)
    }

    @Test
    fun testCopyPasteGroupsAndLabels() {
        val n1 = Node.FlowInputNode(id = 1L, position = ModelOffset(100f, 100f), outputs = emptyList())
        val grp = FlowGroup(id = 10L, title = "Section", position = ModelOffset(80f, 80f), size = ModelOffset(300f, 200f), nodeIds = listOf(1L))
        val lbl = FlowLabel(id = 20L, text = "Notes", position = ModelOffset(50f, 50f))

        val initialFlow = Flow(name = "TestCopyPaste", nodes = listOf(n1), groups = listOf(grp), labels = listOf(lbl))
        val vm = createViewModel(initialFlow)

        // Select node, group, and label
        vm.onEvent(FlowEvent.SelectNodes(setOf(1L)))
        vm.onEvent(FlowEvent.SelectGroups(setOf(10L)))
        vm.onEvent(FlowEvent.SelectLabels(setOf(20L)))

        // Copy
        vm.onEvent(FlowEvent.CopySelectedNodes)

        // Paste at (200, 200)
        vm.onEvent(FlowEvent.PasteNodes(androidx.compose.ui.geometry.Offset(200f, 200f)))

        // Verify copied elements exist
        assertEquals(2, vm.state.value.flow.nodes.size)
        assertEquals(2, vm.state.value.flow.groups.size)
        assertEquals(2, vm.state.value.flow.labels.size)

        val pastedGroup = vm.state.value.flow.groups.last()
        val pastedNode = vm.state.value.flow.nodes.last()
        val pastedLabel = vm.state.value.flow.labels.last()

        assertTrue(pastedGroup.id != 10L)
        assertTrue(pastedNode.id != 1L)
        assertTrue(pastedLabel.id != 20L)

        // Remapped nodeIds in pasted group must point to pastedNode.id
        assertEquals(listOf(pastedNode.id), pastedGroup.nodeIds)

        // Undo removes pasted items cleanly
        vm.undo()
        assertEquals(1, vm.state.value.flow.nodes.size)
        assertEquals(1, vm.state.value.flow.groups.size)
        assertEquals(1, vm.state.value.flow.labels.size)
    }

    @Test
    fun testBatchPaintAndWashSelection() {
        val n1 = Node.FlowInputNode(id = 1L, position = ModelOffset(0f, 0f), outputs = emptyList())
        val grp = FlowGroup(id = 10L, title = "G", position = ModelOffset(100f, 100f), size = ModelOffset(200f, 200f))
        val lbl = FlowLabel(id = 20L, text = "L", position = ModelOffset(50f, 50f))

        val initialFlow = Flow(name = "TestBatchPaint", nodes = listOf(n1), groups = listOf(grp), labels = listOf(lbl))
        val vm = createViewModel(initialFlow)

        // Select all
        vm.onEvent(FlowEvent.SelectNodes(setOf(1L)))
        vm.onEvent(FlowEvent.SelectGroups(setOf(10L)))
        vm.onEvent(FlowEvent.SelectLabels(setOf(20L)))

        // Set active paint color and apply PaintSelection
        vm.onEvent(FlowEvent.SetActivePaintColor("#FFE91E63"))
        vm.onEvent(FlowEvent.PaintSelection)

        assertEquals("#ffe91e63", vm.state.value.flow.nodes.first().color?.lowercase())
        assertEquals("#ffe91e63", vm.state.value.flow.groups.first().color?.lowercase())
        assertEquals("#ffe91e63", vm.state.value.flow.labels.first().color?.lowercase())

        // Apply WashSelection
        vm.onEvent(FlowEvent.WashSelection)
        assertNull(vm.state.value.flow.nodes.first().color)
        assertNull(vm.state.value.flow.groups.first().color)
        assertNull(vm.state.value.flow.labels.first().color)

        // Undo restores painted state
        vm.undo()
        assertEquals("#ffe91e63", vm.state.value.flow.nodes.first().color?.lowercase())
        assertEquals("#ffe91e63", vm.state.value.flow.groups.first().color?.lowercase())
        assertEquals("#ffe91e63", vm.state.value.flow.labels.first().color?.lowercase())
    }

    @Test
    fun testUnifiedMultiSelectionDrag() {
        val n1 = Node.FlowInputNode(id = 1L, position = ModelOffset(0f, 0f), outputs = emptyList())
        val grp = FlowGroup(id = 10L, title = "G", position = ModelOffset(100f, 100f), size = ModelOffset(200f, 200f))
        val lbl = FlowLabel(id = 20L, text = "L", position = ModelOffset(50f, 50f))

        val initialFlow = Flow(name = "TestMultiDrag", nodes = listOf(n1), groups = listOf(grp), labels = listOf(lbl))
        val vm = createViewModel(initialFlow)

        // Select all three
        vm.onEvent(FlowEvent.SelectNodes(setOf(1L)))
        vm.onEvent(FlowEvent.SelectGroups(setOf(10L)))
        vm.onEvent(FlowEvent.SelectLabels(setOf(20L)))

        // Moving the group moves all selected elements by (50, 50)
        vm.onEvent(FlowEvent.MoveGroup(10L, ModelOffset(50f, 50f)))

        assertEquals(ModelOffset(50f, 50f), vm.state.value.flow.nodes.first().position)
        assertEquals(ModelOffset(150f, 150f), vm.state.value.flow.groups.first().position)
        assertEquals(ModelOffset(100f, 100f), vm.state.value.flow.labels.first().position)

        // Moving the label also moves all selected elements by (50, 50)
        vm.onEvent(FlowEvent.MoveLabel(20L, ModelOffset(50f, 50f)))

        assertEquals(ModelOffset(100f, 100f), vm.state.value.flow.nodes.first().position)
        assertEquals(ModelOffset(200f, 200f), vm.state.value.flow.groups.first().position)
        assertEquals(ModelOffset(150f, 150f), vm.state.value.flow.labels.first().position)

        // Moving node via MoveNode & EndMoveNode moves all selected elements by (50, 50)
        vm.onEvent(FlowEvent.MoveNode(1L, androidx.compose.ui.geometry.Offset(50f, 50f), snap = false, showGhost = false))
        vm.onEvent(FlowEvent.EndMoveNode(1L, density = 1f))

        assertEquals(ModelOffset(150f, 150f), vm.state.value.flow.nodes.first().position)
        assertEquals(ModelOffset(250f, 250f), vm.state.value.flow.groups.first().position)
        assertEquals(ModelOffset(200f, 200f), vm.state.value.flow.labels.first().position)
    }

    @Test
    fun testBranchAttachmentOnWireDrop() {
        val n1 = Node.FlowInputNode(id = 1L, position = ModelOffset(0f, 0f), outputs = listOf(OutputPort("out", "Out", DataType.Primitive(PrimitiveType.STRING))))
        val n2 = Node.FlowOutputNode(id = 2L, position = ModelOffset(400f, 0f), inputs = listOf(InputPort("in", "In", DataType.Primitive(PrimitiveType.STRING))))
        val n3 = Node.FlowInputNode(id = 3L, position = ModelOffset(200f, 300f), outputs = listOf(OutputPort("branchOut", "BOut", DataType.Primitive(PrimitiveType.STRING))))
        val mainConn = Connection(sourceNodeId = 1L, sourcePortId = "out", targetNodeId = 2L, targetPortId = "in")
        val initialFlow = Flow(name = "TestBranch", nodes = listOf(n1, n2, n3), connections = listOf(mainConn))
        val vm = createViewModel(initialFlow)

        // Dropping wire from n3:branchOut onto mainConn at (200, 50)
        vm.onEvent(
            FlowEvent.AddJunctionAndBranch(
                connection = mainConn,
                splitPosition = ModelOffset(200f, 50f),
                branchSourceNodeId = 3L,
                branchSourcePortId = "branchOut"
            )
        )

        assertEquals(1, vm.state.value.flow.junctions.size)
        val junc = vm.state.value.flow.junctions.first()
        assertEquals(ModelOffset(200f, 50f), junc.position)

        // 3 connections now: 1 -> junction, junction -> 2, AND 3 -> junction!
        assertEquals(3, vm.state.value.flow.connections.size)
        val cBranch = vm.state.value.flow.connections.find { it.sourceNodeId == 3L && it.targetJunctionId == junc.id }
        assertNotNull(cBranch, "Branch connection from node 3 to junction must exist")

        // Undo restores single main connection
        vm.undo()
        assertEquals(0, vm.state.value.flow.junctions.size)
        assertEquals(1, vm.state.value.flow.connections.size)
        assertEquals(1L, vm.state.value.flow.connections.first().sourceNodeId)
    }

    @Test
    fun testWaypointCrudAndCommands() {
        val conn = Connection(sourceNodeId = 10L, sourcePortId = "out", targetNodeId = 20L, targetPortId = "in")
        val initialFlow = Flow(name = "TestWaypoints", connections = listOf(conn))
        val vm = createViewModel(initialFlow)

        assertTrue(vm.state.value.flow.connections.first().waypoints.isEmpty())

        // Add waypoint
        vm.onEvent(FlowEvent.AddWaypoint(conn, ModelOffset(120f, 80f)))
        assertEquals(1, vm.state.value.flow.connections.first().waypoints.size)
        assertEquals(ModelOffset(120f, 80f), vm.state.value.flow.connections.first().waypoints.first())

        // Move waypoint
        val currentConn = vm.state.value.flow.connections.first()
        vm.onEvent(FlowEvent.MoveWaypoint(currentConn, 0, ModelOffset(135f, 70f)))
        assertEquals(ModelOffset(135f, 70f), vm.state.value.flow.connections.first().waypoints.first())

        // Delete waypoint
        val updatedConn = vm.state.value.flow.connections.first()
        vm.onEvent(FlowEvent.DeleteWaypoint(updatedConn, 0))
        assertTrue(vm.state.value.flow.connections.first().waypoints.isEmpty())

        // Undo restores waypoint
        vm.undo()
        assertEquals(1, vm.state.value.flow.connections.first().waypoints.size)
        assertEquals(ModelOffset(135f, 70f), vm.state.value.flow.connections.first().waypoints.first())
    }

    @Test
    fun testResizeGroupCommandDirect() {
        val cmd = ResizeGroupCommand(10L, ModelOffset(200f, 150f), ModelOffset(250f, 180f))
        val initialFlow = Flow(name = "TestResizeCmd", groups = listOf(FlowGroup(id = 10L, title = "G", position = ModelOffset(0f, 0f), size = ModelOffset(200f, 150f))))
        val state = FlowEditorState(flow = initialFlow)
        val executed = cmd.execute(state)
        assertEquals(250f, executed.flow.groups.first().size.x)
        assertEquals(180f, executed.flow.groups.first().size.y)
        val undone = cmd.undo(executed)
        assertEquals(200f, undone.flow.groups.first().size.x)
        assertEquals(150f, undone.flow.groups.first().size.y)
    }

    @Test
    fun testUpdateWaypointsCommandDirect() {
        val conn = Connection(sourceNodeId = 1L, sourcePortId = "out", targetNodeId = 2L, targetPortId = "in", waypoints = listOf(ModelOffset(10f, 20f)))
        val newWaypoints = listOf(ModelOffset(10f, 20f), ModelOffset(30f, 40f))
        val cmd = UpdateWaypointsCommand(conn, conn.waypoints, newWaypoints)
        val state = FlowEditorState(flow = Flow(name = "TestWpCmd", connections = listOf(conn)))
        val executed = cmd.execute(state)
        assertEquals(2, executed.flow.connections.first().waypoints.size)
        val undone = cmd.undo(executed)
        assertEquals(1, undone.flow.connections.first().waypoints.size)
    }

    @Test
    fun testGroupResizeConsistencyAcrossZoomLevels() {
        val testScales = listOf(0.5f, 1.0f, 1.5f, 2.0f)
        val screenDragAmount = androidx.compose.ui.geometry.Offset(80f, 60f)

        for (scale in testScales) {
            val initialGroup = FlowGroup(id = 10L, title = "Resizable", position = ModelOffset(100f, 100f), size = ModelOffset(300f, 200f))
            val initialFlow = Flow(name = "TestZoomResize", groups = listOf(initialGroup))
            val vm = createViewModel(initialFlow)

            // Pointer gesture produces screen drag; board delta passed to VM is screenDrag / scale
            val boardDelta = ModelOffset(screenDragAmount.x / scale, screenDragAmount.y / scale)
            vm.onEvent(FlowEvent.ResizeGroup(initialGroup.id, boardDelta))

            val updatedGroup = vm.state.value.flow.groups.first()
            val boardSizeDeltaX = updatedGroup.size.x - initialGroup.size.x
            val boardSizeDeltaY = updatedGroup.size.y - initialGroup.size.y

            // On screen, the size change is boardSizeDelta * scale, which MUST match screenDragAmount exactly
            assertEquals(screenDragAmount.x, boardSizeDeltaX * scale, 0.001f, "Width resize on screen must match drag delta at scale $scale")
            assertEquals(screenDragAmount.y, boardSizeDeltaY * scale, 0.001f, "Height resize on screen must match drag delta at scale $scale")
        }
    }

    @Test
    fun testGroupAndLabelMovementConsistencyAcrossZoomLevels() {
        val testScales = listOf(0.5f, 1.0f, 1.5f, 2.0f)
        val screenDragAmount = androidx.compose.ui.geometry.Offset(50f, 75f)

        for (scale in testScales) {
            val group = FlowGroup(id = 1L, title = "G", position = ModelOffset(100f, 100f), size = ModelOffset(300f, 200f))
            val label = FlowLabel(id = 2L, text = "Note", position = ModelOffset(150f, 150f))
            val flow = Flow(name = "TestZoomMove", groups = listOf(group), labels = listOf(label))
            val vm = createViewModel(flow)

            // Move Group: screen drag divided by scale
            val groupBoardDelta = ModelOffset(screenDragAmount.x / scale, screenDragAmount.y / scale)
            vm.onEvent(FlowEvent.MoveGroup(group.id, groupBoardDelta))

            val movedGroup = vm.state.value.flow.groups.first()
            val groupScreenDisplacementX = (movedGroup.position.x - group.position.x) * scale
            val groupScreenDisplacementY = (movedGroup.position.y - group.position.y) * scale

            assertEquals(screenDragAmount.x, groupScreenDisplacementX, 0.001f, "Group screen movement must match mouse drag at scale $scale")
            assertEquals(screenDragAmount.y, groupScreenDisplacementY, 0.001f, "Group screen movement must match mouse drag at scale $scale")

            // Move Label: screen drag divided by scale
            val labelBoardDelta = ModelOffset(screenDragAmount.x / scale, screenDragAmount.y / scale)
            vm.onEvent(FlowEvent.MoveLabel(label.id, labelBoardDelta))

            val movedLabel = vm.state.value.flow.labels.first()
            val labelScreenDisplacementX = (movedLabel.position.x - label.position.x) * scale
            val labelScreenDisplacementY = (movedLabel.position.y - label.position.y) * scale

            assertEquals(screenDragAmount.x, labelScreenDisplacementX, 0.001f, "Label screen movement must match mouse drag at scale $scale")
            assertEquals(screenDragAmount.y, labelScreenDisplacementY, 0.001f, "Label screen movement must match mouse drag at scale $scale")
        }
    }

    @Test
    fun testCollapsedGroupConnectionEndpointsAlignWithPortBadges() {
        val collapsedGroup = FlowGroup(
            id = 100L,
            title = "Input Group",
            position = ModelOffset(100f, 200f),
            size = ModelOffset(400f, 250f),
            isCollapsed = true,
            nodeIds = listOf(1L, 2L)
        )
        // Outgoing connection: from node 1 (inside group) to node 3 (outside)
        val outgoingConn = Connection(sourceNodeId = 1L, sourcePortId = "out", targetNodeId = 3L, targetPortId = "in")
        // Incoming connection: from node 4 (outside) to node 2 (inside group)
        val incomingConn = Connection(sourceNodeId = 4L, sourcePortId = "out", targetNodeId = 2L, targetPortId = "in")
        // Internal connection: both inside collapsed group
        val internalConn = Connection(sourceNodeId = 1L, sourcePortId = "out", targetNodeId = 2L, targetPortId = "in")

        val getPortBoardPos: (Long, String, Boolean) -> androidx.compose.ui.geometry.Offset? = { id, _, _ ->
            when (id) {
                1L -> androidx.compose.ui.geometry.Offset(150f, 220f)
                2L -> androidx.compose.ui.geometry.Offset(250f, 220f)
                3L -> androidx.compose.ui.geometry.Offset(700f, 200f)
                4L -> androidx.compose.ui.geometry.Offset(10f, 200f)
                else -> null
            }
        }

        val testScales = listOf(0.5f, 1.0f, 2.0f)
        val testDensities = listOf(1.0f, 1.5f, 2.0f)
        val boardOffset = androidx.compose.ui.geometry.Offset(50f, 30f)

        for (scale in testScales) {
            for (density in testDensities) {
                val expectedCollapsedWidthPx = maxOf(collapsedGroup.size.x * scale, 200f * density * scale)
                val expectedCollapsedHeightPx = 44f * density * scale

                // 1. Outgoing connection start must align with the collapsed group's right edge center
                val outPoints = ConnectionHitTester.getConnectionScreenPoints(
                    connection = outgoingConn,
                    getPortBoardPosition = getPortBoardPos,
                    junctionMap = emptyMap(),
                    scale = scale,
                    offset = boardOffset,
                    groups = listOf(collapsedGroup),
                    density = density
                )
                assertNotNull(outPoints, "Outgoing points must not be null")
                val expectedOutStartX = (collapsedGroup.position.x * scale + boardOffset.x) + expectedCollapsedWidthPx
                val expectedOutStartY = (collapsedGroup.position.y * scale + boardOffset.y) + (expectedCollapsedHeightPx / 2f)
                assertEquals(expectedOutStartX, outPoints.first().x, 0.001f, "Outgoing wire start X must match port badge at scale $scale, density $density")
                assertEquals(expectedOutStartY, outPoints.first().y, 0.001f, "Outgoing wire start Y must match port badge at scale $scale, density $density")

                // 2. Incoming connection end must align with the collapsed group's left edge center
                val inPoints = ConnectionHitTester.getConnectionScreenPoints(
                    connection = incomingConn,
                    getPortBoardPosition = getPortBoardPos,
                    junctionMap = emptyMap(),
                    scale = scale,
                    offset = boardOffset,
                    groups = listOf(collapsedGroup),
                    density = density
                )
                assertNotNull(inPoints, "Incoming points must not be null")
                val expectedInEndX = (collapsedGroup.position.x * scale + boardOffset.x)
                val expectedInEndY = (collapsedGroup.position.y * scale + boardOffset.y) + (expectedCollapsedHeightPx / 2f)
                assertEquals(expectedInEndX, inPoints.last().x, 0.001f, "Incoming wire end X must match port badge at scale $scale, density $density")
                assertEquals(expectedInEndY, inPoints.last().y, 0.001f, "Incoming wire end Y must match port badge at scale $scale, density $density")

                // 3. Internal connection within the same collapsed group must be hidden (null)
                val internalPoints = ConnectionHitTester.getConnectionScreenPoints(
                    connection = internalConn,
                    getPortBoardPosition = getPortBoardPos,
                    junctionMap = emptyMap(),
                    scale = scale,
                    offset = boardOffset,
                    groups = listOf(collapsedGroup),
                    density = density
                )
                assertNull(internalPoints, "Internal connection within collapsed group must be hidden")
            }
        }
    }

    @Test
    fun testLabelCannotBeResizedOnlyGroupsAreResizable() {
        val label = FlowLabel(id = 5L, text = "Fixed Size Card", position = ModelOffset(50f, 50f))
        val group = FlowGroup(id = 6L, title = "Resizable Group", position = ModelOffset(100f, 100f), size = ModelOffset(200f, 150f))
        val flow = Flow(name = "TestResize", groups = listOf(group), labels = listOf(label))
        val vm = createViewModel(flow)

        // Resizing group works
        vm.onEvent(FlowEvent.ResizeGroup(group.id, ModelOffset(50f, 30f)))
        assertEquals(250f, vm.state.value.flow.groups.first().size.x)
        assertEquals(180f, vm.state.value.flow.groups.first().size.y)

        // Labels have no size property and are not resizable; they stay as content cards
        val currentLabel = vm.state.value.flow.labels.first()
        assertEquals("Fixed Size Card", currentLabel.text)
        assertEquals(ModelOffset(50f, 50f), currentLabel.position)
    }

    @Test
    fun testMidpointWaypointInsertionAtSpecificIndex() {
        val conn = Connection(
            sourceNodeId = 1L,
            sourcePortId = "out",
            targetNodeId = 2L,
            targetPortId = "in",
            waypoints = listOf(ModelOffset(50f, 50f), ModelOffset(150f, 150f))
        )
        val flow = Flow(name = "TestMidpointInsertion", connections = listOf(conn))
        val vm = createViewModel(flow)

        // Insert at index 1 (between 50,50 and 150,150)
        val insertedPoint = ModelOffset(100f, 100f)
        vm.onEvent(FlowEvent.AddWaypoint(conn, insertedPoint, index = 1))

        val updatedConn = vm.state.value.flow.connections.first()
        assertEquals(3, updatedConn.waypoints.size)
        assertEquals(ModelOffset(50f, 50f), updatedConn.waypoints[0])
        assertEquals(ModelOffset(100f, 100f), updatedConn.waypoints[1])
        assertEquals(ModelOffset(150f, 150f), updatedConn.waypoints[2])

        // Undo properly reverts the insertion
        vm.undo()
        assertEquals(2, vm.state.value.flow.connections.first().waypoints.size)
    }

    @Test
    fun testStructuredConnectionCreationWithWaypoints() {
        val flow = Flow(name = "TestStructuredConn")
        val vm = createViewModel(flow)

        val waypoints = listOf(
            ModelOffset(100f, 100f),
            ModelOffset(200f, 100f),
            ModelOffset(200f, 300f)
        )
        vm.onEvent(
            FlowEvent.ConnectPortsWithWaypoints(
                sourceNodeId = 1L,
                sourcePortId = "out",
                targetNodeId = 2L,
                targetPortId = "in",
                waypoints = waypoints
            )
        )

        val connections = vm.state.value.flow.connections
        assertEquals(1, connections.size)
        val conn = connections.first()
        assertEquals(1L, conn.sourceNodeId)
        assertEquals(2L, conn.targetNodeId)
        assertEquals(3, conn.waypoints.size)
        assertEquals(waypoints, conn.waypoints)
    }

    @Test
    fun testStructuredConnectionFloatingTargetOnEscape() {
        val flow = Flow(name = "TestFloatingStructuredConn")
        val vm = createViewModel(flow)

        val intermediate = listOf(ModelOffset(100f, 100f), ModelOffset(200f, 100f))
        val lastPoint = ModelOffset(200f, 300f)

        vm.onEvent(
            FlowEvent.CreateFloatingConnection(
                sourceNodeId = 1L,
                sourcePortId = "out",
                floatingTarget = lastPoint,
                waypoints = intermediate
            )
        )

        val conn = vm.state.value.flow.connections.first()
        assertTrue(conn.isFloating)
        assertEquals(lastPoint, conn.floatingTarget)
        assertEquals(intermediate, conn.waypoints)
    }

    @Test
    fun testToggleStructuredConnectionMode() {
        val flow = Flow(name = "TestToggleStructured")
        val vm = createViewModel(flow)

        assertFalse(vm.state.value.isAdvancedConnectionMode)
        vm.onEvent(FlowEvent.ToggleStructuredConnectionMode)
        assertTrue(vm.state.value.isAdvancedConnectionMode)
        vm.onEvent(FlowEvent.ToggleStructuredConnectionMode)
        assertFalse(vm.state.value.isAdvancedConnectionMode)
    }

    @Test
    fun testStructuredConnectionFlagPersistence() {
        val flow = Flow(name = "TestStructuredFlag")
        val vm = createViewModel(flow)

        vm.onEvent(
            FlowEvent.ConnectPortsWithWaypoints(
                sourceNodeId = 1L,
                sourcePortId = "out",
                targetNodeId = 2L,
                targetPortId = "in",
                waypoints = listOf(ModelOffset(50f, 50f)),
                isStructured = true
            )
        )

        val conn = vm.state.value.flow.connections.first()
        assertTrue(conn.isStructured)

        // Floating structured connection
        vm.onEvent(
            FlowEvent.CreateFloatingConnection(
                sourceNodeId = 1L,
                sourcePortId = "out2",
                floatingTarget = ModelOffset(100f, 100f),
                isStructured = true
            )
        )
        val floatingConn = vm.state.value.flow.connections.find { it.sourcePortId == "out2" }
        assertNotNull(floatingConn)
        assertTrue(floatingConn.isStructured)
    }

    @Test
    fun testMoveWaypointDirectlyToAbsolutePosition() {
        val conn = Connection(
            sourceNodeId = 1L,
            sourcePortId = "out",
            targetNodeId = 2L,
            targetPortId = "in",
            waypoints = listOf(ModelOffset(10f, 10f), ModelOffset(50f, 50f))
        )
        val flow = Flow(name = "TestMoveWpAbsolute", connections = listOf(conn))
        val vm = createViewModel(flow)

        // Move waypoint 0 directly to (200, 300)
        val targetPos = ModelOffset(200f, 300f)
        vm.onEvent(FlowEvent.MoveWaypoint(conn, 0, targetPos))

        val updatedConn = vm.state.value.flow.connections.first()
        assertEquals(targetPos, updatedConn.waypoints[0])
        assertEquals(ModelOffset(50f, 50f), updatedConn.waypoints[1])
    }

    @Test
    fun testConnectPortsWithWaypointsReplacesExistingNonListConnection() {
        val node1 = Node.SystemNode(
            id = 1L,
            position = ModelOffset(0f, 0f),
            title = "Node 1",
            systemAction = "action1",
            inputs = emptyList(),
            outputs = listOf(OutputPort("out", "Out", dataType = DataType.Primitive(PrimitiveType.STRING)))
        )
        val node2 = Node.SystemNode(
            id = 2L,
            position = ModelOffset(200f, 0f),
            title = "Node 2",
            systemAction = "action2",
            inputs = listOf(InputPort("in", "In", dataType = DataType.Primitive(PrimitiveType.STRING))),
            outputs = emptyList()
        )
        val node3 = Node.SystemNode(
            id = 3L,
            position = ModelOffset(100f, 200f),
            title = "Node 3",
            systemAction = "action3",
            inputs = emptyList(),
            outputs = listOf(OutputPort("out", "Out", dataType = DataType.Primitive(PrimitiveType.STRING)))
        )
        val initialConn = Connection(
            sourceNodeId = 1L,
            sourcePortId = "out",
            targetNodeId = 2L,
            targetPortId = "in"
        )
        val flow = Flow(name = "TestSingleConnection", nodes = listOf(node1, node2, node3), connections = listOf(initialConn))
        val vm = createViewModel(flow)

        // Connect node3:out to node2:in with waypoints. Node 2's "in" port is String (not List), so it must replace initialConn!
        vm.onEvent(
            FlowEvent.ConnectPortsWithWaypoints(
                sourceNodeId = 3L,
                sourcePortId = "out",
                targetNodeId = 2L,
                targetPortId = "in",
                waypoints = listOf(ModelOffset(150f, 100f)),
                isStructured = true
            )
        )

        val connections = vm.state.value.flow.connections
        assertEquals(1, connections.size)
        val newConn = connections.first()
        assertEquals(3L, newConn.sourceNodeId)
        assertEquals(2L, newConn.targetNodeId)
        assertTrue(newConn.isStructured)
        assertEquals(listOf(ModelOffset(150f, 100f)), newConn.waypoints)

        // Undo restores initialConn
        vm.undo()
        val afterUndo = vm.state.value.flow.connections
        assertEquals(1, afterUndo.size)
        assertEquals(1L, afterUndo.first().sourceNodeId)
    }

    @Test
    fun testAddJunctionAndBranchPreservesStructuredFlagAndWaypoints() {
        val wp0 = ModelOffset(50f, 50f)
        val wp1 = ModelOffset(100f, 100f)
        val wp2 = ModelOffset(150f, 150f)
        val structuredConn = Connection(
            sourceNodeId = 1L,
            sourcePortId = "out",
            targetNodeId = 2L,
            targetPortId = "in",
            waypoints = listOf(wp0, wp1, wp2),
            isStructured = true,
            orderIndex = 0
        )
        val flow = Flow(name = "TestBranchStructured", connections = listOf(structuredConn))
        val vm = createViewModel(flow)

        val splitPos = ModelOffset(75f, 75f)
        // Split at segmentIndex = 1 (between wp0 and wp1)
        vm.onEvent(
            FlowEvent.AddJunctionAndBranch(
                connection = structuredConn,
                splitPosition = splitPos,
                segmentIndex = 1
            )
        )

        val connections = vm.state.value.flow.connections
        assertEquals(2, connections.size)
        val junctions = vm.state.value.flow.junctions
        assertEquals(1, junctions.size)
        val junc = junctions.first()
        assertEquals(splitPos, junc.position)

        val conn1 = connections.find { it.targetJunctionId == junc.id }
        val conn2 = connections.find { it.sourceJunctionId == junc.id }
        assertNotNull(conn1)
        assertNotNull(conn2)

        assertTrue(conn1.isStructured)
        assertEquals(listOf(wp0), conn1.waypoints)

        assertTrue(conn2.isStructured)
        assertEquals(listOf(wp1, wp2), conn2.waypoints)
        assertEquals(0, conn2.orderIndex)
    }
}


