package org.wip.plugintoolkit.features.flows.viewmodel

import androidx.compose.ui.geometry.Offset
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.SemanticType
import org.wip.plugintoolkit.api.canConvert
import org.wip.plugintoolkit.api.isCompatibleWith
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.InputPort
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.model.OutputPort
import org.wip.plugintoolkit.features.flows.model.PortConstraints

class FlowNodeManager {

    fun handleAddNode(currentState: FlowEditorState, node: Node, density: Float): FlowEditorState {
        val newFlow = currentState.flow.copy(
            nodes = currentState.flow.nodes + node
        )
        return currentState.copy(
            flow = newFlow,
            nextId = currentState.nextId + 1,
            hasUnsavedChanges = true
        )
    }

    fun handleMoveNode(
        currentState: FlowEditorState,
        id: Long,
        delta: Offset,
        snap: Boolean,
        showGhost: Boolean
    ): FlowEditorState {
        val newOffset = currentState.currentDragOffset + delta
        val node = currentState.flow.nodes.find { it.id == id } ?: return currentState
        val ghostToSet = if (showGhost) (node.position + newOffset).snapToGrid() else null

        return currentState.copy(
            draggedNodeId = id,
            currentDragOffset = newOffset,
            ghostPosition = ghostToSet
        )
    }

    fun handleEndMoveNode(currentState: FlowEditorState, id: Long, density: Float): FlowEditorState {
        val node = currentState.flow.nodes.find { it.id == id } ?: return currentState
        val finalOffset = currentState.currentDragOffset

        val isSelectedGroupMove = currentState.selectedNodeIds.contains(id)
        val nodesToMove = if (isSelectedGroupMove) currentState.selectedNodeIds else setOf(id)

        val newPositions = currentState.flow.nodes.associate {
            it.id to if (nodesToMove.contains(it.id)) (it.position + finalOffset).snapToGrid() else it.position
        }

        val updatedNodes = currentState.flow.nodes.map { n ->
            val newPos = newPositions[n.id]!!
            if (newPos != n.position) n.copyWithPosition(newPos) else n
        }

        val newFlow = currentState.flow.copy(nodes = updatedNodes)

        return currentState.copy(
            flow = newFlow,
            hasUnsavedChanges = true,
            draggedNodeId = null,
            currentDragOffset = Offset.Zero,
            ghostPosition = null
        )
    }

    fun handleDeleteNode(currentState: FlowEditorState, id: Long): FlowEditorState {
        val newFlow = currentState.flow.copy(
            nodes = currentState.flow.nodes.filter { it.id != id },
            connections = currentState.flow.connections.filter { it.sourceNodeId != id && it.targetNodeId != id }
        )
        val newValidationErrors = currentState.validationErrors.filter {
            it.sourceNodeId != id && it.targetNodeId != id
        }
        val newPendingConnection = currentState.pendingConnection?.let {
            if (it.sourceNodeId == id || it.targetNodeId == id) null else it
        }

        return currentState.copy(
            flow = newFlow,
            selectedNodeIds = currentState.selectedNodeIds.filter { it != id }.toSet(),
            validationErrors = newValidationErrors,
            pendingConnection = newPendingConnection,
            hasUnsavedChanges = true
        )
    }

    fun handleDeleteSelectedNodes(currentState: FlowEditorState): FlowEditorState {
        val selectedIds = currentState.selectedNodeIds
        if (selectedIds.isEmpty()) return currentState

        val newFlow = currentState.flow.copy(
            nodes = currentState.flow.nodes.filter { !selectedIds.contains(it.id) },
            connections = currentState.flow.connections.filter {
                !selectedIds.contains(it.sourceNodeId) && !selectedIds.contains(it.targetNodeId)
            }
        )

        val newValidationErrors = currentState.validationErrors.filter {
            !selectedIds.contains(it.sourceNodeId) && !selectedIds.contains(it.targetNodeId)
        }
        val newPendingConnection = currentState.pendingConnection?.let {
            if (selectedIds.contains(it.sourceNodeId) || selectedIds.contains(it.targetNodeId)) null else it
        }

        return currentState.copy(
            flow = newFlow,
            selectedNodeIds = emptySet(),
            validationErrors = newValidationErrors,
            pendingConnection = newPendingConnection,
            hasUnsavedChanges = true
        )
    }

    fun handleUpdateInputPortValue(
        currentState: FlowEditorState,
        nodeId: Long,
        portId: String,
        value: Any?
    ): FlowEditorState {
        val updatedNodes = currentState.flow.nodes.map { node ->
            if (node.id == nodeId) {
                node.copyWithUpdatedInput(
                    portId,
                    org.wip.plugintoolkit.features.flows.model.NodeSerializationUtils.anyToJsonElement(value)
                )
            } else node
        }
        val newFlow = currentState.flow.copy(nodes = updatedNodes)
        return currentState.copy(
            flow = newFlow,
            hasUnsavedChanges = true
        )
    }

