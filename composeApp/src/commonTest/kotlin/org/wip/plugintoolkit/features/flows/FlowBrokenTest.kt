package org.wip.plugintoolkit.features.flows

import androidx.compose.ui.geometry.Offset
import org.wip.plugintoolkit.api.Capability
import org.wip.plugintoolkit.api.PluginInfo
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.Node
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FlowBrokenTest {

    private val dummyPluginInfo = PluginInfo(
        id = "test.plugin",
        name = "Test Plugin",
        version = "1.0",
        description = "Test plugin"
    )

    private val capabilityA = Capability(
        name = "Capability A",
        description = "Does A",
        returnType = org.wip.plugintoolkit.api.DataType.Primitive(org.wip.plugintoolkit.api.PrimitiveType.UNIT)
    )

    private val capabilityB = Capability(
        name = "Capability B",
        description = "Does B",
        returnType = org.wip.plugintoolkit.api.DataType.Primitive(org.wip.plugintoolkit.api.PrimitiveType.UNIT)
    )

    private fun createCapabilityNode(capability: Capability, isBroken: Boolean = false): Node.CapabilityNode {
        return Node.CapabilityNode(
            id = capability.name.hashCode().toLong(),
            position = Offset.Zero,
            pluginInfo = dummyPluginInfo,
            capability = capability,
            inputs = emptyList(),
            outputs = emptyList(),
            isBroken = isBroken
        )
    }

    @Test
    fun `test flow is not broken when all capabilities are active and no nodes are explicitly broken`() {
        val flow = Flow(
            name = "Test Flow",
            nodes = listOf(
                createCapabilityNode(capabilityA),
                createCapabilityNode(capabilityB)
            )
        )

        val activeCapabilities = setOf("Capability A", "Capability B")
        assertFalse(flow.isBroken(activeCapabilities), "Flow should not be broken")
    }

    @Test
    fun `test flow is broken when a capability is missing from active capabilities`() {
        val flow = Flow(
            name = "Test Flow",
            nodes = listOf(
                createCapabilityNode(capabilityA),
                createCapabilityNode(capabilityB)
            )
        )

        // Only Capability A is active, meaning Capability B is missing
        val activeCapabilities = setOf("Capability A")
        assertTrue(flow.isBroken(activeCapabilities), "Flow should be broken due to missing capability B")
    }

    @Test
    fun `test flow is broken when a node is explicitly marked as broken even if its capability is active`() {
        val flow = Flow(
            name = "Test Flow",
            nodes = listOf(
                createCapabilityNode(capabilityA, isBroken = true)
            )
        )

        val activeCapabilities = setOf("Capability A")
        assertTrue(flow.isBroken(activeCapabilities), "Flow should be broken because the node is explicitly marked as broken")
    }

    @Test
    fun `test flow is not broken when empty`() {
        val flow = Flow(
            name = "Empty Flow",
            nodes = emptyList()
        )

        val activeCapabilities = setOf("Capability A")
        assertFalse(flow.isBroken(activeCapabilities), "Empty flow should not be broken")
    }
}
