package org.wip.plugintoolkit.features.flows.history

import kotlinx.serialization.json.JsonElement
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.SemanticType
import org.wip.plugintoolkit.features.flows.model.Connection
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
