package org.wip.plugintoolkit.features.plugin.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class PluginSettingsStore(
    val settings: Map<String, JsonElement> = emptyMap(),
    val globalParams: Map<String, JsonElement> = emptyMap(),
    val capabilityParams: Map<String, Map<String, JsonElement>> = emptyMap(),
    val capabilityMaxConcurrency: Map<String, Int> = emptyMap()
) {
    /**
     * Resolves the effective max concurrent executions allowed for a capability.
     * The developer can set a maximum value [developerMax] that the user cannot surpass.
     * The user can only decrease or equal it via [capabilityMaxConcurrency].
     */
    fun getEffectiveMaxConcurrent(capabilityName: String, developerMax: Int?): Int? {
        val userConfigured = capabilityMaxConcurrency[capabilityName]
        return calculateEffectiveMaxConcurrent(developerMax, userConfigured)
    }

    companion object {
        fun calculateEffectiveMaxConcurrent(developerMax: Int?, userConfigured: Int?): Int? {
            val validDevMax = developerMax?.takeIf { it > 0 }
            val validUserMax = userConfigured?.takeIf { it > 0 }

            return when {
                validDevMax != null && validUserMax != null -> minOf(validDevMax, validUserMax)
                validDevMax != null -> validDevMax
                validUserMax != null -> validUserMax
                else -> null
            }
        }
    }
}
