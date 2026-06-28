package org.wip.plugintoolkit.features.flows.viewmodel


import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.SemanticType
import org.wip.plugintoolkit.api.canConvert
import org.wip.plugintoolkit.api.isCompatibleWith
import org.wip.plugintoolkit.api.isSemanticTypeCompatible
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.InputPort
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.model.OutputPort
import org.wip.plugintoolkit.features.flows.model.PortConstraints

class FlowNodeManager(
    private val state: MutableStateFlow<FlowEditorState>,
    private val saveToHistory: () -> Unit,
    private val runTypeInference: () -> Unit
) {

    fun handleAddNode(node: Node, density: Float) {
        saveToHistory()
        state.update { currentState ->
            val intersection =
                findCloseConnection(node, currentState.flow.nodes, currentState.flow.connections, density)

            val newConnections = if (intersection != null) {
                val (connection, ports) = intersection
                val (compatibleInput, compatibleOutput) = ports
                currentState.flow.connections.filter { it != connection } +
                        Connection(connection.sourceNodeId, connection.sourcePortId, node.id, compatibleInput.id) +
                        Connection(node.id, compatibleOutput.id, connection.targetNodeId, connection.targetPortId)
            } else {
                currentState.flow.connections
            }

            val newFlow = currentState.flow.copy(
                nodes = currentState.flow.nodes + node,
                connections = newConnections
            )
            currentState.copy(
                flow = newFlow,
                nextId = currentState.nextId + 1,
                hasUnsavedChanges = true
            )
        }
        runTypeInference()
    }

    fun handleMoveNode(id: Long, delta: Offset, snap: Boolean, showGhost: Boolean) {
        state.update { currentState ->
            val newOffset = currentState.currentDragOffset + delta
            val node = currentState.flow.nodes.find { it.id == id } ?: return@update currentState
            val ghostToSet = if (showGhost) (node.position + newOffset).snapToGrid() else null

            currentState.copy(
                draggedNodeId = id,
                currentDragOffset = newOffset,
                ghostPosition = ghostToSet
            )
        }
    }

    fun handleEndMoveNode(id: Long, density: Float) {
        saveToHistory()
        state.update { currentState ->
            val node = currentState.flow.nodes.find { it.id == id } ?: return@update currentState
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

            var newConnections = currentState.flow.connections

            if (nodesToMove.size == 1) {
                val movedNode = updatedNodes.find { it.id == id }!!
                val intersection = findCloseConnection(
                    movedNode,
                    currentState.flow.nodes.filter { it.id != id },
                    currentState.flow.connections.filter { it.sourceNodeId != id && it.targetNodeId != id },
                    density
                )

                if (intersection != null) {
                    val (connection, ports) = intersection
                    val (compatibleInput, compatibleOutput) = ports
                    newConnections = currentState.flow.connections.filter { it != connection } +
                            Connection(connection.sourceNodeId, connection.sourcePortId, id, compatibleInput.id) +
                            Connection(id, compatibleOutput.id, connection.targetNodeId, connection.targetPortId)
                }
            }

            val newFlow = currentState.flow.copy(nodes = updatedNodes, connections = newConnections)

            currentState.copy(
                flow = newFlow,
                hasUnsavedChanges = true,
                draggedNodeId = null,
                currentDragOffset = Offset.Zero,
                ghostPosition = null
            )
        }
        runTypeInference()
    }

    fun handleDeleteNode(id: Long) {
        saveToHistory()
        state.update { currentState ->
            val newFlow = currentState.flow.copy(
                nodes = currentState.flow.nodes.filter { it.id != id },
                connections = currentState.flow.connections.filter { it.sourceNodeId != id && it.targetNodeId != id }
            )
            currentState.copy(
                flow = newFlow,
                selectedNodeIds = currentState.selectedNodeIds.filter { it != id }.toSet(),
                hasUnsavedChanges = true
            )
        }
        runTypeInference()
    }

    fun handleDeleteSelectedNodes() {
        val selectedIds = state.value.selectedNodeIds
        if (selectedIds.isEmpty()) return

        saveToHistory()
        state.update { currentState ->
            val newFlow = currentState.flow.copy(
                nodes = currentState.flow.nodes.filter { !selectedIds.contains(it.id) },
                connections = currentState.flow.connections.filter {
                    !selectedIds.contains(it.sourceNodeId) && !selectedIds.contains(it.targetNodeId)
                }
            )
            currentState.copy(
                flow = newFlow,
                selectedNodeIds = emptySet(),
                hasUnsavedChanges = true
            )
        }
        runTypeInference()
    }

    fun handleUpdateInputPortValue(nodeId: Long, portId: String, value: Any?) {
        saveToHistory()
        state.update { currentState ->
            val updatedNodes = currentState.flow.nodes.map { node ->
                if (node.id == nodeId) {
                    node.copyWithUpdatedInput(
                        portId,
                        org.wip.plugintoolkit.features.flows.model.NodeSerializationUtils.anyToJsonElement(value)
                    )
                } else node
            }
            val newFlow = currentState.flow.copy(nodes = updatedNodes)
            currentState.copy(
                flow = newFlow,
                hasUnsavedChanges = true
            )
        }
        runTypeInference()
    }

    fun handleUpdateBoundaryNode(
        nodeId: Long,
        portName: String,
        dataType: DataType,
        semanticTypes: List<SemanticType>,
        constraints: PortConstraints?,
        isList: Boolean
    ) {
        saveToHistory()
        state.update { currentState ->
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
                                isList = isList
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
            currentState.copy(
                flow = newFlow,
                hasUnsavedChanges = true
            )
        }
        runTypeInference()
    }

    fun handleUpdateSystemNodeSettings(
        nodeId: Long,
        portId: String,
        semanticTypes: List<SemanticType>,
        inputPortId: String?,
        extensions: List<String>?
    ) {
        saveToHistory()
        state.update { currentState ->
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
                                    port.constraints ?: org.wip.plugintoolkit.features.flows.model.PortConstraints()
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
            currentState.copy(
                flow = newFlow,
                nextId = currentState.nextId + 1,
                hasUnsavedChanges = true
            )
        }
        runTypeInference()
    }

    fun handleBringToFront(nodeId: Long) {
        state.update { currentState ->
            val node = currentState.flow.nodes.find { it.id == nodeId } ?: return@update currentState
            val isAlreadySelected = currentState.selectedNodeIds.contains(nodeId)
            val newSelection = if (isAlreadySelected) {
                currentState.selectedNodeIds
            } else {
                setOf(nodeId)
            }
            currentState.copy(
                flow = currentState.flow.copy(nodes = currentState.flow.nodes.filter { it.id != nodeId } + node),
                selectedNodeIds = newSelection
            )
        }
    }

    fun handleToggleNodeCollapse(nodeId: Long) {
        saveToHistory()
        state.update { currentState ->
            val updatedNodes = currentState.flow.nodes.map { node ->
                if (node.id == nodeId) {
                    val newState = !node.isCollapsed
                    node.copyWithCollapsedState(newState)
                        .copyWithInputsCollapsedState(newState)
                        .copyWithOutputsCollapsedState(newState)
                } else node
            }
            currentState.copy(flow = currentState.flow.copy(nodes = updatedNodes), hasUnsavedChanges = true)
        }
    }

    fun handleToggleNodeInputsCollapse(nodeId: Long) {
        saveToHistory()
        state.update { currentState ->
            val updatedNodes = currentState.flow.nodes.map { node ->
                if (node.id == nodeId) node.copyWithInputsCollapsedState(!node.isInputsCollapsed) else node
            }
            currentState.copy(flow = currentState.flow.copy(nodes = updatedNodes), hasUnsavedChanges = true)
        }
    }

    fun handleToggleNodeOutputsCollapse(nodeId: Long) {
        saveToHistory()
        state.update { currentState ->
            val updatedNodes = currentState.flow.nodes.map { node ->
                if (node.id == nodeId) node.copyWithOutputsCollapsedState(!node.isOutputsCollapsed) else node
            }
            currentState.copy(flow = currentState.flow.copy(nodes = updatedNodes), hasUnsavedChanges = true)
        }
    }

    fun findCloseConnection(
        node: Node,
        nodes: List<Node>,
        connections: List<Connection>,
        density: Float
    ): Pair<Connection, Pair<InputPort, OutputPort>>? {
        val nodeWidth = 300f * density
        val nodeHeight = 180f * density
        val nodeCenter = node.position + Offset(nodeWidth / 2f, nodeHeight / 2f)

        var closestConnection: Connection? = null
        var closestPorts: Pair<InputPort, OutputPort>? = null
        var minDistance = 60f * density

        connections.forEach { connection ->
            val sourceNode = nodes.find { it.id == connection.sourceNodeId } ?: return@forEach
            val targetNode = nodes.find { it.id == connection.targetNodeId } ?: return@forEach

            val sourcePortBoardPos =
                sourceNode.position + getPortRelativeOffset(sourceNode, connection.sourcePortId, density)
            val targetPortBoardPos =
                targetNode.position + getPortRelativeOffset(targetNode, connection.targetPortId, density)

            val dist = getDistanceToBezier(nodeCenter, sourcePortBoardPos, targetPortBoardPos)
            if (dist < minDistance) {
                val ports = isNodeCompatibleWithConnection(
                    node,
                    sourceNode,
                    connection.sourcePortId,
                    targetNode,
                    connection.targetPortId
                )
                if (ports != null) {
                    minDistance = dist
                    closestConnection = connection
                    closestPorts = ports
                }
            }
        }

        val conn = closestConnection
        val pts = closestPorts
        if (conn != null && pts != null) {
            return Pair(conn, pts)
        }
        return null
    }

    fun getPortRelativeOffset(node: Node, portId: String, density: Float): Offset {
        val headerHeight = 48f
        val bodyTopPadding = 12f
        val rowHeight = 48f
        val spacing = 12f
        val dividerHeight = 0.5f

        val isInput = node.inputs.any { it.id == portId }
        val isOutput = node.outputs.any { it.id == portId }

        val xDp = if (isInput) 19f else 281f

        var yDp = headerHeight + bodyTopPadding
        val numInputs = node.inputs.size

        if (isInput) {
            val index = node.inputs.indexOfFirst { it.id == portId }
            if (index != -1) {
                yDp += index * (rowHeight + spacing) + (rowHeight / 2f)
            }
        } else if (isOutput) {
            val index = node.outputs.indexOfFirst { it.id == portId }
            if (index != -1) {
                var precedingHeight = numInputs * rowHeight
                var numGaps = numInputs
                if (numInputs > 0) {
                    precedingHeight += dividerHeight
                    numGaps += 1
                }
                yDp += precedingHeight + (numGaps + index) * spacing + index * rowHeight + (rowHeight / 2f)
            }
        }

        return Offset(xDp * density, yDp * density)
    }

    fun getDistanceToBezier(p: Offset, start: Offset, end: Offset): Float {
        val controlPointOffset = kotlin.math.abs(end.x - start.x) / 2f
        val c1 = Offset(start.x + controlPointOffset, start.y)
        val c2 = Offset(end.x - controlPointOffset, end.y)

        var minDistance = Float.MAX_VALUE
        val steps = 30
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val mt = 1f - t
            val bt = start * (mt * mt * mt) +
                    c1 * (3f * mt * mt * t) +
                    c2 * (3f * mt * t * t) +
                    end * (t * t * t)
            val dist = (p - bt).getDistance()
            if (dist < minDistance) {
                minDistance = dist
            }
        }
        return minDistance
    }

    fun isNodeCompatibleWithConnection(
        node: Node,
        sourceNode: Node,
        sourcePortId: String,
        targetNode: Node,
        targetPortId: String
    ): Pair<InputPort, OutputPort>? {
        val sourcePort = sourceNode.outputs.find { it.id == sourcePortId } ?: return null
        val targetPort = targetNode.inputs.find { it.id == targetPortId } ?: return null

        val compatibleInput = node.inputs.firstOrNull { input ->
            (sourcePort.dataType.isCompatibleWith(input.dataType) || sourcePort.dataType.canConvert(input.dataType)) &&
                    org.wip.plugintoolkit.api.checkSemanticCompatibility(sourcePort.semanticTypes, input.semanticTypes) !is org.wip.plugintoolkit.api.CompatibilityResult.Incompatible
        } ?: return null

        val compatibleOutput = node.outputs.firstOrNull { output ->
            (output.dataType.isCompatibleWith(targetPort.dataType) || output.dataType.canConvert(targetPort.dataType)) &&
                    org.wip.plugintoolkit.api.checkSemanticCompatibility(output.semanticTypes, targetPort.semanticTypes) !is org.wip.plugintoolkit.api.CompatibilityResult.Incompatible
        } ?: return null

        return Pair(compatibleInput, compatibleOutput)
    }

}
