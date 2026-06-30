package org.wip.plugintoolkit.features.job.logic

import kotlinx.coroutines.test.runTest
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobType
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LoopNodeExecutorsTest {

    @BeforeTest
    fun setup() {
        val appDataDir = "build/tmp/test_loop_nodes"
        val flowsDir = Path("$appDataDir/flows")
        if (!SystemFileSystem.exists(flowsDir)) {
            SystemFileSystem.createDirectories(flowsDir)
        }
        val dummyJson = """{"name":"dummy","nodes":[],"connections":[]}"""
        SystemFileSystem.sink(Path("$appDataDir/flows/dummy.json")).buffered().use {
            it.writeString(dummyJson)
        }
    }

    private class MockNodeExecutionContext(
        private val inputValues: Map<String, Any?>,
        override val resumeState: JsonElement? = null
    ) : NodeExecutionContext {
        override val node: Node.SystemNode = Node.SystemNode(
            id = 1,
            position = androidx.compose.ui.geometry.Offset.Zero,
            title = "Mock Loop",
            systemAction = "mock",
            inputs = emptyList(),
            outputs = emptyList()
        )
        override val job = BackgroundJob(
            "1",
            "job",
            JobType.Flow,
            pluginId = "system",
            capabilityName = "mock",
            parameters = emptyMap<String, JsonElement>()
        )
        override val appDataDir = "build/tmp/test_loop_nodes"
        override val runtimeInferredTypes = emptyMap<Pair<Long, String>, DataType>()

        val outputs = mutableMapOf<String, Any?>()
        var subflowCallCount = 0
        var subflowOutputsToReturn: List<Map<String, Any?>> = emptyList()
        var throwPauseOnCallIndex: Int? = null

        override fun getInputValue(portId: String, defaultValue: Any?): Any? {
            return inputValues[portId] ?: defaultValue
        }

        override fun setOutputValue(portId: String, value: Any?) {
            outputs[portId] = value
        }

        override fun addLog(message: String, level: String) {}

        override suspend fun executeSubFlow(flowName: String, parameters: Map<String, JsonElement>): Map<String, Any?> {
            if (throwPauseOnCallIndex == subflowCallCount) {
                throw PauseFlowException(JsonPrimitive("paused-at-$subflowCallCount"))
            }
            val res = subflowOutputsToReturn.getOrNull(subflowCallCount) ?: emptyMap()
            subflowCallCount++
            return res
        }
    }

    @Test
    fun testForNodeExecution() = runTest {
        val context = MockNodeExecutionContext(
            inputValues = mapOf("start" to 0, "end" to 3, "step" to 1, "subflow_name" to "dummy", "input_data" to 0)
        )
        context.subflowOutputsToReturn = listOf(
            mapOf("output_data" to 1),
            mapOf("output_data" to 2),
            mapOf("output_data" to 3)
        )

        val executor = ForNodeExecutor()
        executor.execute(context)

        assertEquals(3, context.subflowCallCount)
        assertEquals(3, context.outputs["output_data"])
    }

    @Test
    fun testForNodePauseAndResume() = runTest {
        // Run first part until pause
        val context1 = MockNodeExecutionContext(
            inputValues = mapOf("start" to 0, "end" to 3, "step" to 1, "subflow_name" to "dummy", "input_data" to 0)
        )
        context1.throwPauseOnCallIndex = 1 // pause on i=1
        context1.subflowOutputsToReturn = listOf(mapOf("output_data" to 10))

        val executor = ForNodeExecutor()

        val pauseException = assertFailsWith<PauseFlowException> {
            executor.execute(context1)
        }

        val state = pauseException.resumeState as kotlinx.serialization.json.JsonObject
        assertEquals(1, state["index"]?.let { it as? JsonPrimitive }?.content?.toIntOrNull())
        assertEquals(10, state["accumulator"]?.let { it as? JsonPrimitive }?.content?.toIntOrNull())

        // Resume
        val context2 = MockNodeExecutionContext(
            inputValues = mapOf("start" to 0, "end" to 3, "step" to 1, "subflow_name" to "dummy", "input_data" to 0),
            resumeState = state
        )
        // Note: subflowCallCount starts from 0 for the resumed context
        context2.subflowOutputsToReturn = listOf(
            mapOf("output_data" to 20),
            mapOf("output_data" to 30)
        )

        executor.execute(context2)

        assertEquals(2, context2.subflowCallCount) // Should have called for i=1 and i=2
        assertEquals(30, context2.outputs["output_data"])
    }
}
