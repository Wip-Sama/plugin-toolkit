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
                newNodes.add(node)
                continue
            }

            val pluginId = node.pluginInfo.id
            val currentManifest = currentManifests[pluginId]

            // If plugin is missing entirely, mark as broken
            if (currentManifest == null) {
                val brokenNode = node.copy(isBroken = true)
                newNodes.add(brokenNode)
                brokenNodes.add(brokenNode)
                continue
            }

            val nodeVersion = node.pluginInfo.version
            val currentVersion = currentManifest.plugin.version

            if (nodeVersion == currentVersion) {
                // Verify capability still exists, just in case
                if (currentManifest.capabilities.none { it.name == node.capability.name }) {
                    val brokenNode = node.copy(isBroken = true)
                    newNodes.add(brokenNode)
                    brokenNodes.add(brokenNode)
                } else {
                    newNodes.add(node)
                }
                continue
            }

            // Version changed. Fetch migrations if available.
            val migrations = if (currentManifest.hasMigrations) getMigrations(pluginId) else emptyList()
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
                    // Just a version bump or unaffected capability, capability intact.
                    val updatedNode = node.copy(
                        pluginInfo = currentManifest.plugin,
                        capability = newCapability
                    )
                    newNodes.add(updatedNode)
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
            val newInputs = node.inputs.mapNotNull { input ->
                val portMig = capMig.portMigrations.find { it.oldName == input.name }
                if (portMig != null && portMig.newName == null) {
                    null // Port removed
                } else {
                    val newName = portMig?.newName ?: input.name
                    // Find the matching parameter in the new capability to get its proper ID/type
                    val newParam = newCapability.parameters?.get(newName)
                    if (newParam != null) {
                        input.copy(
                            id = newName, // Update port ID to new name 
                            name = newName,
                            dataType = newParam.type,
                            semanticTypes = newParam.semanticTypes
                        )
                    } else {
                        input.copy(id = newName, name = newName)
                    }
                }
            }

            val newOutputs = node.outputs.mapNotNull { output ->
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

            val updatedNode = node.copy(
                pluginInfo = currentManifest.plugin,
                capability = newCapability,
                inputs = newInputs,
                outputs = newOutputs
            )

            newNodes.add(updatedNode)

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
