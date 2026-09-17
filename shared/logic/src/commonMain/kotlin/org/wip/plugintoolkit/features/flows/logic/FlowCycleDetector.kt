package org.wip.plugintoolkit.features.flows.logic

import co.touchlab.kermit.Logger
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.Node

sealed class GraphVertex {
    data class NodeVertex(val id: Long) : GraphVertex()
    data class JunctionVertex(val id: Long) : GraphVertex()
}

object FlowCycleDetector {

    fun wouldCreateCycle(
        sourceNodeId: Long,
        targetNodeId: Long,
        connections: List<Connection>
    ): Boolean {
        if (sourceNodeId == targetNodeId) return true
        return wouldCreateCycle(
            source = GraphVertex.NodeVertex(sourceNodeId),
            target = GraphVertex.NodeVertex(targetNodeId),
            connections = connections
        )
    }

    fun wouldCreateCycle(
        sourceNodeId: Long?,
        sourceJunctionId: Long?,
        targetNodeId: Long?,
        targetJunctionId: Long?,
        connections: List<Connection>
    ): Boolean {
        val source = when {
            sourceNodeId != null && sourceNodeId >= 0L -> GraphVertex.NodeVertex(sourceNodeId)
            sourceJunctionId != null -> GraphVertex.JunctionVertex(sourceJunctionId)
            else -> return false
        }
        val target = when {
            targetNodeId != null && targetNodeId >= 0L -> GraphVertex.NodeVertex(targetNodeId)
            targetJunctionId != null -> GraphVertex.JunctionVertex(targetJunctionId)
            else -> return false
        }
        return wouldCreateCycle(source, target, connections)
    }

    fun wouldCreateCycle(
        source: GraphVertex,
        target: GraphVertex,
        connections: List<Connection>
    ): Boolean {
        if (source == target) return true

        val adjacencyList = buildAdjacencyList(connections)
        val visited = mutableSetOf<GraphVertex>()
        val queue = ArrayDeque<GraphVertex>()

        queue.add(target)
        visited.add(target)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (current == source) {
                return true
            }
            val neighbors = adjacencyList[current] ?: emptyList()
            for (neighbor in neighbors) {
                if (neighbor !in visited) {
                    visited.add(neighbor)
                    queue.add(neighbor)
                }
            }
        }
        return false
    }

    fun hasCycle(connections: List<Connection>): Boolean {
        val adjacencyList = buildAdjacencyList(connections)
        val visited = mutableSetOf<GraphVertex>()
        val visiting = mutableSetOf<GraphVertex>()

        fun dfs(vertex: GraphVertex): Boolean {
            if (vertex in visiting) return true
            if (vertex in visited) return false

            visiting.add(vertex)
            val neighbors = adjacencyList[vertex] ?: emptyList()
            for (neighbor in neighbors) {
                if (dfs(neighbor)) return true
            }
            visiting.remove(vertex)
            visited.add(vertex)
            return false
        }

        for (vertex in adjacencyList.keys) {
            if (dfs(vertex)) return true
        }
        return false
    }

    private fun buildAdjacencyList(connections: List<Connection>): Map<GraphVertex, List<GraphVertex>> {
        val nonFloating = connections.filter { !it.isFloating }
        val adj = mutableMapOf<GraphVertex, MutableList<GraphVertex>>()

        for (conn in nonFloating) {
            val srcVertex: GraphVertex? = when {
                conn.sourceNodeId >= 0L -> GraphVertex.NodeVertex(conn.sourceNodeId)
                conn.sourceJunctionId != null -> GraphVertex.JunctionVertex(conn.sourceJunctionId)
                else -> null
            }
            val tgtVertex: GraphVertex? = when {
                conn.targetNodeId >= 0L -> GraphVertex.NodeVertex(conn.targetNodeId)
                conn.targetJunctionId != null -> GraphVertex.JunctionVertex(conn.targetJunctionId)
                else -> null
            }
            if (srcVertex != null && tgtVertex != null) {
                adj.getOrPut(srcVertex) { mutableListOf() }.add(tgtVertex)
            }
        }
        return adj
    }

    fun wouldCreateNestedFlowCycle(
        currentFlowName: String,
        targetFlowName: String,
        allFlows: List<Flow>
    ): Boolean {
        Logger.d { "Checking for nested flow cycle. Current: '$currentFlowName', Target: '$targetFlowName'" }
        if (currentFlowName == targetFlowName) {
            Logger.w { "Self-cycle detected! Current flow '$currentFlowName' matches target flow '$targetFlowName'." }
            return true
        }

        val visited = mutableSetOf<String>()
        val queue = ArrayDeque<String>()

        queue.add(targetFlowName)
        visited.add(targetFlowName)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            Logger.d { "Visiting flow '$current' in dependency graph check" }
            if (current == currentFlowName) {
                Logger.w { "Nested flow cycle detected! Flow '$currentFlowName' is reachable from '$targetFlowName'." }
                return true
            }
            val flow = allFlows.find { it.name == current } ?: continue
            val subflows = flow.nodes.filterIsInstance<Node.SubFlowNode>().map { it.flowName }
            Logger.d { "Flow '$current' contains subflows: $subflows" }
            for (subflow in subflows) {
                if (subflow !in visited) {
                    visited.add(subflow)
                    queue.add(subflow)
                }
            }
        }
        Logger.d { "No nested flow cycle detected. Adding '$targetFlowName' to '$currentFlowName' is safe." }
        return false
    }

}
