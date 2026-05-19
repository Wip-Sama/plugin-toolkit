package org.wip.plugintoolkit.features.flows

import androidx.compose.ui.geometry.Offset
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.io.buffered
import kotlinx.io.writeString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.wip.plugintoolkit.api.DataProcessor
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.JobHandle
import org.wip.plugintoolkit.api.PluginEntry
import org.wip.plugintoolkit.api.PluginSignal
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.InputPort
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.model.OutputPort
import org.wip.plugintoolkit.features.flows.viewmodel.SystemNodesRegistry
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.job.logic.JobWorker
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobType
import org.wip.plugintoolkit.features.plugin.logic.PluginLifecycleCoordinator
import org.wip.plugintoolkit.features.plugin.logic.PluginLoader
import org.wip.plugintoolkit.features.plugin.logic.PluginManager
import org.wip.plugintoolkit.features.settings.logic.SettingsPersistence
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository
import org.wip.plugintoolkit.features.settings.model.AppSettings
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail

class JobWorkerFlowTest {
    private val json = Json { encodeDefaults = true }

    private class FakeSettingsPersistence : SettingsPersistence {
        var settings = AppSettings()
        override fun load(): AppSettings = settings
        override fun save(settings: AppSettings) { this.settings = settings }
        override fun getSettingsDir(): String = "/tmp"
        override fun getJobsDir(): String = "/tmp/jobs"
        override fun openLogFolder() {}
        override fun openLatestLog() {}
    }

    private fun createInputNode(id: Long, name: String, dataType: DataType, semanticType: String? = null): Node.FlowInputNode {
        return Node.FlowInputNode(
            id = id,
            position = Offset.Zero,
            outputs = listOf(
                OutputPort(id = "output_data", name = name, dataType = dataType, semanticType = semanticType)
            )
        )
    }

    private fun createSystemNode(id: Long, action: String): Node.SystemNode {
        return Node.SystemNode(
            id = id,
            position = Offset.Zero,
            title = action.replaceFirstChar { it.uppercase() },
            systemAction = action,
            inputs = SystemNodesRegistry.getInputs(action),
            outputs = SystemNodesRegistry.getOutputs(action)
        )
    }

    private fun createOutputNode(id: Long, name: String, dataType: DataType): Node.FlowOutputNode {
        return Node.FlowOutputNode(
            id = id,
            position = Offset.Zero,
            inputs = listOf(
                InputPort(id = "input_data", name = name, dataType = dataType)
            )
        )
    }

    @Test
    fun testConvertNodeSuccess() = runTest {
        val persistence = FakeSettingsPersistence()
        val settingsRepo = SettingsRepository(persistence, backgroundScope)
        val jobManager = JobManager(backgroundScope, settingsRepo)
        val jobWorker = JobWorker(1, jobManager, backgroundScope)

        // Flow: Input node (String: "123") -> Convert Node (to Int) -> Output Node
        val inputNode = createInputNode(1, "source", DataType.Primitive(PrimitiveType.STRING))
        
        // Convert node with target type Int
        val convertNode = createSystemNode(2, "convert")
            .copyWithUpdatedInput("input_data", "123")

        val outputNode = createOutputNode(3, "result", DataType.Primitive(PrimitiveType.INT))

        val flow = Flow(
            name = "test_convert_success",
            nodes = listOf(inputNode, convertNode, outputNode),
            connections = listOf(
                Connection(1, "output_data", 2, "input_data"),
                Connection(2, "output_data", 3, "input_data")
            )
        )

        val job = BackgroundJob(
            id = "test-job-convert-success",
            name = "Convert Success Job",
            type = JobType.Flow,
            pluginId = "system",
            capabilityName = "test_convert_success",
            parameters = mapOf("1" to JsonPrimitive("123"))
        )

        val outputs = jobWorker.executeSubFlowRecursively(flow, job, "/tmp")

        // Output of convert should be successfully cast to Int
        assertEquals(123, outputs["result"])
    }

