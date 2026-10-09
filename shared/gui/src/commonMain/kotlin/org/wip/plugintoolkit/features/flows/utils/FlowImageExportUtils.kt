package org.wip.plugintoolkit.features.flows.utils

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.FlowGroup
import org.wip.plugintoolkit.features.flows.model.FlowJunction
import org.wip.plugintoolkit.features.flows.model.FlowLabel
import org.wip.plugintoolkit.features.flows.model.FlowImageExportScope
import org.wip.plugintoolkit.features.flows.model.Node
import kotlin.math.max
import kotlin.math.min

/**
 * Filtered elements representing the subgraph to be exported.
 */
data class FilteredFlowElements(
    val nodes: List<Node>,
    val groups: List<FlowGroup>,
    val labels: List<FlowLabel>,
    val junctions: List<FlowJunction>,
    val connections: List<Connection>
) {
    val isEmpty: Boolean
        get() = nodes.isEmpty() && groups.isEmpty() && labels.isEmpty() && junctions.isEmpty()

    val totalElementsCount: Int
        get() = nodes.size + groups.size + labels.size + junctions.size
}

object FlowImageExportUtils {

    const val DEFAULT_NODE_WIDTH: Float = 400f
    const val DEFAULT_NODE_BASE_HEIGHT: Float = 160f
    const val PORT_ROW_HEIGHT: Float = 36f
    const val PORT_HEADER_OFFSET: Float = 54f

    /**
     * Filters flow elements based on the chosen export scope.
     */
    fun filterElements(
        flow: Flow,
        scope: FlowImageExportScope,
        selectedNodeIds: Set<Long> = emptySet(),
        selectedGroupIds: Set<Long> = emptySet(),
        selectedLabelIds: Set<Long> = emptySet(),
        selectedPointIds: Set<Long> = emptySet()
    ): FilteredFlowElements {
        if (scope == FlowImageExportScope.WholeFlow) {
            return FilteredFlowElements(
                nodes = flow.nodes,
                groups = flow.groups,
                labels = flow.labels,
                junctions = flow.junctions,
                connections = flow.connections
            )
        }

        // When a group is selected, all nodes contained in it are included in the section
        val groupContainedNodeIds: Set<Long> = flow.groups
            .filter { it.id in selectedGroupIds }
            .flatMap { it.nodeIds }
            .toSet()

        val effectiveNodeIds = selectedNodeIds + groupContainedNodeIds
        val effectivePointIds = selectedPointIds

        val filteredNodes = flow.nodes.filter { it.id in effectiveNodeIds }
        val filteredGroups = flow.groups.filter { it.id in selectedGroupIds }
        val filteredLabels = flow.labels.filter { it.id in selectedLabelIds }
        val filteredJunctions = flow.junctions.filter { it.id in effectivePointIds }

        // Keep connections whose endpoints are within the included selection
        val filteredConnections = flow.connections.filter { conn ->
            val sourceIncluded = (conn.sourceNodeId in effectiveNodeIds) ||
                    (conn.sourceJunctionId != null && conn.sourceJunctionId in effectivePointIds)
            val targetIncluded = (conn.targetNodeId in effectiveNodeIds) ||
                    (conn.targetJunctionId != null && conn.targetJunctionId in effectivePointIds)
            sourceIncluded && targetIncluded
        }

        return FilteredFlowElements(
            nodes = filteredNodes,
            groups = filteredGroups,
            labels = filteredLabels,
            junctions = filteredJunctions,
            connections = filteredConnections
        )
    }

    /**
     * Computes the bounding rectangle enclosing all filtered elements with the given padding.
     * Returns null if there are no elements to export.
     */
    fun computeExportBounds(
        elements: FilteredFlowElements,
        nodeSizes: Map<Long, IntSize> = emptyMap(),
        padding: Float = 60f
    ): Rect? {
        if (elements.isEmpty) return null

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

        // 1. Nodes
        for (node in elements.nodes) {
            val measuredSize = nodeSizes[node.id]
            val width = measuredSize?.width?.toFloat() ?: DEFAULT_NODE_WIDTH
            val maxPorts = max(node.inputs.size, node.outputs.size)
            val height = measuredSize?.height?.toFloat() ?: (DEFAULT_NODE_BASE_HEIGHT + maxPorts * PORT_ROW_HEIGHT)

            minX = min(minX, node.position.x)
            minY = min(minY, node.position.y)
            maxX = max(maxX, node.position.x + width)
            maxY = max(maxY, node.position.y + height)
        }

        // 2. Groups
        for (group in elements.groups) {
            minX = min(minX, group.position.x)
            minY = min(minY, group.position.y)
            maxX = max(maxX, group.position.x + group.size.x)
            maxY = max(maxY, group.position.y + group.size.y)
        }

        // 3. Labels
        for (label in elements.labels) {
            val estimatedWidth = max(100f, label.text.length * label.fontSize * 0.7f + 24f)
            val estimatedHeight = 40f
            minX = min(minX, label.position.x)
            minY = min(minY, label.position.y)
            maxX = max(maxX, label.position.x + estimatedWidth)
            maxY = max(maxY, label.position.y + estimatedHeight)
        }

        // 4. Junctions
        for (junc in elements.junctions) {
            minX = min(minX, junc.position.x - 20f)
            minY = min(minY, junc.position.y - 20f)
            maxX = max(maxX, junc.position.x + 20f)
            maxY = max(maxY, junc.position.y + 20f)
        }

        // 5. Connection waypoints
        for (conn in elements.connections) {
            for (wp in conn.waypoints) {
                minX = min(minX, wp.x - 10f)
                minY = min(minY, wp.y - 10f)
                maxX = max(maxX, wp.x + 10f)
                maxY = max(maxY, wp.y + 10f)
            }
        }

        if (minX > maxX || minY > maxY) return null

        return Rect(
            left = minX - padding,
            top = minY - padding,
            right = maxX + padding,
            bottom = maxY + padding
        )
    }

    /**
     * Resolves the board position for a given port, preferring measured layout coordinates
     * and falling back to a deterministic calculation based on node position and port index.
     */
    fun resolvePortPosition(
        nodeId: Long,
        portId: String,
        isOutput: Boolean,
        node: Node?,
        measuredPortPositions: Map<Triple<Long, String, Boolean>, Offset>?,
        nodeWidth: Float = DEFAULT_NODE_WIDTH
    ): Offset? {
        measuredPortPositions?.get(Triple(nodeId, portId, isOutput))?.let { return it }
        if (node == null) return null

        val portIndex = if (isOutput) {
            node.outputs.indexOfFirst { it.id == portId }
        } else {
            node.inputs.indexOfFirst { it.id == portId }
        }
        if (portIndex < 0) return null

        val x = if (isOutput) node.position.x + nodeWidth else node.position.x
        val y = node.position.y + PORT_HEADER_OFFSET + (portIndex * PORT_ROW_HEIGHT) + (PORT_ROW_HEIGHT / 2f)
        return Offset(x, y)
    }
}
