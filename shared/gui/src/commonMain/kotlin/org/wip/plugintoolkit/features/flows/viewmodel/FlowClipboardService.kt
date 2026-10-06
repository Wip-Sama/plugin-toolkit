package org.wip.plugintoolkit.features.flows.viewmodel

import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.FlowGroup
import org.wip.plugintoolkit.features.flows.model.FlowJunction
import org.wip.plugintoolkit.features.flows.model.FlowLabel
import org.wip.plugintoolkit.features.flows.model.Node

/**
 * Snapshot of elements currently residing in the flow clipboard.
 */
data class FlowClipboardContent(
    val nodes: List<Node> = emptyList(),
    val connections: List<Connection> = emptyList(),
    val groups: List<FlowGroup> = emptyList(),
    val labels: List<FlowLabel> = emptyList(),
    val junctions: List<FlowJunction> = emptyList()
) {
    val isNotEmpty: Boolean
        get() = nodes.isNotEmpty() || groups.isNotEmpty() || labels.isNotEmpty() || junctions.isNotEmpty()

    val isEmpty: Boolean
        get() = !isNotEmpty
}

/**
 * Injected service managing copied graph elements for clipboard operations.
 * Eliminates static state pollution across tabs and tests.
 */
interface FlowClipboardService {
    fun copy(
        nodes: List<Node>,
        connections: List<Connection>,
        groups: List<FlowGroup>,
        labels: List<FlowLabel>,
        junctions: List<FlowJunction>
    )

    fun getContents(): FlowClipboardContent

    fun clear()

    fun hasContents(): Boolean
}

/**
 * In-memory implementation of [FlowClipboardService].
 */
class InMemoryFlowClipboardService : FlowClipboardService {
    private var content = FlowClipboardContent()

    override fun copy(
        nodes: List<Node>,
        connections: List<Connection>,
        groups: List<FlowGroup>,
        labels: List<FlowLabel>,
        junctions: List<FlowJunction>
    ) {
        content = FlowClipboardContent(
            nodes = nodes,
            connections = connections,
            groups = groups,
            labels = labels,
            junctions = junctions
        )
    }

    override fun getContents(): FlowClipboardContent = content

    override fun clear() {
        content = FlowClipboardContent()
    }

    override fun hasContents(): Boolean = content.isNotEmpty
}
