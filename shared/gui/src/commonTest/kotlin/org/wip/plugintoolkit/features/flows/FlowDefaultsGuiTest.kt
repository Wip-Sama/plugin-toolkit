package org.wip.plugintoolkit.features.flows

import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonPrimitive
import org.wip.plugintoolkit.api.Capability
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.ParameterMetadata
import org.wip.plugintoolkit.api.PluginEntry
import org.wip.plugintoolkit.api.PluginInfo
import org.wip.plugintoolkit.api.PluginManifest
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.api.Requirements
import org.wip.plugintoolkit.core.notification.NotificationService
import org.wip.plugintoolkit.features.flows.history.UpdateInputPortDefaultCommand
import org.wip.plugintoolkit.features.flows.logic.FlowRepository
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.InputPort
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.model.Offset
import org.wip.plugintoolkit.features.flows.model.OutputPort
import org.wip.plugintoolkit.features.flows.viewmodel.FlowEditorState
import org.wip.plugintoolkit.features.flows.viewmodel.FlowNodeManager
import org.wip.plugintoolkit.features.flows.viewmodel.FlowViewModel
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.plugin.logic.PluginManager
import org.wip.plugintoolkit.features.plugin.model.PluginSettingsStore
import org.wip.plugintoolkit.features.plugin.viewmodel.PluginViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FlowDefaultsGuiTest {

    private val testDispatcher = StandardTestDispatcher()
    private val nodeManager = FlowNodeManager()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun testHandleUpdateBoundaryNodeWithDefaultValue() {
        val inputNode = Node.FlowInputNode(
            id = 1L,
            position = Offset(0f, 0f),
            outputs = listOf(OutputPort("out", "Out", DataType.Primitive(PrimitiveType.STRING)))
        )
        val state = FlowEditorState(flow = Flow("test", nodes = listOf(inputNode)))

        val updatedState = nodeManager.handleUpdateBoundaryNode(
            currentState = state,
            nodeId = 1L,
            portName = "Updated Input",
            dataType = DataType.Primitive(PrimitiveType.STRING),
            semanticTypes = emptyList(),
            constraints = null,
            isList = false,
            isRequired = true,
            defaultValue = "my_boundary_default"
        )

        val resultNode = updatedState.flow.nodes.first() as Node.FlowInputNode
        assertEquals("my_boundary_default", resultNode.defaultValue)
        assertEquals("Updated Input", resultNode.outputs.first().name)
    }

    @Test
    fun testHandleUpdateInputPortDefaultOnSystemNode() {
        val systemNode = Node.SystemNode(
            id = 10L,
            position = Offset(0f, 0f),
            title = "Delay",
            systemAction = "delay",
            inputs = listOf(InputPort(id = "ms", name = "Milliseconds", dataType = DataType.Primitive(PrimitiveType.INT))),
            outputs = emptyList()
        )
        val state = FlowEditorState(flow = Flow("test", nodes = listOf(systemNode)))

        val updatedState = nodeManager.handleUpdateInputPortDefault(
            currentState = state,
            nodeId = 10L,
            portId = "ms",
            defaultValue = 5000
        )

        val resultNode = updatedState.flow.nodes.first() as Node.SystemNode
        val port = resultNode.inputs.first { it.id == "ms" }
        assertEquals(5000, port.defaultValue)
    }

    @Test
    fun testUpdateInputPortDefaultCommandUndoRedo() {
        val systemNode = Node.SystemNode(
            id = 10L,
            position = Offset(0f, 0f),
            title = "Delay",
            systemAction = "delay",
            inputs = listOf(InputPort(id = "ms", name = "Milliseconds", dataType = DataType.Primitive(PrimitiveType.INT))),
            outputs = emptyList()
        )
        val state = FlowEditorState(flow = Flow("test", nodes = listOf(systemNode)))

        val command = UpdateInputPortDefaultCommand(
            nodeId = 10L,
            portId = "ms",
            oldDefault = null,
            newDefault = 1000
        )

        val stateAfterExec = command.execute(state)
        val portAfterExec = (stateAfterExec.flow.nodes.first() as Node.SystemNode).inputs.first()
        assertEquals(1000, portAfterExec.defaultValue)

        val stateAfterUndo = command.undo(stateAfterExec)
        val portAfterUndo = (stateAfterUndo.flow.nodes.first() as Node.SystemNode).inputs.first()
        assertNull(portAfterUndo.defaultValue)

        val stateAfterRedo = command.execute(stateAfterUndo)
        val portAfterRedo = (stateAfterRedo.flow.nodes.first() as Node.SystemNode).inputs.first()
        assertEquals(1000, portAfterRedo.defaultValue)
    }

    @Test
    fun testSaveAndClearFlowDefaultsInViewModel() = runTest(testDispatcher) {
        val flowRepository = mockk<FlowRepository>(relaxed = true)
        val notificationService = mockk<NotificationService>(relaxed = true)

        val flow = Flow(
            name = "TestFlow",
            nodes = listOf(
                Node.FlowInputNode(
                    id = 1L,
                    position = Offset(0f, 0f),
                    outputs = listOf(OutputPort("val", "Val", DataType.Primitive(PrimitiveType.STRING)))
                )
            )
        )

        val flowsFlow = MutableStateFlow(listOf(flow))
        every { flowRepository.flows } returns flowsFlow

        val viewModel = FlowViewModel(
            flowRepository = flowRepository,
            notificationService = notificationService
        )
        advanceUntilIdle()

        // Save flow defaults
        viewModel.saveFlowDefaults(flow, mapOf("1_val" to "saved_default_string"))
        advanceUntilIdle()

        verify {
            flowRepository.saveFlow(match {
                it.defaultValues["1_val"] == JsonPrimitive("saved_default_string")
            })
        }

        // Clear flow defaults
        val flowWithDefaults = flow.copy(defaultValues = mapOf("1_val" to JsonPrimitive("saved_default_string")))
        viewModel.clearFlowDefaults(flowWithDefaults)
        advanceUntilIdle()

        verify {
            flowRepository.saveFlow(match {
                it.defaultValues.isEmpty()
            })
        }
    }

    @Test
    fun testPluginViewModelSaveCurrentParametersAsDefault() = runTest(testDispatcher) {
        val jobManager = mockk<JobManager>(relaxed = true)
        val notificationService = mockk<NotificationService>(relaxed = true)
        val pluginManager = mockk<PluginManager>(relaxed = true)

        val initialStore = PluginSettingsStore()
        every { pluginManager.loadPluginSettings("test.plugin") } returns initialStore

        val manifest = PluginManifest(
            manifestVersion = "1.0",
            plugin = PluginInfo(
                id = "test.plugin",
                name = "Test Plugin",
                version = "1.0.0",
                description = "Test Desc"
            ),
            requirements = Requirements(minMemoryMb = 256, minExecutionTimeMs = 1000),
            capabilities = listOf(
                Capability(
                    name = "testCap",
                    description = "Test Capability",
                    returnType = DataType.Primitive(PrimitiveType.UNIT),
                    parameters = mapOf(
                        "param1" to ParameterMetadata(
                            type = DataType.Primitive(PrimitiveType.STRING),
                            description = "Param 1"
                        )
                    )
                )
            )
        )

        val pluginEntry = mockk<PluginEntry>(relaxed = true)
        every { pluginEntry.getManifest() } returns Result.success(manifest)

        val viewModel = PluginViewModel(jobManager, notificationService, pluginManager)
        viewModel.selectPlugin(pluginEntry)
        viewModel.selectCapability(manifest.capabilities.first())

        // User enters a value in Capability Tester
        viewModel.updateParameter("param1", "my_custom_default")

        // User clicks "Set as default"
        viewModel.saveCurrentParametersAsDefault()
        advanceUntilIdle()

        // Verify savePluginSettings was called with updated capabilityParams
        verify {
            pluginManager.savePluginSettings("test.plugin", match {
                it.capabilityParams["testCap"]?.get("param1") == JsonPrimitive("my_custom_default")
            })
        }
    }
}
