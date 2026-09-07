package org.wip.plugintoolkit.features.flows

import androidx.compose.ui.geometry.Offset
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.mp.KoinPlatform.getKoin
import org.wip.plugintoolkit.features.flows.logic.FlowRepository
import org.wip.plugintoolkit.features.flows.logic.SystemNodesRegistry
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.viewmodel.ActiveFlowEditorTracker
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
import kotlin.test.assertTrue

class FlowEditorAutosaveTest {

    private val mockFlowRepo = mockk<FlowRepository>(relaxed = true)
    private val mockJobManager = mockk<JobManager>(relaxed = true)
    private val jobsFlow = MutableStateFlow<List<BackgroundJob>>(emptyList())
    private val mockSettingsRepo = mockk<SettingsRepository>(relaxed = true)
    private val settingsFlow = MutableStateFlow(AppSettings(flows = FlowSettings(autosave = false)))
    private val isLoadedFlow = MutableStateFlow(true)
    private val flowsFlow = MutableStateFlow<List<Flow>>(emptyList())

    @BeforeTest
    fun setUp() {
        stopKoin()
        isLoadedFlow.value = true
        every { mockJobManager.jobs } returns jobsFlow
        every { mockSettingsRepo.settings } returns settingsFlow
        every { mockSettingsRepo.isLoaded } returns isLoadedFlow
        every { mockFlowRepo.flows } returns flowsFlow

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

    private suspend fun createViewModel(
        flowName: String = "TestFlow",
        autosave: Boolean = false,
        isLoaded: Boolean = true
    ): FlowEditorViewModel {
        isLoadedFlow.value = isLoaded
        settingsFlow.value = AppSettings(flows = FlowSettings(autosave = autosave))
        val vm = FlowEditorViewModel(
            initialFlowName = flowName,
            settingsPersistence = MockSettingsPersistence(),
            notificationService = null,
            flowRepository = mockFlowRepo,
            settingsRepository = mockSettingsRepo,
            activeFlowEditorTracker = getKoin().get()
        )
        for (i in 1..100) {
            if (vm.state.value.flow.name == flowName) break
            delay(10)
        }
        return vm
    }

    @Test
    fun testConnectionsWithAutosaveDisabledDoesNotSaveFlow() = runBlocking {
        val viewModel = createViewModel(flowName = "ManualFlow", autosave = false)
        assertFalse(viewModel.isAutoSaveEnabled, "Autosave must be disabled")

        // Add two nodes
        viewModel.onEvent(FlowEvent.AddSystemNode("Log", Offset(100f, 100f)))
        viewModel.onEvent(FlowEvent.AddSystemNode("Log", Offset(300f, 100f)))

        val nodes = viewModel.state.value.flow.nodes
        assertEquals(2, nodes.size)
        val nodeA = nodes[0]
        val nodeB = nodes[1]

        // Verify saveFlow was NOT called for adding nodes
        verify(exactly = 0) { mockFlowRepo.saveFlow(any()) }
        assertTrue(viewModel.state.value.hasUnsavedChanges)

        // Now connect ports using TryConnectPorts
        viewModel.onEvent(FlowEvent.TryConnectPorts(nodeA.id, "output", nodeB.id, "message", false))

        // Check connection exists in editor
        assertEquals(1, viewModel.state.value.flow.connections.size)

        // CRITICAL CHECK: Did TryConnectPorts cause saveFlow to be called?!
        verify(exactly = 0) { mockFlowRepo.saveFlow(any()) }
        assertTrue(viewModel.state.value.hasUnsavedChanges, "Unsaved changes must still be present")
    }

    @Test
    fun testIsAutoSaveEnabledIsFalseWhileSettingsAreLoading() = runBlocking {
        // While settings are loading, even if default AppSettings has autosave = true,
        // isAutoSaveEnabled must be false to prevent prematurely saving before user's preference is loaded.
        val viewModel = createViewModel(flowName = "LoadingSettingsFlow", autosave = true, isLoaded = false)
        assertFalse(
            viewModel.isAutoSaveEnabled,
            "Autosave must be false while settings are still loading from disk"
        )
    }

    @Test
    fun testConnectionDoesNotAutosaveWhileSettingsAreLoading() = runBlocking {
        val viewModel = createViewModel(flowName = "LoadingSettingsFlow", autosave = true, isLoaded = false)

        viewModel.onEvent(FlowEvent.AddSystemNode("Log", Offset(100f, 100f)))
        viewModel.onEvent(FlowEvent.AddSystemNode("Log", Offset(300f, 100f)))

        val nodes = viewModel.state.value.flow.nodes
        assertEquals(2, nodes.size)
        val nodeA = nodes[0]
        val nodeB = nodes[1]

        // Connect ports while settings are loading
        viewModel.onEvent(FlowEvent.TryConnectPorts(nodeA.id, "output", nodeB.id, "message", false))

        // saveFlow must NOT have been called
        verify(exactly = 0) { mockFlowRepo.saveFlow(any()) }
        assertTrue(viewModel.state.value.hasUnsavedChanges)
    }

    @Test
    fun testDiscardUnsavedChangesRevertsFlowState() = runBlocking {
        val initialFlow = Flow("InitialFlow", nodes = listOf(
            Node.SystemNode(1L, org.wip.plugintoolkit.features.flows.model.Offset(0f, 0f), "Log", "Log", SystemNodesRegistry.getInputs("Log"), SystemNodesRegistry.getOutputs("Log")),
            Node.SystemNode(2L, org.wip.plugintoolkit.features.flows.model.Offset(100f, 0f), "Log", "Log", SystemNodesRegistry.getInputs("Log"), SystemNodesRegistry.getOutputs("Log"))
        ), connections = emptyList())
        flowsFlow.value = listOf(initialFlow)

        val viewModel = createViewModel(flowName = "InitialFlow", autosave = false)
        assertEquals(0, viewModel.state.value.flow.connections.size)
        assertFalse(viewModel.state.value.hasUnsavedChanges)

        // Connect nodes
        viewModel.onEvent(FlowEvent.ConnectPorts(1L, "output", 2L, "message"))
        assertEquals(1, viewModel.state.value.flow.connections.size)
        assertTrue(viewModel.state.value.hasUnsavedChanges)

        // Discard changes directly on viewModel
        viewModel.discardUnsavedChanges()

        assertEquals(0, viewModel.state.value.flow.connections.size, "Flow connections must revert back to initial repository flow")
        assertFalse(viewModel.state.value.hasUnsavedChanges, "hasUnsavedChanges must be false after discard")
        verify(exactly = 0) { mockFlowRepo.saveFlow(any()) }
    }

    @Test
    fun testActiveFlowEditorTrackerDiscardIntegration() = runBlocking {
        val initialFlow = Flow("TrackerFlow", nodes = listOf(
            Node.SystemNode(1L, org.wip.plugintoolkit.features.flows.model.Offset(0f, 0f), "Log", "Log", SystemNodesRegistry.getInputs("Log"), SystemNodesRegistry.getOutputs("Log")),
            Node.SystemNode(2L, org.wip.plugintoolkit.features.flows.model.Offset(100f, 0f), "Log", "Log", SystemNodesRegistry.getInputs("Log"), SystemNodesRegistry.getOutputs("Log"))
        ), connections = emptyList())
        flowsFlow.value = listOf(initialFlow)

        val viewModel = createViewModel(flowName = "TrackerFlow", autosave = false)
        assertEquals(0, viewModel.state.value.flow.connections.size)
        assertFalse(viewModel.state.value.hasUnsavedChanges)

        // Connect nodes
        viewModel.onEvent(FlowEvent.ConnectPorts(1L, "output", 2L, "message"))
        assertEquals(1, viewModel.state.value.flow.connections.size)
        assertTrue(viewModel.state.value.hasUnsavedChanges)

        val tracker: ActiveFlowEditorTracker = getKoin().get()
        assertTrue(tracker.hasUnsavedChanges.value)

        // User confirms exit in dialog -> tracker.discardChanges() is invoked
        tracker.discardChanges()

        assertFalse(tracker.hasUnsavedChanges.value, "Tracker must report false after discard")
        assertEquals(0, viewModel.state.value.flow.connections.size, "Flow must be reverted back to 0 connections")
        assertFalse(viewModel.state.value.hasUnsavedChanges, "ViewModel hasUnsavedChanges must be false")
        verify(exactly = 0) { mockFlowRepo.saveFlow(any()) }
    }

    @Test
    fun testTryConnectPortsSavesOnlyOnceWhenAutosaveEnabled() = runBlocking {
        val viewModel = createViewModel(flowName = "AutosaveOnceFlow", autosave = true)
        assertTrue(viewModel.isAutoSaveEnabled)

        viewModel.onEvent(FlowEvent.AddSystemNode("Log", Offset(100f, 100f)))
        viewModel.onEvent(FlowEvent.AddSystemNode("Log", Offset(300f, 100f)))

        val nodes = viewModel.state.value.flow.nodes
        val nodeA = nodes[0]
        val nodeB = nodes[1]

        // Clear previous saveFlow calls from adding nodes
        io.mockk.clearMocks(mockFlowRepo, answers = false, recordedCalls = true, childMocks = false)

        // Connect ports using TryConnectPorts with autosave = true
        viewModel.onEvent(FlowEvent.TryConnectPorts(nodeA.id, "output", nodeB.id, "message", false))

        // With no re-entrancy, saveFlow should be called exactly ONCE, not twice!
        verify(exactly = 1) { mockFlowRepo.saveFlow(any()) }
    }
}
