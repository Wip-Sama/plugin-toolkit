package org.wip.plugintoolkit.features.flows

import kotlinx.serialization.json.JsonPrimitive
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.features.flows.logic.SystemNodesRegistry
import org.wip.plugintoolkit.features.flows.model.InputPort
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.model.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SystemNodesRegistryTest {

    @Test
    fun testErrorNodeConfiguration() {
        val inputs = SystemNodesRegistry.getInputs("error")
        assertEquals(2, inputs.size, "Error node should have exactly two inputs")

        val messagePort = inputs.find { it.id == "message" }
        val dataPort = inputs.find { it.id == "data" }

        assertNotNull(messagePort, "Error node should have a 'message' input port")
        assertNotNull(dataPort, "Error node should have a 'data' input port")

        assertEquals<DataType>(
            DataType.Primitive(PrimitiveType.STRING),
            messagePort.dataType,
            "Message input should be STRING"
        )
        assertEquals(
            "An error occurred during flow execution",
            messagePort.defaultValue,
            "Message default value matches"
        )

        assertEquals<DataType>(DataType.Primitive(PrimitiveType.ANY), dataPort.dataType, "Data input should be ANY")
    }

    @Test
    fun testConvertNodeConfiguration() {
        val inputs = SystemNodesRegistry.getInputs("convert")
        val targetTypePort = inputs.find { it.id == "target_type" }
        assertNotNull(targetTypePort, "Convert node should have target_type input")
        assertTrue(targetTypePort.dataType is DataType.Enum)
        val enumType = targetTypePort.dataType as DataType.Enum
        assertTrue(enumType.options.contains("STRING"))
        assertTrue(enumType.options.contains("INT"))
        assertTrue(enumType.options.contains("DOUBLE"))
        assertTrue(enumType.options.contains("BOOLEAN"))
        assertTrue(enumType.options.contains("AUTO"))
        assertEquals("AUTO", targetTypePort.defaultValue)
    }

    @Test
    fun testStringMergerNodeConfiguration() {
        val inputs = SystemNodesRegistry.getInputs("string_merger")
        val outputs = SystemNodesRegistry.getOutputs("string_merger")

        assertNotNull(inputs.find { it.id == "strings" })
        assertNotNull(inputs.find { it.id == "separator" })
        assertNotNull(inputs.find { it.id == "prefix" })
        assertNotNull(inputs.find { it.id == "postfix" })

        val outPort = outputs.find { it.id == "output" }
        assertNotNull(outPort)
        assertEquals(DataType.Primitive(PrimitiveType.STRING), outPort.dataType)
    }

    @Test
    fun testConditionalNodeConfiguration() {
        val inputs = SystemNodesRegistry.getInputs("conditional")
        assertNotNull(inputs.find { it.id == "condition" })
        assertNotNull(inputs.find { it.id == "expected_value" })
        assertNotNull(inputs.find { it.id == "input_data" })

        val outputs = SystemNodesRegistry.getOutputs("conditional")
        assertNotNull(outputs.find { it.id == "if_true" })
        assertNotNull(outputs.find { it.id == "if_false" })
    }

    @Test
    fun testForNodeConfiguration() {
        val inputs = SystemNodesRegistry.getInputs("for")
        assertNotNull(inputs.find { it.id == "enum_name" })
        assertNotNull(inputs.find { it.id == "items" })
        assertNotNull(inputs.find { it.id == "start" })
        assertNotNull(inputs.find { it.id == "end" })
        assertNotNull(inputs.find { it.id == "step" })

        val outputs = SystemNodesRegistry.getOutputs("for")
        assertNotNull(outputs.find { it.id == "item" })
        assertNotNull(outputs.find { it.id == "index" })
        assertNotNull(outputs.find { it.id == "output_data" })
    }

    @Test
    fun testPropagateTypesConvertExplicit() {
        val node = Node.SystemNode(
            id = 50L,
            position = Offset.Zero,
            title = "Convert",
            systemAction = "convert",
            inputs = listOf(
                InputPort("input_data", "Input", DataType.Primitive(PrimitiveType.ANY)),
                InputPort("target_type", "Target Type", DataType.Enum("TargetType", listOf("INT")), value = JsonPrimitive("INT"))
            ),
            outputs = SystemNodesRegistry.getOutputs("convert")
        )
        val inferred = mutableMapOf<Pair<Long, String>, DataType>()
        val changed = SystemNodesRegistry.propagateTypes(node, inferred)
        assertTrue(changed)
        assertEquals(DataType.Primitive(PrimitiveType.INT), inferred[Pair(50L, "output_data")])
    }

    @Test
    fun testPropagateTypesStringMerger() {
        val node = Node.SystemNode(
            id = 60L,
            position = Offset.Zero,
            title = "String Merger",
            systemAction = "string_merger",
            inputs = SystemNodesRegistry.getInputs("string_merger"),
            outputs = SystemNodesRegistry.getOutputs("string_merger")
        )
        val inferred = mutableMapOf<Pair<Long, String>, DataType>()
        val changed = SystemNodesRegistry.propagateTypes(node, inferred)
        assertTrue(changed)
        assertEquals(DataType.Primitive(PrimitiveType.STRING), inferred[Pair(60L, "output")])
    }

    @Test
    fun testExtractFromStringNodeConfiguration() {
        val inputs = SystemNodesRegistry.getInputs("extract_from_string")
        val outputs = SystemNodesRegistry.getOutputs("extract_from_string")

        assertNotNull(inputs.find { it.id == "string" })
        assertNotNull(inputs.find { it.id == "regex" })
        assertNotNull(inputs.find { it.id == "group_index" })

        val outPort = outputs.find { it.id == "output" }
        assertNotNull(outPort)
        assertEquals(DataType.Array(DataType.Primitive(PrimitiveType.STRING)), outPort.dataType)
    }

    @Test
    fun testListFilterNodeConfiguration() {
        val inputs = SystemNodesRegistry.getInputs("list_filter")
        val outputs = SystemNodesRegistry.getOutputs("list_filter")

        assertNotNull(inputs.find { it.id == "items" })
        assertNotNull(inputs.find { it.id == "pattern" })

        val outPort = outputs.find { it.id == "output" }
        assertNotNull(outPort)
        assertEquals(DataType.Array(DataType.Primitive(PrimitiveType.ANY)), outPort.dataType)
    }

    @Test
    fun testListCheckNodeConfiguration() {
        val inputs = SystemNodesRegistry.getInputs("list_check")
        val outputs = SystemNodesRegistry.getOutputs("list_check")

        assertNotNull(inputs.find { it.id == "items" })
        assertNotNull(inputs.find { it.id == "min_length" })
        assertNotNull(inputs.find { it.id == "max_length" })

        val outPort = outputs.find { it.id == "output" }
        val resultPort = outputs.find { it.id == "result" }
        assertNotNull(outPort)
        assertNotNull(resultPort)
        assertEquals(DataType.Array(DataType.Primitive(PrimitiveType.ANY)), outPort.dataType)
        assertEquals(DataType.Primitive(PrimitiveType.BOOLEAN), resultPort.dataType)
    }

    @Test
    fun testPropagateTypesExtractFromString() {
        val node = Node.SystemNode(
            id = 70L,
            position = Offset.Zero,
            title = "Extract from String",
            systemAction = "extract_from_string",
            inputs = SystemNodesRegistry.getInputs("extract_from_string"),
            outputs = SystemNodesRegistry.getOutputs("extract_from_string")
        )
        val inferred = mutableMapOf<Pair<Long, String>, DataType>()
        val changed = SystemNodesRegistry.propagateTypes(node, inferred)
        assertTrue(changed)
        assertEquals(DataType.Array(DataType.Primitive(PrimitiveType.STRING)), inferred[Pair(70L, "output")])
    }

    @Test
    fun testPropagateTypesListFilterAndCheck() {
        val filterNode = Node.SystemNode(
            id = 80L,
            position = Offset.Zero,
            title = "Lists Filter",
            systemAction = "list_filter",
            inputs = SystemNodesRegistry.getInputs("list_filter"),
            outputs = SystemNodesRegistry.getOutputs("list_filter")
        )
        val checkNode = Node.SystemNode(
            id = 81L,
            position = Offset.Zero,
            title = "List Check",
            systemAction = "list_check",
            inputs = SystemNodesRegistry.getInputs("list_check"),
            outputs = SystemNodesRegistry.getOutputs("list_check")
        )

        val inferred = mutableMapOf<Pair<Long, String>, DataType>(
            Pair(80L, "items") to DataType.Array(DataType.Primitive(PrimitiveType.INT)),
            Pair(81L, "items") to DataType.Array(DataType.Primitive(PrimitiveType.STRING))
        )

        val changedFilter = SystemNodesRegistry.propagateTypes(filterNode, inferred)
        assertTrue(changedFilter)
        assertEquals(DataType.Array(DataType.Primitive(PrimitiveType.INT)), inferred[Pair(80L, "output")])

        val changedCheck = SystemNodesRegistry.propagateTypes(checkNode, inferred)
        assertTrue(changedCheck)
        assertEquals(DataType.Array(DataType.Primitive(PrimitiveType.STRING)), inferred[Pair(81L, "output")])
    }
}

