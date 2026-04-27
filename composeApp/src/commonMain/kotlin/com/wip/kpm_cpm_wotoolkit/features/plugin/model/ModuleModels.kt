package com.wip.kpm_cpm_wotoolkit.features.plugin.model

import kotlinx.serialization.Serializable

@Serializable
data class InstalledModule(
    val pkg: String,
    val name: String,
    val version: String,
    val installPath: String, // Absolute path to the module folder
    val isEnabled: Boolean = true,
    val repoUrl: String? = null, // Source repository URL if remote
    val entryClass: String? = null,
    val entryProperty: String? = null
)

sealed class InstallationSource {
    data class Local(val filePath: String) : InstallationSource()
    data class Remote(val moduleUrl: String, val fileName: String, val pkg: String) : InstallationSource()
}