    @Test
    fun testConvertNodeSoftFailure() = runTest {
        val persistence = FakeSettingsPersistence()
        val settingsRepo = SettingsRepository(persistence, backgroundScope)
        val jobManager = JobManager(backgroundScope, settingsRepo)
        val jobWorker = JobWorker(1, jobManager, backgroundScope)

        // Flow: Input node (String: "invalid-int") -> Convert Node (to Int) -> Output Node
        val inputNode = createInputNode(1, "source", DataType.Primitive(PrimitiveType.STRING))
        
        val convertNode = createSystemNode(2, "convert")
            .copyWithUpdatedInput("input_data", "invalid-int")

        val outputNode = createOutputNode(3, "result", DataType.Primitive(PrimitiveType.INT))

        val flow = Flow(
            name = "test_convert_fail",
            nodes = listOf(inputNode, convertNode, outputNode),
            connections = listOf(
                Connection(1, "output_data", 2, "input_data"),
                Connection(2, "output_data", 3, "input_data")
            )
        )

        val job = BackgroundJob(
            id = "test-job-convert-fail",
            name = "Convert Fail Job",
            type = JobType.Flow,
            pluginId = "system",
            capabilityName = "test_convert_fail",
            parameters = mapOf("1" to JsonPrimitive("invalid-int"))
        )

        // Should complete without throwing exception (soft failure)
        val outputs = jobWorker.executeSubFlowRecursively(flow, job, "/tmp")

        // The target output should be null since conversion failed
        assertNull(outputs["result"])
    }

    @Test
    fun testConditionalNodeFiresOnlyOneBranch_True() = runTest {
        val persistence = FakeSettingsPersistence()
        val settingsRepo = SettingsRepository(persistence, backgroundScope)
        val jobManager = JobManager(backgroundScope, settingsRepo)
        val jobWorker = JobWorker(1, jobManager, backgroundScope)

        // Flow:
        // Input Node -> Conditional Node (condition = true)
        // Conditional if_true -> Log Node (success path)
        // Conditional if_false -> Error Node (should NOT be fired/executed)
        val inputNode = createInputNode(1, "data", DataType.Primitive(PrimitiveType.STRING))
        
        val condNode = createSystemNode(2, "conditional")
            .copyWithUpdatedInput("condition", true)
            .copyWithUpdatedInput("input_data", "hello")

        val logNode = createSystemNode(3, "log")
            .copyWithUpdatedInput("message", "Condition was true")

        val errorNode = createSystemNode(4, "error")
            .copyWithUpdatedInput("message", "Error: should not be executed")

        val flow = Flow(
            name = "test_conditional_true",
            nodes = listOf(inputNode, condNode, logNode, errorNode),
            connections = listOf(
                Connection(1, "output_data", 2, "input_data"),
                Connection(2, "if_true", 3, "data"),
                Connection(2, "if_false", 4, "data")
            )
        )

        val job = BackgroundJob(
            id = "test-job-cond-true",
            name = "Conditional True Job",
            type = JobType.Flow,
            pluginId = "system",
            capabilityName = "test_conditional_true"
        )

        // If both branches were executed, the errorNode would throw an Exception, failing the flow.
        // If only the true branch is executed, this runs successfully.
        val outputs = jobWorker.executeSubFlowRecursively(flow, job, "/tmp")
        
        // Flow should have completed without errors
        assertNotNull(outputs)
    }

