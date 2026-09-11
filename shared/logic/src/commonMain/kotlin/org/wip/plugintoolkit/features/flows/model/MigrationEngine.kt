package org.wip.plugintoolkit.features.flows.model

import org.wip.plugintoolkit.api.PluginManifest
import org.wip.plugintoolkit.api.PluginMigration

data class MigrationResult(
    val migratedFlow: Flow,
    val requiresConsentNodes: List<Node.CapabilityNode>,
    val brokenNodes: List<Node.CapabilityNode>
)

object MigrationEngine {

    /**
     * Resolves the migration path from [fromVersion] to [toVersion] using the available [migrations].
     * Returns a merged `PluginMigration` representing the entire path, or null if no path exists.
     */
    fun resolveMigrationPath(
        fromVersion: String,
        toVersion: String,
        migrations: List<PluginMigration>
    ): PluginMigration? {
        if (fromVersion == toVersion) return null

        // BFS to find the shortest path
        val queue = ArrayDeque<List<PluginMigration>>()
        migrations.filter { it.fromVersion == fromVersion }.forEach { queue.add(listOf(it)) }

        while (queue.isNotEmpty()) {
            val path = queue.removeFirst()
            val currentVersion = path.last().toVersion

            if (currentVersion == toVersion) {
                return mergeMigrations(path)
            }

            migrations.filter { it.fromVersion == currentVersion }.forEach { nextStep ->
                // Avoid cycles
                if (path.none { it.fromVersion == nextStep.toVersion }) {
                    queue.add(path + nextStep)
                }
            }
        }

        return null
    }

    private fun mergeMigrations(path: List<PluginMigration>): PluginMigration {
        if (path.isEmpty()) throw IllegalArgumentException("Path cannot be empty")
        if (path.size == 1) return path.first()

        val fromVersion = path.first().fromVersion
        val toVersion = path.last().toVersion

        // Merge capabilities
        val capabilityMigrations = mutableMapOf<String, org.wip.plugintoolkit.api.CapabilityMigration>()

        for (step in path) {
            for (capMig in step.capabilityMigrations) {
                // If this capability was already migrated in a previous step, we need to chain it
                val existingEntries = capabilityMigrations.entries.filter { it.value.newName == capMig.oldName }

                if (existingEntries.isNotEmpty()) {
                    for (entry in existingEntries) {
                        // Update the existing migration chain
                        val oldMig = entry.value

                        // Merge ports
                        val mergedPorts = oldMig.portMigrations.toMutableList()
                        for (portMig in capMig.portMigrations) {
                            val existingPort = mergedPorts.find { it.newName == portMig.oldName }
                            if (existingPort != null) {
                                mergedPorts.remove(existingPort)
                                mergedPorts.add(
                                    org.wip.plugintoolkit.api.PortMigration(
                                        existingPort.oldName,
                                        portMig.newName
                                    )
                                )
                            } else {
                                mergedPorts.add(portMig)
                            }
                        }

                        capabilityMigrations[entry.key] = org.wip.plugintoolkit.api.CapabilityMigration(
                            oldName = oldMig.oldName,
                            newName = capMig.newName,
                            isDropInReplacement = oldMig.isDropInReplacement && capMig.isDropInReplacement,
                            portMigrations = mergedPorts
                        )
                    }
                }

                // Add as a fresh migration chain if it wasn't already tracked from the original version
                if (!capabilityMigrations.containsKey(capMig.oldName)) {
                    capabilityMigrations[capMig.oldName] = capMig
                }
            }
        }

        return PluginMigration(
            fromVersion = fromVersion,
            toVersion = toVersion,
            capabilityMigrations = capabilityMigrations.values.toList()
        )
    }

