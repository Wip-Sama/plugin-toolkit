package org.wip.plugintoolkit.features.flows.history

import androidx.compose.ui.geometry.Offset
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.features.flows.logic.FlowRepository
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.InputPort
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.model.Offset as ModelOffset
import org.wip.plugintoolkit.features.flows.model.OutputPort
import org.wip.plugintoolkit.features.flows.viewmodel.ActiveFlowEditorTracker
import org.wip.plugintoolkit.features.flows.viewmodel.FlowEditorState
import org.wip.plugintoolkit.features.flows.viewmodel.FlowEditorViewModel
import org.wip.plugintoolkit.features.flows.viewmodel.FlowEvent
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository
import org.wip.plugintoolkit.features.settings.model.AppSettings
import org.wip.plugintoolkit.features.settings.model.FlowSettings
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FlowCommandHistoryTest {

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

    @Test
    fun testAddNodeCommandUndoAndRedo() {
        val initialState = FlowEditorState(
            flow = Flow("Test"),
            nextId = 1L
        )

        val node = Node.SystemNode(
            id = 1L,
            position = ModelOffset(100f, 100f),
            title = "Log",
            systemAction = "log",
            inputs = listOf(InputPort("msg", "Message", DataType.Primitive(PrimitiveType.STRING))),
            outputs = emptyList()
        )

        val command = AddNodeCommand(node)
        val addedState = command.execute(initialState)

        assertEquals(1, addedState.flow.nodes.size)
        assertEquals(1L, addedState.flow.nodes.first().id)
        assertTrue(addedState.hasUnsavedChanges)
        assertEquals(2L, addedState.nextId)

        val undoneState = command.undo(addedState)
        assertEquals(0, undoneState.flow.nodes.size)
        assertTrue(undoneState.hasUnsavedChanges)

        val redoneState = command.execute(undoneState)
        assertEquals(1, redoneState.flow.nodes.size)
        assertEquals(1L, redoneState.flow.nodes.first().id)
    }

    @Test
    fun testMoveNodesCommandUndoAndRedo() {
        val node1 = Node.FlowInputNode(1L, ModelOffset(0f, 0f), emptyList())
        val node2 = Node.FlowOutputNode(2L, ModelOffset(200f, 200f), emptyList())

        val initialState = FlowEditorState(
            flow = Flow("Test", nodes = listOf(node1, node2))
        )

        val moves = mapOf(
            1L to (ModelOffset(0f, 0f) to ModelOffset(50f, 50f)),
            2L to (ModelOffset(200f, 200f) to ModelOffset(300f, 350f))
        )

        val command = MoveNodesCommand(moves)
        val movedState = command.execute(initialState)

        assertEquals(ModelOffset(50f, 50f), movedState.flow.nodes.find { it.id == 1L }?.position)
        assertEquals(ModelOffset(300f, 350f), movedState.flow.nodes.find { it.id == 2L }?.position)

        val undoneState = command.undo(movedState)
        assertEquals(ModelOffset(0f, 0f), undoneState.flow.nodes.find { it.id == 1L }?.position)
        assertEquals(ModelOffset(200f, 200f), undoneState.flow.nodes.find { it.id == 2L }?.position)

        val redoneState = command.execute(undoneState)
        assertEquals(ModelOffset(50f, 50f), redoneState.flow.nodes.find { it.id == 1L }?.position)
        assertEquals(ModelOffset(300f, 350f), redoneState.flow.nodes.find { it.id == 2L }?.position)
    }

    @Test
    fun testDeleteNodesCommandCascadeUndoAndRedo() {
        val node1 = Node.FlowInputNode(1L, ModelOffset(0f, 0f), listOf(OutputPort("out", "Out", DataType.Primitive(PrimitiveType.ANY))))
        val node2 = Node.FlowOutputNode(2L, ModelOffset(200f, 0f), listOf(InputPort("in", "In", DataType.Primitive(PrimitiveType.ANY))))
        val connection = Connection(1L, "out", 2L, "in")

        val stateWithNodes = FlowEditorState(
            flow = Flow("Test", nodes = listOf(node1, node2), connections = listOf(connection))
        )

        val command = DeleteNodesCommand(listOf(node2), listOf(connection))
        val deletedState = command.execute(stateWithNodes)

        assertEquals(1, deletedState.flow.nodes.size)
        assertEquals(0, deletedState.flow.connections.size)

        val undoneState = command.undo(deletedState)
        assertEquals(2, undoneState.flow.nodes.size)
        assertEquals(1, undoneState.flow.connections.size)
        assertEquals(connection, undoneState.flow.connections.first())

        val redoneState = command.execute(undoneState)
        assertEquals(1, redoneState.flow.nodes.size)
        assertEquals(0, redoneState.flow.connections.size)
    }

    @Test
    fun testConnectAndDisconnectPortsCommandUndoAndRedo() {
        val conn1 = Connection(1L, "out1", 2L, "in1")
        val conn2 = Connection(3L, "out2", 2L, "in1") // overwrites conn1 on single input port

        val initialState = FlowEditorState(
            flow = Flow("Test", connections = listOf(conn1))
        )

        val connectCommand = ConnectPortsCommand(conn2, overwrittenConnections = listOf(conn1))
        val connectedState = connectCommand.execute(initialState)

        assertEquals(listOf(conn2), connectedState.flow.connections)

        val undoneState = connectCommand.undo(connectedState)
        assertEquals(listOf(conn1), undoneState.flow.connections)

        val disconnectCommand = DisconnectPortsCommand(conn1)
        val disconnectedState = disconnectCommand.execute(undoneState)
        assertTrue(disconnectedState.flow.connections.isEmpty())

        val undoneDisconnect = disconnectCommand.undo(disconnectedState)
        assertEquals(listOf(conn1), undoneDisconnect.flow.connections)
    }

    @Test
    fun testUpdateNodeCommandUndoAndRedo() {
        val node = Node.FlowInputNode(1L, ModelOffset.Zero, emptyList(), isCollapsed = false)
        val updatedNode = Node.FlowInputNode(1L, ModelOffset.Zero, emptyList(), isCollapsed = true)

        val state = FlowEditorState(flow = Flow("Test", nodes = listOf(node)))
        val command = UpdateNodeCommand(1L, node, updatedNode, "Toggle collapse")

        val collapsedState = command.execute(state)
        assertTrue(collapsedState.flow.nodes.first().isCollapsed)

        val undoneState = command.undo(collapsedState)
        assertFalse(undoneState.flow.nodes.first().isCollapsed)

        val redoneState = command.execute(undoneState)
        assertTrue(redoneState.flow.nodes.first().isCollapsed)
    }

    @Test
    fun testCompositeCommandAtomicUndoAndRedo() {
        val node = Node.FlowInputNode(1L, ModelOffset.Zero, emptyList())
        val conn = Connection(1L, "a", 2L, "b")

        val state = FlowEditorState(flow = Flow("Test"))
        val composite = CompositeCommand(
            "Transaction",
            listOf(AddNodeCommand(node), ConnectPortsCommand(conn))
        )

        val executed = composite.execute(state)
        assertEquals(1, executed.flow.nodes.size)
        assertEquals(1, executed.flow.connections.size)

        val undone = composite.undo(executed)
        assertEquals(0, undone.flow.nodes.size)
        assertEquals(0, undone.flow.connections.size)

        val redone = composite.execute(undone)
        assertEquals(1, redone.flow.nodes.size)
        assertEquals(1, redone.flow.connections.size)
    }

    @Test
    fun testFlowHistoryManagerBoundedCapacityPreventsMemoryLeaks() {
        val manager = FlowHistoryManager(maxStackSize = 5)
        var state = FlowEditorState(flow = Flow("Test"))

        assertFalse(manager.canUndo.value)
        assertFalse(manager.canRedo.value)

        // Execute 10 commands on capacity 5
        for (i in 1..10) {
            val node = Node.FlowInputNode(i.toLong(), ModelOffset.Zero, emptyList())
            state = manager.executeCommand(AddNodeCommand(node), state)
        }

        assertEquals(10, state.flow.nodes.size)
        // Only 5 commands retained in undo stack (preventing unbounded heap memory consumption)
        assertEquals(5, manager.undoCount)
        assertTrue(manager.canUndo.value)
        assertFalse(manager.canRedo.value)

        // Undo all 5 available commands
        for (i in 1..5) {
            val undone = manager.undo(state)
            assertNotNull(undone)
            state = undone
        }

        assertEquals(5, state.flow.nodes.size)
        assertEquals(0, manager.undoCount)
        assertEquals(5, manager.redoCount)
        assertFalse(manager.canUndo.value)
        assertTrue(manager.canRedo.value)
        assertNull(manager.undo(state))

        // Redo 2 commands
        state = manager.redo(state)!!
        state = manager.redo(state)!!
        assertEquals(7, state.flow.nodes.size)
        assertEquals(2, manager.undoCount)
        assertEquals(3, manager.redoCount)

        // Executing a new command flushes redo stack
        val newNode = Node.FlowInputNode(99L, ModelOffset.Zero, emptyList())
        state = manager.executeCommand(AddNodeCommand(newNode), state)
        assertEquals(0, manager.redoCount)
        assertFalse(manager.canRedo.value)
        assertEquals(3, manager.undoCount)

        // Clear resets everything
        manager.clear()
        assertEquals(0, manager.undoCount)
        assertEquals(0, manager.redoCount)
        assertFalse(manager.canUndo.value)
        assertFalse(manager.canRedo.value)
    }

    @Test
    fun testFlowEditorViewModelIntegrationUndoRedo() = runBlocking {
        val viewModel = FlowEditorViewModel(
            initialFlowName = "IntegrationFlow",
            flowRepository = mockFlowRepo
        )
        for (i in 1..100) {
            if (viewModel.state.value.flow.name == "IntegrationFlow") break
            kotlinx.coroutines.delay(10)
        }
        viewModel.bypassReadOnlyForTesting = true

        assertEquals(0, viewModel.state.value.flow.nodes.size)
        assertFalse(viewModel.canUndo.value)
        assertFalse(viewModel.canRedo.value)

        // 1. Add System Node
        viewModel.onEvent(FlowEvent.AddSystemNode("Log", Offset(100f, 100f)))
        assertEquals(1, viewModel.state.value.flow.nodes.size)
        assertTrue(viewModel.canUndo.value)
        assertFalse(viewModel.canRedo.value)

        // 2. Add Flow Input Node
        viewModel.onEvent(FlowEvent.AddFlowInputNode(Offset(200f, 200f)))
        assertEquals(2, viewModel.state.value.flow.nodes.size)
        assertTrue(viewModel.canUndo.value)

        // 3. Undo second node
        viewModel.undo()
        assertEquals(1, viewModel.state.value.flow.nodes.size)
        assertTrue(viewModel.canUndo.value)
        assertTrue(viewModel.canRedo.value)

        // 4. Undo first node
        viewModel.undo()
        assertEquals(0, viewModel.state.value.flow.nodes.size)
        assertFalse(viewModel.canUndo.value)
        assertTrue(viewModel.canRedo.value)

        // 5. Redo first node
        viewModel.redo()
        assertEquals(1, viewModel.state.value.flow.nodes.size)
        assertTrue(viewModel.canUndo.value)
        assertTrue(viewModel.canRedo.value)

        // 6. Redo second node
        viewModel.redo()
        assertEquals(2, viewModel.state.value.flow.nodes.size)
        assertTrue(viewModel.canUndo.value)
        assertFalse(viewModel.canRedo.value)
    }
}
