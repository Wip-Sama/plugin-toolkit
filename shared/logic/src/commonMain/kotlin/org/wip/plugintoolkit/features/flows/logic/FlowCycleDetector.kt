package org.wip.plugintoolkit.features.flows.logic

import co.touchlab.kermit.Logger
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.Node

object FlowCycleDetector {

    fun wouldCreateCycle(sourceNodeId: Long, targetNodeId: Long, connections: List<Connection>): Boolean {
        if (sourceNodeId == targetNodeId) return true

        val adjacencyList =
            connections.groupBy { it.sourceNodeId }.mapValues { entry -> entry.value.map { it.targetNodeId } }
        val visited = mutableSetOf<Long>()
        val queue = ArrayDeque<Long>()

        queue.add(targetNodeId)
        visited.add(targetNodeId)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (current == sourceNodeId) {
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
        val adjacencyList =
            connections.groupBy { it.sourceNodeId }.mapValues { entry -> entry.value.map { it.targetNodeId } }
        val visited = mutableSetOf<Long>()
        val visiting = mutableSetOf<Long>()

        fun dfs(node: Long): Boolean {
            if (node in visiting) return true
            if (node in visited) return false

            visiting.add(node)
            val neighbors = adjacencyList[node] ?: emptyList()
            for (neighbor in neighbors) {
                if (dfs(neighbor)) return true
            }
            visiting.remove(node)
            visited.add(node)
            return false
        }

        for (node in adjacencyList.keys) {
            if (dfs(node)) return true
        }
        return false
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
