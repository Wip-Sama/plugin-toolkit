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
 * transitively by active jobs.
 */
class FlowExecutionGuard(
    private val jobManagerProvider: () -> JobManager?
) {
    /**
     * Checks if [flowName] is currently locked against mutation or deletion.
     */
    fun isFlowLocked(flowName: String, allFlows: List<Flow>): Boolean {
        if (flowName.isBlank()) return false
        val jobManager = jobManagerProvider() ?: return false
        val activeJobs = jobManager.jobs.value.filter {
            it.type == JobType.Flow && (it.status == JobStatus.Running || it.status == JobStatus.Queued)
        }

        // 1. Direct active root flow execution check
        if (activeJobs.any { it.capabilityName == flowName || it.pluginId == flowName }) {
            return true
        }

        // 2. Transitive active subflow execution check
        val activeRootFlowNames = activeJobs.map { it.capabilityName }.toSet()
        val activeRoots = allFlows.filter { it.name in activeRootFlowNames }
        return activeRoots.any { rootFlow ->
            isReferencedAsSubflowTransitively(targetFlowName = flowName, current = rootFlow, allFlows = allFlows)
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
    fun assertCanMutate(flowName: String, allFlows: List<Flow>) {
        if (isFlowLocked(flowName, allFlows)) {
            Logger.w { "Modification rejected for flow '$flowName': active execution lock." }
            throw FlowReadOnlyViolationException(
                flowName = flowName,
                reason = "Flow is currently executing or utilized as an active subflow."
            )
        }
    }
}