    /**
     * Applies migrations to a Flow.
     * 
     * @param flow The old flow to migrate.
     * @param currentManifests A map of plugin ID to its current Manifest.
     * @param getMigrations A function to fetch the migrations for a given plugin ID.
     */
    fun reconcileCapabilityNodePorts(
        node: Node.CapabilityNode,
        actualCapability: org.wip.plugintoolkit.api.Capability,
        currentPluginInfo: org.wip.plugintoolkit.api.PluginInfo
    ): Node.CapabilityNode {
        val existingInputs = node.inputs.toMutableList()
        val existingOutputs = node.outputs.toMutableList()
        var isNodeBroken = node.isBroken

        val capabilityParams = actualCapability.parameters ?: emptyMap()

        // 1. Check/add missing outputs from OUTPUT_LOCATION parameters
        val missingOutputs = capabilityParams.filter { it.value.role == org.wip.plugintoolkit.api.ParameterRole.OUTPUT_LOCATION }
            .mapNotNull { (key, meta) ->
                if (existingOutputs.none { it.id == key }) {
                    OutputPort(
                        id = key,
                        name = key.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                        description = meta.description,
                        dataType = meta.type,
                        semanticTypes = meta.semanticTypes
                    )
                } else null
            }
        val allOutputs = existingOutputs + missingOutputs

        // 2. Check/add inputs
        val inputParams = capabilityParams.filter { it.value.role != org.wip.plugintoolkit.api.ParameterRole.OUTPUT_LOCATION }
        val newInputs = existingInputs.map { input ->
            val paramMeta = inputParams[input.id]
            if (paramMeta != null) {
                input.copy(
                    dataType = paramMeta.type,
                    semanticTypes = paramMeta.semanticTypes,
                    isRequired = paramMeta.required,
                    defaultValue = input.defaultValue ?: paramMeta.defaultValue,
                    condition = paramMeta.condition
                )
            } else {
                input
            }
        }.toMutableList()

        for ((key, meta) in inputParams) {
            if (newInputs.none { it.id == key }) {
                if (meta.required && meta.defaultValue == null && meta.autogeneratedPattern == null) {
                    isNodeBroken = true
                }
                newInputs.add(
                    InputPort(
                        id = key,
                        name = key,
                        description = meta.description,
                        dataType = meta.type,
                        semanticTypes = meta.semanticTypes,
                        defaultValue = meta.defaultValue,
                        isRequired = meta.required,
                        constraints = meta.constraints?.let { PortConstraints(regex = it.regex) },
                        isAdvanced = meta.isAdvanced,
                        condition = meta.condition
                    )
                )
            }
        }

        return node.copy(
            pluginInfo = currentPluginInfo,
            capability = actualCapability,
            inputs = newInputs,
            outputs = allOutputs,
            isBroken = isNodeBroken
        )
    }

