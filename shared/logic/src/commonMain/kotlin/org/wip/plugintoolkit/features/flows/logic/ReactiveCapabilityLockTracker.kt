package org.wip.plugintoolkit.features.flows.logic

import kotlinx.coroutines.flow.Flow as CoroutineFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import org.wip.plugintoolkit.api.Capability
import org.wip.plugintoolkit.features.flows.model.Flow as ModelFlow
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.plugin.logic.PluginManager
import org.wip.plugintoolkit.features.plugin.utils.CapabilityLockStatus
import org.wip.plugintoolkit.features.plugin.utils.CapabilityLockUtils

/**
 * Detailed readiness state for a flow capability node or palette item.
 */
sealed class NodeReadinessState {
    object Ready : NodeReadinessState()
    data class Locked(val missingLocks: List<String>, val missingSettings: List<String>) : NodeReadinessState()
    object Broken : NodeReadinessState()
    object MissingDependency : NodeReadinessState()
}

/**
 * Reactive service tracking capability locks and settings dynamically for flow editing and execution.
 */
class ReactiveCapabilityLockTracker(
    private val pluginManager: PluginManager
) {
    /**
     * Reactive snapshot of all installed plugin locks: Map<PluginPackage, Map<LockKey, IsSatisfied>>
     */
    val pluginLocksState: StateFlow<Map<String, Map<String, Boolean>>>
        get() = pluginManager.pluginLocksState

    /**
     * Evaluates lock and configuration status for a given capability reactively.
     */
    fun observeCapabilityStatus(pluginId: String, capability: Capability): CoroutineFlow<NodeReadinessState> {
        return pluginLocksState.combine(pluginManager.installedPlugins) { locksMap, _ ->
            val manifest = pluginManager.getManifest(pluginId) ?: return@combine NodeReadinessState.MissingDependency
            val pluginLocks = locksMap[pluginId] ?: locksMap.values.fold(emptyMap()) { acc, next -> acc + next }
            val pluginSettings = pluginManager.loadPluginSettings(pluginId).settings

            val lockStatus = CapabilityLockUtils.checkCapabilityLockStatus(
                capability = capability,
                providedLocks = pluginLocks,
                providedSettings = pluginSettings
            )

            when (lockStatus) {
                is CapabilityLockStatus.Unlocked -> {
                    val isSchemaReady = capability.isReady(pluginSettings, manifest.settings)
                    if (isSchemaReady) {
                        NodeReadinessState.Ready
                    } else {
                        NodeReadinessState.Locked(
                            missingLocks = emptyList(),
                            missingSettings = capability.requiresSettings.filter { it !in pluginSettings }
                        )
                    }
                }
                is CapabilityLockStatus.Locked -> NodeReadinessState.Locked(
                    missingLocks = lockStatus.missingLocks,
                    missingSettings = lockStatus.missingSettings
                )
            }
        }.distinctUntilChanged()
    }

    /**
     * Validates whether a flow is ready for execution, verifying all capability and parameter locks.
     */
    fun validateFlowForExecution(flow: ModelFlow): Result<Unit> {
        val currentLocks = pluginLocksState.value
        val globalLocks = currentLocks.values.fold(emptyMap<String, Boolean>()) { acc, m -> acc + m }

        flow.nodes.filterIsInstance<Node.CapabilityNode>().forEach { node ->
            if (node.isBroken) {
                return Result.failure(IllegalStateException("Node '${node.title}' is broken."))
            }
            val pluginId = node.pluginInfo.id
            val nodeLocks = currentLocks[pluginId] ?: globalLocks
            val nodeSettings = pluginManager.loadPluginSettings(pluginId).settings

            val status = CapabilityLockUtils.checkCapabilityLockStatus(
                capability = node.capability,
                providedLocks = nodeLocks,
                providedSettings = nodeSettings
            )

            if (status is CapabilityLockStatus.Locked) {
                return Result.failure(
                    IllegalStateException(
                        "Capability '${node.capability.name}' is locked. " +
                        "Missing locks: ${status.missingLocks}, Missing settings: ${status.missingSettings}"
                    )
                )
            }

            if (!node.isReady(flow.connections, nodeSettings, nodeLocks)) {
                return Result.failure(IllegalStateException("Node '${node.title}' has unsatisfied parameter configurations."))
            }
        }
        return Result.success(Unit)
    }
}
