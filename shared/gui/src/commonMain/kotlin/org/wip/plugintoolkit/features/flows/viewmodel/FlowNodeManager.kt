package org.wip.plugintoolkit.features.flows.viewmodel

import org.wip.plugintoolkit.api.CommonSemanticTypes
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.SemanticType
import org.wip.plugintoolkit.api.canConvert
import org.wip.plugintoolkit.api.isCompatibleWith
import org.wip.plugintoolkit.features.flows.model.BoardElement
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.ConnectionPoint
import org.wip.plugintoolkit.features.flows.model.FlowGroup
import org.wip.plugintoolkit.features.flows.model.FlowLabel
import org.wip.plugintoolkit.features.flows.model.InputPort
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.model.Offset as ModelOffset
import org.wip.plugintoolkit.features.flows.model.OutputPort
import org.wip.plugintoolkit.features.flows.model.PortConstraints
import org.wip.plugintoolkit.features.flows.ui.snapToGrid
import org.wip.plugintoolkit.features.flows.ui.toModelOffset

class FlowNodeManager {

    private fun isPointInGroup(x: Float, y: Float, group: FlowGroup): Boolean =
        group.containsPoint(x, y)

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
        delta: ModelOffset,
        snap: Boolean,
        showGhost: Boolean
    ): FlowEditorState {
        val newOffset = currentState.currentDragOffset + delta
        val node = currentState.flow.nodes.find { it.id == id }
        val ghostToSet = if (showGhost && node != null) (node.position + newOffset).snapToGrid() else null

        var capturedJunctions = currentState.capturedJunctionIds
        var capturedWaypoints = currentState.capturedWaypoints
        if (currentState.draggedNodeId == null) {
            val isGroup = currentState.flow.groups.any { it.id == id }
            val isSelectedMove = (isGroup && currentState.selectedGroupIds.contains(id)) || 
                                 (currentState.flow.nodes.any { it.id == id } && currentState.selectedNodeIds.contains(id)) ||
                                 (currentState.flow.labels.any { it.id == id } && currentState.selectedLabelIds.contains(id)) ||
                                 (currentState.flow.junctions.any { it.id == id } && currentState.selectedPointIds.contains(id))
            val groupsToMove = if (isSelectedMove) currentState.selectedGroupIds else if (isGroup) setOf(id) else emptySet()

            val targetGroups = currentState.flow.groups.filter { it.id in groupsToMove }
            if (targetGroups.isNotEmpty()) {
                capturedJunctions = currentState.flow.junctions.filter { junc ->
                    targetGroups.any { grp -> isPointInGroup(junc.position.x, junc.position.y, grp) }
                }.map { it.id }.toSet()
                
                val waypointsMap = mutableMapOf<Connection, Set<Int>>()
                for (conn in currentState.flow.connections) {
                    val wpsInside = conn.waypoints.mapIndexedNotNull { index, wp ->
                        if (targetGroups.any { grp -> isPointInGroup(wp.x, wp.y, grp) }) index else null
                    }.toSet()
                    if (wpsInside.isNotEmpty()) {
                        waypointsMap[conn] = wpsInside
                    }
                }
                capturedWaypoints = waypointsMap
            }
        }

        return currentState.copy(
            draggedNodeId = id,
            currentDragOffset = newOffset,
            ghostPosition = ghostToSet,
            capturedJunctionIds = capturedJunctions,
            capturedWaypoints = capturedWaypoints
        )
    }

    fun handleEndMoveNode(currentState: FlowEditorState, id: Long, density: Float): FlowEditorState {
        val finalOffset = currentState.currentDragOffset
        val isNode = currentState.flow.nodes.any { it.id == id }
        val isGroup = currentState.flow.groups.any { it.id == id }
        val isLabel = currentState.flow.labels.any { it.id == id }
        val isPoint = currentState.flow.junctions.any { it.id == id }

        val isSelectedMove = (isNode && currentState.selectedNodeIds.contains(id)) ||
                (isGroup && currentState.selectedGroupIds.contains(id)) ||
                (isLabel && currentState.selectedLabelIds.contains(id)) ||
                (isPoint && currentState.selectedPointIds.contains(id))

        val nodesToMove = (if (isSelectedMove) currentState.selectedNodeIds else if (isNode) setOf(id) else emptySet()).toMutableSet()
        val groupsToMove = if (isSelectedMove) currentState.selectedGroupIds else if (isGroup) setOf(id) else emptySet()
        val labelsToMove = if (isSelectedMove) currentState.selectedLabelIds else if (isLabel) setOf(id) else emptySet()
        val pointsToMove = (if (isSelectedMove) currentState.selectedPointIds else if (isPoint) setOf(id) else emptySet()) + currentState.capturedJunctionIds

        for (grpId in groupsToMove) {
            currentState.flow.groups.find { it.id == grpId }?.let { nodesToMove.addAll(it.nodeIds) }
        }

        val newPositions = currentState.flow.nodes.associate {
            it.id to if (nodesToMove.contains(it.id)) (it.position + finalOffset).snapToGrid() else it.position
        }

        val updatedNodes = currentState.flow.nodes.map { n ->
            val newPos = newPositions[n.id]!!
            if (newPos != n.position) n.copyWithPosition(newPos) else n
        }

        val updatedGroups = currentState.flow.groups.map { grp ->
            if (groupsToMove.contains(grp.id)) {
                grp.copy(position = (grp.position + finalOffset).snapToGrid())
            } else grp
        }

        val updatedLabels = currentState.flow.labels.map { lbl ->
            if (labelsToMove.contains(lbl.id)) {
                lbl.copy(position = (lbl.position + finalOffset).snapToGrid())
            } else lbl
        }

        val updatedJunctions = currentState.flow.junctions.map { junc ->
            if (pointsToMove.contains(junc.id)) {
                junc.copy(position = (junc.position + finalOffset).snapToGrid())
            } else junc
        }
        
        val updatedConnections = currentState.flow.connections.map { conn ->
            val capturedWps = currentState.capturedWaypoints[conn]
            if (capturedWps != null && capturedWps.isNotEmpty()) {
                val newWps = conn.waypoints.mapIndexed { index, wp ->
                    if (capturedWps.contains(index)) (wp + finalOffset).snapToGrid() else wp
                }
                conn.copy(waypoints = newWps)
            } else conn
        }

        val reorderedNodes = updatedNodes.filter { !nodesToMove.contains(it.id) } + updatedNodes.filter { nodesToMove.contains(it.id) }
        val newFlow = currentState.flow.copy(nodes = reorderedNodes, groups = updatedGroups, labels = updatedLabels, junctions = updatedJunctions, connections = updatedConnections)
        val (newSelectedNodes, newSelectedGroups, newSelectedLabels, newSelectedPoints) = if (isSelectedMove) {
            listOf(currentState.selectedNodeIds, currentState.selectedGroupIds, currentState.selectedLabelIds, currentState.selectedPointIds)
        } else when {
            isNode -> listOf(setOf(id), emptySet(), emptySet(), emptySet())
            isGroup -> listOf(emptySet(), setOf(id), emptySet(), emptySet())
            isLabel -> listOf(emptySet(), emptySet(), setOf(id), emptySet())
            isPoint -> listOf(emptySet(), emptySet(), emptySet(), setOf(id))
            else -> listOf(currentState.selectedNodeIds, currentState.selectedGroupIds, currentState.selectedLabelIds, currentState.selectedPointIds)
        }

        return currentState.copy(
            flow = newFlow,
            selectedNodeIds = newSelectedNodes,
            selectedGroupIds = newSelectedGroups,
            selectedLabelIds = newSelectedLabels,
            selectedPointIds = newSelectedPoints,
            hasUnsavedChanges = true,
            draggedNodeId = null,
            currentDragOffset = org.wip.plugintoolkit.features.flows.model.Offset.Zero,
            ghostPosition = null,
            capturedJunctionIds = emptySet(),
            capturedWaypoints = emptyMap()
        )
    }

    fun handleDeleteBoardElement(currentState: FlowEditorState, id: Long): FlowEditorState {
        val updatedFlow = currentState.flow.withoutBoardElement(id)
        val newValidationErrors = currentState.validationErrors.filter {
            it.sourceNodeId != id && it.targetNodeId != id
        }
        val newPendingConnection = currentState.pendingConnection?.let {
            if (it.sourceNodeId == id || it.targetNodeId == id) null else it
        }

        return currentState.copy(
            flow = updatedFlow,
            validationErrors = newValidationErrors,
            pendingConnection = newPendingConnection,
            selectedNodeIds = currentState.selectedNodeIds - id,
            selectedGroupIds = currentState.selectedGroupIds - id,
            selectedLabelIds = currentState.selectedLabelIds - id,
            selectedPointIds = currentState.selectedPointIds - id,
            hasUnsavedChanges = true
        )
    }

    fun handleDeleteNode(currentState: FlowEditorState, id: Long): FlowEditorState {
        val newFlow = currentState.flow.copy(
            nodes = currentState.flow.nodes.filter { it.id != id },
            connections = currentState.flow.connections.filter { it.sourceNodeId != id && it.targetNodeId != id },
            groups = currentState.flow.groups.map { g -> g.copy(nodeIds = g.nodeIds.filter { it != id }) }
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
            },
            groups = currentState.flow.groups.map { g -> g.copy(nodeIds = g.nodeIds.filter { it !in selectedIds }) }
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
            selectedNodeIds = setOf(nodeId),
            selectedPointIds = emptySet(),
            selectedGroupIds = emptySet(),
            selectedLabelIds = emptySet(),
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
        isRequired: Boolean,
        defaultValue: Any? = null
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
                            isRequired = isRequired,
                            defaultValue = defaultValue
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
            selectedNodeIds = setOf(nodeId),
            selectedPointIds = emptySet(),
            selectedGroupIds = emptySet(),
            selectedLabelIds = emptySet(),
            hasUnsavedChanges = true
        )
    }

    fun handleUpdateInputPortDefault(
        currentState: FlowEditorState,
        nodeId: Long,
        portId: String,
        defaultValue: Any?
    ): FlowEditorState {
        val updatedNodes = currentState.flow.nodes.map { node ->
            if (node.id == nodeId) {
                node.copyWithUpdatedInputDefault(portId, defaultValue)
            } else node
        }
        val newFlow = currentState.flow.copy(nodes = updatedNodes)
        return currentState.copy(
            flow = newFlow,
            selectedNodeIds = setOf(nodeId),
            selectedPointIds = emptySet(),
            selectedGroupIds = emptySet(),
            selectedLabelIds = emptySet(),
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
                            port.copy(
                                constraints = currentConstraints.copy(extensions = extensions),
                                semanticTypes = if (node.systemAction.lowercase() == "load") {
                                    listOf(CommonSemanticTypes.PATH_FILE)
                                } else {
                                    port.semanticTypes
                                }
                            )
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
            selectedNodeIds = setOf(nodeId),
            selectedPointIds = emptySet(),
            selectedGroupIds = emptySet(),
            selectedLabelIds = emptySet(),
            hasUnsavedChanges = true
        )
    }

    fun handleBringToFront(currentState: FlowEditorState, nodeId: Long, isCtrlPressed: Boolean = false): FlowEditorState {
        val element = currentState.flow.findBoardElement(nodeId) ?: return currentState
        return when (element) {
            is Node -> {
                val isAlreadySelected = currentState.selectedNodeIds.contains(nodeId)
                val newSelection = if (isCtrlPressed) {
                    if (isAlreadySelected) currentState.selectedNodeIds - nodeId else currentState.selectedNodeIds + nodeId
                } else if (isAlreadySelected) {
                    currentState.selectedNodeIds
                } else {
                    setOf(nodeId)
                }
                currentState.copy(
                    flow = currentState.flow.copy(nodes = currentState.flow.nodes.filter { it.id != nodeId } + element),
                    selectedNodeIds = newSelection,
                    selectedPointIds = if (isCtrlPressed || isAlreadySelected) currentState.selectedPointIds else emptySet(),
                    selectedGroupIds = if (isCtrlPressed || isAlreadySelected) currentState.selectedGroupIds else emptySet(),
                    selectedLabelIds = if (isCtrlPressed || isAlreadySelected) currentState.selectedLabelIds else emptySet()
                )
            }
            is FlowGroup -> {
                val isAlreadySelected = currentState.selectedGroupIds.contains(nodeId)
                val newSelection = if (isCtrlPressed) {
                    if (isAlreadySelected) currentState.selectedGroupIds - nodeId else currentState.selectedGroupIds + nodeId
                } else if (isAlreadySelected) {
                    currentState.selectedGroupIds
                } else {
                    setOf(nodeId)
                }
                currentState.copy(
                    flow = currentState.flow.copy(groups = currentState.flow.groups.filter { it.id != nodeId } + element),
                    selectedGroupIds = newSelection,
                    selectedNodeIds = if (isCtrlPressed || isAlreadySelected) currentState.selectedNodeIds else emptySet(),
                    selectedPointIds = if (isCtrlPressed || isAlreadySelected) currentState.selectedPointIds else emptySet(),
                    selectedLabelIds = if (isCtrlPressed || isAlreadySelected) currentState.selectedLabelIds else emptySet()
                )
            }
            is FlowLabel -> {
                val isAlreadySelected = currentState.selectedLabelIds.contains(nodeId)
                val newSelection = if (isCtrlPressed) {
                    if (isAlreadySelected) currentState.selectedLabelIds - nodeId else currentState.selectedLabelIds + nodeId
                } else if (isAlreadySelected) {
                    currentState.selectedLabelIds
                } else {
                    setOf(nodeId)
                }
                currentState.copy(
                    flow = currentState.flow.copy(labels = currentState.flow.labels.filter { it.id != nodeId } + element),
                    selectedLabelIds = newSelection,
                    selectedNodeIds = if (isCtrlPressed || isAlreadySelected) currentState.selectedNodeIds else emptySet(),
                    selectedPointIds = if (isCtrlPressed || isAlreadySelected) currentState.selectedPointIds else emptySet(),
                    selectedGroupIds = if (isCtrlPressed || isAlreadySelected) currentState.selectedGroupIds else emptySet()
                )
            }
            is ConnectionPoint -> {
                val isAlreadySelected = currentState.selectedPointIds.contains(nodeId)
                val newSelection = if (isCtrlPressed) {
                    if (isAlreadySelected) currentState.selectedPointIds - nodeId else currentState.selectedPointIds + nodeId
                } else if (isAlreadySelected) {
                    currentState.selectedPointIds
                } else {
                    setOf(nodeId)
                }
                currentState.copy(
                    selectedPointIds = newSelection,
                    selectedNodeIds = if (isCtrlPressed || isAlreadySelected) currentState.selectedNodeIds else emptySet(),
                    selectedGroupIds = if (isCtrlPressed || isAlreadySelected) currentState.selectedGroupIds else emptySet(),
                    selectedLabelIds = if (isCtrlPressed || isAlreadySelected) currentState.selectedLabelIds else emptySet()
                )
            }
            else -> currentState
        }
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

    fun handleMoveNode(
        currentState: FlowEditorState,
        id: Long,
        delta: androidx.compose.ui.geometry.Offset,
        snap: Boolean,
        showGhost: Boolean
    ): FlowEditorState = handleMoveNode(currentState, id, delta.toModelOffset(), snap, showGhost)

    fun handlePan(currentState: FlowEditorState, delta: ModelOffset): FlowEditorState {
        val newOffset = currentState.offset + delta
        return if (currentState.draggedNodeId != null) {
            val dragCorrection = delta / currentState.scale
            currentState.copy(
                offset = newOffset,
                currentDragOffset = currentState.currentDragOffset - dragCorrection
            )
        } else {
            currentState.copy(offset = newOffset)
        }
    }

    fun handlePan(currentState: FlowEditorState, delta: androidx.compose.ui.geometry.Offset): FlowEditorState =
        handlePan(currentState, delta.toModelOffset())

    fun handleRefreshNode(
        currentState: FlowEditorState,
        nodeId: Long,
        manifests: Map<String, org.wip.plugintoolkit.api.PluginManifest>
    ): Pair<FlowEditorState, Node.CapabilityNode?> {
        val node = currentState.flow.nodes.find { it.id == nodeId } as? Node.CapabilityNode ?: return Pair(currentState, null)
        val manifest = manifests[node.pluginInfo.id] ?: manifests.values.find { m ->
            m.capabilities.any { it.name == node.capability.name }
        } ?: return Pair(currentState, null)

        val refreshedNode = org.wip.plugintoolkit.features.flows.model.MigrationEngine.refreshCapabilityNode(node, manifest)
        val updatedNodes = currentState.flow.nodes.map { if (it.id == nodeId) refreshedNode else it }
        val newState = currentState.copy(
            flow = currentState.flow.copy(nodes = updatedNodes),
            hasUnsavedChanges = true
        )
        return Pair(newState, refreshedNode)
    }

    fun handleRefreshBrokenNodes(
        currentState: FlowEditorState,
        manifests: Map<String, org.wip.plugintoolkit.api.PluginManifest>
    ): Pair<FlowEditorState, Int> {
        val brokenNodes = currentState.flow.nodes.filterIsInstance<Node.CapabilityNode>().filter { it.isBroken }
        if (brokenNodes.isEmpty()) return Pair(currentState, 0)

        var healedCount = 0
        val updatedNodes = currentState.flow.nodes.map { node ->
            if (node is Node.CapabilityNode && node.isBroken) {
                val manifest = manifests[node.pluginInfo.id] ?: manifests.values.find { m ->
                    m.capabilities.any { it.name == node.capability.name }
                }
                if (manifest != null) {
                    val healed = org.wip.plugintoolkit.features.flows.model.MigrationEngine.refreshCapabilityNode(node, manifest)
                    if (!healed.isBroken) healedCount++
                    healed
                } else {
                    node
                }
            } else {
                node
            }
        }

        val newState = if (healedCount > 0) {
            currentState.copy(
                flow = currentState.flow.copy(nodes = updatedNodes),
                hasUnsavedChanges = true
            )
        } else {
            currentState
        }
        return Pair(newState, healedCount)
    }

    fun handleReplaceNode(
        currentState: FlowEditorState,
        nodeId: Long,
        targetNode: Node,
        inputMappings: Map<String, String?>,
        outputMappings: Map<String, String?>
    ): FlowEditorState {
        val oldNode = currentState.flow.nodes.find { it.id == nodeId } ?: return currentState
        val updatedNodes = currentState.flow.nodes.map { if (it.id == nodeId) targetNode else it }

        val remainingConnections = currentState.flow.connections.mapNotNull { conn ->
            if (conn.targetNodeId == nodeId) {
                val targetPort = inputMappings[conn.targetPortId]
                if (targetPort != null) {
                    conn.copy(targetPortId = targetPort)
                } else {
                    null
                }
            } else if (conn.sourceNodeId == nodeId) {
                val sourcePort = outputMappings[conn.sourcePortId]
                if (sourcePort != null) {
                    conn.copy(sourcePortId = sourcePort)
                } else {
                    null
                }
            } else {
                conn
            }
        }

        val updatedPendingConnection = currentState.pendingConnection?.let {
            if (it.sourceNodeId == nodeId || it.targetNodeId == nodeId) null else it
        }

        val updatedValidationErrors = currentState.validationErrors.filter {
            it.sourceNodeId != nodeId && it.targetNodeId != nodeId
        }

        return currentState.copy(
            flow = currentState.flow.copy(
                nodes = updatedNodes,
                connections = remainingConnections
            ),
            pendingConnection = updatedPendingConnection,
            validationErrors = updatedValidationErrors,
            hasUnsavedChanges = true
        )
    }
}