    /**
     * Applies migrations to a Flow.
     * 
     * @param flow The old flow to migrate.
     * @param currentManifests A map of plugin ID to its current Manifest.
     * @param getMigrations A function to fetch the migrations for a given plugin ID.
     */
    suspend fun migrateFlow(
        flow: Flow,
        currentManifests: Map<String, PluginManifest>,
        getMigrations: suspend (String) -> List<PluginMigration>
    ): MigrationResult {
        val newNodes = mutableListOf<Node>()
        var newConnections = flow.connections.toMutableList()
        val requiresConsent = mutableListOf<Node.CapabilityNode>()
        val brokenNodes = mutableListOf<Node.CapabilityNode>()

        for (node in flow.nodes) {
            if (node !is Node.CapabilityNode) {
                if (node is Node.SystemNode) {
                    val systemInputs = org.wip.plugintoolkit.features.flows.logic.SystemNodesRegistry.getInputs(node.systemAction)
                    val newInputs = node.inputs.map { input ->
                        val defInput = systemInputs.find { it.id == input.id }
                        if (defInput != null) {
                            input.copy(isRequired = defInput.isRequired)
                        } else input
                    }
                    newNodes.add(node.copy(inputs = newInputs))
                } else {
                    newNodes.add(node)
                }
                continue
            }

            val pluginId = node.pluginInfo.id
            var currentManifest = currentManifests[pluginId]

            // If plugin is missing under its original ID, check if any enabled plugin provides this capability
            if (currentManifest == null) {
                currentManifest = currentManifests.values.find { manifest ->
                    manifest.capabilities.any { it.name == node.capability.name }
                }
            }

            // If no plugin provides this capability, mark as broken
            if (currentManifest == null) {
                val brokenNode = node.copy(isBroken = true)
                newNodes.add(brokenNode)
                brokenNodes.add(brokenNode)
                continue
            }

            val nodeVersion = node.pluginInfo.version
            val currentVersion = currentManifest.plugin.version

            if (nodeVersion == currentVersion) {
                val actualCapability = currentManifest.capabilities.find { it.name == node.capability.name }
                if (actualCapability == null) {
                    val brokenNode = node.copy(isBroken = true)
                    newNodes.add(brokenNode)
                    brokenNodes.add(brokenNode)
                } else {
                    val reconciledNode = reconcileCapabilityNodePorts(node, actualCapability, currentManifest.plugin)
                    newNodes.add(reconciledNode)
                    if (reconciledNode.isBroken) {
                        brokenNodes.add(reconciledNode)
                    }
                }
                continue
            }

            // Version changed. Fetch migrations if available.
            val migrations = if (currentManifest.hasMigrations) getMigrations(currentManifest.plugin.id) else emptyList()
            val migrationPath = resolveMigrationPath(nodeVersion, currentVersion, migrations)

            val capMig = migrationPath?.capabilityMigrations?.find { it.oldName == node.capability.name }

            if (capMig == null) {
                // Capability was not touched in migrations (or no migration path exists). Verify it still exists in the manifest.
                val newCapability = currentManifest.capabilities.find { it.name == node.capability.name }
                if (newCapability == null) {
                    // Removed without a migration rule! Break it.
                    val brokenNode = node.copy(isBroken = true)
                    newNodes.add(brokenNode)
                    brokenNodes.add(brokenNode)
                } else {
                    val reconciledNode = reconcileCapabilityNodePorts(node, newCapability, currentManifest.plugin)
                    newNodes.add(reconciledNode)
                    if (reconciledNode.isBroken) {
                        brokenNodes.add(reconciledNode)
                    }
                }
                continue
            }

            if (capMig.newName == null) {
                // Capability explicitly removed/unsupported. Break it.
                val brokenNode = node.copy(isBroken = true)
                newNodes.add(brokenNode)
                brokenNodes.add(brokenNode)
                continue
            }

            val newCapability = currentManifest.capabilities.find { it.name == capMig.newName }
            if (newCapability == null) {
                // Migration points to a capability that doesn't exist. Break it.
                val brokenNode = node.copy(isBroken = true)
                newNodes.add(brokenNode)
                brokenNodes.add(brokenNode)
                continue
            }

            // Apply port migrations
            val migratedInputs = node.inputs.mapNotNull { input ->
                val portMig = capMig.portMigrations.find { it.oldName == input.name }
                if (portMig != null && portMig.newName == null) {
                    null // Port removed
                } else {
                    val newName = portMig?.newName ?: input.name
                    val newParam = newCapability.parameters?.get(newName)
                    if (newParam != null) {
                        input.copy(
                            id = newName,
                            name = newName,
                            dataType = newParam.type,
                            semanticTypes = newParam.semanticTypes
                        )
                    } else {
                        input.copy(id = newName, name = newName)
                    }
                }
            }

            val migratedOutputs = node.outputs.mapNotNull { output ->
                val portMig = capMig.portMigrations.find { it.oldName == output.name }
                if (portMig != null && portMig.newName == null) {
                    null // Port removed
                } else {
                    val newName = portMig?.newName ?: output.name
                    val newOut = newCapability.outputs?.find { it.name == newName }
                    if (newOut != null) {
                        output.copy(
                            id = newName,
                            name = newName,
                            dataType = newOut.type,
                            semanticTypes = newOut.semanticTypes
                        )
                    } else {
                        output.copy(id = newName, name = newName)
                    }
                }
            }

            val intermediateNode = node.copy(
                inputs = migratedInputs,
                outputs = migratedOutputs
            )
            val updatedNode = reconcileCapabilityNodePorts(intermediateNode, newCapability, currentManifest.plugin)
            newNodes.add(updatedNode)
            if (updatedNode.isBroken) {
                brokenNodes.add(updatedNode)
            }

            // Update connections using this node
            newConnections = newConnections.mapNotNull { conn ->
                if (conn.sourceNodeId == node.id) {
                    val portMig = capMig.portMigrations.find { it.oldName == conn.sourcePortId }
                    if (portMig != null && portMig.newName == null) {
                        null // Connection dropped
                    } else {
                        conn.copy(sourcePortId = portMig?.newName ?: conn.sourcePortId)
                    }
                } else if (conn.targetNodeId == node.id) {
                    val portMig = capMig.portMigrations.find { it.oldName == conn.targetPortId }
                    if (portMig != null && portMig.newName == null) {
                        null // Connection dropped
                    } else {
                        conn.copy(targetPortId = portMig?.newName ?: conn.targetPortId)
                    }
                } else {
                    conn
                }
            }.toMutableList()

            if (!capMig.isDropInReplacement) {
                requiresConsent.add(updatedNode)
            }
        }

        return MigrationResult(
            migratedFlow = flow.copy(nodes = newNodes, connections = newConnections),
            requiresConsentNodes = requiresConsent,
            brokenNodes = brokenNodes
        )
    }
}
