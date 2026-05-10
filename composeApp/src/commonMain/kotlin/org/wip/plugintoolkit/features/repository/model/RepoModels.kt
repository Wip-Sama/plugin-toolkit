package org.wip.plugintoolkit.features.repository.model

import kotlinx.serialization.Serializable
import org.wip.plugintoolkit.api.PluginManifest

@Serializable
data class ExtensionRepo(
    val name: String,
    val url: String, // index.json url
    val schemaVersion: Int = 1,
    val signPublicKey: String? = null,
    val signAlgorithm: String = "SHA256",
    val pluginsFolder: String? = null
)

@Serializable
data class RepoIndex(
    val name: String? = null,
    val url: String? = null,
    val schemaVersion: Int = 1,
    val signPublicKey: String? = null,
    val signAlgorithm: String? = null,
    val pluginsFolder: String? = null,
    val plugins: List<ExtensionPlugin> = emptyList()
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
    val manifest: PluginManifest? = null
)
