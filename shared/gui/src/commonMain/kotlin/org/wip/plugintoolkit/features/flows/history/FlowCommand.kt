package org.wip.plugintoolkit.features.flows.history

import kotlinx.serialization.json.JsonElement
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.SemanticType
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.FlowGroup
import org.wip.plugintoolkit.features.flows.model.FlowJunction
import org.wip.plugintoolkit.features.flows.model.FlowLabel
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.model.Offset as ModelOffset
import org.wip.plugintoolkit.features.flows.model.PortConstraints
import org.wip.plugintoolkit.features.flows.viewmodel.FlowEditorState

/**
 * Command representing an undoable/redoable state transformation on [FlowEditorState].
 */
interface FlowCommand {
    val description: String
    fun execute(state: FlowEditorState): FlowEditorState
    fun undo(state: FlowEditorState): FlowEditorState
}

/**
 * Command recording node translations. Storing only previous and new offsets avoids
 * retaining full Flow graph copies on drag operations.
 */
data class MoveNodesCommand(
    private val moves: Map<Long, Pair<ModelOffset, ModelOffset>>
) : FlowCommand {
    override val description: String = "Move ${moves.size} node(s)"

    override fun execute(state: FlowEditorState): FlowEditorState {
        val updated = state.flow.nodes.map { node ->
            val move = moves[node.id]
            if (move != null) node.copyWithPosition(move.second) else node
        }
        return state.copy(flow = state.flow.copy(nodes = updated), hasUnsavedChanges = true)
    }

    override fun undo(state: FlowEditorState): FlowEditorState {
        val updated = state.flow.nodes.map { node ->
            val move = moves[node.id]
            if (move != null) node.copyWithPosition(move.first) else node
        }
        return state.copy(flow = state.flow.copy(nodes = updated), hasUnsavedChanges = true)
    }
}

/**
 * Command recording multi-element translations (Nodes, Groups, Labels) in a single atomic transaction.
 */
data class MoveBoardElementsCommand(
    val nodeMoves: Map<Long, Pair<ModelOffset, ModelOffset>> = emptyMap(),
    val groupMoves: Map<Long, Pair<ModelOffset, ModelOffset>> = emptyMap(),
    val labelMoves: Map<Long, Pair<ModelOffset, ModelOffset>> = emptyMap()
) : FlowCommand {
    override val description: String = "Move ${nodeMoves.size + groupMoves.size + labelMoves.size} element(s)"

    override fun execute(state: FlowEditorState): FlowEditorState {
        val updatedNodes = state.flow.nodes.map { node ->
            val move = nodeMoves[node.id]
            if (move != null) node.copyWithPosition(move.second) else node
        }
        val updatedGroups = state.flow.groups.map { group ->
            val move = groupMoves[group.id]
            if (move != null) group.copy(position = move.second) else group
        }
        val updatedLabels = state.flow.labels.map { label ->
            val move = labelMoves[label.id]
            if (move != null) label.copy(position = move.second) else label
        }
        return state.copy(
            flow = state.flow.copy(nodes = updatedNodes, groups = updatedGroups, labels = updatedLabels),
            hasUnsavedChanges = true
        )
    }

    override fun undo(state: FlowEditorState): FlowEditorState {
        val updatedNodes = state.flow.nodes.map { node ->
            val move = nodeMoves[node.id]
            if (move != null) node.copyWithPosition(move.first) else node
        }
        val updatedGroups = state.flow.groups.map { group ->
            val move = groupMoves[group.id]
            if (move != null) group.copy(position = move.first) else group
        }
        val updatedLabels = state.flow.labels.map { label ->
            val move = labelMoves[label.id]
            if (move != null) label.copy(position = move.first) else label
        }
        return state.copy(
            flow = state.flow.copy(nodes = updatedNodes, groups = updatedGroups, labels = updatedLabels),
            hasUnsavedChanges = true
        )
    }
}

/**
 * Command recording node additions.
 */
