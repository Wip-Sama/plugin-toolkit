package org.wip.plugintoolkit.features.flows.model

import androidx.compose.ui.geometry.Offset
import org.wip.plugintoolkit.api.Capability
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.ParameterMetadata
import org.wip.plugintoolkit.api.PluginInfo
import org.wip.plugintoolkit.api.PrimitiveType
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NodeModelsTest {

    @Test
    fun testCapabilityNodeIsReadyWithDefaultValue() {
        val pluginInfo = PluginInfo("test_plugin", "Test", "1.0", "Test plugin")
        val capability = Capability(
            name = "TestCapability",
            description = "Test",
            returnType = DataType.Primitive(PrimitiveType.STRING),
            parameters = mapOf(
                "param1" to ParameterMetadata(
                    description = "A required parameter",
                    type = DataType.Primitive(PrimitiveType.STRING),
                    required = true
                )
            )
        )

        // Case 1: No value, no defaultValue -> Not ready
        val inputPortNotReady = InputPort(
            id = "param1",
            name = "Param 1",
            dataType = DataType.Primitive(PrimitiveType.STRING),
            value = null,
            defaultValue = null
        )
        val node1 = Node.CapabilityNode(
            id = 1L,
            position = Offset.Zero,
            pluginInfo = pluginInfo,
            capability = capability,
            inputs = listOf(inputPortNotReady),
            outputs = emptyList()
        )
        assertFalse(node1.isReady(emptyList()))

        // Case 2: defaultValue provided -> Ready
        val inputPortReadyDefault = InputPort(
            id = "param1",
            name = "Param 1",
            dataType = DataType.Primitive(PrimitiveType.STRING),
            value = null,
            defaultValue = "default_value"
        )
        val node2 = Node.CapabilityNode(
            id = 2L,
            position = Offset.Zero,
            pluginInfo = pluginInfo,
            capability = capability,
            inputs = listOf(inputPortReadyDefault),
            outputs = emptyList()
        )
        assertTrue(node2.isReady(emptyList()))

        // Case 3: value provided -> Ready
        val inputPortReadyValue = InputPort(
            id = "param1",
            name = "Param 1",
            dataType = DataType.Primitive(PrimitiveType.STRING),
            value = "actual_value",
            defaultValue = null
        )
        val node3 = Node.CapabilityNode(
            id = 3L,
            position = Offset.Zero,
            pluginInfo = pluginInfo,
            capability = capability,
            inputs = listOf(inputPortReadyValue),
            outputs = emptyList()
        )
        assertTrue(node3.isReady(emptyList()))
    }
}
