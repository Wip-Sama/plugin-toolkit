package org.wip.plugintoolkit.features.flows

import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.InputPort
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.model.Offset
import org.wip.plugintoolkit.features.flows.model.OutputPort
import org.wip.plugintoolkit.features.job.logic.DefaultSystemNodeExecutorRegistry
import org.wip.plugintoolkit.features.job.logic.FlowEngine
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobType
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository
import kotlin.test.Test
import kotlin.test.assertEquals

class FlowDefaultsTest : JobWorkerFlowTestBase() {

    @Test
    fun testFlowInputNodeDefaultValueExecution() = runTest {
        val persistence = FakeSettingsPersistence()
        val settingsRepo = SettingsRepository(persistence, backgroundScope)
        settingsRepo.updateSettings { persistence.settings }
        val jobManager = JobManager(backgroundScope, settingsRepo)

        // Input node with defaultValue = "node_default"
        val inputNode = Node.FlowInputNode(
            id = 1L,
            position = Offset.Zero,
            outputs = listOf(OutputPort("output_data", "source", DataType.Primitive(PrimitiveType.STRING))),
            defaultValue = "node_default"
        )
        val outputNode = createOutputNode(2L, "result", DataType.Primitive(PrimitiveType.STRING))

        val flow = Flow(
            name = "test_input_default",
            nodes = listOf(inputNode, outputNode),
            connections = listOf(
                Connection(1L, "output_data", 2L, "input_data")
            )
        )

        // Job executed WITHOUT runtime parameters
        val job = BackgroundJob(
            id = "test-job-input-default",
            name = "Test Job",
            type = JobType.Flow,
            pluginId = "system",
            capabilityName = "test_input_default",
            parameters = emptyMap()
        )

        val engine = FlowEngine(
            jobManager,
            DefaultSystemNodeExecutorRegistry(mockk(relaxed = true)),
            mockk(relaxed = true),
            mockk(relaxed = true),
            backgroundScope
        )

        val outputs = engine.executeSubFlowRecursively(flow, job, "/tmp")
        assertEquals("node_default", outputs["result"])
    }

    @Test
    fun testFlowDefaultValuesOverrideNodeDefault() = runTest {
        val persistence = FakeSettingsPersistence()
        val settingsRepo = SettingsRepository(persistence, backgroundScope)
        settingsRepo.updateSettings { persistence.settings }
        val jobManager = JobManager(backgroundScope, settingsRepo)

        val inputNode = Node.FlowInputNode(
            id = 1L,
            position = Offset.Zero,
            outputs = listOf(OutputPort("output_data", "source", DataType.Primitive(PrimitiveType.STRING))),
            defaultValue = "node_default"
        )
        val outputNode = createOutputNode(2L, "result", DataType.Primitive(PrimitiveType.STRING))

        val flow = Flow(
            name = "test_flow_level_default",
            nodes = listOf(inputNode, outputNode),
            connections = listOf(
                Connection(1L, "output_data", 2L, "input_data")
            ),
            defaultValues = mapOf(
                "1_output_data" to JsonPrimitive("flow_level_default")
            )
        )

        val job = BackgroundJob(
            id = "test-job-flow-default",
            name = "Test Job",
            type = JobType.Flow,
            pluginId = "system",
            capabilityName = "test_flow_level_default",
            parameters = emptyMap()
        )

        val engine = FlowEngine(
            jobManager,
            DefaultSystemNodeExecutorRegistry(mockk(relaxed = true)),
            mockk(relaxed = true),
            mockk(relaxed = true),
            backgroundScope
        )

        val outputs = engine.executeSubFlowRecursively(flow, job, "/tmp")
        assertEquals("flow_level_default", outputs["result"])
    }

    @Test
    fun testRuntimeParameterOverridesFlowDefaultAndNodeDefault() = runTest {
        val persistence = FakeSettingsPersistence()
        val settingsRepo = SettingsRepository(persistence, backgroundScope)
        settingsRepo.updateSettings { persistence.settings }
        val jobManager = JobManager(backgroundScope, settingsRepo)

        val inputNode = Node.FlowInputNode(
            id = 1L,
            position = Offset.Zero,
            outputs = listOf(OutputPort("output_data", "source", DataType.Primitive(PrimitiveType.STRING))),
            defaultValue = "node_default"
        )
        val outputNode = createOutputNode(2L, "result", DataType.Primitive(PrimitiveType.STRING))

        val flow = Flow(
            name = "test_runtime_override",
            nodes = listOf(inputNode, outputNode),
            connections = listOf(
                Connection(1L, "output_data", 2L, "input_data")
            ),
            defaultValues = mapOf(
                "1_output_data" to JsonPrimitive("flow_level_default")
            )
        )

        val job = BackgroundJob(
            id = "test-job-runtime-override",
            name = "Test Job",
            type = JobType.Flow,
            pluginId = "system",
            capabilityName = "test_runtime_override",
            parameters = mapOf("1" to JsonPrimitive("runtime_value"))
        )

        val engine = FlowEngine(
            jobManager,
            DefaultSystemNodeExecutorRegistry(mockk(relaxed = true)),
            mockk(relaxed = true),
            mockk(relaxed = true),
            backgroundScope
        )

        val outputs = engine.executeSubFlowRecursively(flow, job, "/tmp")
        assertEquals("runtime_value", outputs["result"])
    }

    @Test
    fun testCopyWithUpdatedInputDefaultOnSystemNode() {
        val systemNode = Node.SystemNode(
            id = 10L,
            position = Offset.Zero,
            title = "Log",
            systemAction = "log",
            inputs = listOf(
                InputPort(id = "message", name = "Message", dataType = DataType.Primitive(PrimitiveType.STRING)),
                InputPort(id = "level", name = "Level", dataType = DataType.Primitive(PrimitiveType.STRING))
            ),
            outputs = emptyList()
        )

        val updatedNode = systemNode.copyWithUpdatedInputDefault("message", "Default log message")
        val messagePort = updatedNode.inputs.first { it.id == "message" }
        val levelPort = updatedNode.inputs.first { it.id == "level" }

        assertEquals("Default log message", messagePort.defaultValue)
        assertEquals(null, levelPort.defaultValue)
    }

    @Test
    fun testFlowSerializationWithDefaults() {
        val flow = Flow(
            name = "SerializationTest",
            version = "1.0.0",
            nodes = listOf(
                Node.FlowInputNode(
                    id = 1L,
                    position = Offset(10f, 20f),
                    outputs = listOf(OutputPort("data", "Data", DataType.Primitive(PrimitiveType.STRING))),
                    defaultValue = "persisted_input_default"
                )
            ),
            defaultValues = mapOf(
                "1_data" to JsonPrimitive("persisted_flow_default")
            )
        )

        val json = Json { prettyPrint = true }
        val encoded = json.encodeToString(flow)
        val decoded = json.decodeFromString<Flow>(encoded)

        assertEquals("SerializationTest", decoded.name)
        assertEquals(1, decoded.defaultValues.size)
        assertEquals(JsonPrimitive("persisted_flow_default"), decoded.defaultValues["1_data"])

        val decodedInputNode = decoded.nodes.first() as Node.FlowInputNode
        assertEquals("persisted_input_default", decodedInputNode.defaultValue)
    }
}