data class AddNodeCommand(
    private val node: Node
) : FlowCommand {
    override val description: String = "Add node '${node.title}'"

    override fun execute(state: FlowEditorState): FlowEditorState {
        val newNodes = state.flow.nodes + node
        val nextId = maxOf(state.nextId, node.id + 1)
        return state.copy(
            flow = state.flow.copy(nodes = newNodes),
            nextId = nextId,
            hasUnsavedChanges = true
        )
    }

    override fun undo(state: FlowEditorState): FlowEditorState {
        val newNodes = state.flow.nodes.filter { it.id != node.id }
        val newConnections = state.flow.connections.filter {
            it.sourceNodeId != node.id && it.targetNodeId != node.id
        }
        return state.copy(
            flow = state.flow.copy(nodes = newNodes, connections = newConnections),
            hasUnsavedChanges = true
        )
    }
}

/**
 * Command recording node deletions alongside all cascading connections removed.
 */
data class DeleteNodesCommand(
    private val deletedNodes: List<Node>,
    private val cascadeConnections: List<Connection>
) : FlowCommand {
    override val description: String = "Delete ${deletedNodes.size} node(s)"

    private val deletedNodeIds = deletedNodes.map { it.id }.toSet()

    override fun execute(state: FlowEditorState): FlowEditorState {
        val newNodes = state.flow.nodes.filter { it.id !in deletedNodeIds }
        val newConnections = state.flow.connections.filter {
            it.sourceNodeId !in deletedNodeIds && it.targetNodeId !in deletedNodeIds
        }
        return state.copy(
            flow = state.flow.copy(nodes = newNodes, connections = newConnections),
            selectedNodeIds = state.selectedNodeIds - deletedNodeIds,
            hasUnsavedChanges = true
        )
    }

    override fun undo(state: FlowEditorState): FlowEditorState {
        return state.copy(
            flow = state.flow.copy(
                nodes = state.flow.nodes + deletedNodes,
                connections = state.flow.connections + cascadeConnections
            ),
            hasUnsavedChanges = true
        )
    }
}

/**
 * Command recording addition of a connection.
 */
data class ConnectPortsCommand(
    private val connection: Connection,
    private val overwrittenConnections: List<Connection> = emptyList()
) : FlowCommand {
    override val description: String = "Connect port"

    override fun execute(state: FlowEditorState): FlowEditorState {
        val filtered = if (overwrittenConnections.isNotEmpty()) {
            state.flow.connections.filter { it !in overwrittenConnections }
        } else {
            state.flow.connections
        }
        if (filtered.any { it == connection }) return state
        return state.copy(
            flow = state.flow.copy(connections = filtered + connection),
            hasUnsavedChanges = true
        )
    }

    override fun undo(state: FlowEditorState): FlowEditorState {
        val withoutNew = state.flow.connections.filter { it != connection }
        return state.copy(
            flow = state.flow.copy(connections = withoutNew + overwrittenConnections),
            hasUnsavedChanges = true
        )
    }
}

/**
 * Command recording deletion of a connection.
 */
data class DisconnectPortsCommand(
    private val connection: Connection
) : FlowCommand {
    override val description: String = "Delete connection"

    override fun execute(state: FlowEditorState): FlowEditorState {
        return state.copy(
            flow = state.flow.copy(connections = state.flow.connections.filter { it != connection }),
            hasUnsavedChanges = true
        )
    }

    override fun undo(state: FlowEditorState): FlowEditorState {
        return state.copy(
            flow = state.flow.copy(connections = state.flow.connections + connection),
            hasUnsavedChanges = true
        )
    }
}

/**
 * Command recording port input value changes.
 */
