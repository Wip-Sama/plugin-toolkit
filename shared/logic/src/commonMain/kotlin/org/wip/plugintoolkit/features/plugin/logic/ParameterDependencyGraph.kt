package org.wip.plugintoolkit.features.plugin.logic

import kotlinx.serialization.json.JsonElement
import org.wip.plugintoolkit.api.ConditionSource
import org.wip.plugintoolkit.api.ParameterConditionEvaluator
import org.wip.plugintoolkit.api.ParameterMetadata

object ParameterDependencyGraph {

    /**
     * Orders parameters topologically so that parameters with dependencies are evaluated
     * after their upstream dependencies.
     */
    fun getTopologicalOrder(parameters: Map<String, ParameterMetadata>): List<String> {
        val adj = mutableMapOf<String, MutableList<String>>()
        parameters.keys.forEach { adj[it] = mutableListOf() }

        parameters.forEach { (name, meta) ->
            meta.condition?.conditions?.forEach { cond ->
                if (cond.source == ConditionSource.PARAMETER && parameters.containsKey(cond.target) && cond.target != name) {
                    // name depends on cond.target (target -> name)
                    adj[cond.target]?.add(name)
                }
            }
        }

        val visited = mutableSetOf<String>()
        val result = mutableListOf<String>()

        fun dfs(node: String) {
            visited.add(node)
            for (neighbor in adj[node] ?: emptyList()) {
                if (neighbor !in visited) {
                    dfs(neighbor)
                }
            }
            result.add(node)
        }

        for (node in parameters.keys) {
            if (node !in visited) {
                dfs(node)
            }
        }

        return result.reversed()
    }

    /**
     * Computes the set of parameter names that are currently active and visible,
     * evaluating conditions in topological order.
     */
    fun computeActiveParameters(
        parameters: Map<String, ParameterMetadata>,
        values: Map<String, JsonElement>,
        settings: Map<String, JsonElement> = emptyMap(),
        locks: Map<String, Boolean> = emptyMap()
    ): Set<String> {
        val topologicalOrder = getTopologicalOrder(parameters)
        val activeParams = mutableSetOf<String>()

        for (paramName in topologicalOrder) {
            val meta = parameters[paramName] ?: continue
            val condition = meta.condition

            // Check if upstream dependencies are satisfied
            val satisfied = ParameterConditionEvaluator.isSatisfied(
                conditionGroup = condition,
                parameters = values,
                settings = settings,
                locks = locks
            )

            if (satisfied) {
                activeParams.add(paramName)
            }
        }

        return activeParams
    }

    /**
     * Checks if an individual parameter is currently active.
     */
    fun isParameterActive(
        paramName: String,
        metadata: ParameterMetadata,
        currentValues: Map<String, JsonElement>,
        settings: Map<String, JsonElement> = emptyMap(),
        locks: Map<String, Boolean> = emptyMap()
    ): Boolean {
        val condition = metadata.condition ?: return true
        return ParameterConditionEvaluator.isSatisfied(
            conditionGroup = condition,
            parameters = currentValues,
            settings = settings,
            locks = locks
        )
    }
}
