package org.wip.plugintoolkit.features.flows

import androidx.compose.ui.geometry.Offset
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.wip.plugintoolkit.api.DataProcessor
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PluginEntry
import org.wip.plugintoolkit.api.PluginRequest
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.InputPort
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.model.OutputPort
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.job.logic.JobWorker
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobType
import org.wip.plugintoolkit.features.plugin.logic.PluginLifecycleCoordinator
import org.wip.plugintoolkit.features.plugin.logic.PluginLoader
import org.wip.plugintoolkit.features.plugin.logic.PluginManager
import org.wip.plugintoolkit.features.settings.logic.SettingsPersistence
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class JobWorkerFlowDynamicListTest : JobWorkerFlowTestBase() {

    @Test
    fun testDynamicListParameters() = runTest {
        val persistence = FakeSettingsPersistence()
        val settingsRepo = SettingsRepository(persistence, backgroundScope)
        val jobManager = JobManager(backgroundScope, settingsRepo)
        val jobWorker = JobWorker(1, jobManager, backgroundScope)

        // Flow:
        // Input Node 1 (String "A")
        // Input Node 2 (String "B")
        // Merge Node (Capability node expecting a List of String, returning String)
        // Output Node

        val inputNode1 = createInputNode(1, "source1", DataType.Primitive(PrimitiveType.STRING))
        val inputNode2 = createInputNode(2, "source2", DataType.Primitive(PrimitiveType.STRING))

        val listInputPort = InputPort(
            id = "list_input",
            name = "List Input",
            dataType = DataType.Array(DataType.Primitive(PrimitiveType.STRING))
        )
        val mergeNode = Node.CapabilityNode(
            id = 3,
            position = Offset.Zero,
            pluginInfo = org.wip.plugintoolkit.api.PluginInfo(
                id = "test-plugin",
                name = "Test Plugin",
                version = "1.0.0",
                description = "Test"
            ),
            capability = org.wip.plugintoolkit.api.Capability(
                name = "merge",
                description = "test merge",
                returnType = DataType.Primitive(PrimitiveType.STRING)
            ),
            inputs = listOf(listInputPort),
            outputs = listOf(
                OutputPort(
                    id = "result",
                    name = "result",
                    dataType = DataType.Primitive(PrimitiveType.STRING)
                )
            )
        )

        val outputNode = createOutputNode(4, "result", DataType.Primitive(PrimitiveType.STRING))

        val flow = Flow(
            name = "test_dynamic_list",
            nodes = listOf(inputNode1, inputNode2, mergeNode, outputNode),
            connections = listOf(
                // Connection 1: source1 -> mergeNode, orderIndex = 1
                Connection(1, "output_data", 3, "list_input", orderIndex = 1),
                // Connection 2: source2 -> mergeNode, orderIndex = 0
                Connection(2, "output_data", 3, "list_input", orderIndex = 0),
                Connection(3, "result", 4, "input_data")
            )
        )

        // We need to mock the PluginLoader and DataProcessor to verify the parameters
        mockkObject(PluginLoader)
        val mockPluginEntry = mockk<PluginEntry>(relaxed = true)
        val mockProcessor = mockk<DataProcessor>(relaxed = true)
        val mockPluginManager = mockk<PluginManager>(relaxed = true)
        val mockLifecycleCoordinator = mockk<PluginLifecycleCoordinator>(relaxed = true)

        startKoin {
            modules(module {
                single<SettingsPersistence> { persistence }
                single { mockPluginManager }
                single { mockLifecycleCoordinator }
            })
        }

        every { PluginLoader.getPluginById("test-plugin") } returns mockPluginEntry
        every { mockPluginEntry.getProcessor() } answers { Result.success(mockProcessor) }
        every { mockPluginEntry.getManifest() } answers {
            Result.success(
                org.wip.plugintoolkit.api.PluginManifest(
                    manifestVersion = "1.0",
                    plugin = org.wip.plugintoolkit.api.PluginInfo(
                        id = "test-plugin",
                        name = "Test Plugin",
                        version = "1.0.0",
                        description = "Test"
                    ),
                    requirements = org.wip.plugintoolkit.api.Requirements(minMemoryMb = 128, minExecutionTimeMs = 100),
                    capabilities = listOf(
                        org.wip.plugintoolkit.api.Capability(
                            name = "merge",
                            description = "test merge",
                            returnType = DataType.Primitive(PrimitiveType.STRING),
                            parameters = mapOf(
                                "list_input" to org.wip.plugintoolkit.api.ParameterMetadata(
                                    description = "list_input",
                                    type = DataType.Array(DataType.Primitive(PrimitiveType.STRING))
                                )
                            )
                        )
                    )
                )
            )
        }

        var capturedParameters: Map<String, kotlinx.serialization.json.JsonElement>? = null
        coEvery { mockProcessor.process(any(), any()) } coAnswers {
            val req = firstArg<PluginRequest>()
            capturedParameters = req.parameters
            org.wip.plugintoolkit.api.ExecutionResult.Success(
                org.wip.plugintoolkit.api.PluginResponse(
                    result = kotlinx.serialization.json.JsonObject(mapOf("result" to JsonPrimitive("merged-result")))
                )
            )
        }

        val job = BackgroundJob(
            id = "test-job-dynamic-list",
            name = "Dynamic List Job",
            type = JobType.Flow,
            pluginId = "system",
            capabilityName = "test_dynamic_list",
            parameters = mapOf("1" to JsonPrimitive("A"), "2" to JsonPrimitive("B"))
        )

        val outputs = org.wip.plugintoolkit.features.job.logic.FlowEngine(
            jobManager,
            org.wip.plugintoolkit.features.job.logic.DefaultSystemNodeExecutorRegistry(),
            mockk(relaxed = true),
            mockk(relaxed = true),
            backgroundScope
        ).executeSubFlowRecursively(flow, job, "/tmp")

        // The expected order based on orderIndex is source2 (B) then source1 (A)
        assertNotNull(capturedParameters)
        val listInputParam = capturedParameters!!["list_input"]!!

        val jsonArray = listInputParam.jsonArray
        assertEquals(2, jsonArray.size)
        assertEquals("B", jsonArray[0].jsonPrimitive.content)
        assertEquals("A", jsonArray[1].jsonPrimitive.content)

        assertEquals("merged-result", outputs["result"])
    }

    @Test
    fun testDynamicListFlattening() = runTest {
        val persistence = FakeSettingsPersistence()
        val settingsRepo = SettingsRepository(persistence, backgroundScope)
        val jobManager = JobManager(backgroundScope, settingsRepo)
        val jobWorker = JobWorker(1, jobManager, backgroundScope)

        val inputNode1 = createInputNode(1, "source1", DataType.Array(DataType.Primitive(PrimitiveType.STRING)))
        val inputNode2 = createInputNode(2, "source2", DataType.Array(DataType.Primitive(PrimitiveType.STRING)))

        val listInputPort = InputPort(
            id = "list_input",
            name = "List Input",
            dataType = DataType.Array(DataType.Primitive(PrimitiveType.STRING))
        )
        val mergeNode = Node.CapabilityNode(
            id = 3,
            position = Offset.Zero,
            pluginInfo = org.wip.plugintoolkit.api.PluginInfo(
                id = "test-plugin",
                name = "Test Plugin",
                version = "1.0.0",
                description = "Test"
            ),
            capability = org.wip.plugintoolkit.api.Capability(
                name = "merge",
                description = "test merge",
                returnType = DataType.Primitive(PrimitiveType.STRING)
            ),
            inputs = listOf(listInputPort),
            outputs = listOf(
                OutputPort(
                    id = "result",
                    name = "result",
                    dataType = DataType.Primitive(PrimitiveType.STRING)
                )
            )
        )

        val outputNode = createOutputNode(4, "result", DataType.Primitive(PrimitiveType.STRING))

        val flow = Flow(
            name = "test_dynamic_list_flattening",
            nodes = listOf(inputNode1, inputNode2, mergeNode, outputNode),
            connections = listOf(
                Connection(1, "output_data", 3, "list_input", orderIndex = 0),
                Connection(2, "output_data", 3, "list_input", orderIndex = 1),
                Connection(3, "result", 4, "input_data")
            )
        )

        mockkObject(PluginLoader)
        val mockPluginEntry = mockk<PluginEntry>(relaxed = true)
        val mockProcessor = mockk<DataProcessor>(relaxed = true)
        val mockPluginManager = mockk<PluginManager>(relaxed = true)
        val mockLifecycleCoordinator = mockk<PluginLifecycleCoordinator>(relaxed = true)

        startKoin {
            modules(module {
                single<SettingsPersistence> { persistence }
                single { mockPluginManager }
                single { mockLifecycleCoordinator }
            })
        }

        every { PluginLoader.getPluginById("test-plugin") } returns mockPluginEntry
        every { mockPluginEntry.getProcessor() } answers { Result.success(mockProcessor) }
        every { mockPluginEntry.getManifest() } answers {
            Result.success(
                org.wip.plugintoolkit.api.PluginManifest(
                    manifestVersion = "1.0",
                    plugin = org.wip.plugintoolkit.api.PluginInfo(
                        id = "test-plugin",
                        name = "Test Plugin",
                        version = "1.0.0",
                        description = "Test"
                    ),
                    requirements = org.wip.plugintoolkit.api.Requirements(minMemoryMb = 128, minExecutionTimeMs = 100),
                    capabilities = listOf(
                        org.wip.plugintoolkit.api.Capability(
                            name = "merge",
                            description = "test merge",
                            returnType = DataType.Primitive(PrimitiveType.STRING),
                            parameters = mapOf(
                                "list_input" to org.wip.plugintoolkit.api.ParameterMetadata(
                                    description = "list_input",
                                    type = DataType.Array(DataType.Primitive(PrimitiveType.STRING))
                                )
                            )
                        )
                    )
                )
            )
        }

        var capturedParameters: Map<String, kotlinx.serialization.json.JsonElement>? = null
        coEvery { mockProcessor.process(any(), any()) } coAnswers {
            val req = firstArg<PluginRequest>()
            capturedParameters = req.parameters
            org.wip.plugintoolkit.api.ExecutionResult.Success(
                org.wip.plugintoolkit.api.PluginResponse(
                    result = kotlinx.serialization.json.JsonObject(mapOf("result" to JsonPrimitive("merged-result")))
                )
            )
        }

        val job = BackgroundJob(
            id = "test-job-dynamic-list-flattening",
            name = "Dynamic List Flattening Job",
            type = JobType.Flow,
            pluginId = "system",
            capabilityName = "test_dynamic_list_flattening",
            parameters = mapOf(
                "1" to kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive("A"), JsonPrimitive("B"))),
                "2" to kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive("C"), JsonPrimitive("D")))
            )
        )

        val engine = org.wip.plugintoolkit.features.job.logic.FlowEngine(
            jobManager,
            org.wip.plugintoolkit.features.job.logic.DefaultSystemNodeExecutorRegistry(),
            mockPluginManager,
            mockLifecycleCoordinator,
            backgroundScope
        )
        val outputs = engine.executeSubFlowRecursively(flow, job, "/tmp")

        assertNotNull(capturedParameters)
        val listInputParam = capturedParameters!!["list_input"]!!

        val jsonArray = listInputParam.jsonArray
        assertEquals(4, jsonArray.size)
        assertEquals("A", jsonArray[0].jsonPrimitive.content)
        assertEquals("B", jsonArray[1].jsonPrimitive.content)
        assertEquals("C", jsonArray[2].jsonPrimitive.content)
        assertEquals("D", jsonArray[3].jsonPrimitive.content)

        assertEquals("merged-result", outputs["result"])
    }

}
