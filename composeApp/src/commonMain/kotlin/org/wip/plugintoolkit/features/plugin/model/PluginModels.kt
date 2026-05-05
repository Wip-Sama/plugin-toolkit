package org.wip.plugintoolkit.features.plugin.model

import kotlinx.serialization.Serializable

@Serializable
data class InstalledPlugin(
    val pkg: String,
    val name: String,
    val version: String,
    val installPath: String, // Absolute path to the plugin folder
    val isEnabled: Boolean = true,
    val isValidated: Boolean = false,
    val repoUrl: String? = null, // Source repository URL if remote
    val jarFileName: String? = null,
    val description: String? = null
)

sealed class InstallationSource {
    data class Local(val filePath: String) : InstallationSource()
    data class Remote(val pluginUrl: String, val fileName: String, val pkg: String) : InstallationSource()
}
