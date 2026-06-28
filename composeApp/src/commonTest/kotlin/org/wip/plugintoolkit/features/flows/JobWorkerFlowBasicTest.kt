package org.wip.plugintoolkit.features.flows

import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.koin.core.context.stopKoin
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.job.logic.JobWorker
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobType
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

class JobWorkerFlowBasicTest : JobWorkerFlowTestBase() {

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
            .copyWithUpdatedInput("input_data", JsonPrimitive("123"))

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

        val outputs = org.wip.plugintoolkit.features.job.logic.FlowEngine(
            jobManager,
            org.wip.plugintoolkit.features.job.logic.DefaultSystemNodeExecutorRegistry(mockk(relaxed = true)),
            mockk(relaxed = true),
            mockk(relaxed = true),
            backgroundScope
        ).executeSubFlowRecursively(flow, job, "/tmp")

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
            .copyWithUpdatedInput("input_data", JsonPrimitive("invalid-int"))

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
        val outputs = org.wip.plugintoolkit.features.job.logic.FlowEngine(
            jobManager,
            org.wip.plugintoolkit.features.job.logic.DefaultSystemNodeExecutorRegistry(mockk(relaxed = true)),
            mockk(relaxed = true),
            mockk(relaxed = true),
            backgroundScope
        ).executeSubFlowRecursively(flow, job, "/tmp")

        // The target output should be null since conversion failed
        assertNull(outputs["result"])
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
            .copyWithUpdatedInput("message", JsonPrimitive("Execution failed"))

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
            org.wip.plugintoolkit.features.job.logic.FlowEngine(
                jobManager,
                org.wip.plugintoolkit.features.job.logic.DefaultSystemNodeExecutorRegistry(mockk(relaxed = true)),
                mockk(relaxed = true),
                mockk(relaxed = true),
                backgroundScope
            ).executeSubFlowRecursively(flow, job, "/tmp")
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

}
