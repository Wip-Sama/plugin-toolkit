package org.wip.plugintoolkit.features.flows

import androidx.compose.ui.geometry.Offset
import kotlinx.serialization.json.Json
import org.wip.plugintoolkit.core.utils.PlatformUtils
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.Node
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FlowImportExportTest {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun testZipAndUnzipRoundtrip() {
        val flowA = Flow(name = "FlowA", nodes = emptyList(), connections = emptyList())
        val flowB = Flow(name = "FlowB", nodes = emptyList(), connections = emptyList())

        val entries = mapOf(
            "FlowA.json" to json.encodeToString(Flow.serializer(), flowA),
            "FlowB.json" to json.encodeToString(Flow.serializer(), flowB)
        )

        // Zip
        val zipBytes = PlatformUtils.zipEntries(entries)
        assertTrue(zipBytes.isNotEmpty(), "Zip bytes should not be empty")

        // Unzip
        val unzipped = PlatformUtils.unzipEntries(zipBytes)
        assertEquals(2, unzipped.size, "Should extract exactly 2 entries")

        val extractedFlowA = json.decodeFromString<Flow>(unzipped["FlowA.json"]!!)
        val extractedFlowB = json.decodeFromString<Flow>(unzipped["FlowB.json"]!!)

        assertEquals("FlowA", extractedFlowA.name)
        assertEquals("FlowB", extractedFlowB.name)
    }

    @Test
    fun testTransitiveDependencyResolution() {
        // Flow C: no subflows
        val flowC = Flow(name = "FlowC", nodes = emptyList(), connections = emptyList())

        // Flow B: subflow referencing Flow C
        val subflowNodeC = Node.SubFlowNode(
            id = 1L,
            position = Offset(0f, 0f),
            flowName = "FlowC",
            inputs = emptyList(),
            outputs = emptyList(),
            inputMappings = emptyList(),
            outputMappings = emptyList()
        )
        val flowB = Flow(name = "FlowB", nodes = listOf(subflowNodeC), connections = emptyList())

        // Flow A: subflow referencing Flow B
        val subflowNodeB = Node.SubFlowNode(
            id = 2L,
            position = Offset(0f, 0f),
            flowName = "FlowB",
            inputs = emptyList(),
            outputs = emptyList(),
            inputMappings = emptyList(),
            outputMappings = emptyList()
        )
        val flowA = Flow(name = "FlowA", nodes = listOf(subflowNodeB), connections = emptyList())

        val allFlows = listOf(flowA, flowB, flowC)

        // Resolve dependencies
        val deps = getTransitiveSubflowDependencies(flowA, allFlows)
        assertEquals(2, deps.size, "Flow A should depend transitively on B and C")
        assertTrue(deps.any { it.name == "FlowB" })
        assertTrue(deps.any { it.name == "FlowC" })
    }

    @Test
    fun testRenameReferencesOnConflictResolution() {
        // Flow B
        val flowB = Flow(name = "FlowB", nodes = emptyList(), connections = emptyList())

        // Flow A depending on Flow B
        val subflowNodeB = Node.SubFlowNode(
            id = 2L,
            position = Offset(0f, 0f),
            flowName = "FlowB",
            inputs = emptyList(),
            outputs = emptyList(),
            inputMappings = emptyList(),
            outputMappings = emptyList()
        )
        val flowA = Flow(name = "FlowA", nodes = listOf(subflowNodeB), connections = emptyList())

        val importedFlows = mutableListOf(flowA, flowB)
        val renamedFlows = mapOf("FlowB" to "FlowB (Imported)")

        // Update subflow references
        val updatedFlows = importedFlows.map { flow ->
            val updatedNodes = flow.nodes.map { node ->
                if (node is Node.SubFlowNode && renamedFlows.containsKey(node.flowName)) {
                    node.copy(flowName = renamedFlows[node.flowName]!!)
                } else {
                    node
                }
            }
            flow.copy(nodes = updatedNodes)
        }

        val updatedFlowA = updatedFlows.find { it.name == "FlowA" }!!
        val subflowNode = updatedFlowA.nodes.first() as Node.SubFlowNode
        assertEquals(
            "FlowB (Imported)",
            subflowNode.flowName,
            "Subflow reference in FlowA should be updated to renamed FlowB"
        )
    }

    private fun getTransitiveSubflowDependencies(flow: Flow, allFlows: List<Flow>): List<Flow> {
        val dependencies = mutableSetOf<Flow>()
        val toVisit = mutableListOf(flow)
        val visited = mutableSetOf<String>()

        while (toVisit.isNotEmpty()) {
            val current = toVisit.removeAt(0)
            if (current.name in visited) continue
            visited.add(current.name)
            if (current != flow) {
                dependencies.add(current)
            }

            val subflowNames = current.nodes
                .filterIsInstance<Node.SubFlowNode>()
                .map { it.flowName }

            subflowNames.forEach { subflowName ->
                val depFlow = allFlows.find { it.name == subflowName }
                if (depFlow != null && depFlow.name !in visited) {
                    toVisit.add(depFlow)
                }
            }
        }
        return dependencies.toList()
    }

    @Test
    fun testGenerateUniqueFlowNameWithCustomName() {
        val existingNames = setOf("FlowA", "FlowB", "MyCustomFlow")

        fun generateUnique(baseName: String): String {
            if (baseName !in existingNames) return baseName
            var candidate = "$baseName (Imported)"
            if (candidate !in existingNames) return candidate
            var counter = 2
            while (candidate in existingNames) {
                candidate = "$baseName (Imported $counter)"
                counter++
            }
            return candidate
        }

        // Test non-clashing custom name (should return unchanged)
        assertEquals("NewUniqueFlow", generateUnique("NewUniqueFlow"))

        // Test clashing base name (should generate a unique name with suffix)
        assertEquals("FlowA (Imported)", generateUnique("FlowA"))

        // Test with clashing suffix (should append sequential counter)
        val existingNamesWithSuffix = existingNames + "FlowA (Imported)"
        fun generateUnique2(baseName: String): String {
            if (baseName !in existingNamesWithSuffix) return baseName
            var candidate = "$baseName (Imported)"
            if (candidate !in existingNamesWithSuffix) return candidate
            var counter = 2
            while (candidate in existingNamesWithSuffix) {
                candidate = "$baseName (Imported $counter)"
                counter++
            }
            return candidate
        }
        assertEquals("FlowA (Imported 2)", generateUnique2("FlowA"))
    }
}