    @Test
    fun testConditionalNodeFiresOnlyOneBranch_False() = runTest {
        val persistence = FakeSettingsPersistence()
        val settingsRepo = SettingsRepository(persistence, backgroundScope)
        val jobManager = JobManager(backgroundScope, settingsRepo)
        val jobWorker = JobWorker(1, jobManager, backgroundScope)

        // Flow:
        // Input Node -> Conditional Node (condition = false)
        // Conditional if_true -> Error Node (should NOT be executed)
        // Conditional if_false -> Log Node (success path)
        val inputNode = createInputNode(1, "data", DataType.Primitive(PrimitiveType.STRING))
        
        val condNode = createSystemNode(2, "conditional")
            .copyWithUpdatedInput("condition", false)
            .copyWithUpdatedInput("input_data", "hello")

        val errorNode = createSystemNode(3, "error")
            .copyWithUpdatedInput("message", "Error: should not be executed")

        val logNode = createSystemNode(4, "log")
            .copyWithUpdatedInput("message", "Condition was false")

        val flow = Flow(
            name = "test_conditional_false",
            nodes = listOf(inputNode, condNode, errorNode, logNode),
            connections = listOf(
                Connection(1, "output_data", 2, "input_data"),
                Connection(2, "if_true", 3, "data"),
                Connection(2, "if_false", 4, "data")
            )
        )

        val job = BackgroundJob(
            id = "test-job-cond-false",
            name = "Conditional False Job",
            type = JobType.Flow,
            pluginId = "system",
            capabilityName = "test_conditional_false"
        )

        // If the true branch runs, it throws an exception.
        // Otherwise, it runs successfully.
        val outputs = jobWorker.executeSubFlowRecursively(flow, job, "/tmp")
        
        assertNotNull(outputs)
    }

    @Test
    fun testErrorNodeHaltsFlowAndAppendsData() = runTest {
        val persistence = FakeSettingsPersistence()
        val settingsRepo = SettingsRepository(persistence, backgroundScope)
        val jobManager = JobManager(backgroundScope, settingsRepo)
        val jobWorker = JobWorker(1, jobManager, backgroundScope)

        // Flow: Input node (String: "debug_info") -> Error Node (message = "Execution failed")
        val inputNode = createInputNode(1, "data", DataType.Primitive(PrimitiveType.STRING))
        
        val errorNode = createSystemNode(2, "error")
            .copyWithUpdatedInput("message", "Execution failed")

        val flow = Flow(
            name = "test_error_halt",
            nodes = listOf(inputNode, errorNode),
            connections = listOf(
                Connection(1, "output_data", 2, "data")
            )
        )

        val job = BackgroundJob(
            id = "test-job-error-halt",
            name = "Error Halt Job",
            type = JobType.Flow,
            pluginId = "system",
            capabilityName = "test_error_halt",
            parameters = mapOf("1" to JsonPrimitive("debug_info"))
        )

        try {
            jobWorker.executeSubFlowRecursively(flow, job, "/tmp")
            fail("Flow should have failed due to Error Node execution")
        } catch (e: Exception) {
            assertEquals("Execution failed: debug_info", e.message)
        }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
        unmockkAll()
    }

