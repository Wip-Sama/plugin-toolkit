package com.wip.plugin.api

import kotlinx.serialization.json.JsonElement

/**
 * A generic request sent to a plugin capability.
 *
 * @property method The name of the capability to invoke.
 * @property parameters A map of parameter names to their JSON-serialized values.
 * @property resumeState Optional state to resume a previously paused task.
 */
data class PluginRequest(
    val method: String,
    val parameters: Map<String, JsonElement> = emptyMap(),
    val resumeState: JsonElement? = null
)

/**
 * A generic response returned by a plugin capability.
 *
 * @property result The JSON-serialized result of the capability execution.
 * @property metadata Optional metadata about the response.
 * @property resumeState If the task was paused, this contains the state to be used for resumption.
 */
data class PluginResponse(
    val result: JsonElement,
    val metadata: Map<String, String>? = null,
    val resumeState: JsonElement? = null
)
