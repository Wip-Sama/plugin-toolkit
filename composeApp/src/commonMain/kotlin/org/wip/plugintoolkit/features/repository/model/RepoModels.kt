package org.wip.plugintoolkit.features.repository.model

import kotlinx.serialization.Serializable
import org.wip.plugintoolkit.api.PluginManifest

@Serializable
data class ExtensionRepo(
    val name: String,
    val url: String, // index.json url or local file path
    val schemaVersion: Int = 1,
    val signPublicKey: String? = null,
    val signAlgorithm: String = "SHA256",
    val pluginsFolder: String? = null,
    val flowsFolder: String? = null
) {
    val isLocal: Boolean
        get() = !url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)

    val isRemote: Boolean
        get() = !isLocal

    fun getBaseLocation(): String {
        val normalized = url.replace('\\', '/')
        return if (normalized.contains('/')) normalized.substringBeforeLast("/") else normalized
    }
}

sealed class RepoValidationResult {
    object Idle : RepoValidationResult()
    object Checking : RepoValidationResult()
    data class Valid(
        val name: String,
        val pluginCount: Int,
        val flowCount: Int,
        val index: RepoIndex,
        val isLocal: Boolean
    ) : RepoValidationResult()
    data class Invalid(val reason: String) : RepoValidationResult()
}

@Serializable
data class RepoIndex(
    val name: String? = null,
    val url: String? = null,
    val schemaVersion: Int = 1,
    val signPublicKey: String? = null,
    val signAlgorithm: String? = null,
    val pluginsFolder: String? = null,
    val flowsFolder: String? = null,
    val plugins: List<ExtensionPlugin> = emptyList(),
    val flows: List<ExtensionFlow> = emptyList()
)

@Serializable
data class ExtensionPlugin(
    val name: String,
    val fileName: String,
    val description: String? = null,
    val pkg: String,
    val version: String,
    val minAppVersion: String? = null,
    val repoUrl: String? = null, // Filled during parsing to track source
    val size: Long? = null,
    val hash: String? = null,
    val signature: String? = null,
    val isSignatureValid: Boolean? = null,
    val manifest: PluginManifest? = null
)

@Serializable
data class ExtensionFlow(
    val name: String,
    val fileName: String,
    val description: String? = null,
    val version: String,
    val minAppVersion: String? = null,
    val repoUrl: String? = null, // Filled during parsing to track source
    val size: Long? = null,
    val hash: String? = null,
    val signature: String? = null,
    val isSignatureValid: Boolean? = null
)

