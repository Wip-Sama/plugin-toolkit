package org.wip.plugintoolkit.features.job.logic

import kotlinx.coroutines.test.runTest
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.model.Offset
import org.wip.plugintoolkit.features.flows.model.OutputPort
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
        override val resumeState: JsonElement? = null,
        override val currentFlow: Flow? = null,
        override val pluginEnums: Map<String, List<String>> = emptyMap(),
        override val node: Node.SystemNode = Node.SystemNode(
            id = 1,
            position = Offset.Zero,
            title = "Mock Loop",
            systemAction = "mock",
            inputs = emptyList(),
            outputs = emptyList()
        )
    ) : NodeExecutionContext {

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
        val executedNodeIds = mutableSetOf<Long>()
        var subflowCallCount = 0
        var subflowOutputsToReturn: List<Map<String, Any?>> = emptyList()
        var throwPauseOnCallIndex: Int? = null

        var dynamicSubflowCallCount = 0
        var dynamicSubflowOutputsToReturn: List<Map<String, Any?>> = emptyList()
        val capturedDynamicSubflows = mutableListOf<Flow>()
        val capturedDynamicParameters = mutableListOf<Map<String, JsonElement>>()

        override fun getInputValue(portId: String, defaultValue: Any?): Any? {
            return inputValues[portId] ?: defaultValue
        }

        override fun setOutputValue(portId: String, value: Any?) {
            outputs[portId] = value
        }

        override fun addLog(message: String, level: String) {}

        override fun markNodesExecuted(nodeIds: Set<Long>) {
            executedNodeIds.addAll(nodeIds)
        }

        override suspend fun executeDynamicSubFlow(flow: Flow, parameters: Map<String, JsonElement>): Map<String, Any?> {
            capturedDynamicSubflows.add(flow)
            capturedDynamicParameters.add(parameters)
            val res = dynamicSubflowOutputsToReturn.getOrNull(dynamicSubflowCallCount) ?: emptyMap()
            dynamicSubflowCallCount++
            return res
        }

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
    fun testForNodeExecutionWithSubflow() = runTest {
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
        val context1 = MockNodeExecutionContext(
            inputValues = mapOf("start" to 0, "end" to 3, "step" to 1, "subflow_name" to "dummy", "input_data" to 0)
        )
        context1.throwPauseOnCallIndex = 1
        context1.subflowOutputsToReturn = listOf(mapOf("output_data" to 10))

        val executor = ForNodeExecutor()

        val pauseException = assertFailsWith<PauseFlowException> {
            executor.execute(context1)
        }

        val state = pauseException.resumeState as kotlinx.serialization.json.JsonObject
        assertEquals(1, state["index"]?.let { it as? JsonPrimitive }?.content?.toIntOrNull())
        assertEquals(10, state["accumulator"]?.let { it as? JsonPrimitive }?.content?.toIntOrNull())

        val context2 = MockNodeExecutionContext(
            inputValues = mapOf("start" to 0, "end" to 3, "step" to 1, "subflow_name" to "dummy", "input_data" to 0),
            resumeState = state
        )
        context2.subflowOutputsToReturn = listOf(
            mapOf("output_data" to 20),
            mapOf("output_data" to 30)
        )

        executor.execute(context2)

        assertEquals(2, context2.subflowCallCount)
        assertEquals(30, context2.outputs["output_data"])
    }

    @Test
    fun testForNodeIteratingEnumVariantsInFlow() = runTest {
        val forNode = Node.SystemNode(
            id = 1L,
            position = Offset.Zero,
            title = "For Node",
            systemAction = "for",
            inputs = emptyList(),
            outputs = listOf(
                OutputPort("item", "item", DataType.Primitive(org.wip.plugintoolkit.api.PrimitiveType.ANY)),
                OutputPort("output_data", "output_data", DataType.Primitive(org.wip.plugintoolkit.api.PrimitiveType.ANY))
            )
        )
        val downstreamNode = Node.SystemNode(
            id = 2L,
            position = Offset.Zero,
            title = "Process Variant",
            systemAction = "log",
            inputs = emptyList(),
            outputs = listOf(OutputPort("output", "output", DataType.Primitive(org.wip.plugintoolkit.api.PrimitiveType.ANY)))
        )
        val testFlow = Flow(
            name = "EnumIterationTestFlow",
            nodes = listOf(forNode, downstreamNode),
            connections = listOf(
                Connection(
                    sourceNodeId = 1L,
                    sourcePortId = "item",
                    targetNodeId = 2L,
                    targetPortId = "message"
                )
            )
        )

        val context = MockNodeExecutionContext(
            inputValues = mapOf(
                "enum_name" to "AIModelArchitecture",
                "subflow_name" to ""
            ),
            currentFlow = testFlow,
            pluginEnums = mapOf(
                "AIModelArchitecture" to listOf("FAST_INFERENCE", "HIGH_PRECISION", "CUSTOM_CHECKPOINT")
            )
        )
        context.dynamicSubflowOutputsToReturn = listOf(
            mapOf("output" to "Result for FAST_INFERENCE"),
            mapOf("output" to "Result for HIGH_PRECISION"),
            mapOf("output" to "Result for CUSTOM_CHECKPOINT")
        )

        val executor = ForNodeExecutor()
        executor.execute(context)

        assertEquals(3, context.dynamicSubflowCallCount)
        assertEquals(
            listOf("Result for FAST_INFERENCE", "Result for HIGH_PRECISION", "Result for CUSTOM_CHECKPOINT"),
            context.outputs["output_data"]
        )
        assertEquals(setOf(2L), context.executedNodeIds)
    }

    @Test
    fun testForNodeIteratingListItemsInFlow() = runTest {
        val forNode = Node.SystemNode(
            id = 10L,
            position = Offset.Zero,
            title = "For Node",
            systemAction = "for",
            inputs = emptyList(),
            outputs = listOf(OutputPort("item", "item", DataType.Primitive(org.wip.plugintoolkit.api.PrimitiveType.ANY)))
        )
        val targetNode = Node.SystemNode(
            id = 20L,
            position = Offset.Zero,
            title = "Process Item",
            systemAction = "log",
            inputs = emptyList(),
            outputs = emptyList()
        )
        val testFlow = Flow(
            name = "ListIterationTestFlow",
            nodes = listOf(forNode, targetNode),
            connections = listOf(
                Connection(sourceNodeId = 10L, sourcePortId = "item", targetNodeId = 20L, targetPortId = "data")
            )
        )

        val context = MockNodeExecutionContext(
            inputValues = mapOf(
                "items" to listOf("Item1", "Item2"),
                "subflow_name" to ""
            ),
            currentFlow = testFlow,
            node = forNode
        )
        context.dynamicSubflowOutputsToReturn = listOf(
            mapOf("output_data" to "Processed: Item1"),
            mapOf("output_data" to "Processed: Item2")
        )

        val executor = ForNodeExecutor()
        executor.execute(context)

        assertEquals(2, context.dynamicSubflowCallCount)
        assertEquals(listOf("Processed: Item1", "Processed: Item2"), context.outputs["output_data"])
        assertEquals("Item2", context.outputs["item"])
    }

    @Test
    fun testConditionalNodeWithExpectedValueMatch() = runTest {
        val context = MockNodeExecutionContext(
            inputValues = mapOf(
                "condition" to "FAST_INFERENCE",
                "expected_value" to "FAST_INFERENCE",
                "input_data" to "Payload"
            )
        )
        val executor = ConditionalNodeExecutor()
        executor.execute(context)

        assertEquals("Payload", context.outputs["if_true"])
        assertEquals(null, context.outputs["if_false"])
    }

    @Test
    fun testConditionalNodeWithExpectedValueMismatch() = runTest {
        val context = MockNodeExecutionContext(
            inputValues = mapOf(
                "condition" to "HIGH_PRECISION",
                "expected_value" to "FAST_INFERENCE",
                "input_data" to "Payload"
            )
        )
        val executor = ConditionalNodeExecutor()
        executor.execute(context)

        assertEquals(null, context.outputs["if_true"])
        assertEquals("Payload", context.outputs["if_false"])
    }
}
