package org.wip.plugintoolkit.features.flows

import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.viewmodel.FlowEditorViewModel
import org.wip.plugintoolkit.features.flows.viewmodel.FlowEvent
import org.wip.plugintoolkit.features.settings.logic.SettingsPersistence
import org.wip.plugintoolkit.features.settings.model.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MockSettingsPersistence : SettingsPersistence {
    override fun load(): AppSettings = AppSettings()
    override fun save(settings: AppSettings) {}
    override fun getSettingsDir(): String = "build/tmp/test_flows"
    override fun getJobsDir(): String = "build/tmp/test_flows/jobs"
    override fun openLogFolder() {}
    override fun openLatestLog() {}
}

class FlowCycleTest {

    @Test
    fun testCyclePrevention() {
        val viewModel = FlowEditorViewModel(
            initialFlowName = "Default Flow",
            settingsPersistence = MockSettingsPersistence(),
            notificationService = null
        )

        // 1. Add some system nodes: Node A (Log), Node B (Log), Node C (Log)
        viewModel.onEvent(FlowEvent.AddSystemNode("Log", Offset(100f, 100f)))
        viewModel.onEvent(FlowEvent.AddSystemNode("Log", Offset(200f, 200f)))
        viewModel.onEvent(FlowEvent.AddSystemNode("Log", Offset(300f, 300f)))

        val state = viewModel.state.value
        val flow = state.flow
        
        assertEquals(3, flow.nodes.size)
        val nodeA = flow.nodes[0]
        val nodeB = flow.nodes[1]
        val nodeC = flow.nodes[2]

        // 2. Connect Node A (output) -> Node B (message)
        viewModel.onEvent(FlowEvent.ConnectPorts(nodeA.id, "output", nodeB.id, "message"))
        
        // 3. Connect Node B (output) -> Node C (message)
        viewModel.onEvent(FlowEvent.ConnectPorts(nodeB.id, "output", nodeC.id, "message"))

        assertEquals(2, viewModel.state.value.flow.connections.size)

        // 4. Try to connect Node C (output) -> Node A (message) - This forms a cycle: A -> B -> C -> A
        viewModel.onEvent(FlowEvent.ConnectPorts(nodeC.id, "output", nodeA.id, "message"))

        // Connection should be rejected, so total connections remain 2
        assertEquals(2, viewModel.state.value.flow.connections.size)
        assertFalse(viewModel.state.value.flow.connections.any { it.sourceNodeId == nodeC.id && it.targetNodeId == nodeA.id })

        // 5. Connect Node A (output) -> Node C (message)
        // Note: Node C (message) already has an incoming connection from Node B.
        // Connecting A -> C will replace the existing B -> C connection (single inflow constraint).
        // Since B -> C is replaced by A -> C, the graph becomes A -> B, A -> C (two branches, no cycles).
        // Let's verify this is allowed!
        viewModel.onEvent(FlowEvent.ConnectPorts(nodeA.id, "output", nodeC.id, "message"))

        val currentConnections = viewModel.state.value.flow.connections
        assertEquals(2, currentConnections.size)
        assertTrue(currentConnections.any { it.sourceNodeId == nodeA.id && it.targetNodeId == nodeC.id })
        assertFalse(currentConnections.any { it.sourceNodeId == nodeB.id && it.targetNodeId == nodeC.id })
    }

