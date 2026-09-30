package org.wip.plugintoolkit.features.job.logic

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.model.Offset
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DataNodeExecutorsTest {

    private class MockContext(
        private val inputs: Map<String, Any?>,
        override val runtimeInferredTypes: Map<Pair<Long, String>, DataType> = emptyMap()
    ) : NodeExecutionContext {
        override val node: Node.SystemNode = Node.SystemNode(
            id = 1L,
            position = Offset.Zero,
            title = "Test",
            systemAction = "test",
            inputs = emptyList(),
            outputs = emptyList()
        )
        override val job: BackgroundJob = BackgroundJob(
            id = "test-job",
            name = "Test Job",
            type = JobType.Flow,
            pluginId = "system",
            capabilityName = "test",
            parameters = emptyMap<String, JsonElement>()
        )
        override val appDataDir: String = "/tmp"
        override val resumeState: JsonElement? = null
        val outputs = mutableMapOf<String, Any?>()

        override fun getInputValue(portId: String, defaultValue: Any?): Any? = inputs[portId] ?: defaultValue
        override fun setOutputValue(portId: String, value: Any?) { outputs[portId] = value }
        override fun addLog(message: String, level: String) {}
        override suspend fun executeSubFlow(flowName: String, parameters: Map<String, JsonElement>): Map<String, Any?> = emptyMap()
    }

    @Test
    fun testConvertNodeWithExplicitTargetTypeInt() = runTest {
        val context = MockContext(
            inputs = mapOf(
                "input_data" to "123",
                "target_type" to "INT"
            )
        )
        val executor = ConvertNodeExecutor()
        executor.execute(context)

        assertEquals(true, context.outputs["success"])
        assertEquals(123, context.outputs["output_data"])
    }

    @Test
    fun testConvertNodeWithExplicitTargetTypeDouble() = runTest {
        val context = MockContext(
            inputs = mapOf(
                "input_data" to "3.1415",
                "target_type" to "DOUBLE"
            )
        )
        val executor = ConvertNodeExecutor()
        executor.execute(context)

        assertEquals(true, context.outputs["success"])
        assertEquals(3.1415, context.outputs["output_data"])
    }

    @Test
    fun testConvertNodeWithExplicitTargetTypeBoolean() = runTest {
        val context = MockContext(
            inputs = mapOf(
                "input_data" to "true",
                "target_type" to "BOOLEAN"
            )
        )
        val executor = ConvertNodeExecutor()
        executor.execute(context)

        assertEquals(true, context.outputs["success"])
        assertEquals(true, context.outputs["output_data"])
    }

    @Test
    fun testConvertNodeWithAutoFallbackToRuntimeInferred() = runTest {
        val context = MockContext(
            inputs = mapOf(
                "input_data" to 999,
                "target_type" to "AUTO"
            ),
            runtimeInferredTypes = mapOf(Pair(1L, "output_data") to DataType.Primitive(PrimitiveType.STRING))
        )
        val executor = ConvertNodeExecutor()
        executor.execute(context)

        assertEquals(true, context.outputs["success"])
        assertEquals("999", context.outputs["output_data"])
    }

    @Test
    fun testStringMergerNodeBasic() = runTest {
        val context = MockContext(
            inputs = mapOf(
                "strings" to listOf("Alpha", "Beta", "Gamma"),
                "separator" to ", ",
                "prefix" to "<",
                "postfix" to ">"
            )
        )
        val executor = StringMergerNodeExecutor()
        executor.execute(context)

        assertEquals("<Alpha, Beta, Gamma>", context.outputs["output"])
        assertEquals("<Alpha, Beta, Gamma>", context.outputs["output_data"])
    }

    @Test
    fun testStringMergerNodeWithSet() = runTest {
        val context = MockContext(
            inputs = mapOf(
                "strings" to linkedSetOf("First", "Second"),
                "separator" to " -> "
            )
        )
        val executor = StringMergerNodeExecutor()
        executor.execute(context)

        assertEquals("First -> Second", context.outputs["output"])
    }

    @Test
    fun testMergerNodeWithIterableAndSet() = runTest {
        val context = MockContext(
            inputs = mapOf(
                "list1" to setOf("A", "B"),
                "list2" to listOf("C", "D")
            )
        )
        val executor = MergerNodeExecutor()
        executor.execute(context)

        val result = context.outputs["output"] as? List<*>
        assertTrue(result != null)
        assertEquals(listOf("A", "B", "C", "D"), result)
    }

    @Test
    fun testExtractFromStringNodeWithoutGroups() = runTest {
        val context = MockContext(
            inputs = mapOf(
                "string" to "E:\\Manga\\The Beginning After The End\\s2\\244",
                "regex" to "[\\d]+"
            )
        )
        val executor = ExtractFromStringNodeExecutor()
        executor.execute(context)

        assertEquals(listOf("2", "244"), context.outputs["output"])
        assertEquals(listOf("2", "244"), context.outputs["matches"])
    }

    @Test
    fun testExtractFromStringNodeWithCaptureGroup() = runTest {
        val context = MockContext(
            inputs = mapOf(
                "string" to "Chapter 244 and Season 2",
                "regex" to "(?:Chapter|Season)\\s+(\\d+)"
            )
        )
        val executor = ExtractFromStringNodeExecutor()
        executor.execute(context)

        // Capture group present without group_index extracts group 1
        assertEquals(listOf("244", "2"), context.outputs["output"])
    }

    @Test
    fun testExtractFromStringNodeWithExplicitGroupIndex() = runTest {
        val context = MockContext(
            inputs = mapOf(
                "string" to "item:apple cost:5 item:orange cost:10",
                "regex" to "item:(\\w+)\\s+cost:(\\d+)",
                "group_index" to 2
            )
        )
        val executor = ExtractFromStringNodeExecutor()
        executor.execute(context)

        assertEquals(listOf("5", "10"), context.outputs["output"])
    }

    @Test
    fun testExtractFromStringNodeInvalidRegex() = runTest {
        val context = MockContext(
            inputs = mapOf(
                "string" to "Some text",
                "regex" to "[unclosed"
            )
        )
        val executor = ExtractFromStringNodeExecutor()
        executor.execute(context)

        assertEquals(emptyList<String>(), context.outputs["output"])
    }

    @Test
    fun testListFilterSingleIndex() = runTest {
        val items = listOf("zero", "one", "two", "three", "four")

        // Last element using -1
        val contextLast = MockContext(inputs = mapOf("items" to items, "pattern" to "-1"))
        ListFilterNodeExecutor().execute(contextLast)
        assertEquals(listOf("four"), contextLast.outputs["output"])

        // Last element with brackets [-1]
        val contextLastBrackets = MockContext(inputs = mapOf("items" to items, "pattern" to "[-1]"))
        ListFilterNodeExecutor().execute(contextLastBrackets)
        assertEquals(listOf("four"), contextLastBrackets.outputs["output"])

        // First element
        val contextFirst = MockContext(inputs = mapOf("items" to items, "pattern" to "0"))
        ListFilterNodeExecutor().execute(contextFirst)
        assertEquals(listOf("zero"), contextFirst.outputs["output"])

        // Out of bounds index
        val contextOob = MockContext(inputs = mapOf("items" to items, "pattern" to "99"))
        ListFilterNodeExecutor().execute(contextOob)
        assertEquals(emptyList<Any?>(), contextOob.outputs["output"])
    }

    @Test
    fun testListFilterPythonSlices() = runTest {
        val items = listOf(0, 1, 2, 3, 4)

        // Range slice 1:3
        val ctxRange = MockContext(inputs = mapOf("items" to items, "pattern" to "1:3"))
        ListFilterNodeExecutor().execute(ctxRange)
        assertEquals(listOf(1, 2), ctxRange.outputs["output"])

        // Prefix slice :2
        val ctxPrefix = MockContext(inputs = mapOf("items" to items, "pattern" to ":2"))
        ListFilterNodeExecutor().execute(ctxPrefix)
        assertEquals(listOf(0, 1), ctxPrefix.outputs["output"])

        // Suffix slice 3:
        val ctxSuffix = MockContext(inputs = mapOf("items" to items, "pattern" to "3:"))
        ListFilterNodeExecutor().execute(ctxSuffix)
        assertEquals(listOf(3, 4), ctxSuffix.outputs["output"])

        // Step ::2
        val ctxStep = MockContext(inputs = mapOf("items" to items, "pattern" to "::2"))
        ListFilterNodeExecutor().execute(ctxStep)
        assertEquals(listOf(0, 2, 4), ctxStep.outputs["output"])

        // Reverse ::-1
        val ctxReverse = MockContext(inputs = mapOf("items" to items, "pattern" to "::-1"))
        ListFilterNodeExecutor().execute(ctxReverse)
        assertEquals(listOf(4, 3, 2, 1, 0), ctxReverse.outputs["output"])

        // Negative bounds -3:-1
        val ctxNegBounds = MockContext(inputs = mapOf("items" to items, "pattern" to "-3:-1"))
        ListFilterNodeExecutor().execute(ctxNegBounds)
        assertEquals(listOf(2, 3), ctxNegBounds.outputs["output"])

        // Negative step slice 4:1:-1
        val ctxNegStep = MockContext(inputs = mapOf("items" to items, "pattern" to "4:1:-1"))
        ListFilterNodeExecutor().execute(ctxNegStep)
        assertEquals(listOf(4, 3, 2), ctxNegStep.outputs["output"])
    }

    @Test
    fun testListCheckMinMaxBounds() = runTest {
        val items = listOf("A", "B", "C")

        // Passing min check
        val ctxMinPass = MockContext(inputs = mapOf("items" to items, "min_length" to 2))
        ListCheckNodeExecutor().execute(ctxMinPass)
        assertEquals(true, ctxMinPass.outputs["result"])
        assertEquals(listOf("A", "B", "C"), ctxMinPass.outputs["output"])

        // Failing min check
        val ctxMinFail = MockContext(inputs = mapOf("items" to items, "min_length" to 5))
        ListCheckNodeExecutor().execute(ctxMinFail)
        assertEquals(false, ctxMinFail.outputs["result"])
        assertEquals(emptyList<Any?>(), ctxMinFail.outputs["output"])

        // Passing max check
        val ctxMaxPass = MockContext(inputs = mapOf("items" to items, "max_length" to 4))
        ListCheckNodeExecutor().execute(ctxMaxPass)
        assertEquals(true, ctxMaxPass.outputs["result"])
        assertEquals(listOf("A", "B", "C"), ctxMaxPass.outputs["output"])

        // Failing max check
        val ctxMaxFail = MockContext(inputs = mapOf("items" to items, "max_length" to 2))
        ListCheckNodeExecutor().execute(ctxMaxFail)
        assertEquals(false, ctxMaxFail.outputs["result"])
        assertEquals(emptyList<Any?>(), ctxMaxFail.outputs["output"])

        // Both min and max within bounds
        val ctxBothPass = MockContext(inputs = mapOf("items" to items, "min_length" to 2, "max_length" to 4))
        ListCheckNodeExecutor().execute(ctxBothPass)
        assertEquals(true, ctxBothPass.outputs["result"])
        assertEquals(listOf("A", "B", "C"), ctxBothPass.outputs["output"])
    }
}