data class UpdateInputPortValueCommand(
    private val nodeId: Long,
    private val portId: String,
    private val oldValue: JsonElement?,
    private val newValue: JsonElement?
) : FlowCommand {
    override val description: String = "Update port value"

    override fun execute(state: FlowEditorState): FlowEditorState {
        val updated = state.flow.nodes.map { node ->
            if (node.id == nodeId) node.copyWithUpdatedInput(portId, newValue) else node
        }
        return state.copy(flow = state.flow.copy(nodes = updated), hasUnsavedChanges = true)
    }

    override fun undo(state: FlowEditorState): FlowEditorState {
        val updated = state.flow.nodes.map { node ->
            if (node.id == nodeId) node.copyWithUpdatedInput(portId, oldValue) else node
        }
        return state.copy(flow = state.flow.copy(nodes = updated), hasUnsavedChanges = true)
    }
}

/**
 * Command recording port input default value changes.
 */
data class UpdateInputPortDefaultCommand(
    private val nodeId: Long,
    private val portId: String,
    private val oldDefault: Any?,
    private val newDefault: Any?
) : FlowCommand {
    override val description: String = "Update port default"

    override fun execute(state: FlowEditorState): FlowEditorState {
        val updated = state.flow.nodes.map { node ->
            if (node.id == nodeId) node.copyWithUpdatedInputDefault(portId, newDefault) else node
        }
        return state.copy(flow = state.flow.copy(nodes = updated), hasUnsavedChanges = true)
    }

    override fun undo(state: FlowEditorState): FlowEditorState {
        val updated = state.flow.nodes.map { node ->
            if (node.id == nodeId) node.copyWithUpdatedInputDefault(portId, oldDefault) else node
        }
        return state.copy(flow = state.flow.copy(nodes = updated), hasUnsavedChanges = true)
    }
}


/**
 * Command recording boundary node definition modifications.
 */
data class UpdateBoundaryNodeCommand(
    private val nodeId: Long,
    private val oldNode: Node,
    private val newNode: Node
) : FlowCommand {
    override val description: String = "Update boundary node"

    override fun execute(state: FlowEditorState): FlowEditorState {
        val updated = state.flow.nodes.map { if (it.id == nodeId) newNode else it }
        return state.copy(flow = state.flow.copy(nodes = updated), hasUnsavedChanges = true)
    }

    override fun undo(state: FlowEditorState): FlowEditorState {
        val updated = state.flow.nodes.map { if (it.id == nodeId) oldNode else it }
        return state.copy(flow = state.flow.copy(nodes = updated), hasUnsavedChanges = true)
    }
}

/**
 * Command recording system node configuration modifications.
 */
data class UpdateSystemNodeSettingsCommand(
    private val nodeId: Long,
    private val oldNode: Node,
    private val newNode: Node
) : FlowCommand {
    override val description: String = "Update system node settings"

    override fun execute(state: FlowEditorState): FlowEditorState {
        val updated = state.flow.nodes.map { if (it.id == nodeId) newNode else it }
        return state.copy(flow = state.flow.copy(nodes = updated), hasUnsavedChanges = true)
    }

    override fun undo(state: FlowEditorState): FlowEditorState {
        val updated = state.flow.nodes.map { if (it.id == nodeId) oldNode else it }
        return state.copy(flow = state.flow.copy(nodes = updated), hasUnsavedChanges = true)
    }
}

/**
 * Command recording connection reordering.
 */
data class UpdateConnectionOrderCommand(
    private val oldConnections: List<Connection>,
    private val newConnections: List<Connection>
) : FlowCommand {
    override val description: String = "Update connection order"

    override fun execute(state: FlowEditorState): FlowEditorState =
        state.copy(flow = state.flow.copy(connections = newConnections), hasUnsavedChanges = true)

    override fun undo(state: FlowEditorState): FlowEditorState =
        state.copy(flow = state.flow.copy(connections = oldConnections), hasUnsavedChanges = true)
}

/**
 * Generic command recording node modifications (collapsing, property changes, etc.).
 */
data class UpdateNodeCommand(
    private val nodeId: Long,
    private val oldNode: Node,
    private val newNode: Node,
    override val description: String = "Update node"
) : FlowCommand {
    override fun execute(state: FlowEditorState): FlowEditorState {
        val updated = state.flow.nodes.map { if (it.id == nodeId) newNode else it }
        return state.copy(flow = state.flow.copy(nodes = updated), hasUnsavedChanges = true)
    }

    override fun undo(state: FlowEditorState): FlowEditorState {
        val updated = state.flow.nodes.map { if (it.id == nodeId) oldNode else it }
        return state.copy(flow = state.flow.copy(nodes = updated), hasUnsavedChanges = true)
    }
}