    @Test
    fun testNestedFlowCyclePrevention() = runBlocking {
        val appDataDir = "build/tmp/test_flows"
        val flowsDir = Path("$appDataDir/flows")
        
        // Clean up or ensure directory exists
        if (SystemFileSystem.exists(flowsDir)) {
            SystemFileSystem.list(flowsDir).forEach { SystemFileSystem.delete(it) }
        } else {
            SystemFileSystem.createDirectories(flowsDir)
        }

        // Create Flow A, Flow B, Flow C
        val flowA = Flow("Flow A")
        val flowB = Flow("Flow B")
        val flowC = Flow("Flow C")

        val json = kotlinx.serialization.json.Json { prettyPrint = true }
        
        // Write Flow B and Flow C to files so they can be loaded as other flows
        SystemFileSystem.sink(Path("$appDataDir/flows/Flow_B.json")).buffered().use {
            it.writeString(json.encodeToString(Flow.serializer(), flowB))
        }
        SystemFileSystem.sink(Path("$appDataDir/flows/Flow_C.json")).buffered().use {
            it.writeString(json.encodeToString(Flow.serializer(), flowC))
        }
        // Write Flow A initially so it exists
        SystemFileSystem.sink(Path("$appDataDir/flows/Flow_A.json")).buffered().use {
            it.writeString(json.encodeToString(Flow.serializer(), flowA))
        }

        // 1. Load Flow A
        val viewModelA = FlowEditorViewModel(
            initialFlowName = "Flow A",
            settingsPersistence = MockSettingsPersistence(),
            notificationService = null
        ).apply { bypassReadOnlyForTesting = true }

        // Wait until loadFlow completes and populates state.value.flows
        var loadedA = false
        for (i in 1..50) {
            if (viewModelA.state.value.flows.size >= 3) {
                loadedA = true
                break
            }
            delay(10)
        }
        assertTrue(loadedA, "Flows failed to load in Flow A editor")
        
        // 2. Add Flow B as a subflow in Flow A
        viewModelA.onEvent(FlowEvent.AddSubFlowNode("Flow B", Offset(100f, 100f)))
        viewModelA.onEvent(FlowEvent.Save)
        delay(50) // Wait for save to disk to finish

        // 3. Load Flow B
        val viewModelB = FlowEditorViewModel(
            initialFlowName = "Flow B",
            settingsPersistence = MockSettingsPersistence(),
            notificationService = null
        ).apply { bypassReadOnlyForTesting = true }

        var loadedB = false
        for (i in 1..50) {
            if (viewModelB.state.value.flows.size >= 3) {
                loadedB = true
                break
            }
            delay(10)
        }
        assertTrue(loadedB, "Flows failed to load in Flow B editor")

        // 4. Add Flow C as a subflow in Flow B
        viewModelB.onEvent(FlowEvent.AddSubFlowNode("Flow C", Offset(100f, 100f)))
        viewModelB.onEvent(FlowEvent.Save)
        delay(50)

        // 5. Load Flow C
        val viewModelC = FlowEditorViewModel(
            initialFlowName = "Flow C",
            settingsPersistence = MockSettingsPersistence(),
            notificationService = null
        ).apply { bypassReadOnlyForTesting = true }

        var loadedC = false
        for (i in 1..50) {
            if (viewModelC.state.value.flows.size >= 3) {
                loadedC = true
                break
            }
            delay(10)
        }
        assertTrue(loadedC, "Flows failed to load in Flow C editor")

        // 6. Try to add Flow A as a subflow inside Flow C - This should form a cycle: A -> B -> C -> A
        val initialNodesCount = viewModelC.state.value.flow.nodes.size
        viewModelC.onEvent(FlowEvent.AddSubFlowNode("Flow A", Offset(100f, 100f)))

        // Connection/Add should be rejected, so nodes count should remain unchanged
        assertEquals(initialNodesCount, viewModelC.state.value.flow.nodes.size, "Should reject subflow addition that forms a cycle")

        // 7. Try to add Flow C to itself as a subflow (self-dependency)
        viewModelC.onEvent(FlowEvent.AddSubFlowNode("Flow C", Offset(100f, 100f)))
        assertEquals(initialNodesCount, viewModelC.state.value.flow.nodes.size, "Should reject self-referential subflow addition")
    }

    @Test
    fun testReadOnlyStateReasons() = runBlocking {
        val appDataDir = "build/tmp/test_flows"
        val flowsDir = Path("$appDataDir/flows")
        
        // Clean up or ensure directory exists
        if (SystemFileSystem.exists(flowsDir)) {
            SystemFileSystem.list(flowsDir).forEach { SystemFileSystem.delete(it) }
        } else {
            SystemFileSystem.createDirectories(flowsDir)
        }

        // Create Flow A and Flow B
        val flowA = Flow("Flow A")
        val flowB = Flow("Flow B")

        val json = kotlinx.serialization.json.Json { prettyPrint = true }
        
        // Write Flow B to file so it can be loaded
        SystemFileSystem.sink(Path("$appDataDir/flows/Flow_B.json")).buffered().use {
            it.writeString(json.encodeToString(Flow.serializer(), flowB))
        }
        // Write Flow A initially so it exists
        SystemFileSystem.sink(Path("$appDataDir/flows/Flow_A.json")).buffered().use {
            it.writeString(json.encodeToString(Flow.serializer(), flowA))
        }

        // 1. Load Flow A and add Flow B as a subflow
        val viewModelA = FlowEditorViewModel(
            initialFlowName = "Flow A",
            settingsPersistence = MockSettingsPersistence(),
            notificationService = null
        ).apply { bypassReadOnlyForTesting = true }

        var loadedA = false
        for (i in 1..50) {
            if (viewModelA.state.value.flows.size >= 2) {
                loadedA = true
                break
            }
            delay(10)
        }
        assertTrue(loadedA, "Flows failed to load in Flow A editor")
        
        viewModelA.onEvent(FlowEvent.AddSubFlowNode("Flow B", Offset(100f, 100f)))
        viewModelA.onEvent(FlowEvent.Save)
        delay(50) // Wait for save to disk to finish

        // 2. Load Flow B without bypassing read-only
        val viewModelB = FlowEditorViewModel(
            initialFlowName = "Flow B",
            settingsPersistence = MockSettingsPersistence(),
            notificationService = null
        )

        var loadedB = false
        for (i in 1..50) {
            if (viewModelB.state.value.flows.size >= 2) {
                loadedB = true
                break
            }
            delay(10)
        }
        assertTrue(loadedB, "Flows failed to load in Flow B editor")

        viewModelB.updateReadOnlyState()

        val stateB = viewModelB.state.value
        assertTrue(stateB.isReadOnly, "Flow B should be read-only because it is used in Flow A")
        assertTrue(stateB.readOnlyReasons.contains(org.wip.plugintoolkit.features.flows.viewmodel.ReadOnlyReason.UsedInOtherFlows), "Reason should be UsedInOtherFlows")
    }
}
