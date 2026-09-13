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
import org.wip.plugintoolkit.features.flows.logic.FlowTypeInference
import org.wip.plugintoolkit.features.flows.ui.snapToGrid
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.ConnectionPoint
import org.wip.plugintoolkit.features.flows.model.FlowGroup
import org.wip.plugintoolkit.features.flows.model.FlowJunction
import org.wip.plugintoolkit.features.flows.model.FlowLabel
import org.wip.plugintoolkit.features.flows.model.InputPort
import org.wip.plugintoolkit.features.shortcuts.logic.DefaultShortcutCatalog
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutActionId
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutGesture
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutPointerButton
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
import androidx.compose.ui.geometry.Offset
import org.wip.plugintoolkit.features.flows.utils.SplineMathUtils
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
        val n3 = Node.FlowOutputNode(id = 3L, position = ModelOffset(200f, 300f), inputs = listOf(InputPort("branchIn", "BIn", DataType.Primitive(PrimitiveType.STRING))))
        val mainConn = Connection(sourceNodeId = 1L, sourcePortId = "out", targetNodeId = 2L, targetPortId = "in")
        val initialFlow = Flow(name = "TestBranch", nodes = listOf(n1, n2, n3), connections = listOf(mainConn))
        val vm = createViewModel(initialFlow)

        // Dropping wire from n3:branchIn onto mainConn at (200, 50)
        vm.onEvent(
            FlowEvent.AddJunctionAndBranch(
                connection = mainConn,
                splitPosition = ModelOffset(200f, 50f),
                branchTargetNodeId = 3L,
                branchTargetPortId = "branchIn"
            )
        )

        assertEquals(1, vm.state.value.flow.junctions.size)
        val junc = vm.state.value.flow.junctions.first()
        assertEquals(ModelOffset(200f, 50f), junc.position)

        // 3 connections now: 1 -> junction, junction -> 2, AND junction -> 3!
        assertEquals(3, vm.state.value.flow.connections.size)
        val cBranch = vm.state.value.flow.connections.find { it.sourceJunctionId == junc.id && it.targetNodeId == 3L }
        assertNotNull(cBranch, "Branch connection from junction to node 3 must exist")

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

    @Test
    fun testOrthogonalSplineWithWaypointsRemainsOrthogonal() {
        val p0 = Offset(100f, 100f)
        val p1 = Offset(250f, 180f)
        val p2 = Offset(400f, 300f)
        val orthoPoints = SplineMathUtils.computeOrthogonalPoints(listOf(p0, p1, p2))
        assertTrue(orthoPoints.size > 3)
        // Verify every segment is strictly axis-aligned (either dx == 0 or dy == 0)
        for (i in 0 until orthoPoints.size - 1) {
            val a = orthoPoints[i]
            val b = orthoPoints[i + 1]
            assertTrue(a.x == b.x || a.y == b.y, "Segment from $a to $b must be strictly orthogonal")
        }
        val path = SplineMathUtils.buildConnectionPath(listOf(p0, p1, p2), ConnectionCurveStyle.Orthogonal)
        assertFalse(path.isEmpty)
    }

    @Test
    fun testShiftSnapsAt45DegreesAndCtrlSnapsAt90Degrees() {
        val start = Offset(100f, 100f)
        val current = Offset(180f, 120f) // roughly 14 degrees
        val shiftSnapped = SplineMathUtils.snapToStraightAngle(start, current)
        val dx = kotlin.math.abs(shiftSnapped.x - start.x)
        val dy = kotlin.math.abs(shiftSnapped.y - start.y)
        // At 45 degrees, dx is either 0, dy is 0, or dx is equal to dy
        val isHorizontal = dy < 0.01f
        val isVertical = dx < 0.01f
        val isDiagonal = kotlin.math.abs(dx - dy) < 0.01f
        assertTrue(isHorizontal || isVertical || isDiagonal, "Shift snap must produce 45 degree angle increment")

        val ctrlSnapped = SplineMathUtils.snapToOrthogonal(start, current)
        assertTrue(ctrlSnapped.x == start.x || ctrlSnapped.y == start.y, "Ctrl snap must be strictly horizontal or vertical (90 deg)")
    }

    @Test
    fun testDeleteConnectionCleansUpOrphanJunction() {
        val junc = FlowJunction(id = 50L, position = ModelOffset(100f, 100f))
        val conn = Connection(
            sourceNodeId = 1L,
            sourcePortId = "out",
            targetNodeId = -1L,
            targetPortId = "",
            targetJunctionId = 50L
        )
        val flow = Flow(name = "TestOrphanJunction", junctions = listOf(junc), connections = listOf(conn))
        val vm = createViewModel(flow)

        assertEquals(1, vm.state.value.flow.junctions.size)
        vm.onEvent(FlowEvent.DeleteConnection(conn))
        // Since no other connections connect to junction 50, it must be cleaned up
        assertEquals(0, vm.state.value.flow.junctions.size)
        assertEquals(0, vm.state.value.flow.connections.size)
    }

    @Test
    fun testDeleteConnectionKeepsJunctionIfOtherConnectionsRemain() {
        val junc = FlowJunction(id = 50L, position = ModelOffset(100f, 100f))
        val conn1 = Connection(
            sourceNodeId = 1L,
            sourcePortId = "out",
            targetNodeId = -1L,
            targetPortId = "",
            targetJunctionId = 50L
        )
        val conn2 = Connection(
            sourceNodeId = -1L,
            sourcePortId = "",
            sourceJunctionId = 50L,
            targetNodeId = 2L,
            targetPortId = "in"
        )
        val conn3 = Connection(
            sourceNodeId = -1L,
            sourcePortId = "",
            sourceJunctionId = 50L,
            targetNodeId = 3L,
            targetPortId = "in"
        )
        val flow = Flow(name = "TestJunctionKept", junctions = listOf(junc), connections = listOf(conn1, conn2, conn3))
        val vm = createViewModel(flow)

        assertEquals(1, vm.state.value.flow.junctions.size)
        assertEquals(3, vm.state.value.flow.connections.size)
        vm.onEvent(FlowEvent.DeleteConnection(conn2))
        // conn1 and conn3 still connect through junction 50, so junction and remaining connections must remain
        assertEquals(1, vm.state.value.flow.junctions.size)
        assertEquals(2, vm.state.value.flow.connections.size)
        assertTrue(vm.state.value.flow.connections.contains(conn1))
        assertTrue(vm.state.value.flow.connections.contains(conn3))
    }

    @Test
    fun testDeleteConnectionPurgesJunctionWhenSourceLost() {
        val junc = FlowJunction(id = 50L, position = ModelOffset(100f, 100f))
        val conn1 = Connection(
            sourceNodeId = 1L,
            sourcePortId = "out",
            targetNodeId = -1L,
            targetPortId = "",
            targetJunctionId = 50L
        )
        val conn2 = Connection(
            sourceNodeId = -1L,
            sourcePortId = "",
            sourceJunctionId = 50L,
            targetNodeId = 2L,
            targetPortId = "in"
        )
        val flow = Flow(name = "TestJunctionPurged", junctions = listOf(junc), connections = listOf(conn1, conn2))
        val vm = createViewModel(flow)

        assertEquals(1, vm.state.value.flow.junctions.size)
        vm.onEvent(FlowEvent.DeleteConnection(conn1))
        // Since junction 50 lost its source connection, it is a stray point and must be purged along with conn2
        assertEquals(0, vm.state.value.flow.junctions.size)
        assertEquals(0, vm.state.value.flow.connections.size)
    }

    @Test
    fun testConnectPortsWithWaypointsToExistingJunction() {
        val junc = FlowJunction(id = 99L, position = ModelOffset(200f, 200f))
        val flow = Flow(name = "TestConnectToJunction", junctions = listOf(junc))
        val vm = createViewModel(flow)

        vm.onEvent(
            FlowEvent.ConnectPortsWithWaypoints(
                sourceNodeId = 10L,
                sourcePortId = "out",
                targetNodeId = -1L,
                targetPortId = "",
                targetJunctionId = 99L,
                waypoints = listOf(ModelOffset(150f, 150f)),
                isStructured = true
            )
        )

        val connections = vm.state.value.flow.connections
        assertEquals(1, connections.size)
        val conn = connections.first()
        assertEquals(10L, conn.sourceNodeId)
        assertEquals("out", conn.sourcePortId)
        assertEquals(99L, conn.targetJunctionId)
        assertEquals(listOf(ModelOffset(150f, 150f)), conn.waypoints)
        assertTrue(conn.isStructured)
    }

    @Test
    fun testComputeOrthogonalPointsContinuousRouting() {
        // Test an L-corner path: (0,0) -> (100,0) -> (100,100)
        val lPoints = listOf(Offset(0f, 0f), Offset(100f, 0f), Offset(100f, 100f))
        val orthoL = SplineMathUtils.computeOrthogonalPoints(lPoints)
        // Should only have 3 points without extra midX insertions
        assertEquals(3, orthoL.size)
        assertEquals(Offset(0f, 0f), orthoL[0])
        assertEquals(Offset(100f, 0f), orthoL[1])
        assertEquals(Offset(100f, 100f), orthoL[2])

        // Verify that all segments in an arbitrary orthogonal path are strictly horizontal or vertical
        val steppedPoints = listOf(Offset(0f, 0f), Offset(100f, 50f), Offset(200f, 100f))
        val orthoStepped = SplineMathUtils.computeOrthogonalPoints(steppedPoints)
        for (i in 0 until orthoStepped.size - 1) {
            val pA = orthoStepped[i]
            val pB = orthoStepped[i + 1]
            val isHorizontal = kotlin.math.abs(pA.y - pB.y) < 0.01f
            val isVertical = kotlin.math.abs(pA.x - pB.x) < 0.01f
            assertTrue(isHorizontal || isVertical, "Segment from $pA to $pB must be orthogonal")
        }
    }

    @Test
    fun testComputeOrthogonalPointsDirectionalContinuity() {
        val points = listOf(
            Offset(0f, 0f),
            Offset(50f, 100f),
            Offset(50f, 200f),
            Offset(150f, 200f)
        )
        val ortho = SplineMathUtils.computeOrthogonalPoints(points)
        // Ensure strictly orthogonal
        for (i in 0 until ortho.size - 1) {
            val pA = ortho[i]
            val pB = ortho[i + 1]
            val isHorizontal = kotlin.math.abs(pA.y - pB.y) < 0.01f
            val isVertical = kotlin.math.abs(pA.x - pB.x) < 0.01f
            assertTrue(isHorizontal || isVertical, "Segment $pA -> $pB must be orthogonal")
        }
        assertEquals(Offset(0f, 0f), ortho.first())
        assertEquals(Offset(150f, 200f), ortho.last())
    }

    @Test
    fun testStructuredConnectionRetainsCurveStyle() {
        val conn = Connection(
            sourceNodeId = 1L,
            sourcePortId = "out",
            targetNodeId = 2L,
            targetPortId = "in",
            waypoints = listOf(ModelOffset(50f, 50f)),
            isStructured = true
        )
        val getPortPos: (Long, String, Boolean) -> Offset? = { id, _, _ ->
            if (id == 1L) Offset(0f, 0f) else Offset(100f, 100f)
        }
        val proj = ConnectionHitTester.findClosestConnectionWithProjection(
            position = Offset(25f, 25f),
            connections = listOf(conn),
            getPortBoardPosition = getPortPos,
            scale = 1f,
            offset = Offset.Zero,
            junctions = emptyList(),
            curveStyle = ConnectionCurveStyle.Straight
        )
        assertNotNull(proj)
        assertEquals(conn, proj.connection)
        // With Straight style, (25, 25) lies exactly on the segment (0,0) -> (50,50)
        assertEquals(25f, proj.projectedPoint.x, 0.5f)
        assertEquals(25f, proj.projectedPoint.y, 0.5f)
        assertEquals(0, proj.segmentIndex)
    }

    @Test
    fun testDeleteConnectionSegmentSplitsConnectionInMiddle() {
        val conn = Connection(
            sourceNodeId = 1L,
            sourcePortId = "out",
            targetNodeId = 2L,
            targetPortId = "in",
            waypoints = listOf(ModelOffset(100f, 50f), ModelOffset(200f, 150f)),
            color = "#FF00FF"
        )
        val flow = Flow(name = "TestSplitConn", connections = listOf(conn))
        val vm = createViewModel(flow)

        // Delete middle segment (segmentIndex = 1)
        vm.onEvent(FlowEvent.DeleteConnectionSegment(conn, segmentIndex = 1))

        val conns = vm.state.value.flow.connections
        assertEquals(2, conns.size)

        // Part 1: sourceNodeId = 1L, floatingTarget = (100, 50), empty waypoints
        val conn1 = conns.find { it.sourceNodeId == 1L }
        assertNotNull(conn1)
        assertEquals(ModelOffset(100f, 50f), conn1.floatingTarget)
        assertEquals(emptyList(), conn1.waypoints)
        assertEquals(-1L, conn1.targetNodeId)

        // Part 2: starts from new junction at (200, 150), targets node 2
        val createdJunc = vm.state.value.flow.junctions.firstOrNull()
        assertNotNull(createdJunc)
        assertEquals(ModelOffset(200f, 150f), createdJunc.position)

        val conn2 = conns.find { it.targetNodeId == 2L }
        assertNotNull(conn2)
        assertEquals(createdJunc.id, conn2.sourceJunctionId)
        assertEquals(emptyList(), conn2.waypoints)
    }

    @Test
    fun testDeleteConnectionSegmentFirstAndLastSegments() {
        val conn = Connection(
            sourceNodeId = 1L,
            sourcePortId = "out",
            targetNodeId = 2L,
            targetPortId = "in",
            waypoints = listOf(ModelOffset(100f, 50f))
        )
        val flow = Flow(name = "TestSegDelete", connections = listOf(conn))

        // Case 1: Deleting last segment (segmentIndex = 1) turns connection into floating at waypoint 0
        val vm1 = createViewModel(flow)
        vm1.onEvent(FlowEvent.DeleteConnectionSegment(conn, segmentIndex = 1))
        val conns1 = vm1.state.value.flow.connections
        assertEquals(1, conns1.size)
        val floatConn = conns1.first()
        assertEquals(1L, floatConn.sourceNodeId)
        assertEquals(-1L, floatConn.targetNodeId)
        assertEquals(ModelOffset(100f, 50f), floatConn.floatingTarget)
        assertTrue(floatConn.waypoints.isEmpty())

        // Case 2: Deleting first segment (segmentIndex = 0) creates junction at waypoint 0 and connects junction to target
        val vm2 = createViewModel(flow)
        vm2.onEvent(FlowEvent.DeleteConnectionSegment(conn, segmentIndex = 0))
        val conns2 = vm2.state.value.flow.connections
        assertEquals(1, conns2.size)
        val juncConn = conns2.first()
        assertEquals(2L, juncConn.targetNodeId)
        assertNotNull(juncConn.sourceJunctionId)
        val junc = vm2.state.value.flow.junctions.firstOrNull()
        assertNotNull(junc)
        assertEquals(ModelOffset(100f, 50f), junc.position)
        assertEquals(junc.id, juncConn.sourceJunctionId)
    }

    @Test
    fun testComputeSegmentMidpointsOrthogonal() {
        val points = listOf(Offset(0f, 0f), Offset(100f, 0f), Offset(100f, 100f))
        val midpoints = SplineMathUtils.computeSegmentMidpoints(points, ConnectionCurveStyle.Orthogonal)
        assertEquals(2, midpoints.size)
        assertEquals(50f, midpoints[0].x, 0.5f)
        assertEquals(0f, midpoints[0].y, 0.5f)
        assertEquals(100f, midpoints[1].x, 0.5f)
        assertEquals(50f, midpoints[1].y, 0.5f)
    }

    @Test
    fun testSelectPointsAndClearSelection() {
        val junc1 = FlowJunction(id = 101L, position = ModelOffset(100f, 100f))
        val junc2 = FlowJunction(id = 102L, position = ModelOffset(200f, 200f))
        val flow = Flow(name = "TestPointSelection", junctions = listOf(junc1, junc2))
        val vm = createViewModel(flow)

        assertEquals(emptySet(), vm.state.value.selectedPointIds)

        vm.onEvent(FlowEvent.SelectPoints(setOf(101L)))
        assertEquals(setOf(101L), vm.state.value.selectedPointIds)

        vm.onEvent(FlowEvent.SelectPoints(setOf(101L, 102L)))
        assertEquals(setOf(101L, 102L), vm.state.value.selectedPointIds)

        vm.onEvent(FlowEvent.ClearSelection)
        assertTrue(vm.state.value.selectedPointIds.isEmpty())
    }

    @Test
    fun testDeleteSelectedPointsRemovesPointsAndTheirConnections() {
        val junc1 = FlowJunction(id = 101L, position = ModelOffset(100f, 100f))
        val conn1 = Connection(
            sourceNodeId = 1L,
            sourcePortId = "out",
            targetNodeId = -1L,
            targetPortId = "",
            targetJunctionId = 101L
        )
        val conn2 = Connection(
            sourceNodeId = -1L,
            sourcePortId = "",
            sourceJunctionId = 101L,
            targetNodeId = 2L,
            targetPortId = "in"
        )
        val flow = Flow(name = "TestDeletePoints", junctions = listOf(junc1), connections = listOf(conn1, conn2))
        val vm = createViewModel(flow)

        vm.onEvent(FlowEvent.SelectPoints(setOf(101L)))
        assertEquals(setOf(101L), vm.state.value.selectedPointIds)

        vm.onEvent(FlowEvent.DeleteSelectedNodes)
        assertTrue(vm.state.value.selectedPointIds.isEmpty())
        assertTrue(vm.state.value.flow.junctions.isEmpty())
        assertTrue(vm.state.value.flow.connections.isEmpty())
    }

    @Test
    fun testSplitConnectionAndConnectRejectsMultipleEntrypoints() {
        val originalConn = Connection(
            sourceNodeId = 1L,
            sourcePortId = "out",
            targetNodeId = 2L,
            targetPortId = "in",
            color = "#00FFCC",
            isStructured = true
        )
        val flow = Flow(name = "TestSplitWire", connections = listOf(originalConn))
        val vm = createViewModel(flow)

        // Split connection at (150, 150) and attempt to branch from node 3 ("out") into already-sourced wire
        vm.onEvent(
            FlowEvent.SplitConnectionAndConnect(
                connection = originalConn,
                splitPosition = ModelOffset(150f, 150f),
                sourceNodeId = 3L,
                sourcePortId = "out",
                intermediatePoints = listOf(ModelOffset(120f, 150f))
            )
        )

        val resultFlow = vm.state.value.flow
        // Multi-entrypoint split is rejected!
        assertEquals(1, resultFlow.connections.size)
        assertTrue(resultFlow.junctions.isEmpty())
    }

    @Test
    fun testPointShortcutCatalogMappings() {
        val actions = DefaultShortcutCatalog.actions

        val movePointAction = actions.find { it.id == ShortcutActionId.FLOW_MOVE_POINT }
        assertNotNull(movePointAction)
        assertTrue(
            movePointAction.defaultTriggers.any {
                it.pointerButton == ShortcutPointerButton.Left && it.gesture == ShortcutGesture.Drag &&
                        !it.isAlt && !it.isShift && !it.isCtrl
            }
        )

        val ramificationAction = actions.find { it.id == ShortcutActionId.FLOW_CREATE_RAMIFICATION }
        assertNotNull(ramificationAction)
        assertTrue(
            ramificationAction.defaultTriggers.any {
                it.pointerButton == ShortcutPointerButton.Left && it.gesture == ShortcutGesture.Drag && it.isAlt
            }
        )

        val branchWireAction = actions.find { it.id == ShortcutActionId.FLOW_BRANCH_WIRE }
        assertNotNull(branchWireAction)
        assertTrue(
            branchWireAction.defaultTriggers.any {
                it.pointerButton == ShortcutPointerButton.Left && it.gesture == ShortcutGesture.Drag && it.isShift
            }
        )
    }

    @Test
    fun testSplitConnectionAndConnectFromInputPortToWire() {
        val originalConn = Connection(
            sourceNodeId = 1L,
            sourcePortId = "out",
            targetNodeId = 2L,
            targetPortId = "in",
            color = "#FF0055"
        )
        val flow = Flow(name = "TestSplitWireInput", connections = listOf(originalConn))
        val vm = createViewModel(flow)

        // Drag from input port of node 3 ("in") and snap onto originalConn at (200, 200)
        vm.onEvent(
            FlowEvent.SplitConnectionAndConnect(
                connection = originalConn,
                splitPosition = ModelOffset(200f, 200f),
                targetNodeId = 3L,
                targetPortId = "in",
                intermediatePoints = listOf(ModelOffset(200f, 250f))
            )
        )

        val resultFlow = vm.state.value.flow
        // Intermediate point + split point = 2 junctions
        assertEquals(2, resultFlow.junctions.size)
        val splitJunc = resultFlow.junctions.find { it.position == ModelOffset(200f, 200f) }
        assertNotNull(splitJunc)

        // Original wire split into 2: (1 -> splitJunc) and (splitJunc -> 2)
        val splitIn = resultFlow.connections.find { it.sourceNodeId == 1L && it.targetJunctionId == splitJunc.id }
        assertNotNull(splitIn)
        val splitOut = resultFlow.connections.find { it.sourceJunctionId == splitJunc.id && it.targetNodeId == 2L }
        assertNotNull(splitOut)

        // Branch starts from splitJunc and feeds into Node 3 input
        val branchStart = resultFlow.connections.find { it.sourceJunctionId == splitJunc.id && it.targetJunctionId != null }
        assertNotNull(branchStart)
        val branchTarget = resultFlow.connections.find { it.targetNodeId == 3L && it.targetPortId == "in" }
        assertNotNull(branchTarget)
        // Ensure no invalid floating nodes
        assertTrue(resultFlow.connections.none { it.sourceNodeId == -1L && it.sourceJunctionId == null })
    }

    @Test
    fun testFinalizeStructuredConnectionWithPointsCreatesConnectionPoints() {
        val flow = Flow(name = "TestStructuredWithPoints")
        val vm = createViewModel(flow)

        vm.onEvent(
            FlowEvent.FinalizeStructuredConnectionWithPoints(
                sourceNodeId = 1L,
                sourcePortId = "out",
                targetNodeId = 2L,
                targetPortId = "in",
                points = listOf(ModelOffset(50f, 50f), ModelOffset(100f, 50f))
            )
        )

        val resultFlow = vm.state.value.flow
        // 2 intermediate points created as unified ConnectionPoints (junctions)
        assertEquals(2, resultFlow.junctions.size)
        // 3 connection segments created, none with legacy waypoints
        assertEquals(3, resultFlow.connections.size)
        assertTrue(resultFlow.connections.all { it.waypoints.isEmpty() })

        val pt1 = resultFlow.junctions.find { it.position == ModelOffset(50f, 50f) }
        val pt2 = resultFlow.junctions.find { it.position == ModelOffset(100f, 50f) }
        assertNotNull(pt1)
        assertNotNull(pt2)

        // Verify connectivity: 1 -> pt1 -> pt2 -> 2
        assertTrue(resultFlow.connections.any { it.sourceNodeId == 1L && it.targetJunctionId == pt1.id })
        assertTrue(resultFlow.connections.any { it.sourceJunctionId == pt1.id && it.targetJunctionId == pt2.id })
        assertTrue(resultFlow.connections.any { it.sourceJunctionId == pt2.id && it.targetNodeId == 2L })
    }

    @Test
    fun testDeleteDownstreamSegmentKeepsUpstreamPointsAndConnectionsAlive() {
        val j1 = FlowJunction(101L, ModelOffset(50f, 50f))
        val j2 = FlowJunction(102L, ModelOffset(100f, 50f))
        val seg1 = Connection(sourceNodeId = 1L, sourcePortId = "out", targetNodeId = -1L, targetPortId = "", targetJunctionId = 101L)
        val seg2 = Connection(sourceNodeId = -1L, sourcePortId = "", sourceJunctionId = 101L, targetNodeId = -1L, targetPortId = "", targetJunctionId = 102L)
        val seg3 = Connection(sourceNodeId = -1L, sourcePortId = "", sourceJunctionId = 102L, targetNodeId = 2L, targetPortId = "in")

        val flow = Flow(name = "TestCascade", junctions = listOf(j1, j2), connections = listOf(seg1, seg2, seg3))
        val vm = createViewModel(flow)

        // Delete seg3 (downstream segment)
        vm.onEvent(FlowEvent.DeleteConnection(seg3))

        val resultFlow = vm.state.value.flow
        // Seg1 and Seg2 as well as J1 and J2 must still exist
        assertEquals(2, resultFlow.junctions.size)
        assertEquals(2, resultFlow.connections.size)
        assertTrue(resultFlow.junctions.any { it.id == 101L })
        assertTrue(resultFlow.junctions.any { it.id == 102L })
        assertTrue(resultFlow.connections.any { it.targetJunctionId == 101L })
        assertTrue(resultFlow.connections.any { it.targetJunctionId == 102L })
    }

    @Test
    fun testNormalizeWirePointsConvertsLegacyWaypointsToConnectionPoints() {
        val legacyConn = Connection(
            sourceNodeId = 1L,
            sourcePortId = "out",
            targetNodeId = 2L,
            targetPortId = "in",
            waypoints = listOf(ModelOffset(40f, 60f), ModelOffset(80f, 100f))
        )
        val flow = Flow(name = "TestNormalize", connections = listOf(legacyConn))
        val vm = createViewModel(flow)

        vm.onEvent(FlowEvent.NormalizeWirePoints)

        val resultFlow = vm.state.value.flow
        // Waypoints converted to 2 junctions
        assertEquals(2, resultFlow.junctions.size)
        // 3 connection segments without waypoints
        assertEquals(3, resultFlow.connections.size)
        assertTrue(resultFlow.connections.all { it.waypoints.isEmpty() })
    }

    @Test
    fun testDeleteSelectedNodesDeletesPointsWhenOnlyPointsSelected() {
        val j1 = FlowJunction(101L, ModelOffset(50f, 50f))
        val conn1 = Connection(sourceNodeId = 1L, sourcePortId = "out", targetNodeId = -1L, targetPortId = "", targetJunctionId = 101L)
        val conn2 = Connection(sourceNodeId = -1L, sourcePortId = "", sourceJunctionId = 101L, targetNodeId = 2L, targetPortId = "in")

        val flow = Flow(name = "TestDeletePointsOnly", junctions = listOf(j1), connections = listOf(conn1, conn2))
        val vm = createViewModel(flow)

        // Select ONLY the junction point, no nodes selected
        vm.onEvent(FlowEvent.SelectPoints(setOf(101L)))
        assertTrue(vm.state.value.selectedPointIds.contains(101L))
        assertTrue(vm.state.value.selectedNodeIds.isEmpty())

        // Trigger DeleteSelectedNodes (triggered by Canc / Del)
        vm.onEvent(FlowEvent.DeleteSelectedNodes)

        val resultFlow = vm.state.value.flow
        assertTrue(resultFlow.junctions.isEmpty(), "Junction should be deleted")
        assertTrue(resultFlow.connections.isEmpty(), "Cascading connections should be purged")
        assertTrue(vm.state.value.selectedPointIds.isEmpty())
    }

    @Test
    fun testNodeAndPointMoveDoesNotDoubleOffset() {
        val node = Node.FlowInputNode(
            id = 1L,
            position = ModelOffset(100f, 100f),
            outputs = listOf(OutputPort("out", "Output", DataType.Primitive(PrimitiveType.STRING)))
        )
        val junc = FlowJunction(10L, ModelOffset(200f, 200f))
        val flow = Flow(name = "TestMoveNoDoubleOffset", nodes = listOf(node), junctions = listOf(junc))
        val vm = createViewModel(flow)

        // Select both node and point
        vm.onEvent(FlowEvent.SelectNodes(setOf(1L)))
        vm.onEvent(FlowEvent.SelectPoints(setOf(10L)))

        // Move node by (50, 50)
        vm.onEvent(FlowEvent.MoveNode(1L, Offset(50f, 50f), snap = false, showGhost = false))
        vm.onEvent(FlowEvent.EndMoveNode(1L, density = 1f))

        val movedNode = vm.state.value.flow.nodes.find { it.id == 1L }
        val movedJunc = vm.state.value.flow.junctions.find { it.id == 10L }
        assertNotNull(movedNode)
        assertNotNull(movedJunc)
        // Both should move by +50, not +100
        assertEquals(150f, movedNode.position.x)
        assertEquals(150f, movedNode.position.y)
        assertEquals(250f, movedJunc.position.x)
        assertEquals(250f, movedJunc.position.y)
    }

    @Test
    fun testMoveJunctionMovesSelectedNodesTogether() {
        val node = Node.FlowInputNode(
            id = 1L,
            position = ModelOffset(100f, 100f),
            outputs = listOf(OutputPort("out", "Output", DataType.Primitive(PrimitiveType.STRING)))
        )
        val junc = FlowJunction(10L, ModelOffset(200f, 200f))
        val flow = Flow(name = "TestMoveJuncWithNodes", nodes = listOf(node), junctions = listOf(junc))
        val vm = createViewModel(flow)

        // Select both node and point
        vm.onEvent(FlowEvent.SelectNodes(setOf(1L)))
        vm.onEvent(FlowEvent.SelectPoints(setOf(10L)))

        // Move junction transiently during drag
        vm.onEvent(FlowEvent.MoveJunction(10L, ModelOffset(30f, 40f), isTransient = true))

        val movingNode = vm.state.value.flow.nodes.find { it.id == 1L }
        val movingJunc = vm.state.value.flow.junctions.find { it.id == 10L }
        assertNotNull(movingNode)
        assertNotNull(movingJunc)
        assertEquals(130f, movingNode.position.x)
        assertEquals(140f, movingNode.position.y)
        assertEquals(230f, movingJunc.position.x)
        assertEquals(240f, movingJunc.position.y)

        // Transient move should NOT record command
        assertFalse(vm.canUndo.value)

        // Finish move with EndMoveJunction
        vm.onEvent(
            FlowEvent.EndMoveJunction(
                pointMoves = mapOf(10L to (ModelOffset(200f, 200f) to ModelOffset(230f, 240f))),
                nodeMoves = mapOf(1L to (ModelOffset(100f, 100f) to ModelOffset(130f, 140f)))
            )
        )

        // Now exactly 1 undo step exists
        assertTrue(vm.canUndo.value)

        // Undo should restore both
        vm.undo()
        val undoneNode = vm.state.value.flow.nodes.find { it.id == 1L }
        val undoneJunc = vm.state.value.flow.junctions.find { it.id == 10L }
        assertEquals(100f, undoneNode?.position?.x)
        assertEquals(100f, undoneNode?.position?.y)
        assertEquals(200f, undoneJunc?.position?.x)
        assertEquals(200f, undoneJunc?.position?.y)
    }

    @Test
    fun testWireBranchRejectsIncompatibleDataTypes() {
        val srcNode = Node.FlowInputNode(
            id = 1L,
            position = ModelOffset(0f, 0f),
            outputs = listOf(OutputPort("out_str", "String Out", DataType.Primitive(PrimitiveType.STRING)))
        )
        val tgtNode1 = Node.FlowOutputNode(
            id = 2L,
            position = ModelOffset(200f, 0f),
            inputs = listOf(InputPort("in_str", "String In", DataType.Primitive(PrimitiveType.STRING)))
        )
        val tgtNode2 = Node.FlowOutputNode(
            id = 3L,
            position = ModelOffset(200f, 100f),
            inputs = listOf(InputPort("in_bool", "Bool In", DataType.Primitive(PrimitiveType.BOOLEAN)))
        )
        val conn = Connection(sourceNodeId = 1L, sourcePortId = "out_str", targetNodeId = 2L, targetPortId = "in_str")
        val flow = Flow(name = "TestIncompatibleBranch", nodes = listOf(srcNode, tgtNode1, tgtNode2), connections = listOf(conn))
        val vm = createViewModel(flow)

        // Try to branch conn to tgtNode2 which expects BOOLEAN
        vm.onEvent(
            FlowEvent.AddJunctionAndBranch(
                connection = conn,
                splitPosition = ModelOffset(100f, 50f),
                segmentIndex = 0,
                branchTargetNodeId = 3L,
                branchTargetPortId = "in_bool"
            )
        )

        // Connection should be rejected!
        val resultFlow = vm.state.value.flow
        assertEquals(1, resultFlow.connections.size)
        assertTrue(resultFlow.junctions.isEmpty())
    }

    @Test
    fun testWireNetworkRejectsMultipleEntrypoints() {
        val srcNode1 = Node.FlowInputNode(
            id = 1L,
            position = ModelOffset(0f, 0f),
            outputs = listOf(OutputPort("out1", "Out 1", DataType.Primitive(PrimitiveType.STRING)))
        )
        val srcNode2 = Node.FlowInputNode(
            id = 2L,
            position = ModelOffset(0f, 100f),
            outputs = listOf(OutputPort("out2", "Out 2", DataType.Primitive(PrimitiveType.STRING)))
        )
        val tgtNode = Node.FlowOutputNode(
            id = 3L,
            position = ModelOffset(200f, 0f),
            inputs = listOf(InputPort("in", "In", DataType.Primitive(PrimitiveType.STRING)))
        )
        val conn = Connection(sourceNodeId = 1L, sourcePortId = "out1", targetNodeId = 3L, targetPortId = "in")
        val flow = Flow(name = "TestMultiEntrypoint", nodes = listOf(srcNode1, srcNode2, tgtNode), connections = listOf(conn))
        val vm = createViewModel(flow)

        // Try to connect srcNode2 into the existing connection wire (creating a second source/entrypoint)
        vm.onEvent(
            FlowEvent.AddJunctionAndBranch(
                connection = conn,
                splitPosition = ModelOffset(100f, 50f),
                segmentIndex = 0,
                branchSourceNodeId = 2L,
                branchSourcePortId = "out2"
            )
        )

        // Connection must be rejected
        val resultFlow = vm.state.value.flow
        assertEquals(1, resultFlow.connections.size)
        assertTrue(resultFlow.junctions.isEmpty())
    }

    @Test
    fun testWireBranchAllowsMissingSemanticTypes() {
        val srcNode = Node.FlowInputNode(
            id = 1L,
            position = ModelOffset(0f, 0f),
            outputs = listOf(OutputPort("out_path", "Path Out", DataType.Primitive(PrimitiveType.STRING), semanticTypes = emptyList()))
        )
        val tgtNode1 = Node.FlowOutputNode(
            id = 2L,
            position = ModelOffset(200f, 0f),
            inputs = listOf(InputPort("in_path1", "Path In 1", DataType.Primitive(PrimitiveType.STRING), semanticTypes = emptyList()))
        )
        val tgtNode2 = Node.FlowOutputNode(
            id = 3L,
            position = ModelOffset(200f, 100f),
            inputs = listOf(InputPort("in_path2", "Path In 2", DataType.Primitive(PrimitiveType.STRING), semanticTypes = emptyList()))
        )
        val conn = Connection(sourceNodeId = 1L, sourcePortId = "out_path", targetNodeId = 2L, targetPortId = "in_path1")
        val flow = Flow(name = "TestMissingSemanticsAllowed", nodes = listOf(srcNode, tgtNode1, tgtNode2), connections = listOf(conn))
        val vm = createViewModel(flow)

        // Branching wire when semantic types are absent on both sides must succeed!
        vm.onEvent(
            FlowEvent.AddJunctionAndBranch(
                connection = conn,
                splitPosition = ModelOffset(100f, 50f),
                segmentIndex = 0,
                branchTargetNodeId = 3L,
                branchTargetPortId = "in_path2"
            )
        )

        val resultFlow = vm.state.value.flow
        assertEquals(1, resultFlow.junctions.size)
        // Splits into 2 segments + 1 branch = 3 connections
        assertEquals(3, resultFlow.connections.size)
    }

    @Test
    fun testGridSnappingForPointsAndDragging() {
        val rawModel = ModelOffset(123f, 287f)
        val snappedModel = rawModel.snapToGrid(50f)
        assertEquals(ModelOffset(100f, 300f), snappedModel)

        val rawCompose = Offset(74f, 168f)
        val snappedCompose = rawCompose.snapToGrid(50f)
        assertEquals(Offset(50f, 150f), snappedCompose)
    }

    @Test
    fun testRejectMultipleIncomingConnectionsToNonArrayInput() {
        val srcNode1 = Node.SystemNode(
            id = 1L,
            position = ModelOffset(0f, 0f),
            title = "Src 1",
            systemAction = "act1",
            inputs = emptyList(),
            outputs = listOf(OutputPort("out1", "Out 1", dataType = DataType.Primitive(PrimitiveType.STRING)))
        )
        val tgtNode = Node.SystemNode(
            id = 2L,
            position = ModelOffset(200f, 0f),
            title = "Tgt",
            systemAction = "act2",
            inputs = listOf(InputPort("in_single", "In Single", dataType = DataType.Primitive(PrimitiveType.STRING))),
            outputs = emptyList()
        )
        val srcNode2 = Node.SystemNode(
            id = 3L,
            position = ModelOffset(0f, 200f),
            title = "Src 2",
            systemAction = "act3",
            inputs = emptyList(),
            outputs = listOf(OutputPort("out2", "Out 2", dataType = DataType.Primitive(PrimitiveType.STRING)))
        )
        val otherTgtNode = Node.SystemNode(
            id = 4L,
            position = ModelOffset(200f, 200f),
            title = "Other Tgt",
            systemAction = "act4",
            inputs = listOf(InputPort("in_other", "In Other", dataType = DataType.Primitive(PrimitiveType.STRING))),
            outputs = emptyList()
        )

        // Existing connection: 1:out1 -> 2:in_single
        val conn1 = Connection(sourceNodeId = 1L, sourcePortId = "out1", targetNodeId = 2L, targetPortId = "in_single")
        // Existing connection: 3:out2 -> 4:in_other
        val conn2 = Connection(sourceNodeId = 3L, sourcePortId = "out2", targetNodeId = 4L, targetPortId = "in_other")

        val flow = Flow(
            name = "TestMultiConnectionReject",
            nodes = listOf(srcNode1, tgtNode, srcNode2, otherTgtNode),
            connections = listOf(conn1, conn2)
        )
        val vm = createViewModel(flow)

        // Verify isInputPortAlreadyConnected is true for 2:in_single and 4:in_other
        assertTrue(vm.state.value.flow.isInputPortAlreadyConnected(2L, "in_single"))
        assertTrue(vm.state.value.flow.isInputPortAlreadyConnected(4L, "in_other"))

        // 1. Attempt AddJunctionAndBranch from conn2 into already connected 2:in_single
        vm.onEvent(
            FlowEvent.AddJunctionAndBranch(
                connection = conn2,
                splitPosition = ModelOffset(100f, 200f),
                branchTargetNodeId = 2L,
                branchTargetPortId = "in_single"
            )
        )
        // Must be rejected! Connections count stays 2
        assertEquals(2, vm.state.value.flow.connections.size)
        assertEquals(0, vm.state.value.flow.junctions.size)

        // 2. Attempt SplitConnectionAndConnect into 2:in_single
        vm.onEvent(
            FlowEvent.SplitConnectionAndConnect(
                connection = conn2,
                splitPosition = ModelOffset(100f, 200f),
                targetNodeId = 2L,
                targetPortId = "in_single"
            )
        )
        assertEquals(2, vm.state.value.flow.connections.size)
        assertEquals(0, vm.state.value.flow.junctions.size)

        // 3. Attempt FinalizeStructuredConnectionWithPoints into 2:in_single
        vm.onEvent(
            FlowEvent.FinalizeStructuredConnectionWithPoints(
                sourceNodeId = 3L,
                sourcePortId = "out2",
                targetNodeId = 2L,
                targetPortId = "in_single",
                points = listOf(ModelOffset(100f, 100f))
            )
        )
        assertEquals(2, vm.state.value.flow.connections.size)
        assertEquals(0, vm.state.value.flow.junctions.size)
    }

    @Test
    fun testFlowBrokenAndTypeInferenceValidationOnMultipleInputs() {
        val srcNode1 = Node.SystemNode(
            id = 1L,
            position = ModelOffset(0f, 0f),
            title = "Src 1",
            systemAction = "act1",
            inputs = emptyList(),
            outputs = listOf(OutputPort("out1", "Out 1", dataType = DataType.Primitive(PrimitiveType.STRING)))
        )
        val srcNode2 = Node.SystemNode(
            id = 2L,
            position = ModelOffset(0f, 100f),
            title = "Src 2",
            systemAction = "act2",
            inputs = emptyList(),
            outputs = listOf(OutputPort("out2", "Out 2", dataType = DataType.Primitive(PrimitiveType.STRING)))
        )
        val tgtNode = Node.SystemNode(
            id = 3L,
            position = ModelOffset(200f, 50f),
            title = "Tgt",
            systemAction = "act3",
            inputs = listOf(InputPort("in_single", "In Single", dataType = DataType.Primitive(PrimitiveType.STRING))),
            outputs = emptyList()
        )
        val conn1 = Connection(sourceNodeId = 1L, sourcePortId = "out1", targetNodeId = 3L, targetPortId = "in_single")
        val conn2 = Connection(sourceNodeId = 2L, sourcePortId = "out2", targetNodeId = 3L, targetPortId = "in_single")

        val multiConnectedFlow = Flow(
            name = "TestMultiFlowBroken",
            nodes = listOf(srcNode1, srcNode2, tgtNode),
            connections = listOf(conn1, conn2)
        )

        // Flow.isBroken should report true because a non-array input has > 1 incoming connection
        assertTrue(multiConnectedFlow.isBroken(setOf("act1", "act2", "act3")))

        // FlowTypeInference should emit a ValidationError
        val inferenceResult = FlowTypeInference.runTypeInference(multiConnectedFlow)
        val multiConnErrors = inferenceResult.validationErrors.filter { it.targetPortId == "in_single" }
        assertTrue(multiConnErrors.isNotEmpty(), "Validation errors must contain error for multi-connected input port")
    }

    @Test
    fun testArrayInputPortAllowsMultipleConnections() {
        val srcNode1 = Node.SystemNode(
            id = 1L,
            position = ModelOffset(0f, 0f),
            title = "Src 1",
            systemAction = "act1",
            inputs = emptyList(),
            outputs = listOf(OutputPort("out1", "Out 1", dataType = DataType.Primitive(PrimitiveType.STRING)))
        )
        val srcNode2 = Node.SystemNode(
            id = 2L,
            position = ModelOffset(0f, 100f),
            title = "Src 2",
            systemAction = "act2",
            inputs = emptyList(),
            outputs = listOf(OutputPort("out2", "Out 2", dataType = DataType.Primitive(PrimitiveType.STRING)))
        )
        val tgtNode = Node.SystemNode(
            id = 3L,
            position = ModelOffset(200f, 50f),
            title = "Tgt Array",
            systemAction = "act3",
            inputs = listOf(InputPort("in_list", "In List", dataType = DataType.Array(DataType.Primitive(PrimitiveType.STRING)))),
            outputs = emptyList()
        )
        val conn1 = Connection(sourceNodeId = 1L, sourcePortId = "out1", targetNodeId = 3L, targetPortId = "in_list", orderIndex = 0)
        val conn2 = Connection(sourceNodeId = 2L, sourcePortId = "out2", targetNodeId = 3L, targetPortId = "in_list", orderIndex = 1)

        val arrayFlow = Flow(
            name = "TestArrayListAllowed",
            nodes = listOf(srcNode1, srcNode2, tgtNode),
            connections = listOf(conn1, conn2)
        )

        // isInputPortAlreadyConnected is false for Array ports because they accept lists
        assertFalse(arrayFlow.isInputPortAlreadyConnected(3L, "in_list"))

        // Flow is NOT broken for array multiple inputs
        assertFalse(arrayFlow.isBroken(setOf("act1", "act2", "act3")))

        val inferenceResult = FlowTypeInference.runTypeInference(arrayFlow)
        val errors = inferenceResult.validationErrors.filter { it.targetPortId == "in_list" }
        assertTrue(errors.isEmpty(), "Array ports should have no validation errors for multiple connections")
    }
}