/**
 * Composite transaction command grouping multiple sub-commands atomically.
 */
data class CompositeCommand(
    override val description: String,
    private val commands: List<FlowCommand>
) : FlowCommand {
    override fun execute(state: FlowEditorState): FlowEditorState =
        commands.fold(state) { current, cmd -> cmd.execute(current) }

    override fun undo(state: FlowEditorState): FlowEditorState =
        commands.asReversed().fold(state) { current, cmd -> cmd.undo(current) }
}

/**
 * Command for adding a visual group container.
 */
data class AddGroupCommand(
    private val group: FlowGroup
) : FlowCommand {
    override val description: String = "Add group '${group.title}'"
    override fun execute(state: FlowEditorState): FlowEditorState =
        state.copy(flow = state.flow.copy(groups = state.flow.groups + group), hasUnsavedChanges = true)
    override fun undo(state: FlowEditorState): FlowEditorState =
        state.copy(flow = state.flow.copy(groups = state.flow.groups.filter { it.id != group.id }), hasUnsavedChanges = true)
}

/**
 * Command for deleting a visual group container.
 */
data class DeleteGroupCommand(
    private val group: FlowGroup
) : FlowCommand {
    override val description: String = "Delete group '${group.title}'"
    override fun execute(state: FlowEditorState): FlowEditorState =
        state.copy(flow = state.flow.copy(groups = state.flow.groups.filter { it.id != group.id }), hasUnsavedChanges = true)
    override fun undo(state: FlowEditorState): FlowEditorState =
        state.copy(flow = state.flow.copy(groups = state.flow.groups + group), hasUnsavedChanges = true)
}

/**
 * Command for updating group properties (title, color, collapse state, etc.).
 */
data class UpdateGroupCommand(
    private val oldGroup: FlowGroup,
    private val newGroup: FlowGroup
) : FlowCommand {
    override val description: String = "Update group '${newGroup.title}'"
    override fun execute(state: FlowEditorState): FlowEditorState =
        state.copy(flow = state.flow.copy(groups = state.flow.groups.map { if (it.id == newGroup.id) newGroup else it }), hasUnsavedChanges = true)
    override fun undo(state: FlowEditorState): FlowEditorState =
        state.copy(flow = state.flow.copy(groups = state.flow.groups.map { if (it.id == oldGroup.id) oldGroup else it }), hasUnsavedChanges = true)
}

/**
 * Command for moving a group.
 */
data class MoveGroupCommand(
    private val groupId: Long,
    private val oldPos: ModelOffset,
    private val newPos: ModelOffset,
    private val movedNodeIds: Set<Long> = emptySet()
) : FlowCommand {
    override val description: String = "Move group"
    override fun execute(state: FlowEditorState): FlowEditorState {
        val delta = newPos - oldPos
        val updatedNodes = if (movedNodeIds.isNotEmpty()) {
            state.flow.nodes.map {
                if (it.id in movedNodeIds) it.copyWithPosition(it.position + delta) else it
            }
        } else state.flow.nodes
        return state.copy(
            flow = state.flow.copy(
                groups = state.flow.groups.map { if (it.id == groupId) it.copy(position = newPos) else it },
                nodes = updatedNodes
            ),
            hasUnsavedChanges = true
        )
    }
    override fun undo(state: FlowEditorState): FlowEditorState {
        val delta = newPos - oldPos
        val updatedNodes = if (movedNodeIds.isNotEmpty()) {
            state.flow.nodes.map {
                if (it.id in movedNodeIds) it.copyWithPosition(it.position - delta) else it
            }
        } else state.flow.nodes
        return state.copy(
            flow = state.flow.copy(
                groups = state.flow.groups.map { if (it.id == groupId) it.copy(position = oldPos) else it },
                nodes = updatedNodes
            ),
            hasUnsavedChanges = true
        )
    }
}