    @Test
    fun testFlowPauseAndResumeBetweenNodes() = runTest {
        val persistence = FakeSettingsPersistence()
        val settingsRepo = SettingsRepository(persistence, backgroundScope)
        val jobManager = JobManager(backgroundScope, settingsRepo)

        val mockPluginManager = mockk<PluginManager>(relaxed = true)
        val mockLifecycleCoordinator = mockk<PluginLifecycleCoordinator>(relaxed = true)

        startKoin {
            modules(module {
                single<SettingsPersistence> { persistence }
                single { mockPluginManager }
                single { mockLifecycleCoordinator }
            })
        }

        // Mock PluginLoader and the PluginEntry / DataProcessor
        mockkObject(PluginLoader)
        val mockPluginEntry = mockk<PluginEntry>(relaxed = true)
        val mockProcessor = mockk<DataProcessor>(relaxed = true)
        val mockHandle1 = mockk<JobHandle>(relaxed = true)
        val mockHandle2 = mockk<JobHandle>(relaxed = true)

        every { PluginLoader.getPluginById("test-plugin") } returns mockPluginEntry
        every { mockPluginEntry.getProcessor() } returns mockProcessor

        // First call to processAsync (Node 2)
        val deferred1 = kotlinx.coroutines.CompletableDeferred<org.wip.plugintoolkit.api.ExecutionResult>()
        every { mockHandle1.result } returns deferred1
        
        // Second call to processAsync (Node 3)
        val deferred2 = kotlinx.coroutines.CompletableDeferred<org.wip.plugintoolkit.api.ExecutionResult>()
        every { mockHandle2.result } returns deferred2

        var callCount = 0
        every { mockProcessor.processAsync(any(), any()) } answers {
            callCount++
            if (callCount == 1) mockHandle1 else mockHandle2
        }

        // Flow: Input node (1) -> Capability Node A (2) -> Capability Node B (3) -> Output Node (4)
        val inputNode = createInputNode(1, "source", DataType.Primitive(PrimitiveType.STRING))
        val capNodeA = Node.CapabilityNode(
            id = 2,
            position = Offset.Zero,
            pluginInfo = org.wip.plugintoolkit.api.PluginInfo(id = "test-plugin", name = "Test Plugin", version = "1.0.0", description = "Test"),
            capability = org.wip.plugintoolkit.api.Capability(name = "capA", description = "test A", returnType = DataType.Primitive(PrimitiveType.STRING)),
            inputs = listOf(InputPort(id = "input", name = "input", dataType = DataType.Primitive(PrimitiveType.STRING))),
            outputs = listOf(OutputPort(id = "result", name = "result", dataType = DataType.Primitive(PrimitiveType.STRING)))
        )
        val capNodeB = Node.CapabilityNode(
            id = 3,
            position = Offset.Zero,
            pluginInfo = org.wip.plugintoolkit.api.PluginInfo(id = "test-plugin", name = "Test Plugin", version = "1.0.0", description = "Test"),
            capability = org.wip.plugintoolkit.api.Capability(name = "capB", description = "test B", returnType = DataType.Primitive(PrimitiveType.STRING)),
            inputs = listOf(InputPort(id = "input", name = "input", dataType = DataType.Primitive(PrimitiveType.STRING))),
            outputs = listOf(OutputPort(id = "result", name = "result", dataType = DataType.Primitive(PrimitiveType.STRING)))
        )
        val outputNode = createOutputNode(4, "result", DataType.Primitive(PrimitiveType.STRING))

        val flow = Flow(
            name = "test_pause_resume",
            nodes = listOf(inputNode, capNodeA, capNodeB, outputNode),
            connections = listOf(
                Connection(1, "output_data", 2, "input"),
                Connection(2, "result", 3, "input"),
                Connection(3, "result", 4, "input_data")
            )
        )

        // Save flow json to settings dir
        val appDataDir = persistence.getSettingsDir()
        val safeName = flow.name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val file = kotlinx.io.files.Path("$appDataDir/flows/$safeName.json")
        val parent = file.parent
        if (parent != null && !kotlinx.io.files.SystemFileSystem.exists(parent)) {
            kotlinx.io.files.SystemFileSystem.createDirectories(parent)
        }
        val flowJson = json.encodeToString(Flow.serializer(), flow)
        kotlinx.io.files.SystemFileSystem.sink(file).buffered().use { it.writeString(flowJson) }

        val job = BackgroundJob(
            id = "test-job-pause-resume",
            name = "Pausable Job",
            type = JobType.Flow,
            status = org.wip.plugintoolkit.features.job.model.JobStatus.Queued,
            pluginId = "system",
            capabilityName = flow.name,
            parameters = mapOf("1" to JsonPrimitive("start-val")),
            isPausable = true
        )

        jobManager.enqueueJob(job)
        val jobWorker = JobWorker(1, jobManager, backgroundScope)
        jobWorker.start()

        // Wait for job to start running
        kotlinx.coroutines.withTimeout(2000) {
            while (jobManager.jobs.value.first().status != org.wip.plugintoolkit.features.job.model.JobStatus.Running) {
                kotlinx.coroutines.delay(10)
            }
        }

        // Complete first capability successfully
        deferred1.complete(org.wip.plugintoolkit.api.ExecutionResult.Success(
            org.wip.plugintoolkit.api.PluginResponse(
                result = kotlinx.serialization.json.JsonObject(mapOf("result" to JsonPrimitive("res-A")))
            )
        ))

        // Request flow pause
        jobManager.pauseJob(job.id)

        // Wait for job to transition to Paused status and save resumeState
        kotlinx.coroutines.withTimeout(2000) {
            while (jobManager.jobs.value.first().resumeState == null) {
                kotlinx.coroutines.delay(10)
            }
        }

        // Verify state is saved
        val pausedJob = jobManager.jobs.value.first()
        val resumeState = pausedJob.resumeState as? kotlinx.serialization.json.JsonObject
        assertNotNull(resumeState)
        
        // Resume the job
        jobManager.resumeJob(job.id)

        // Wait for job to transition back to Running
        kotlinx.coroutines.withTimeout(2000) {
            while (jobManager.jobs.value.first().status != org.wip.plugintoolkit.features.job.model.JobStatus.Running) {
                kotlinx.coroutines.delay(10)
            }
        }

        // Complete the second capability
        deferred2.complete(org.wip.plugintoolkit.api.ExecutionResult.Success(
            org.wip.plugintoolkit.api.PluginResponse(
                result = kotlinx.serialization.json.JsonObject(mapOf("result" to JsonPrimitive("res-B")))
            )
        ))

        // Wait for job to complete
        kotlinx.coroutines.withTimeout(2000) {
            while (jobManager.endedJobs.value.none { it.id == job.id }) {
                kotlinx.coroutines.delay(10)
            }
        }

        val finalJob = jobManager.endedJobs.value.first { it.id == job.id }
        assertEquals(org.wip.plugintoolkit.features.job.model.JobStatus.Completed, finalJob.status)
        val outputs = Json.decodeFromString<Map<String, String>>(finalJob.result ?: "")
        assertEquals("res-B", outputs["result"])
    }

