package org.wip.plugintoolkit.features.board.viewmodel

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.wip.plugintoolkit.api.Capability
import org.wip.plugintoolkit.api.PluginInfo
import org.wip.plugintoolkit.features.board.model.*
import kotlin.math.roundToInt

data class FlowState(
    val flows: List<Flow> = emptyList(),
    val selectedFlowId: String? = null,
    val offset: Offset = Offset.Zero,
    val scale: Float = 1f,
    val nextId: Long = 0L,
    val hasUnsavedChanges: Boolean = false,
    val draggedNodeId: Long? = null,
    val currentDragOffset: Offset = Offset.Zero,
    val ghostPosition: Offset? = null
) {
    val currentFlow: Flow? get() = flows.find { it.name == selectedFlowId } ?: flows.firstOrNull()
}

sealed interface FlowEvent {
    data class AddCapabilityNode(val pluginInfo: PluginInfo, val capability: Capability, val position: Offset) : FlowEvent
    data class AddSystemNode(val systemAction: String, val position: Offset) : FlowEvent
    data class AddFlowInputNode(val position: Offset) : FlowEvent
    data class AddFlowOutputNode(val position: Offset) : FlowEvent
    data class AddSubFlowNode(val flowName: String, val position: Offset) : FlowEvent
    
    data class MoveNode(val id: Long, val delta: Offset, val snap: Boolean = false, val showGhost: Boolean = false) : FlowEvent
    data class EndMoveNode(val id: Long) : FlowEvent
    data class DeleteNode(val id: Long) : FlowEvent
    
    data class ConnectPorts(val sourceNodeId: Long, val sourcePortId: String, val targetNodeId: Long, val targetPortId: String) : FlowEvent
    data class DeleteConnection(val connection: Connection) : FlowEvent
    
    data class Pan(val delta: Offset) : FlowEvent
    data class Zoom(val delta: Float, val focusPosition: Offset) : FlowEvent
    data class SetZoom(val scale: Float) : FlowEvent
    data object ResetBoard : FlowEvent

    // Flow Management
    data class SelectFlow(val flowName: String) : FlowEvent
    data class CreateFlow(val name: String) : FlowEvent
    data class RenameFlow(val oldName: String, val newName: String) : FlowEvent
    data class DeleteFlow(val name: String) : FlowEvent
    data class ExpandSubFlow(val nodeId: Long) : FlowEvent
    
    data object Save : FlowEvent
    data class UpdateInputPortValue(val nodeId: Long, val portId: String, val value: Any?) : FlowEvent
    data class BringToFront(val nodeId: Long) : FlowEvent
}

class FlowViewModel : ViewModel() {
    // TODO: Inject PluginRegistry using Koin here. Currently omitted to avoid breaking build if interface doesn't match perfectly.
    
    private val _state = MutableStateFlow(FlowState(flows = emptyList()))
    val state: StateFlow<FlowState> = _state.asStateFlow()

    private val gridSize = 50f
    private val zoomLevels = listOf(0.1f, 0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f, 4.0f, 5.0f)

