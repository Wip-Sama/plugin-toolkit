package org.wip.plugintoolkit.features.flows

import androidx.compose.ui.geometry.Offset
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.wip.plugintoolkit.features.flows.logic.FlowRepository
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.viewmodel.ActiveFlowEditorTracker
import org.wip.plugintoolkit.features.flows.viewmodel.FlowEditorViewModel
import org.wip.plugintoolkit.features.flows.viewmodel.FlowEvent
import org.wip.plugintoolkit.features.flows.viewmodel.ReadOnlyReason
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobStatus
import org.wip.plugintoolkit.features.job.model.JobType
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository
import org.wip.plugintoolkit.features.settings.model.AppSettings
import org.wip.plugintoolkit.features.settings.model.FlowSettings
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FlowEditorReadOnlyTest {

    private val mockFlowRepo = mockk<FlowRepository>(relaxed = true)
    private val mockJobManager = mockk<JobManager>(relaxed = true)
    private val jobsFlow = MutableStateFlow<List<BackgroundJob>>(emptyList())
    private val mockSettingsRepo = mockk<SettingsRepository>(relaxed = true)
    private val settingsFlow = MutableStateFlow(AppSettings(flows = FlowSettings(autosave = false)))

    @BeforeTest
    fun setUp() {
        stopKoin()
        every { mockJobManager.jobs } returns jobsFlow
        every { mockSettingsRepo.settings } returns settingsFlow
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

    private suspend fun createViewModel(
        flowName: String = "TestFlow",
        autosave: Boolean = false
    ): FlowEditorViewModel {
        settingsFlow.value = AppSettings(flows = FlowSettings(autosave = autosave))
        val vm = FlowEditorViewModel(
            initialFlowName = flowName,
            settingsPersistence = MockSettingsPersistence(),
            notificationService = null,
            flowRepository = mockFlowRepo,
            settingsRepository = mockSettingsRepo
        )
        for (i in 1..100) {
            if (vm.state.value.flow.name == flowName) break
            kotlinx.coroutines.delay(10)
        }
        return vm
    }

    @Test
    fun testRunningFlowDetectedAsReadOnly() = runBlocking {
        val flowName = "MyRunningFlow"
        val runningJob = BackgroundJob(
            id = "job-1",
            name = "Flow: $flowName",
            type = JobType.Flow,
            status = JobStatus.Running,
            pluginId = "system",
            capabilityName = flowName
        )
        jobsFlow.value = listOf(runningJob)

        val viewModel = createViewModel(flowName = flowName)
        viewModel.updateReadOnlyState()

        assertTrue(viewModel.state.value.isReadOnly, "Running flow must be marked as read-only")
        assertTrue(
            viewModel.state.value.readOnlyReasons.contains(ReadOnlyReason.Running),
            "ReadOnlyReasons must contain Running"
        )
    }

    @Test
    fun testModifyingEventsBlockedInReadOnlyMode() = runBlocking {
        val flowName = "ReadOnlyFlow"
        val runningJob = BackgroundJob(
            id = "job-2",
            name = "Flow: $flowName",
            type = JobType.Flow,
            status = JobStatus.Running,
            pluginId = "system",
            capabilityName = flowName
        )
        jobsFlow.value = listOf(runningJob)

        val viewModel = createViewModel(flowName = flowName)
        viewModel.updateReadOnlyState()
        assertTrue(viewModel.state.value.isReadOnly)

        // Add node via testing bypass to test event rejection
        viewModel.bypassReadOnlyForTesting = true
        viewModel.onEvent(FlowEvent.AddSystemNode("Log", Offset(100f, 100f)))
        val nodeCount = viewModel.state.value.flow.nodes.size
        viewModel.bypassReadOnlyForTesting = false

        // 1. TryConnectPorts
        viewModel.onEvent(FlowEvent.TryConnectPorts(1L, "out", 2L, "in", false))
        assertEquals(0, viewModel.state.value.flow.connections.size)

        // 2. PasteNodes
        viewModel.onEvent(FlowEvent.PasteNodes(Offset(200f, 200f)))
        assertEquals(nodeCount, viewModel.state.value.flow.nodes.size)

        // 3. DeleteSelectedNodes
        viewModel.onEvent(FlowEvent.DeleteSelectedNodes)
        assertEquals(nodeCount, viewModel.state.value.flow.nodes.size)

        // 4. UpdateInputPortValue
        viewModel.onEvent(FlowEvent.UpdateInputPortValue(1L, "text", null))

        // 5. UpdateConnectionOrder
        viewModel.onEvent(FlowEvent.UpdateConnectionOrder(Connection(1L, "a", 2L, "b"), 1))

        // 6. ToggleNodeCollapse
        val initialCollapse = viewModel.state.value.flow.nodes.firstOrNull()?.isCollapsed ?: false
        viewModel.onEvent(FlowEvent.ToggleNodeCollapse(1L))
        assertEquals(initialCollapse, viewModel.state.value.flow.nodes.firstOrNull()?.isCollapsed)

        // 7. Undo / Redo
        viewModel.undo()
        viewModel.redo()

        // 8. Save
        viewModel.onEvent(FlowEvent.Save)
        verify(exactly = 0) { mockFlowRepo.saveFlow(any()) }
    }

    @Test
    fun testPanningWithAutosaveDisabledDoesNotSaveFlow() = runBlocking {
        val viewModel = createViewModel(flowName = "ManualSaveFlow", autosave = false)
        assertFalse(viewModel.isAutoSaveEnabled, "Autosave should be disabled")

        // Add a node to create unsaved changes
        viewModel.onEvent(FlowEvent.AddSystemNode("Log", Offset(100f, 100f)))
        assertTrue(viewModel.state.value.hasUnsavedChanges, "Should have unsaved changes")

        // Pan the board
        viewModel.onEvent(FlowEvent.Pan(Offset(50f, 50f)))

        // Verify saveFlow was NOT called
        verify(exactly = 0) { mockFlowRepo.saveFlow(any()) }
        assertTrue(viewModel.state.value.hasUnsavedChanges, "Unsaved changes must still be present")
    }

    @Test
    fun testAutosaveBlockedWhenReadOnly() = runBlocking {
        val flowName = "ReadOnlyAutosaveFlow"
        val runningJob = BackgroundJob(
            id = "job-3",
            name = "Flow: $flowName",
            type = JobType.Flow,
            status = JobStatus.Running,
            pluginId = "system",
            capabilityName = flowName
        )
        jobsFlow.value = listOf(runningJob)

        // Initialize with autosave disabled so AddSystemNode does not auto-save immediately
        val viewModel = createViewModel(flowName = flowName, autosave = false)

        // Bypass read-only to create unsaved changes
        viewModel.bypassReadOnlyForTesting = true
        viewModel.onEvent(FlowEvent.AddSystemNode("Log", Offset(100f, 100f)))
        viewModel.bypassReadOnlyForTesting = false
        assertTrue(viewModel.state.value.hasUnsavedChanges, "Must have unsaved changes")

        // Now enable autosave in settings
        settingsFlow.value = AppSettings(flows = FlowSettings(autosave = true))
        assertTrue(viewModel.isAutoSaveEnabled, "Autosave should now be enabled")

        // Flow is read-only with unsaved changes
        viewModel.updateReadOnlyState()
        assertTrue(viewModel.state.value.isReadOnly, "Flow must be read-only")

        // Trigger Pan (which would normally trigger autosave if enabled)
        viewModel.onEvent(FlowEvent.Pan(Offset(10f, 10f)))

        // Verify saveFlow was NEVER called while read-only
        verify(exactly = 0) { mockFlowRepo.saveFlow(any()) }
        assertTrue(viewModel.state.value.hasUnsavedChanges, "Unsaved changes must still be preserved")
    }
}
