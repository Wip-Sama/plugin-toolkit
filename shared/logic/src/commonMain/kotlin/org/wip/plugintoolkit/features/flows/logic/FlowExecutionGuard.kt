package org.wip.plugintoolkit.features.flows.logic

import co.touchlab.kermit.Logger
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.job.model.JobStatus
import org.wip.plugintoolkit.features.job.model.JobType

/**
 * Exception thrown when a flow modification or deletion is attempted on an active or locked flow.
 */
class FlowReadOnlyViolationException(
    val flowName: String,
    val reason: String
) : IllegalStateException("Cannot mutate flow '$flowName': $reason")

/**
 * Centralized guard verifying whether a flow is currently executing or utilized
 * transitively by active jobs. Serves as the single source of truth for flow execution locks.
 */
class FlowExecutionGuard(
    private val jobManagerProvider: () -> JobManager?,
    private val flowProvider: (() -> List<Flow>)? = null
) {
    /**
     * Checks if [flowName] is currently locked against mutation or deletion.
     * A flow is locked if it is directly or transitively part of an active execution
     * (Running, Queued, PauseRequested, or Paused).
     */
    fun isFlowLocked(flowName: String, allFlows: List<Flow>? = null): Boolean {
        if (flowName.isBlank()) return false
        val flows = allFlows ?: flowProvider?.invoke() ?: emptyList()
        val jobManager = jobManagerProvider() ?: return false
        val activeJobs = jobManager.jobs.value.filter {
            it.type == JobType.Flow && (
                it.status == JobStatus.Running ||
                it.status == JobStatus.Queued ||
                it.status == JobStatus.PauseRequested ||
                it.status == JobStatus.Paused
            )
        }

        // 1. Direct active root flow execution check
        if (activeJobs.any { it.capabilityName == flowName || it.pluginId == flowName }) {
            return true
        }

        // 2. Transitive active subflow execution check
        val activeRootFlowNames = activeJobs.map { it.capabilityName }.toSet()
        val activeRoots = flows.filter { it.name in activeRootFlowNames }
        return activeRoots.any { rootFlow ->
            isReferencedAsSubflowTransitively(targetFlowName = flowName, current = rootFlow, allFlows = flows)
        }
    }

    /**
     * Checks if [flowName] is currently actively running (or pause requested) either as a root flow or transitive subflow.
     */
    fun isFlowRunning(flowName: String, allFlows: List<Flow>? = null): Boolean {
        if (flowName.isBlank()) return false
        val flows = allFlows ?: flowProvider?.invoke() ?: emptyList()
        val jobManager = jobManagerProvider() ?: return false
        val runningJobs = jobManager.jobs.value.filter {
            it.type == JobType.Flow && (it.status == JobStatus.Running || it.status == JobStatus.PauseRequested)
        }

        if (runningJobs.any { it.capabilityName == flowName || it.pluginId == flowName }) {
            return true
        }

        val runningRootFlowNames = runningJobs.map { it.capabilityName }.toSet()
        val runningRoots = flows.filter { it.name in runningRootFlowNames }
        return runningRoots.any { rootFlow ->
            isReferencedAsSubflowTransitively(targetFlowName = flowName, current = rootFlow, allFlows = flows)
        }
    }

    /**
     * Checks if [flowName] currently has an execution paused either as a root flow or transitive subflow.
     */
    fun isFlowPaused(flowName: String, allFlows: List<Flow>? = null): Boolean {
        if (flowName.isBlank()) return false
        val flows = allFlows ?: flowProvider?.invoke() ?: emptyList()
        val jobManager = jobManagerProvider() ?: return false
        val pausedJobs = jobManager.jobs.value.filter {
            it.type == JobType.Flow && it.status == JobStatus.Paused
        }

        if (pausedJobs.any { it.capabilityName == flowName || it.pluginId == flowName }) {
            return true
        }

        val pausedRootFlowNames = pausedJobs.map { it.capabilityName }.toSet()
        val pausedRoots = flows.filter { it.name in pausedRootFlowNames }
        return pausedRoots.any { rootFlow ->
            isReferencedAsSubflowTransitively(targetFlowName = flowName, current = rootFlow, allFlows = flows)
        }
    }

    private fun isReferencedAsSubflowTransitively(
        targetFlowName: String,
        current: Flow,
        allFlows: List<Flow>,
        visited: MutableSet<String> = mutableSetOf()
    ): Boolean {
        if (!visited.add(current.name)) return false
        val subflowNodes = current.nodes.filterIsInstance<Node.SubFlowNode>()
        if (subflowNodes.any { it.flowName == targetFlowName }) return true

        return subflowNodes.any { subflowNode ->
            val nextFlow = allFlows.find { it.name == subflowNode.flowName } ?: return@any false
            isReferencedAsSubflowTransitively(targetFlowName, nextFlow, allFlows, visited)
        }
    }

    /**
     * Asserts that [flowName] can be mutated. Throws [FlowReadOnlyViolationException] if locked.
     */
    fun assertCanMutate(flowName: String, allFlows: List<Flow>? = null) {
        val flows = allFlows ?: flowProvider?.invoke() ?: emptyList()
        if (isFlowLocked(flowName, flows)) {
            Logger.w { "Modification rejected for flow '$flowName': active execution lock." }
            throw FlowReadOnlyViolationException(
                flowName = flowName,
                reason = "Flow is currently executing or utilized as an active subflow."
            )
        }
    }
}

/**
 * Recursively collects all plugin IDs (package names) referenced by [Node.CapabilityNode]
 * instances in this [Flow] and its referenced subflows.
 */
fun Flow.getAllReferencedPluginIds(
    allFlows: List<Flow>,
    visited: MutableSet<String> = mutableSetOf()
): Set<String> {
    if (!visited.add(name)) return emptySet()
    val result = mutableSetOf<String>()
    nodes.forEach { node ->
        when (node) {
            is Node.CapabilityNode -> {
                val pkg = node.pluginInfo.id
                if (pkg.isNotBlank()) {
                    result.add(pkg)
                }
            }
            is Node.SubFlowNode -> {
                val subFlow = allFlows.find { it.name == node.flowName }
                if (subFlow != null) {
                    result.addAll(subFlow.getAllReferencedPluginIds(allFlows, visited))
                }
            }
            else -> {}
        }
    }
    return result
}

