package org.wip.plugintoolkit.features.flows

import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.features.flows.viewmodel.SystemNodesRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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
}
