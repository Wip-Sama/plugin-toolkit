package org.wip.plugintoolkit.features.flows

import androidx.compose.ui.geometry.Offset
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
import org.wip.plugintoolkit.api.PluginContext
import org.wip.plugintoolkit.api.PluginEntry
import org.wip.plugintoolkit.api.PluginRequest
import org.wip.plugintoolkit.api.PluginSignal
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.core.utils.DefaultSemanticRegistry
import org.wip.plugintoolkit.core.utils.SemanticRegistry
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
import kotlin.time.Duration.Companion.milliseconds

class JobWorkerFlowPauseResumeTest : JobWorkerFlowTestBase() {

    @Test
    fun testFlowPauseAndResumeBetweenNodes() = runTest {
        val persistence = FakeSettingsPersistence()
        val settingsRepo = SettingsRepository(persistence, backgroundScope)
        val jobManager = JobManager(backgroundScope, settingsRepo)

        val mockPluginManager = mockk<PluginManager>(relaxed = true)
        val mockPluginContext = mockk<org.wip.plugintoolkit.api.PluginContext>(relaxed = true)
        every { mockPluginManager.createPluginContext(any(), any(), any(), any(), any()) } returns mockPluginContext
        val mockLifecycleCoordinator = mockk<PluginLifecycleCoordinator>(relaxed = true)

        stopKoin()
        startKoin {
            modules(module {
                single<SettingsPersistence> { persistence }
                single { settingsRepo }
                single { mockPluginManager }
                single { mockLifecycleCoordinator }
                single<SemanticRegistry> { DefaultSemanticRegistry() }
            })
        }

        // Mock PluginLoader and the PluginEntry / DataProcessor
        mockkObject(PluginLoader)
        val mockPluginEntry = mockk<PluginEntry>(relaxed = true)
        val mockProcessor = mockk<DataProcessor>(relaxed = true)
        val mockHandle1 = mockk<JobHandle>(relaxed = true)
        val mockHandle2 = mockk<JobHandle>(relaxed = true)

        every { PluginLoader.getPluginById("test-plugin") } returns mockPluginEntry
        every { mockPluginEntry.getProcessor() } answers { Result.success(mockProcessor) }
        every { mockPluginEntry.getManifest() } answers {
            Result.success(
                org.wip.plugintoolkit.api.PluginManifest(
                    manifestVersion = "1.0",
                    plugin = org.wip.plugintoolkit.api.PluginInfo("test-plugin", "Test Plugin", "1.0.0", "Test"),
                    requirements = org.wip.plugintoolkit.api.Requirements(128, 1000)
                )
            )
        }

        // First call to processAsync (Node 2)
        val deferred1 = kotlinx.coroutines.CompletableDeferred<org.wip.plugintoolkit.api.ExecutionResult>()
        every { mockHandle1.result } returns deferred1

        // Second call to processAsync (Node 3)
        val deferred2 = kotlinx.coroutines.CompletableDeferred<org.wip.plugintoolkit.api.ExecutionResult>()
        every { mockHandle2.result } returns deferred2

        var callCount = 0
        coEvery { mockProcessor.process(any(), any()) } coAnswers {
            callCount++
            if (callCount == 1) {
                val req = firstArg<PluginRequest>()
                val ctx = secondArg<PluginContext>()
                ctx.onSignal { signal ->
                    if (signal == PluginSignal.PAUSE) {
                        deferred1.complete(org.wip.plugintoolkit.api.ExecutionResult.Paused(JsonPrimitive("saved-node-1")))
                    }
                }
                deferred1.await()
            } else {
                deferred2.await()
            }
        }

        // Flow: Input node (1) -> Capability Node A (2) -> Capability Node B (3) -> Output Node (4)
        val inputNode = createInputNode(1, "source", DataType.Primitive(PrimitiveType.STRING))
        val capNodeA = Node.CapabilityNode(
            id = 2,
            position = Offset.Zero,
            pluginInfo = org.wip.plugintoolkit.api.PluginInfo(
                id = "test-plugin",
                name = "Test Plugin",
                version = "1.0.0",
                description = "Test"
            ),
            capability = org.wip.plugintoolkit.api.Capability(
                name = "capA",
                description = "test A",
                returnType = DataType.Primitive(PrimitiveType.STRING)
            ),
            inputs = listOf(
                InputPort(
                    id = "input",
                    name = "input",
                    dataType = DataType.Primitive(PrimitiveType.STRING)
                )
            ),
            outputs = listOf(
                OutputPort(
                    id = "result",
                    name = "result",
                    dataType = DataType.Primitive(PrimitiveType.STRING)
                )
            )
        )
        val capNodeB = Node.CapabilityNode(
            id = 3,
            position = Offset.Zero,
            pluginInfo = org.wip.plugintoolkit.api.PluginInfo(
                id = "test-plugin",
                name = "Test Plugin",
                version = "1.0.0",
                description = "Test"
            ),
            capability = org.wip.plugintoolkit.api.Capability(
                name = "capB",
                description = "test B",
                returnType = DataType.Primitive(PrimitiveType.STRING)
            ),
            inputs = listOf(
                InputPort(
                    id = "input",
                    name = "input",
                    dataType = DataType.Primitive(PrimitiveType.STRING)
                )
            ),
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
        withContext(Dispatchers.Default) {
            withContext(Dispatchers.Default) {
                withTimeout(5000.milliseconds) {
                    while (true) {
                        val activeJob = jobManager.jobs.value.find { it.id == job.id }
                        val endedJob = jobManager.endedJobs.value.find { it.id == job.id }
                        if (activeJob?.status == org.wip.plugintoolkit.features.job.model.JobStatus.Running || endedJob != null) break
                        delay(10.milliseconds)
                    }
                }
            }
        }

        // Complete first capability successfully
        deferred1.complete(
            org.wip.plugintoolkit.api.ExecutionResult.Success(
                org.wip.plugintoolkit.api.PluginResponse(
                    result = kotlinx.serialization.json.JsonObject(mapOf("result" to JsonPrimitive("res-A")))
                )
            )
        )

        // Request flow pause
        jobManager.pauseJob(job.id)

        // Wait for job to transition to Paused status and save resumeState
        withContext(Dispatchers.Default) {
            withContext(Dispatchers.Default) {
                withTimeout(5000) {
                    while (true) {
                        val activeJob = jobManager.jobs.value.find { it.id == job.id }
                        if (activeJob?.resumeState != null) break
                        delay(10)
                    }
                }
            }
        }

        // Verify state is saved
        val pausedJob =
            jobManager.jobs.value.find { it.id == job.id } ?: jobManager.endedJobs.value.first { it.id == job.id }
        val resumeState = pausedJob.resumeState as? kotlinx.serialization.json.JsonObject
        assertNotNull(resumeState)

        // Resume the job
        jobManager.resumeJob(job.id)

        // Wait for job to transition back to Running
        withContext(Dispatchers.Default) {
            withContext(Dispatchers.Default) {
                withTimeout(5000) {
                    while (true) {
                        val activeJob = jobManager.jobs.value.find { it.id == job.id }
                        val endedJob = jobManager.endedJobs.value.find { it.id == job.id }
                        if (activeJob?.status == org.wip.plugintoolkit.features.job.model.JobStatus.Running || endedJob != null) break
                        delay(10)
                    }
                }
            }
        }

        // Complete the second capability
        deferred2.complete(
            org.wip.plugintoolkit.api.ExecutionResult.Success(
                org.wip.plugintoolkit.api.PluginResponse(
                    result = kotlinx.serialization.json.JsonObject(mapOf("result" to JsonPrimitive("res-B")))
                )
            )
        )

        // Wait for job to complete
        withTimeout(2000) {
            while (jobManager.endedJobs.value.none { it.id == job.id }) {
                delay(10)
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
        val mockPluginContext = mockk<org.wip.plugintoolkit.api.PluginContext>(relaxed = true)
        every { mockPluginManager.createPluginContext(any(), any(), any(), any(), any()) } returns mockPluginContext
        val mockLifecycleCoordinator = mockk<PluginLifecycleCoordinator>(relaxed = true)

        stopKoin()
        startKoin {
            modules(module {
                single<SettingsPersistence> { persistence }
                single { settingsRepo }
                single { mockPluginManager }
                single { mockLifecycleCoordinator }
                single<SemanticRegistry> { DefaultSemanticRegistry() }
            })
        }

        // Mock PluginLoader and the PluginEntry / DataProcessor
        mockkObject(PluginLoader)
        val mockPluginEntry = mockk<PluginEntry>(relaxed = true)
        val mockProcessor = mockk<DataProcessor>(relaxed = true)
        val mockHandle1 = mockk<JobHandle>(relaxed = true)
        val mockHandle2 = mockk<JobHandle>(relaxed = true)

        every { PluginLoader.getPluginById("test-plugin") } returns mockPluginEntry
        every { mockPluginEntry.getProcessor() } answers { Result.success(mockProcessor) }
        every { mockPluginEntry.getManifest() } answers {
            Result.success(
                org.wip.plugintoolkit.api.PluginManifest(
                    manifestVersion = "1.0",
                    plugin = org.wip.plugintoolkit.api.PluginInfo("test-plugin", "Test Plugin", "1.0.0", "Test"),
                    requirements = org.wip.plugintoolkit.api.Requirements(128, 1000)
                )
            )
        }

        var capabilityResumedWithState: kotlinx.serialization.json.JsonElement? = null
        var isSecondCall = false
        val deferred1 = kotlinx.coroutines.CompletableDeferred<org.wip.plugintoolkit.api.ExecutionResult>()
        val deferred2 = kotlinx.coroutines.CompletableDeferred<org.wip.plugintoolkit.api.ExecutionResult>()

        every { mockHandle1.result } returns deferred1
        every { mockHandle2.result } returns deferred2

        coEvery { mockProcessor.process(any(), any()) } coAnswers {
            val req = firstArg<PluginRequest>()
            if (isSecondCall) {
                capabilityResumedWithState = req.resumeState
                org.wip.plugintoolkit.api.ExecutionResult.Success(
                    org.wip.plugintoolkit.api.PluginResponse(
                        result = kotlinx.serialization.json.JsonObject(mapOf("result" to JsonPrimitive("finished")))
                    )
                )
            } else {
                deferred1.await()
            }
        }

        // Flow: Input node (1) -> Capability Node (2) -> Output Node (3)
        val inputNode = createInputNode(1, "source", DataType.Primitive(PrimitiveType.STRING))
        val capNode = Node.CapabilityNode(
            id = 2,
            position = Offset.Zero,
            pluginInfo = org.wip.plugintoolkit.api.PluginInfo(
                id = "test-plugin",
                name = "Test Plugin",
                version = "1.0.0",
                description = "Test"
            ),
            capability = org.wip.plugintoolkit.api.Capability(
                name = "cap",
                description = "test cap",
                returnType = DataType.Primitive(PrimitiveType.STRING)
            ),
            inputs = listOf(
                InputPort(
                    id = "input",
                    name = "input",
                    dataType = DataType.Primitive(PrimitiveType.STRING)
                )
            ),
            outputs = listOf(
                OutputPort(
                    id = "result",
                    name = "result",
                    dataType = DataType.Primitive(PrimitiveType.STRING)
                )
            )
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
        withTimeout(5000) {
            while (true) {
                val activeJob = jobManager.jobs.value.find { it.id == job.id }
                val endedJob = jobManager.endedJobs.value.find { it.id == job.id }
                if (activeJob?.status == org.wip.plugintoolkit.features.job.model.JobStatus.Running || endedJob != null) break
                delay(10)
            }
        }

        // Simulate capability returning Paused result
        deferred1.complete(
            org.wip.plugintoolkit.api.ExecutionResult.Paused(
                JsonPrimitive("saved-mid-state")
            )
        )

        // Wait for flow job to transition to Paused status
        withTimeout(5000) {
            while (true) {
                val activeJob = jobManager.jobs.value.find { it.id == job.id }
                val endedJob = jobManager.endedJobs.value.find { it.id == job.id }
                if (activeJob?.status == org.wip.plugintoolkit.features.job.model.JobStatus.Paused || endedJob != null) break
                delay(10)
            }
        }

        // Verify capability resume state is serialized
        println("JOBS: ${jobManager.jobs.value}")
        println("ENDED: ${jobManager.endedJobs.value}")
        val pausedJob =
            jobManager.jobs.value.find { it.id == job.id } ?: jobManager.endedJobs.value.first { it.id == job.id }
        println("PAUSED JOB: $pausedJob")
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
        withTimeout(2000) {
            while (jobManager.endedJobs.value.none { it.id == job.id }) {
                delay(10)
            }
        }

        // Verify the capability received the saved resume state
        assertEquals(JsonPrimitive("saved-mid-state"), capabilityResumedWithState)

        val finalJob = jobManager.endedJobs.value.first { it.id == job.id }
        assertEquals(org.wip.plugintoolkit.features.job.model.JobStatus.Completed, finalJob.status)
        jobWorker.stop()
    }

    @Test
    fun testFlowPauseAndResumeWithRealSignalPropagation() = runTest {
        val persistence = FakeSettingsPersistence()
        val settingsRepo = SettingsRepository(persistence, backgroundScope)
        val jobManager = JobManager(backgroundScope, settingsRepo)

        val mockPluginManager = mockk<PluginManager>(relaxed = true)
        val mockPluginContext = mockk<org.wip.plugintoolkit.api.PluginContext>(relaxed = true)
        every { mockPluginManager.createPluginContext(any(), any(), any(), any(), any()) } returns mockPluginContext
        val mockLifecycleCoordinator = mockk<PluginLifecycleCoordinator>(relaxed = true)

        val signalManager = org.wip.plugintoolkit.features.plugin.logic.DefaultPluginSignalManager()
        val mockContext = mockk<PluginContext>(relaxed = true)
        every { mockContext.signals } returns signalManager
        every { mockContext.onSignal(any()) } answers {
            signalManager.onSignal(firstArg())
        }
        every { mockPluginManager.createPluginContext(any(), any(), any(), any(), any()) } returns mockContext

        stopKoin()
        startKoin {
            modules(module {
                single<SettingsPersistence> { persistence }
                single { settingsRepo }
                single { mockPluginManager }
                single { mockLifecycleCoordinator }
                single<SemanticRegistry> { DefaultSemanticRegistry() }
            })
        }

        try {

            // Mock PluginLoader and the PluginEntry / DataProcessor
            mockkObject(PluginLoader)
            val mockPluginEntry = mockk<PluginEntry>(relaxed = true)
            val mockProcessor = mockk<DataProcessor>(relaxed = true)

            every { PluginLoader.getPluginById("test-plugin") } returns mockPluginEntry
            every { mockPluginEntry.getProcessor() } answers { Result.success(mockProcessor) }
            every { mockPluginEntry.getManifest() } answers {
                Result.success(
                    org.wip.plugintoolkit.api.PluginManifest(
                        manifestVersion = "1.0",
                        plugin = org.wip.plugintoolkit.api.PluginInfo("test-plugin", "Test Plugin", "1.0.0", "Test"),
                        requirements = org.wip.plugintoolkit.api.Requirements(128, 1000)
                    )
                )
            }

            var capabilityResumedWithState: kotlinx.serialization.json.JsonElement? = null
            var isSecondCall = false
            val deferred1 = kotlinx.coroutines.CompletableDeferred<org.wip.plugintoolkit.api.ExecutionResult>()
            val deferred2 = kotlinx.coroutines.CompletableDeferred<org.wip.plugintoolkit.api.ExecutionResult>()

            coEvery { mockProcessor.process(any(), any()) } coAnswers {
                val req = firstArg<PluginRequest>()
                val ctx = secondArg<PluginContext>()
                if (isSecondCall) {
                    capabilityResumedWithState = req.resumeState
                    org.wip.plugintoolkit.api.ExecutionResult.Success(
                        org.wip.plugintoolkit.api.PluginResponse(
                            result = kotlinx.serialization.json.JsonObject(mapOf("result" to JsonPrimitive("finished")))
                        )
                    )
                } else {
                    ctx.onSignal { signal ->
                        if (signal == org.wip.plugintoolkit.api.PluginSignal.PAUSE) {
                            deferred1.complete(
                                org.wip.plugintoolkit.api.ExecutionResult.Paused(
                                    JsonPrimitive("saved-mid-state")
                                )
                            )
                        }
                    }
                    deferred1.await()
                }
            }

            // Flow: Input node (1) -> Capability Node (2) -> Output Node (3)
            val inputNode = createInputNode(1, "source", DataType.Primitive(PrimitiveType.STRING))
            val capNode = Node.CapabilityNode(
                id = 2,
                position = Offset.Zero,
                pluginInfo = org.wip.plugintoolkit.api.PluginInfo(
                    id = "test-plugin",
                    name = "Test Plugin",
                    version = "1.0.0",
                    description = "Test"
                ),
                capability = org.wip.plugintoolkit.api.Capability(
                    name = "cap",
                    description = "test cap",
                    returnType = DataType.Primitive(PrimitiveType.STRING)
                ),
                inputs = listOf(
                    InputPort(
                        id = "input",
                        name = "input",
                        dataType = DataType.Primitive(PrimitiveType.STRING)
                    )
                ),
                outputs = listOf(
                    OutputPort(
                        id = "result",
                        name = "result",
                        dataType = DataType.Primitive(PrimitiveType.STRING)
                    )
                )
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
            withTimeout(5000) {
                while (true) {
                    val activeJob = jobManager.jobs.value.find { it.id == job.id }
                    if (activeJob?.status == org.wip.plugintoolkit.features.job.model.JobStatus.Running) break
                    delay(10)
                }
            }

            // Call jobManager.pauseJob(job.id) to trigger the pause signal propagation
            jobManager.pauseJob(job.id)

            // Wait for flow job to transition to Paused status and save resumeState
            withTimeout(5000) {
                while (true) {
                    val activeJob = jobManager.jobs.value.find { it.id == job.id }
                    if (activeJob?.resumeState != null) break
                    delay(10)
                }
            }

            // Verify capability resume state is serialized
            val pausedJob =
                jobManager.jobs.value.find { it.id == job.id } ?: jobManager.endedJobs.value.first { it.id == job.id }
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
            withTimeout(2000) {
                while (jobManager.endedJobs.value.none { it.id == job.id }) {
                    delay(10)
                }
            }

            // Verify the capability received the saved resume state
            assertEquals(JsonPrimitive("saved-mid-state"), capabilityResumedWithState)

            val finalJob = jobManager.endedJobs.value.first { it.id == job.id }
            assertEquals(org.wip.plugintoolkit.features.job.model.JobStatus.Completed, finalJob.status)
            jobWorker.stop()
        } finally {
            stopKoin()
        }
    }

}