    @Test
    fun testFlowPauseAndResumeMidCapability() = runTest {
        val persistence = FakeSettingsPersistence()
        val settingsRepo = SettingsRepository(persistence, backgroundScope)
        val jobManager = JobManager(backgroundScope, settingsRepo)

        val mockPluginManager = mockk<PluginManager>(relaxed = true)
        val mockLifecycleCoordinator = mockk<PluginLifecycleCoordinator>(relaxed = true)

        startKoin {
            modules(module {
                single<SettingsPersistence> { persistence }
                single { mockPluginManager }
                single { mockLifecycleCoordinator }
            })
        }

        // Mock PluginLoader and the PluginEntry / DataProcessor
        mockkObject(PluginLoader)
        val mockPluginEntry = mockk<PluginEntry>(relaxed = true)
        val mockProcessor = mockk<DataProcessor>(relaxed = true)
        val mockHandle1 = mockk<JobHandle>(relaxed = true)
        val mockHandle2 = mockk<JobHandle>(relaxed = true)

        every { PluginLoader.getPluginById("test-plugin") } returns mockPluginEntry
        every { mockPluginEntry.getProcessor() } returns mockProcessor

        var capabilityResumedWithState: kotlinx.serialization.json.JsonElement? = null
        var isSecondCall = false
        val deferred1 = kotlinx.coroutines.CompletableDeferred<org.wip.plugintoolkit.api.ExecutionResult>()
        val deferred2 = kotlinx.coroutines.CompletableDeferred<org.wip.plugintoolkit.api.ExecutionResult>()

        every { mockHandle1.result } returns deferred1
        every { mockHandle2.result } returns deferred2

        every { mockProcessor.processAsync(any(), any()) } answers {
            val req = firstArg<org.wip.plugintoolkit.api.PluginRequest>()
            if (isSecondCall) {
                capabilityResumedWithState = req.resumeState
                deferred2.complete(org.wip.plugintoolkit.api.ExecutionResult.Success(
                    org.wip.plugintoolkit.api.PluginResponse(
                        result = kotlinx.serialization.json.JsonObject(mapOf("result" to JsonPrimitive("finished")))
                    )
                ))
                mockHandle2
            } else {
                mockHandle1
            }
        }

        // Flow: Input node (1) -> Capability Node (2) -> Output Node (3)
        val inputNode = createInputNode(1, "source", DataType.Primitive(PrimitiveType.STRING))
        val capNode = Node.CapabilityNode(
            id = 2,
            position = Offset.Zero,
            pluginInfo = org.wip.plugintoolkit.api.PluginInfo(id = "test-plugin", name = "Test Plugin", version = "1.0.0", description = "Test"),
            capability = org.wip.plugintoolkit.api.Capability(name = "cap", description = "test cap", returnType = DataType.Primitive(PrimitiveType.STRING)),
            inputs = listOf(InputPort(id = "input", name = "input", dataType = DataType.Primitive(PrimitiveType.STRING))),
            outputs = listOf(OutputPort(id = "result", name = "result", dataType = DataType.Primitive(PrimitiveType.STRING)))
        )
        val outputNode = createOutputNode(3, "result", DataType.Primitive(PrimitiveType.STRING))

        val flow = Flow(
            name = "test_cap_pause",
            nodes = listOf(inputNode, capNode, outputNode),
            connections = listOf(
                Connection(1, "output_data", 2, "input"),
                Connection(2, "result", 3, "input_data")
            )
        )

        // Save flow json
        val appDataDir = persistence.getSettingsDir()
        val safeName = flow.name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val file = kotlinx.io.files.Path("$appDataDir/flows/$safeName.json")
        val parent = file.parent
        if (parent != null && !kotlinx.io.files.SystemFileSystem.exists(parent)) {
            kotlinx.io.files.SystemFileSystem.createDirectories(parent)
        }
        val flowJson = json.encodeToString(Flow.serializer(), flow)
        kotlinx.io.files.SystemFileSystem.sink(file).buffered().use { it.writeString(flowJson) }

        val job = BackgroundJob(
            id = "test-job-cap-pause",
            name = "Mid-cap Pause Job",
            type = JobType.Flow,
            status = org.wip.plugintoolkit.features.job.model.JobStatus.Queued,
            pluginId = "system",
            capabilityName = flow.name,
            parameters = mapOf("1" to JsonPrimitive("start-val")),
            isPausable = true
        )

        jobManager.enqueueJob(job)
        val jobWorker = JobWorker(1, jobManager, backgroundScope)
        jobWorker.start()

        // Wait for job to start running
        kotlinx.coroutines.withTimeout(2000) {
            while (jobManager.jobs.value.first().status != org.wip.plugintoolkit.features.job.model.JobStatus.Running) {
                kotlinx.coroutines.delay(10)
            }
        }

        // Simulate capability returning Paused result
        deferred1.complete(org.wip.plugintoolkit.api.ExecutionResult.Paused(
            JsonPrimitive("saved-mid-state")
        ))

        // Wait for flow job to transition to Paused status
        kotlinx.coroutines.withTimeout(2000) {
            while (jobManager.jobs.value.first().status != org.wip.plugintoolkit.features.job.model.JobStatus.Paused) {
                kotlinx.coroutines.delay(10)
            }
        }

        // Verify capability resume state is serialized
        val pausedJob = jobManager.jobs.value.first()
        val resumeState = pausedJob.resumeState as? kotlinx.serialization.json.JsonObject
        assertNotNull(resumeState)
        val capResumeStates = resumeState["capabilityResumeStates"]?.jsonObject
        assertNotNull(capResumeStates)
        assertEquals(JsonPrimitive("saved-mid-state"), capResumeStates["2"])

        // Set flag for the second run
        isSecondCall = true

        // Resume the job
        jobManager.resumeJob(job.id)

        // Wait for job to complete
        kotlinx.coroutines.withTimeout(2000) {
            while (jobManager.endedJobs.value.none { it.id == job.id }) {
                kotlinx.coroutines.delay(10)
            }
        }

        // Verify the capability received the saved resume state
        assertEquals(JsonPrimitive("saved-mid-state"), capabilityResumedWithState)

        val finalJob = jobManager.endedJobs.value.first { it.id == job.id }
        assertEquals(org.wip.plugintoolkit.features.job.model.JobStatus.Completed, finalJob.status)
    }

