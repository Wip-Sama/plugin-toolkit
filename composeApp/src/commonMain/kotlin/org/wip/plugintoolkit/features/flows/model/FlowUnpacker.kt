package org.wip.plugintoolkit.features.flows.model

object FlowUnpacker {
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

    fun unpackSubflowInFlow(parentFlow: Flow, nodeId: Long, subflow: Flow): Flow {
        val subFlowNode = parentFlow.nodes.find { it.id == nodeId } as? Node.SubFlowNode ?: return parentFlow
        val baseOffset = subFlowNode.position

        var nextId = (parentFlow.nodes.maxOfOrNull { it.id } ?: -1L) + 1
        val oldToNewId = mutableMapOf<Long, Long>()
        subflow.nodes.forEach { node ->
            oldToNewId[node.id] = nextId++
        }

        val newNodes = subflow.nodes.filter {
            it !is Node.FlowInputNode && it !is Node.FlowOutputNode
        }.map { node ->
            val newId = oldToNewId[node.id]!!
            node.copyWithPosition(node.position + baseOffset).copyWithId(newId)
        }

        val newConnections = subflow.connections.filter { conn ->
            val sourceNode = subflow.nodes.find { it.id == conn.sourceNodeId }
            val targetNode = subflow.nodes.find { it.id == conn.targetNodeId }
            sourceNode !is Node.FlowInputNode && sourceNode !is Node.FlowOutputNode &&
                    targetNode !is Node.FlowInputNode && targetNode !is Node.FlowOutputNode
        }.map { conn ->
            Connection(
                sourceNodeId = oldToNewId[conn.sourceNodeId] ?: conn.sourceNodeId,
                sourcePortId = conn.sourcePortId,
                targetNodeId = oldToNewId[conn.targetNodeId] ?: conn.targetNodeId,
                targetPortId = conn.targetPortId
            )
        }

        val incomingConnections = parentFlow.connections.filter { it.targetNodeId == nodeId }
        val outgoingConnections = parentFlow.connections.filter { it.sourceNodeId == nodeId }

        val mappedIncomingConnections = incomingConnections.flatMap { conn ->
            val boundaryNodeId = subFlowNode.inputMappings.find { it.portId == conn.targetPortId }?.boundaryNodeId
                ?: conn.targetPortId.toLongOrNull() ?: return@flatMap emptyList()
            val internalConns = subflow.connections.filter { it.sourceNodeId == boundaryNodeId }
            internalConns.mapNotNull { internalConn ->
                val newTargetNodeId = oldToNewId[internalConn.targetNodeId] ?: return@mapNotNull null
                Connection(
                    sourceNodeId = conn.sourceNodeId,
                    sourcePortId = conn.sourcePortId,
                    targetNodeId = newTargetNodeId,
                    targetPortId = internalConn.targetPortId
                )
            }
        }

        val mappedOutgoingConnections = outgoingConnections.flatMap { conn ->
            val boundaryNodeId = subFlowNode.outputMappings.find { it.portId == conn.sourcePortId }?.boundaryNodeId
                ?: conn.sourcePortId.toLongOrNull() ?: return@flatMap emptyList()
            val internalConns = subflow.connections.filter { it.targetNodeId == boundaryNodeId }
            internalConns.mapNotNull { internalConn ->
                val newSourceNodeId = oldToNewId[internalConn.sourceNodeId] ?: return@mapNotNull null
                Connection(
                    sourceNodeId = newSourceNodeId,
                    sourcePortId = internalConn.sourcePortId,
                    targetNodeId = conn.targetNodeId,
                    targetPortId = conn.targetPortId
                )
            }
        }

        val updatedNodes = parentFlow.nodes.filter { it.id != nodeId } + newNodes
        val externalConnections =
            parentFlow.connections.filter { it.sourceNodeId != nodeId && it.targetNodeId != nodeId }
        val updatedConnections =
            externalConnections + newConnections + mappedIncomingConnections + mappedOutgoingConnections

        return parentFlow.copy(nodes = updatedNodes, connections = updatedConnections)
    }
}
