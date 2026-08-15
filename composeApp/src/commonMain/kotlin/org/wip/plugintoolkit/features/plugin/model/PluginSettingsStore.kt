package org.wip.plugintoolkit.features.plugin.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class PluginSettingsStore(
    val settings: Map<String, JsonElement> = emptyMap(),
    val globalParams: Map<String, JsonElement> = emptyMap(),
    val capabilityParams: Map<String, Map<String, JsonElement>> = emptyMap()
)