/**
 * Command for adding a text label/note.
 */
data class AddLabelCommand(
    private val label: FlowLabel
) : FlowCommand {
    override val description: String = "Add label"
    override fun execute(state: FlowEditorState): FlowEditorState =
        state.copy(flow = state.flow.copy(labels = state.flow.labels + label), hasUnsavedChanges = true)
    override fun undo(state: FlowEditorState): FlowEditorState =
        state.copy(flow = state.flow.copy(labels = state.flow.labels.filter { it.id != label.id }), hasUnsavedChanges = true)
}

/**
 * Command for deleting a text label/note.
 */
data class DeleteLabelCommand(
    private val label: FlowLabel
) : FlowCommand {
    override val description: String = "Delete label"
    override fun execute(state: FlowEditorState): FlowEditorState =
        state.copy(flow = state.flow.copy(labels = state.flow.labels.filter { it.id != label.id }), hasUnsavedChanges = true)
    override fun undo(state: FlowEditorState): FlowEditorState =
        state.copy(flow = state.flow.copy(labels = state.flow.labels + label), hasUnsavedChanges = true)
}

/**
 * Command for updating label text, font size, or color.
 */
data class UpdateLabelCommand(
    private val oldLabel: FlowLabel,
    private val newLabel: FlowLabel
) : FlowCommand {
    override val description: String = "Update label"
    override fun execute(state: FlowEditorState): FlowEditorState =
        state.copy(flow = state.flow.copy(labels = state.flow.labels.map { if (it.id == newLabel.id) newLabel else it }), hasUnsavedChanges = true)
    override fun undo(state: FlowEditorState): FlowEditorState =
        state.copy(flow = state.flow.copy(labels = state.flow.labels.map { if (it.id == oldLabel.id) oldLabel else it }), hasUnsavedChanges = true)
}

/**
 * Command for moving a label.
 */
data class MoveLabelCommand(
    private val labelId: Long,
    private val oldPos: ModelOffset,
    private val newPos: ModelOffset
) : FlowCommand {
    override val description: String = "Move label"
    override fun execute(state: FlowEditorState): FlowEditorState =
        state.copy(flow = state.flow.copy(labels = state.flow.labels.map { if (it.id == labelId) it.copy(position = newPos) else it }), hasUnsavedChanges = true)
    override fun undo(state: FlowEditorState): FlowEditorState =
        state.copy(flow = state.flow.copy(labels = state.flow.labels.map { if (it.id == labelId) it.copy(position = oldPos) else it }), hasUnsavedChanges = true)
}

/**
 * Command for adding a flow junction point.
 */
data class AddJunctionCommand(
    private val junction: FlowJunction,
    private val originalConnection: Connection? = null,
    private val createdConnections: List<Connection> = emptyList()
) : FlowCommand {
    override val description: String = "Add junction"
    override fun execute(state: FlowEditorState): FlowEditorState {
        val updatedConns = if (originalConnection != null) {
            state.flow.connections.filter { it != originalConnection } + createdConnections
        } else state.flow.connections
        return state.copy(
            flow = state.flow.copy(
                junctions = state.flow.junctions + junction,
                connections = updatedConns
            ),
            hasUnsavedChanges = true
        )
    }
    override fun undo(state: FlowEditorState): FlowEditorState {
        val updatedConns = if (originalConnection != null) {
            state.flow.connections.filter { it !in createdConnections } + originalConnection
        } else {
            state.flow.connections.filter { it.sourceJunctionId != junction.id && it.targetJunctionId != junction.id }
        }
        return state.copy(
            flow = state.flow.copy(
                junctions = state.flow.junctions.filter { it.id != junction.id },
                connections = updatedConns
            ),
            hasUnsavedChanges = true
        )
    }
}

/**
 * Command for deleting a flow junction point and its connected segments.
 */