    fun onEvent(event: FlowEvent) {
        when (event) {
            is FlowEvent.AddCapabilityNode -> handleAddNode(
                Node.CapabilityNode(
                    id = _state.value.nextId,
                    position = event.position.snapToGrid(),
                    pluginInfo = event.pluginInfo,
                    capability = event.capability,
                    inputs = event.capability.parameters?.map { (key, meta) ->
                        InputPort(key, key, meta.type, meta.semanticType, meta.defaultValue)
                    } ?: emptyList(),
                    outputs = listOf(OutputPort("result", "Result", event.capability.returnType, event.capability.semanticType))
                )
            )
            is FlowEvent.AddSystemNode -> handleAddNode(
                Node.SystemNode(
                    id = _state.value.nextId,
                    position = event.position.snapToGrid(),
                    title = event.systemAction.capitalize(),
                    systemAction = event.systemAction,
                    inputs = emptyList(), // TODO: Define standard inputs
                    outputs = emptyList() // TODO: Define standard outputs
                )
            )
            is FlowEvent.AddFlowInputNode -> handleAddNode(
                Node.FlowInputNode(
                    id = _state.value.nextId,
                    position = event.position.snapToGrid(),
                    outputs = emptyList() // Configurable by user in future
                )
            )
            is FlowEvent.AddFlowOutputNode -> handleAddNode(
                Node.FlowOutputNode(
                    id = _state.value.nextId,
                    position = event.position.snapToGrid(),
                    inputs = emptyList() // Configurable by user in future
                )
            )
            is FlowEvent.AddSubFlowNode -> handleAddNode(
                Node.SubFlowNode(
                    id = _state.value.nextId,
                    position = event.position.snapToGrid(),
                    flowName = event.flowName,
                    inputs = emptyList(), // Load from target flow
                    outputs = emptyList() // Load from target flow
                )
            )
            is FlowEvent.ExpandSubFlow -> handleExpandSubFlow(event.nodeId)
            is FlowEvent.MoveNode -> handleMoveNode(event.id, event.delta, event.snap, event.showGhost)
            is FlowEvent.EndMoveNode -> handleEndMoveNode(event.id)
            is FlowEvent.DeleteNode -> handleDeleteNode(event.id)
            is FlowEvent.ConnectPorts -> handleConnectPorts(event.sourceNodeId, event.sourcePortId, event.targetNodeId, event.targetPortId)
            is FlowEvent.DeleteConnection -> handleDeleteConnection(event.connection)
            is FlowEvent.Pan -> handlePan(event.delta)
            is FlowEvent.Zoom -> handleZoom(event.delta, event.focusPosition)
            is FlowEvent.SetZoom -> handleSetZoom(event.scale)
            is FlowEvent.ResetBoard -> handleResetBoard()
            is FlowEvent.SelectFlow -> _state.update { it.copy(selectedFlowId = event.flowName) }
            is FlowEvent.CreateFlow -> handleCreateFlow(event.name)
            is FlowEvent.RenameFlow -> handleRenameFlow(event.oldName, event.newName)
            is FlowEvent.DeleteFlow -> handleDeleteFlow(event.name)
            is FlowEvent.ExpandSubFlow -> handleExpandSubFlow(event.nodeId)
            is FlowEvent.Save -> handleSave()
            is FlowEvent.UpdateInputPortValue -> handleUpdateInputPortValue(event.nodeId, event.portId, event.value)
            is FlowEvent.BringToFront -> handleBringToFront(event.nodeId)
        }
    }

    private fun handleAddNode(node: Node) {
        _state.update { currentState ->
            val updatedFlows = currentState.flows.map { flow ->
                if (flow.name == (currentState.selectedFlowId ?: currentState.flows.firstOrNull()?.name)) {
                    flow.copy(nodes = flow.nodes + node)
                } else flow
            }
            currentState.copy(
                flows = updatedFlows,
                nextId = currentState.nextId + 1,
                hasUnsavedChanges = true
            )
        }
    }

    private fun handleMoveNode(id: Long, delta: Offset, snap: Boolean, showGhost: Boolean) {
        _state.update { currentState ->
            val newOffset = currentState.currentDragOffset + delta
            val node = currentState.currentFlow?.nodes?.find { it.id == id } ?: return@update currentState
            val ghostToSet = if (showGhost) (node.position + newOffset).snapToGrid() else null
            
            currentState.copy(
                draggedNodeId = id,
                currentDragOffset = newOffset,
                ghostPosition = ghostToSet
            )
        }
    }

    private fun handleEndMoveNode(id: Long) {
        _state.update { currentState ->
            val currentFlow = currentState.currentFlow ?: return@update currentState
            val node = currentFlow.nodes.find { it.id == id } ?: return@update currentState
            val finalPosition = (node.position + currentState.currentDragOffset).snapToGrid()
            
            val updatedFlows = currentState.flows.map { flow ->
                if (flow.name == currentFlow.name) {
                    flow.copy(nodes = flow.nodes.map { 
                        if (it.id == id) it.copyWithPosition(finalPosition) 
                        else it 
                    })
                } else flow
            }
            currentState.copy(
                flows = updatedFlows, 
                hasUnsavedChanges = true,
                draggedNodeId = null,
                currentDragOffset = Offset.Zero,
                ghostPosition = null
            )
        }
    }

    private fun handleDeleteNode(id: Long) {
        _state.update { currentState ->
            val updatedFlows = currentState.flows.map { flow ->
                if (flow.name == (currentState.selectedFlowId ?: currentState.flows.firstOrNull()?.name)) {
                    flow.copy(
                        nodes = flow.nodes.filter { it.id != id },
                        connections = flow.connections.filter { it.sourceNodeId != id && it.targetNodeId != id }
                    )
                } else flow
            }
            currentState.copy(flows = updatedFlows, hasUnsavedChanges = true)
        }
    }

