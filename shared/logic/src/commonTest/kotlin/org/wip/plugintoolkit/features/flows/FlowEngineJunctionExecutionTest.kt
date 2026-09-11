package org.wip.plugintoolkit.features.flows

import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.FlowJunction
import org.wip.plugintoolkit.features.flows.model.Offset
import org.wip.plugintoolkit.features.job.logic.DefaultSystemNodeExecutorRegistry
import org.wip.plugintoolkit.features.job.logic.FlowEngine
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobType
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository
import kotlin.test.Test
import kotlin.test.assertEquals

class FlowEngineJunctionExecutionTest : JobWorkerFlowTestBase() {

    @Test
    fun testFlowEngineExecutesJunctionBranchedConnections() = runTest {
        val persistence = FakeSettingsPersistence()
        val settingsRepo = SettingsRepository(persistence, backgroundScope)
        settingsRepo.updateSettings { persistence.settings }
        val jobManager = JobManager(backgroundScope, settingsRepo)

        // Flow: Input node (1) -> Junction (100) -> Output 1 (2) & Output 2 (3)
        val inputNode = createInputNode(1, "message", DataType.Primitive(PrimitiveType.STRING))
        val outputNode1 = createOutputNode(2, "result1", DataType.Primitive(PrimitiveType.STRING))
        val outputNode2 = createOutputNode(3, "result2", DataType.Primitive(PrimitiveType.STRING))

        // Semantic connections with junctionIds
        val branch1 = Connection(
            sourceNodeId = 1L,
            sourcePortId = "output_data",
            targetNodeId = 2L,
            targetPortId = "input_data",
            junctionIds = listOf(100L)
        )
        val branch2 = Connection(
            sourceNodeId = 1L,
            sourcePortId = "output_data",
            targetNodeId = 3L,
            targetPortId = "input_data",
            junctionIds = listOf(100L)
        )
        // Also add a harmless floating wire to verify it doesn't break execution
        val floating = Connection.createFloating(
            sourceNodeId = 1L,
            sourcePortId = "output_data",
            floatingTarget = Offset(200f, 300f)
        )

        val flow = Flow(
            name = "test_junction_exec",
            nodes = listOf(inputNode, outputNode1, outputNode2),
            junctions = listOf(FlowJunction(100L, Offset(100f, 100f))),
            connections = listOf(branch1, branch2, floating)
        )

        val job = BackgroundJob(
            id = "test-job-junction",
            name = "Junction Job",
            type = JobType.Flow,
            pluginId = "system",
            capabilityName = "test_junction_exec",
            parameters = mapOf("1" to JsonPrimitive("Hello Junction!"))
        )

        val outputs = FlowEngine(
            jobManager,
            DefaultSystemNodeExecutorRegistry(mockk(relaxed = true)),
            mockk(relaxed = true),
            mockk(relaxed = true),
            backgroundScope
        ).executeSubFlowRecursively(flow, job, "/tmp")

        assertEquals("Hello Junction!", outputs["result1"])
        assertEquals("Hello Junction!", outputs["result2"])
    }

    @Test
    fun testFlowEngineExecutesSegmentedJunctionConnections() = runTest {
        val persistence = FakeSettingsPersistence()
        val settingsRepo = SettingsRepository(persistence, backgroundScope)
        settingsRepo.updateSettings { persistence.settings }
        val jobManager = JobManager(backgroundScope, settingsRepo)

        // Flow: Input node (1) -> J1 -> Output 1 (2) & Output 2 (3) via segmented wires
        val inputNode = createInputNode(1, "message", DataType.Primitive(PrimitiveType.STRING))
        val outputNode1 = createOutputNode(2, "resA", DataType.Primitive(PrimitiveType.STRING))
        val outputNode2 = createOutputNode(3, "resB", DataType.Primitive(PrimitiveType.STRING))

        val wireToJunction = Connection(
            sourceNodeId = 1L,
            sourcePortId = "output_data",
            targetNodeId = -1L,
            targetPortId = "",
            targetJunctionId = 100L
        )
        val wireFromJunctionTo2 = Connection(
            sourceNodeId = -1L,
            sourcePortId = "",
            sourceJunctionId = 100L,
            targetNodeId = 2L,
            targetPortId = "input_data"
        )
        val wireFromJunctionTo3 = Connection(
            sourceNodeId = -1L,
            sourcePortId = "",
            sourceJunctionId = 100L,
            targetNodeId = 3L,
            targetPortId = "input_data"
        )

        val flow = Flow(
            name = "test_segmented_junction_exec",
            nodes = listOf(inputNode, outputNode1, outputNode2),
            junctions = listOf(FlowJunction(100L, Offset(100f, 100f))),
            connections = listOf(wireToJunction, wireFromJunctionTo2, wireFromJunctionTo3)
        )

        val job = BackgroundJob(
            id = "test-job-segmented-junction",
            name = "Segmented Junction Job",
            type = JobType.Flow,
            pluginId = "system",
            capabilityName = "test_segmented_junction_exec",
            parameters = mapOf("1" to JsonPrimitive("Branch Works"))
        )

        val outputs = FlowEngine(
            jobManager,
            DefaultSystemNodeExecutorRegistry(mockk(relaxed = true)),
            mockk(relaxed = true),
            mockk(relaxed = true),
            backgroundScope
        ).executeSubFlowRecursively(flow, job, "/tmp")

        assertEquals("Branch Works", outputs["resA"])
        assertEquals("Branch Works", outputs["resB"])
    }
}
