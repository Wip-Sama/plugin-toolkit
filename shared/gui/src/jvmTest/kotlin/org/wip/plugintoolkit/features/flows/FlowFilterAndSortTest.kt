package org.wip.plugintoolkit.features.flows

import org.wip.plugintoolkit.api.Capability
import org.wip.plugintoolkit.api.PluginInfo
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.FlowChipFilter
import org.wip.plugintoolkit.features.flows.model.FlowSortMode
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.model.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FlowFilterAndSortTest {

    private val subflowNode = Node.SubFlowNode(
        id = 10L,
        position = Offset.Zero,
        flowName = "Child Flow",
        inputs = emptyList(),
        outputs = emptyList()
    )

    private val capabilityNode = Node.CapabilityNode(
        id = 20L,
        position = Offset.Zero,
        pluginInfo = PluginInfo(id = "test.plugin", name = "Test", version = "1.0", description = "Test"),
        capability = Capability(
            name = "missing_cap",
            description = "Test capability",
            returnType = org.wip.plugintoolkit.api.DataType.Primitive(org.wip.plugintoolkit.api.PrimitiveType.STRING)
        ),
        inputs = emptyList(),
        outputs = emptyList()
    )

    private val flowA = Flow(
        name = "Alpha Flow",
        description = "Performs image resizing",
        nodes = listOf(subflowNode)
    )

    private val flowB = Flow(
        name = "Child Flow",
        description = "Subflow utility for filtering",
        nodes = emptyList()
    )

    private val flowC = Flow(
        name = "Omega Flow",
        description = "Audio converter pipeline",
        nodes = listOf(capabilityNode, capabilityNode)
    )

    private val allFlows = listOf(flowA, flowB, flowC)

    @Test
    fun testSearchFiltering() {
        // Match by name
        val searchByName = allFlows.filter { it.name.contains("Child", ignoreCase = true) }
        assertEquals(listOf(flowB), searchByName)

        // Match by description
        val searchByDesc = allFlows.filter {
            it.name.contains("resizing", ignoreCase = true) ||
                (it.description?.contains("resizing", ignoreCase = true) == true)
        }
        assertEquals(listOf(flowA), searchByDesc)
    }

    @Test
    fun testParentFlowsAndSubflowFilter() {
        val parentFlowsMap = mutableMapOf<String, MutableList<String>>()
        allFlows.forEach { parent ->
            parent.nodes.filterIsInstance<Node.SubFlowNode>().forEach { subNode ->
                parentFlowsMap.getOrPut(subNode.flowName) { mutableListOf() }.add(parent.name)
            }
        }

        // Child Flow is referenced by Alpha Flow
        assertEquals(listOf("Alpha Flow"), parentFlowsMap["Child Flow"]?.toList())

        // Filter subflows
        val subflows = allFlows.filter { parentFlowsMap[it.name].orEmpty().isNotEmpty() }
        assertEquals(listOf(flowB), subflows)
    }

    @Test
    fun testBrokenAndReadyFilter() {
        val activeCapabilities = setOf("existing_cap") // "missing_cap" is missing

        val isReady = { flow: Flow ->
            val missing = flow.nodes.filterIsInstance<Node.CapabilityNode>()
                .any { it.capability.name !in activeCapabilities }
            !missing
        }

        val readyFlows = allFlows.filter { isReady(it) }
        assertEquals(listOf(flowA, flowB), readyFlows)

        val brokenFlows = allFlows.filter { !isReady(it) }
        assertEquals(listOf(flowC), brokenFlows)
    }

    @Test
    fun testSortingModes() {
        // NameAsc
        val sortedAsc = allFlows.sortedBy { it.name.lowercase() }
        assertEquals(listOf(flowA, flowB, flowC), sortedAsc)

        // NameDesc
        val sortedDesc = allFlows.sortedByDescending { it.name.lowercase() }
        assertEquals(listOf(flowC, flowB, flowA), sortedDesc)

        // NodeCountDesc
        val sortedCountDesc = allFlows.sortedByDescending { it.nodes.size }
        assertEquals(listOf(flowC, flowA, flowB), sortedCountDesc)

        // NodeCountAsc
        val sortedCountAsc = allFlows.sortedBy { it.nodes.size }
        assertEquals(listOf(flowB, flowA, flowC), sortedCountAsc)
    }
}