    private fun handleConnectPorts(sourceNodeId: Long, sourcePortId: String, targetNodeId: Long, targetPortId: String) {
        // Prevent connecting to self
        if (sourceNodeId == targetNodeId) return

        _state.update { currentState ->
            val currentFlow = currentState.currentFlow ?: return@update currentState
            val sourceNode = currentFlow.nodes.find { it.id == sourceNodeId }
            val targetNode = currentFlow.nodes.find { it.id == targetNodeId }
            
            if (sourceNode == null || targetNode == null) return@update currentState

            val sourcePort = sourceNode.outputs.find { it.id == sourcePortId }
            val targetPort = targetNode.inputs.find { it.id == targetPortId }

            if (sourcePort == null || targetPort == null) return@update currentState

            // Strict matching: datatype and semantic type must match
            // Uncomment to enforce:
            // if (sourcePort.dataType != targetPort.dataType || sourcePort.semanticType != targetPort.semanticType) {
            //    return@update currentState
            // }

            val newConnection = Connection(sourceNodeId, sourcePortId, targetNodeId, targetPortId)
            
            val updatedFlows = currentState.flows.map { flow ->
                if (flow.name == (currentState.selectedFlowId ?: currentState.flows.firstOrNull()?.name)) {
                    val filteredConnections = flow.connections.filterNot { 
                        it.targetNodeId == targetNodeId && it.targetPortId == targetPortId 
                    }
                    flow.copy(connections = filteredConnections + newConnection)
                } else flow
            }

            currentState.copy(flows = updatedFlows, hasUnsavedChanges = true)
        }
    }

    private fun handleDeleteConnection(connection: Connection) {
        _state.update { currentState ->
            val updatedFlows = currentState.flows.map { flow ->
                if (flow.name == (currentState.selectedFlowId ?: currentState.flows.firstOrNull()?.name)) {
                    flow.copy(connections = flow.connections.filter { it != connection })
                } else flow
            }
            currentState.copy(flows = updatedFlows, hasUnsavedChanges = true)
        }
    }

    private fun handlePan(delta: Offset) {
        _state.update { currentState ->
            currentState.copy(offset = currentState.offset + delta)
        }
    }

    private fun handleZoom(delta: Float, focusPosition: Offset) {
        _state.update { currentState ->
            val currentZoom = currentState.scale
            val currentIndex = zoomLevels.indexOfFirst { it >= currentZoom }

            val newScale = if (delta > 0) {
                val newIndex = (currentIndex - 1).coerceAtLeast(0)
                zoomLevels[newIndex]
            } else {
                val newIndex =
                    (if (currentIndex == -1) zoomLevels.size - 1 else currentIndex + 1).coerceAtMost(zoomLevels.size - 1)
                zoomLevels[newIndex]
            }

            if (newScale == currentZoom) return@update currentState

            val boardFocus = (focusPosition - currentState.offset) / currentZoom
            val newOffset = focusPosition - boardFocus * newScale

            currentState.copy(scale = newScale, offset = newOffset)
        }
    }

    private fun handleSetZoom(scale: Float) {
        _state.update { currentState ->
            val newScale = zoomLevels.minByOrNull { kotlin.math.abs(it - scale) } ?: scale
            currentState.copy(scale = newScale)
        }
    }

    private fun handleResetBoard() {
        _state.update { currentState ->
            val updatedFlows = currentState.flows.map { flow ->
                if (flow.name == (currentState.selectedFlowId ?: currentState.flows.firstOrNull()?.name)) {
                    flow.copy(nodes = emptyList(), connections = emptyList())
                } else flow
            }
            currentState.copy(flows = updatedFlows, offset = Offset.Zero, scale = 1f, hasUnsavedChanges = true)
        }
    }

    private fun handleCreateFlow(name: String) {
        _state.update { it.copy(flows = it.flows + Flow(name), selectedFlowId = name, hasUnsavedChanges = true) }
    }

    private fun handleRenameFlow(oldName: String, newName: String) {
        _state.update { state ->
            state.copy(
                flows = state.flows.map { if (it.name == oldName) it.copy(name = newName) else it },
                selectedFlowId = if (state.selectedFlowId == oldName) newName else state.selectedFlowId,
                hasUnsavedChanges = true
            )
        }
    }

    private fun handleDeleteFlow(name: String) {
        _state.update { state ->
            val newFlows = state.flows.filter { it.name != name }
            state.copy(
                flows = if (newFlows.isEmpty()) listOf(Flow("Default Flow")) else newFlows,
                selectedFlowId = if (state.selectedFlowId == name) null else state.selectedFlowId,
                hasUnsavedChanges = true
            )
        }
    }

