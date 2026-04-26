package com.wip.plugin.api

import kotlinx.serialization.json.JsonElement

/**
 * A generic request sent to a plugin.
 * This removes the need for hardcoded request classes in the main app.
 */
data class PluginRequest(
    val method: String,
    val parameters: Map<String, JsonElement> = emptyMap()
)

/**
 * A generic response returned by a plugin.
 */
data class PluginResponse(
    val result: JsonElement,
    val metadata: Map<String, String>? = null
)
