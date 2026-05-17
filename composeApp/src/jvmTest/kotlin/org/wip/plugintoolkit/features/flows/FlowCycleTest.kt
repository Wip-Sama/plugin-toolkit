package org.wip.plugintoolkit.features.flows

import androidx.compose.ui.geometry.Offset
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.viewmodel.FlowEvent
import org.wip.plugintoolkit.features.flows.viewmodel.FlowEditorViewModel
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
}