    private fun handleExpandSubFlow(nodeId: Long) {
        _state.update { currentState ->
            val currentFlow = currentState.currentFlow ?: return@update currentState
            val subFlowNode = currentFlow.nodes.find { it.id == nodeId } as? Node.SubFlowNode ?: return@update currentState
            val targetFlow = currentState.flows.find { it.name == subFlowNode.flowName } ?: return@update currentState

            // 1. Calculate offset for new nodes based on sub-flow node position
            val baseOffset = subFlowNode.position
            
            // 2. Clone nodes with new IDs
            var nextId = currentState.nextId
            val oldToNewId = mutableMapOf<Long, Long>()
            val newNodes = targetFlow.nodes.map { node ->
                val newId = nextId++
                oldToNewId[node.id] = newId
                node.copyWithPosition(node.position + baseOffset).let { 
                    // Need a way to set ID on clones if Node was immutable. 
                    // NodeModels.kt doesn't have setId, so I'll need to use copy() if it was a data class.
                    // Assuming Node subclasses are data classes.
                    when(it) {
                        is Node.CapabilityNode -> it.copy(id = newId)
                        is Node.SystemNode -> it.copy(id = newId)
                        is Node.FlowInputNode -> it.copy(id = newId)
                        is Node.FlowOutputNode -> it.copy(id = newId)
                        is Node.SubFlowNode -> it.copy(id = newId)
                    }
                }
            }

            // 3. Clone connections with new IDs
            val newConnections = targetFlow.connections.map { conn ->
                Connection(
                    sourceNodeId = oldToNewId[conn.sourceNodeId] ?: conn.sourceNodeId,
                    sourcePortId = conn.sourcePortId,
                    targetNodeId = oldToNewId[conn.targetNodeId] ?: conn.targetNodeId,
                    targetPortId = conn.targetPortId
                )
            }

            // 4. Update flow: remove sub-flow node and add expanded content
            val updatedNodes = currentFlow.nodes.filter { it.id != nodeId } + newNodes
            val updatedConnections = currentFlow.connections.filter { it.sourceNodeId != nodeId && it.targetNodeId != nodeId } + newConnections

            val updatedFlows = currentState.flows.map { 
                if (it.name == currentFlow.name) it.copy(nodes = updatedNodes, connections = updatedConnections) 
                else it 
            }

            currentState.copy(flows = updatedFlows, nextId = nextId, hasUnsavedChanges = true)
        }
    }

    private fun handleSave() {
        // TODO: Persist flows to disk/server
        _state.update { it.copy(hasUnsavedChanges = false) }
    }

    private fun handleUpdateInputPortValue(nodeId: Long, portId: String, value: Any?) {
        _state.update { currentState ->
            val updatedFlows = currentState.flows.map { flow ->
                if (flow.name == (currentState.selectedFlowId ?: currentState.flows.firstOrNull()?.name)) {
                    flow.copy(nodes = flow.nodes.map { node ->
                        if (node.id == nodeId) {
                            when (node) {
                                is Node.CapabilityNode -> node.copy(inputs = node.inputs.map { if (it.id == portId) it.copy(value = value) else it })
                                is Node.SystemNode -> node.copy(inputs = node.inputs.map { if (it.id == portId) it.copy(value = value) else it })
                                is Node.FlowInputNode -> node // No inputs
                                is Node.FlowOutputNode -> node.copy(inputs = node.inputs.map { if (it.id == portId) it.copy(value = value) else it })
                                is Node.SubFlowNode -> node.copy(inputs = node.inputs.map { if (it.id == portId) it.copy(value = value) else it })
                            }
                        } else node
                    })
                } else flow
            }
            currentState.copy(flows = updatedFlows, hasUnsavedChanges = true)
        }
    }

    private fun handleBringToFront(nodeId: Long) {
        _state.update { currentState ->
            val updatedFlows = currentState.flows.map { flow ->
                if (flow.name == (currentState.selectedFlowId ?: currentState.flows.firstOrNull()?.name)) {
                    val node = flow.nodes.find { it.id == nodeId } ?: return@map flow
                    flow.copy(nodes = flow.nodes.filter { it.id != nodeId } + node)
                } else flow
            }
            currentState.copy(flows = updatedFlows)
        }
    }

    private fun Offset.snapToGrid(): Offset {
        return Offset(
            (x / gridSize).roundToInt() * gridSize,
            (y / gridSize).roundToInt() * gridSize
        )
    }
}
