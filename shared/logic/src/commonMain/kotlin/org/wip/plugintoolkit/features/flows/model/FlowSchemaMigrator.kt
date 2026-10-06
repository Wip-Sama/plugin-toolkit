package org.wip.plugintoolkit.features.flows.model

import co.touchlab.kermit.Logger

/**
 * Handles schema migration across revisions of [Flow] persistence models.
 * Ensures backward-compatibility when loading flows saved by older versions.
 */
object FlowSchemaMigrator {

    /**
     * Upgrades [flow] to the latest [Flow.CURRENT_SCHEMA_VERSION] if its schemaVersion is outdated.
     */
    fun migrate(flow: Flow): Flow {
        var current = flow
        val originalVersion = current.schemaVersion

        if (current.schemaVersion < 1) {
            Logger.i { "Migrating flow '${current.name}' from schema ${current.schemaVersion} to 1" }
            current = current.copy(
                schemaVersion = 1
            ).purgeStrayPoints().healDuplicateConnections()
        }

        if (current.schemaVersion != originalVersion) {
            Logger.i { "Flow '${current.name}' successfully migrated from schema $originalVersion to ${current.schemaVersion}" }
        }

        return current
    }
}