    fun handleUpdateBoundaryNode(
        currentState: FlowEditorState,
        nodeId: Long,
        portName: String,
        dataType: DataType,
        semanticTypes: List<SemanticType>,
        constraints: PortConstraints?,
        isList: Boolean,
        isRequired: Boolean
    ): FlowEditorState {
        val updatedNodes = currentState.flow.nodes.map { node ->
            if (node.id == nodeId) {
                when (node) {
                    is Node.FlowInputNode -> {
                        val finalDataType = if (isList) DataType.Array(dataType) else dataType
                        val port = node.outputs.first().copy(
                            name = portName,
                            dataType = finalDataType,
                            semanticTypes = semanticTypes
                        )
                        node.copy(
                            outputs = listOf(port),
                            constraints = constraints,
                            isList = isList,
                            isRequired = isRequired
                        )
                    }

                    is Node.FlowOutputNode -> {
                        val finalDataType = if (isList) DataType.Array(dataType) else dataType
                        val port = node.inputs.first().copy(
                            name = portName,
                            dataType = finalDataType,
                            semanticTypes = semanticTypes
                        )
                        node.copy(inputs = listOf(port))
                    }

                    else -> node
                }
            } else node
        }
        val newFlow = currentState.flow.copy(nodes = updatedNodes)
        return currentState.copy(
            flow = newFlow,
            hasUnsavedChanges = true
        )
    }

    fun handleUpdateSystemNodeSettings(
        currentState: FlowEditorState,
        nodeId: Long,
        portId: String,
        semanticTypes: List<SemanticType>,
        inputPortId: String?,
        extensions: List<String>?
    ): FlowEditorState {
        val updatedNodes = currentState.flow.nodes.map { node ->
            if (node.id == nodeId && node is Node.SystemNode) {
                val updatedOutputs = node.outputs.map { port ->
                    if (port.id == portId) {
                        port.copy(semanticTypes = semanticTypes)
                    } else port
                }
                val updatedInputs = if (inputPortId != null) {
                    node.inputs.map { port ->
                        if (port.id == inputPortId) {
                            val currentConstraints =
                                port.constraints ?: PortConstraints()
                            port.copy(constraints = currentConstraints.copy(extensions = extensions))
                        } else port
                    }
                } else node.inputs

                node.copy(
                    outputs = updatedOutputs,
                    inputs = updatedInputs
                )
            } else node
        }
        val newFlow = currentState.flow.copy(nodes = updatedNodes)
        return currentState.copy(
            flow = newFlow,
            nextId = currentState.nextId + 1,
            hasUnsavedChanges = true
        )
    }

    fun handleBringToFront(currentState: FlowEditorState, nodeId: Long): FlowEditorState {
        val node = currentState.flow.nodes.find { it.id == nodeId } ?: return currentState
        val isAlreadySelected = currentState.selectedNodeIds.contains(nodeId)
        val newSelection = if (isAlreadySelected) {
            currentState.selectedNodeIds
        } else {
            setOf(nodeId)
        }
        return currentState.copy(
            flow = currentState.flow.copy(nodes = currentState.flow.nodes.filter { it.id != nodeId } + node),
            selectedNodeIds = newSelection
        )
    }

    fun handleToggleNodeCollapse(currentState: FlowEditorState, nodeId: Long): FlowEditorState {
        val updatedNodes = currentState.flow.nodes.map { node ->
            if (node.id == nodeId) {
                val newState = !node.isCollapsed
                node.copyWithCollapsedState(newState)
                    .copyWithInputsCollapsedState(newState)
                    .copyWithOutputsCollapsedState(newState)
            } else node
        }
        return currentState.copy(flow = currentState.flow.copy(nodes = updatedNodes), hasUnsavedChanges = true)
    }

    fun handleToggleNodeInputsCollapse(currentState: FlowEditorState, nodeId: Long): FlowEditorState {
        val updatedNodes = currentState.flow.nodes.map { node ->
            if (node.id == nodeId) node.copyWithInputsCollapsedState(!node.isInputsCollapsed) else node
        }
        return currentState.copy(flow = currentState.flow.copy(nodes = updatedNodes), hasUnsavedChanges = true)
    }

    fun handleToggleNodeOutputsCollapse(currentState: FlowEditorState, nodeId: Long): FlowEditorState {
        val updatedNodes = currentState.flow.nodes.map { node ->
            if (node.id == nodeId) node.copyWithOutputsCollapsedState(!node.isOutputsCollapsed) else node
        }
        return currentState.copy(flow = currentState.flow.copy(nodes = updatedNodes), hasUnsavedChanges = true)
    }

}
