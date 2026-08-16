package org.wip.plugintoolkit.features.flows

import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.job.logic.JobWorker
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobType
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository
import kotlin.test.Test
import kotlin.test.assertNotNull

class JobWorkerFlowConditionalTest : JobWorkerFlowTestBase() {

    @Test
    fun testConditionalNodeFiresOnlyOneBranch_True() = runTest {
        val persistence = FakeSettingsPersistence()
        val settingsRepo = SettingsRepository(persistence, backgroundScope)
        settingsRepo.updateSettings { persistence.settings }
        val jobManager = JobManager(backgroundScope, settingsRepo)
        val jobWorker = JobWorker(1, jobManager, backgroundScope)

        // Flow:
        // Input Node -> Conditional Node (condition = true)
        // Conditional if_true -> Log Node (success path)
        // Conditional if_false -> Error Node (should NOT be fired/executed)
        val inputNode = createInputNode(1, "data", DataType.Primitive(PrimitiveType.STRING))

        val condNode = createSystemNode(2, "conditional")
            .copyWithUpdatedInput("condition", JsonPrimitive(true))
            .copyWithUpdatedInput("input_data", JsonPrimitive("hello"))

        val logNode = createSystemNode(3, "log")
            .copyWithUpdatedInput("message", JsonPrimitive("Condition was true"))

        val errorNode = createSystemNode(4, "error")
            .copyWithUpdatedInput("message", JsonPrimitive("Error: should not be executed"))

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
        val outputs = org.wip.plugintoolkit.features.job.logic.FlowEngine(
            jobManager,
            org.wip.plugintoolkit.features.job.logic.DefaultSystemNodeExecutorRegistry(mockk(relaxed = true)),
            mockk(relaxed = true),
            mockk(relaxed = true),
            backgroundScope
        ).executeSubFlowRecursively(flow, job, "/tmp")

        // Flow should have completed without errors
        assertNotNull(outputs)
    }

    @Test
    fun testConditionalNodeFiresOnlyOneBranch_False() = runTest {
        val persistence = FakeSettingsPersistence()
        val settingsRepo = SettingsRepository(persistence, backgroundScope)
        settingsRepo.updateSettings { persistence.settings }
        val jobManager = JobManager(backgroundScope, settingsRepo)
        val jobWorker = JobWorker(1, jobManager, backgroundScope)

        // Flow:
        // Input Node -> Conditional Node (condition = false)
        // Conditional if_true -> Error Node (should NOT be executed)
        // Conditional if_false -> Log Node (success path)
        val inputNode = createInputNode(1, "data", DataType.Primitive(PrimitiveType.STRING))

        val condNode = createSystemNode(2, "conditional")
            .copyWithUpdatedInput("condition", JsonPrimitive(false))
            .copyWithUpdatedInput("input_data", JsonPrimitive("hello"))

        val errorNode = createSystemNode(3, "error")
            .copyWithUpdatedInput("message", JsonPrimitive("Error: should not be executed"))

        val logNode = createSystemNode(4, "log")
            .copyWithUpdatedInput("message", JsonPrimitive("Condition was false"))

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
        val outputs = org.wip.plugintoolkit.features.job.logic.FlowEngine(
            jobManager,
            org.wip.plugintoolkit.features.job.logic.DefaultSystemNodeExecutorRegistry(mockk(relaxed = true)),
            mockk(relaxed = true),
            mockk(relaxed = true),
            backgroundScope
        ).executeSubFlowRecursively(flow, job, "/tmp")

        assertNotNull(outputs)
    }

}
