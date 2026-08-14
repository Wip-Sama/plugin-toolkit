package org.wip.plugintoolkit.features.plugin.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import org.wip.plugintoolkit.api.PluginManifest

@Serializable
data class PluginSettingsStore(
    val settings: Map<String, JsonElement> = emptyMap(),
    val globalParams: Map<String, JsonElement> = emptyMap(),
    val capabilityParams: Map<String, Map<String, JsonElement>> = emptyMap()
)

fun PluginManifest.defaultCustomSettings(): Map<String, JsonElement> = settings.orEmpty().mapNotNull { (key, metadata) ->
    metadata.defaultValue?.let { key to it }
}.toMap()

/** Manifest defaults with persisted user values taking precedence. */
fun PluginSettingsStore.resolveCustomSettings(manifest: PluginManifest?): Map<String, JsonElement> =
    (manifest?.defaultCustomSettings() ?: emptyMap()) + settings

/** Values available to generated inputs and lock evaluation in the settings UI. */
fun PluginSettingsStore.resolveProvidedValues(manifest: PluginManifest?): Map<String, JsonElement> =
    resolveCustomSettings(manifest) + globalParams
