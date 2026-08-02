package org.wip.plugintoolkit.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * A generic request sent to a plugin capability.
 *
 * @property method The name of the capability to invoke.
 * @property parameters A map of parameter names to their JSON-serialized values.
 * @property resumeState Optional state to resume a previously paused task.
 */
@Serializable
class PluginRequest(
    val method: String,
    val parameters: Map<String, JsonElement> = emptyMap(),
    val resumeState: JsonElement? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PluginRequest) return false
        return method == other.method && parameters == other.parameters && resumeState == other.resumeState
    }

    override fun hashCode(): Int {
        var result = method.hashCode()
        result = 31 * result + parameters.hashCode()
        result = 31 * result + (resumeState?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "PluginRequest(method='$method', parameters=$parameters, resumeState=$resumeState)"
    }

    class Builder(var method: String) {
        private var parameters: Map<String, JsonElement> = emptyMap()
        private var resumeState: JsonElement? = null

        fun setParameters(parameters: Map<String, JsonElement>) = apply { this.parameters = parameters }
        fun setResumeState(resumeState: JsonElement?) = apply { this.resumeState = resumeState }
        fun build() = PluginRequest(method, parameters, resumeState)
    }
}

/**
 * A generic response returned by a plugin capability.
 *
 * @property result The JSON-serialized result of the capability execution.
 * @property metadata Optional metadata about the response.
 * @property resumeState If the task was paused, this contains the state to be used for resumption.
 */
@Serializable
class PluginResponse(
    val result: JsonElement,
    val metadata: Map<String, String>? = null,
    val resumeState: JsonElement? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PluginResponse) return false
        return result == other.result && metadata == other.metadata && resumeState == other.resumeState
    }

    override fun hashCode(): Int {
        var res = result.hashCode()
        res = 31 * res + (metadata?.hashCode() ?: 0)
        res = 31 * res + (resumeState?.hashCode() ?: 0)
        return res
    }

    override fun toString(): String {
        return "PluginResponse(result=$result, metadata=$metadata, resumeState=$resumeState)"
    }

    class Builder(var result: JsonElement) {
        private var metadata: Map<String, String>? = null
        private var resumeState: JsonElement? = null

        fun setMetadata(metadata: Map<String, String>?) = apply { this.metadata = metadata }
        fun setResumeState(resumeState: JsonElement?) = apply { this.resumeState = resumeState }
        fun build() = PluginResponse(result, metadata, resumeState)
    }
}