data class DeleteJunctionCommand(
    private val junction: FlowJunction,
    private val cascadingConnections: List<Connection> = emptyList()
) : FlowCommand {
    override val description: String = "Delete junction"
    override fun execute(state: FlowEditorState): FlowEditorState =
        state.copy(
            flow = state.flow.copy(
                junctions = state.flow.junctions.filter { it.id != junction.id },
                connections = state.flow.connections.filter { it !in cascadingConnections }
            ),
            hasUnsavedChanges = true
        )
    override fun undo(state: FlowEditorState): FlowEditorState =
        state.copy(
            flow = state.flow.copy(
                junctions = state.flow.junctions + junction,
                connections = state.flow.connections + cascadingConnections
            ),
            hasUnsavedChanges = true
        )
}

/**
 * Command for moving a flow junction.
 */
data class MoveJunctionCommand(
    private val junctionId: Long,
    private val oldPos: ModelOffset,
    private val newPos: ModelOffset
) : FlowCommand {
    override val description: String = "Move junction"
    override fun execute(state: FlowEditorState): FlowEditorState =
        state.copy(flow = state.flow.copy(junctions = state.flow.junctions.map { if (it.id == junctionId) it.copy(position = newPos) else it }), hasUnsavedChanges = true)
    override fun undo(state: FlowEditorState): FlowEditorState =
        state.copy(flow = state.flow.copy(junctions = state.flow.junctions.map { if (it.id == junctionId) it.copy(position = oldPos) else it }), hasUnsavedChanges = true)
}

/**
 * Command for painting flow elements (nodes, connections, groups, labels) with undo/redo support.
 */
data class PaintElementsCommand(
    private val oldFlow: Flow,
    private val newFlow: Flow,
    override val description: String = "Paint elements"
) : FlowCommand {
    override fun execute(state: FlowEditorState): FlowEditorState =
        state.copy(flow = newFlow, hasUnsavedChanges = true)
    override fun undo(state: FlowEditorState): FlowEditorState =
        state.copy(flow = oldFlow, hasUnsavedChanges = true)
}

/**
 * Command for resizing a visual group container.
 */
data class ResizeGroupCommand(
    private val groupId: Long,
    private val oldSize: ModelOffset,
    private val newSize: ModelOffset
) : FlowCommand {
    override val description: String = "Resize group"
    override fun execute(state: FlowEditorState): FlowEditorState =
        state.copy(flow = state.flow.copy(groups = state.flow.groups.map { if (it.id == groupId) it.copy(size = newSize) else it }), hasUnsavedChanges = true)
    override fun undo(state: FlowEditorState): FlowEditorState =
        state.copy(flow = state.flow.copy(groups = state.flow.groups.map { if (it.id == groupId) it.copy(size = oldSize) else it }), hasUnsavedChanges = true)
}

/**
 * Command for updating connection waypoints (green routing points).
 */
data class UpdateWaypointsCommand(
    private val connection: Connection,
    private val oldWaypoints: List<ModelOffset>,
    private val newWaypoints: List<ModelOffset>
) : FlowCommand {
    override val description: String = "Update waypoints"
    override fun execute(state: FlowEditorState): FlowEditorState =
        state.copy(flow = state.flow.copy(connections = state.flow.connections.map {
            if (it == connection || (it.sourceNodeId == connection.sourceNodeId && it.sourcePortId == connection.sourcePortId && it.targetNodeId == connection.targetNodeId && it.targetPortId == connection.targetPortId)) {
                it.copy(waypoints = newWaypoints)
            } else it
        }), hasUnsavedChanges = true)
    override fun undo(state: FlowEditorState): FlowEditorState =
        state.copy(flow = state.flow.copy(connections = state.flow.connections.map {
            if (it == connection || (it.sourceNodeId == connection.sourceNodeId && it.sourcePortId == connection.sourcePortId && it.targetNodeId == connection.targetNodeId && it.targetPortId == connection.targetPortId)) {
                it.copy(waypoints = oldWaypoints)
            } else it
        }), hasUnsavedChanges = true)
}

