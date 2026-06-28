package org.wip.plugintoolkit.features.flows.viewmodel

import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.canConvert
import org.wip.plugintoolkit.api.isCompatibleWith
import org.wip.plugintoolkit.api.isSemanticTypeCompatible
import org.wip.plugintoolkit.core.notification.NotificationService
import org.wip.plugintoolkit.features.flows.logic.format
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.Node
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.flow_editor_incompatible_semantics
import plugintoolkit.composeapp.generated.resources.flow_editor_incompatible_types
import plugintoolkit.composeapp.generated.resources.flow_editor_same_node_warning

class FlowConnectionManager(
    private val state: MutableStateFlow<FlowEditorState>,
    private val notificationService: NotificationService?,
    private val viewModelScope: CoroutineScope,
    private val saveToHistory: () -> Unit,
    private val runTypeInference: () -> Unit,
    private val onEvent: (FlowEvent) -> Unit
) {

    fun handleConnectPorts(sourceNodeId: Long, sourcePortId: String, targetNodeId: Long, targetPortId: String) {
        if (sourceNodeId == targetNodeId) return

        saveToHistory()
        state.update { currentState ->
            val sourceNode = currentState.flow.nodes.find { it.id == sourceNodeId }
            val targetNode = currentState.flow.nodes.find { it.id == targetNodeId }

            if (sourceNode == null || targetNode == null) return@update currentState

            val sourcePort = sourceNode.outputs.find { it.id == sourcePortId }
            val targetPort = targetNode.inputs.find { it.id == targetPortId }

            if (sourcePort == null || targetPort == null) return@update currentState

            if (!sourcePort.dataType.isCompatibleWith(targetPort.dataType) ||
                !isSemanticTypeCompatible(sourcePort.semanticTypes, targetPort.semanticTypes)
            ) {
                return@update currentState
            }

            val isList = targetPort.dataType is DataType.Array
            val filteredConnections = if (isList) {
                currentState.flow.connections
            } else {
                currentState.flow.connections.filterNot {
                    it.targetNodeId == targetNodeId && it.targetPortId == targetPortId
                }
            }

            if (filteredConnections.any { it.sourceNodeId == sourceNodeId && it.sourcePortId == sourcePortId && it.targetNodeId == targetNodeId && it.targetPortId == targetPortId }) {
                return@update currentState // Already connected exactly
            }

            if (org.wip.plugintoolkit.features.flows.logic.FlowCycleDetector.wouldCreateCycle(
                    sourceNodeId,
                    targetNodeId,
                    filteredConnections
                )
            ) {
                notificationService?.toast("Cannot connect: Connecting these ports would create a loop (Directed Cyclic Graph). Enforcing Directed Acyclic Graph (DAG).")
                return@update currentState
            }

            val orderIndex = if (isList) {
                filteredConnections.count { it.targetNodeId == targetNodeId && it.targetPortId == targetPortId }
            } else null

            val newConnection = Connection(sourceNodeId, sourcePortId, targetNodeId, targetPortId, orderIndex)

            val newFlow = currentState.flow.copy(connections = filteredConnections + newConnection)
            currentState.copy(
                flow = newFlow,
                hasUnsavedChanges = true
            )
        }
        runTypeInference()
    }

    fun handleTryConnectPorts(
        sourceNodeId: Long,
        sourcePortId: String,
        targetNodeId: Long,
        targetPortId: String,
        isShiftPressed: Boolean
    ) {
        if (sourceNodeId == targetNodeId) {
            viewModelScope.launch {
                val message = getString(Res.string.flow_editor_same_node_warning)
                notificationService?.toast(message)
            }
            return
        }

        val currentState = state.value
        val sourceNode = currentState.flow.nodes.find { it.id == sourceNodeId }
        val targetNode = currentState.flow.nodes.find { it.id == targetNodeId }

        if (sourceNode == null || targetNode == null) return

        val sourcePort = sourceNode.outputs.find { it.id == sourcePortId }
        val targetPort = targetNode.inputs.find { it.id == targetPortId }

        if (sourcePort == null || targetPort == null) return

        val sourceInferredType = currentState.inferredTypes[Pair(sourceNodeId, sourcePortId)] ?: sourcePort.dataType
        val targetInferredType = currentState.inferredTypes[Pair(targetNodeId, targetPortId)] ?: targetPort.dataType
        val sourceInferredSemantic =
            currentState.inferredSemanticTypes[Pair(sourceNodeId, sourcePortId)] ?: sourcePort.semanticTypes
        val targetInferredSemantic =
            currentState.inferredSemanticTypes[Pair(targetNodeId, targetPortId)] ?: targetPort.semanticTypes

        val typesCompatible = sourceInferredType.isCompatibleWith(targetInferredType)
        val semanticsCompatible = isSemanticTypeCompatible(sourceInferredSemantic, targetInferredSemantic)

        if (typesCompatible && semanticsCompatible) {
            onEvent(FlowEvent.ConnectPorts(sourceNodeId, sourcePortId, targetNodeId, targetPortId))
        } else if (semanticsCompatible && sourceInferredType.canConvert(targetInferredType)) {
            if (isShiftPressed) {
                onEvent(FlowEvent.AutoConvertAndConnect(sourceNodeId, sourcePortId, targetNodeId, targetPortId))
            } else {
                state.update {
                    it.copy(
                        pendingConnection = PendingConnection(
                            sourceNodeId = sourceNodeId,
                            sourcePortId = sourcePortId,
                            targetNodeId = targetNodeId,
                            targetPortId = targetPortId,
                            sourceType = sourceInferredType,
                            targetType = targetInferredType
                        )
                    )
                }
            }
        } else {
            viewModelScope.launch {
                val message = if (!typesCompatible) {
                    getString(
                        Res.string.flow_editor_incompatible_types,
                        sourceInferredType.format(),
                        targetInferredType.format()
                    )
                } else {
                    getString(
                        Res.string.flow_editor_incompatible_semantics,
                        sourceInferredSemantic.joinToString { it.canonicalId },
                        targetInferredSemantic.joinToString { it.canonicalId })
                }
                notificationService?.toast(message)
            }
        }
    }

    fun handleAutoConvertAndConnect(
        sourceNodeId: Long,
        sourcePortId: String,
        targetNodeId: Long,
        targetPortId: String
    ) {
        if (sourceNodeId == targetNodeId) return

        saveToHistory()
        state.update { currentState ->
            val sourceNode = currentState.flow.nodes.find { it.id == sourceNodeId }
            val targetNode = currentState.flow.nodes.find { it.id == targetNodeId }

            if (sourceNode == null || targetNode == null) return@update currentState

            val sourcePort = sourceNode.outputs.find { it.id == sourcePortId }
            val targetPort = targetNode.inputs.find { it.id == targetPortId }

            if (sourcePort == null || targetPort == null) return@update currentState

            val midPosition = Offset(
                (sourceNode.position.x + targetNode.position.x) / 2f,
                (sourceNode.position.y + targetNode.position.y) / 2f
            ).snapToGrid()

            val convertNode = Node.SystemNode(
                id = currentState.nextId,
                position = midPosition,
                title = "Convert",
                systemAction = "convert",
                inputs = SystemNodesRegistry.getInputs("convert"),
                outputs = SystemNodesRegistry.getOutputs("convert")
            )

            val conn1 = Connection(sourceNodeId, sourcePortId, convertNode.id, "input_data")
            val conn2 = Connection(convertNode.id, "output_data", targetNodeId, targetPortId)

            val filteredConnections = currentState.flow.connections.filterNot {
                it.targetNodeId == targetNodeId && it.targetPortId == targetPortId
            }

            val newFlow = currentState.flow.copy(
                nodes = currentState.flow.nodes + convertNode,
                connections = filteredConnections + conn1 + conn2
            )
            currentState.copy(
                flow = newFlow,
                nextId = currentState.nextId + 1,
                hasUnsavedChanges = true
            )
        }
        runTypeInference()
    }

    fun handleDeleteConnection(connection: Connection) {
        saveToHistory()
        state.update { currentState ->
            val remainingConnections = currentState.flow.connections.filter { it != connection }.map { conn ->
                if (conn.targetNodeId == connection.targetNodeId && conn.targetPortId == connection.targetPortId && (conn.orderIndex
                        ?: 0) > (connection.orderIndex ?: 0)
                ) {
                    conn.copy(orderIndex = (conn.orderIndex ?: 0) - 1)
                } else {
                    conn
                }
            }
            currentState.copy(
                flow = currentState.flow.copy(connections = remainingConnections),
                hasUnsavedChanges = true
            )
        }
        runTypeInference()
    }

    fun handleUpdateConnectionOrder(connection: Connection, newOrderIndex: Int) {
        saveToHistory()
        state.update { currentState ->
            val targetConnections = currentState.flow.connections.filter {
                it.targetNodeId == connection.targetNodeId && it.targetPortId == connection.targetPortId
            }.sortedBy { it.orderIndex ?: 0 }.toMutableList()

            val currentIdx = targetConnections.indexOfFirst { it == connection }
            if (currentIdx == -1 || currentIdx == newOrderIndex || newOrderIndex < 0 || newOrderIndex >= targetConnections.size) return@update currentState

            val conn = targetConnections.removeAt(currentIdx)
            targetConnections.add(newOrderIndex, conn)

            // Reassign indices
            val remapped = targetConnections.mapIndexed { index, c -> c.copy(orderIndex = index) }

            val newConnections = currentState.flow.connections.map { existing ->
                remapped.find { it.sourceNodeId == existing.sourceNodeId && it.sourcePortId == existing.sourcePortId && it.targetNodeId == existing.targetNodeId && it.targetPortId == existing.targetPortId }
                    ?: existing
            }

            currentState.copy(flow = currentState.flow.copy(connections = newConnections), hasUnsavedChanges = true)
        }
    }

    fun handleMoveConnectionFirst(connection: Connection) {
        handleUpdateConnectionOrder(connection, 0)
    }

    fun handleMoveConnectionLast(connection: Connection) {
        val targetCount =
            state.value.flow.connections.count { it.targetNodeId == connection.targetNodeId && it.targetPortId == connection.targetPortId }
        handleUpdateConnectionOrder(connection, targetCount - 1)
    }

}