    @Test
    fun testFlowPauseAndResumeWithRealSignalPropagation() = runTest {
        val persistence = FakeSettingsPersistence()
        val settingsRepo = SettingsRepository(persistence, backgroundScope)
        val jobManager = JobManager(backgroundScope, settingsRepo)

        val mockPluginManager = mockk<PluginManager>(relaxed = true)
        val mockLifecycleCoordinator = mockk<PluginLifecycleCoordinator>(relaxed = true)

        val signalManager = org.wip.plugintoolkit.features.plugin.logic.DefaultPluginSignalManager()
        val mockContext = mockk<org.wip.plugintoolkit.api.PluginContext>(relaxed = true)
        every { mockContext.signals } returns signalManager
        every { mockContext.onSignal(any()) } answers {
            signalManager.onSignal(firstArg())
        }
        every { mockPluginManager.createPluginContext(any(), any()) } returns mockContext

        startKoin {
            modules(module {
                single<SettingsPersistence> { persistence }
                single { mockPluginManager }
                single { mockLifecycleCoordinator }
            })
        }

        // Mock PluginLoader and the PluginEntry / DataProcessor
        mockkObject(PluginLoader)
        val mockPluginEntry = mockk<PluginEntry>(relaxed = true)
        val mockProcessor = mockk<DataProcessor>(relaxed = true)

        every { PluginLoader.getPluginById("test-plugin") } returns mockPluginEntry
        every { mockPluginEntry.getProcessor() } returns mockProcessor

        var capabilityResumedWithState: kotlinx.serialization.json.JsonElement? = null
        var isSecondCall = false
        val deferred1 = kotlinx.coroutines.CompletableDeferred<org.wip.plugintoolkit.api.ExecutionResult>()
        val deferred2 = kotlinx.coroutines.CompletableDeferred<org.wip.plugintoolkit.api.ExecutionResult>()

        every { mockProcessor.processAsync(any(), any()) } answers {
            val req = firstArg<org.wip.plugintoolkit.api.PluginRequest>()
            val ctx = secondArg<org.wip.plugintoolkit.api.PluginContext>()
            if (isSecondCall) {
                capabilityResumedWithState = req.resumeState
                deferred2.complete(org.wip.plugintoolkit.api.ExecutionResult.Success(
                    org.wip.plugintoolkit.api.PluginResponse(
                        result = kotlinx.serialization.json.JsonObject(mapOf("result" to JsonPrimitive("finished")))
                    )
                ))
                object : JobHandle {
                    override val result = deferred2
                    override fun pause() {}
                    override fun cancel(force: Boolean) {}
                }
            } else {
                ctx.onSignal { signal ->
                    if (signal == PluginSignal.PAUSE) {
                        deferred1.complete(org.wip.plugintoolkit.api.ExecutionResult.Paused(
                            JsonPrimitive("saved-mid-state")
                        ))
                    }
                }
                object : JobHandle {
                    override val result = deferred1
                    override fun pause() {
                        backgroundScope.launch {
                            ctx.signals.sendSignal(PluginSignal.PAUSE)
                        }
                    }
                    override fun cancel(force: Boolean) {}
                }
            }
        }

        // Flow: Input node (1) -> Capability Node (2) -> Output Node (3)
        val inputNode = createInputNode(1, "source", DataType.Primitive(PrimitiveType.STRING))
        val capNode = Node.CapabilityNode(
            id = 2,
            position = Offset.Zero,
            pluginInfo = org.wip.plugintoolkit.api.PluginInfo(id = "test-plugin", name = "Test Plugin", version = "1.0.0", description = "Test"),
            capability = org.wip.plugintoolkit.api.Capability(name = "cap", description = "test cap", returnType = DataType.Primitive(PrimitiveType.STRING)),
            inputs = listOf(InputPort(id = "input", name = "input", dataType = DataType.Primitive(PrimitiveType.STRING))),
            outputs = listOf(OutputPort(id = "result", name = "result", dataType = DataType.Primitive(PrimitiveType.STRING)))
        )
        val outputNode = createOutputNode(3, "result", DataType.Primitive(PrimitiveType.STRING))

        val flow = Flow(
            name = "test_cap_pause_propagation",
            nodes = listOf(inputNode, capNode, outputNode),
            connections = listOf(
                Connection(1, "output_data", 2, "input"),
                Connection(2, "result", 3, "input_data")
            )
        )

        // Save flow json
        val appDataDir = persistence.getSettingsDir()
        val safeName = flow.name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val file = kotlinx.io.files.Path("$appDataDir/flows/$safeName.json")
        val parent = file.parent
        if (parent != null && !kotlinx.io.files.SystemFileSystem.exists(parent)) {
            kotlinx.io.files.SystemFileSystem.createDirectories(parent)
        }
        val flowJson = json.encodeToString(Flow.serializer(), flow)
        kotlinx.io.files.SystemFileSystem.sink(file).buffered().use { it.writeString(flowJson) }

        val job = BackgroundJob(
            id = "test-job-cap-pause-propagation",
            name = "Mid-cap Pause Signal Propagation Job",
            type = JobType.Flow,
            status = org.wip.plugintoolkit.features.job.model.JobStatus.Queued,
            pluginId = "system",
            capabilityName = flow.name,
            parameters = mapOf("1" to JsonPrimitive("start-val")),
            isPausable = true
        )

        jobManager.enqueueJob(job)
        val jobWorker = JobWorker(1, jobManager, backgroundScope)
        jobWorker.start()

        // Wait for job to start running
        kotlinx.coroutines.withTimeout(2000) {
            while (jobManager.jobs.value.first().status != org.wip.plugintoolkit.features.job.model.JobStatus.Running) {
                kotlinx.coroutines.delay(10)
            }
        }

        // Call jobManager.pauseJob(job.id) to trigger the pause signal propagation
        jobManager.pauseJob(job.id)

        // Wait for flow job to transition to Paused status and save resumeState
        kotlinx.coroutines.withTimeout(2000) {
            while (jobManager.jobs.value.first().resumeState == null) {
                kotlinx.coroutines.delay(10)
            }
        }

        // Verify capability resume state is serialized
        val pausedJob = jobManager.jobs.value.first()
        val resumeState = pausedJob.resumeState as? kotlinx.serialization.json.JsonObject
        assertNotNull(resumeState)
        val capResumeStates = resumeState["capabilityResumeStates"]?.jsonObject
        assertNotNull(capResumeStates)
        assertEquals(JsonPrimitive("saved-mid-state"), capResumeStates["2"])

        // Set flag for the second run
        isSecondCall = true

        // Resume the job
        jobManager.resumeJob(job.id)

        // Wait for job to complete
        kotlinx.coroutines.withTimeout(2000) {
            while (jobManager.endedJobs.value.none { it.id == job.id }) {
                kotlinx.coroutines.delay(10)
            }
        }

        // Verify the capability received the saved resume state
        assertEquals(JsonPrimitive("saved-mid-state"), capabilityResumedWithState)

        val finalJob = jobManager.endedJobs.value.first { it.id == job.id }
        assertEquals(org.wip.plugintoolkit.features.job.model.JobStatus.Completed, finalJob.status)
    }
}
