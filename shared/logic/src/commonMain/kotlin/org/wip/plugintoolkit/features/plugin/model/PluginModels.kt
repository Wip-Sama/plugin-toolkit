package org.wip.plugintoolkit.features.plugin.model

import kotlinx.serialization.Serializable

@Serializable
enum class PluginLifecycleStatus {
    PENDING_SETUP,
    SETTING_UP,
    VALIDATING,
    VALIDATED,
    VALIDATION_FAILED,
    DISABLED
}

@Serializable
data class InstalledPlugin(
    val pkg: String,
    val name: String,
    val version: String,
    val installPath: String, // Absolute path to the plugin folder
    val isEnabled: Boolean = true,
    val isValidated: Boolean = false,
    val isSetupCompleted: Boolean = false,
    val status: PluginLifecycleStatus = if (!isEnabled) {
        PluginLifecycleStatus.DISABLED
    } else if (isValidated) {
        PluginLifecycleStatus.VALIDATED
    } else {
        PluginLifecycleStatus.PENDING_SETUP
    },
    val isCompatible: Boolean = true,
    val compatibilityError: String? = null,
    val repoUrl: String? = null, // Source repository URL if remote
    val jarFileName: String? = null,
    val description: String? = null,
    val loadError: String? = null,
    val requiredAction: String? = null,
    val configurationPrompted: Boolean = false,
    val signaturePrompted: Boolean = false,
    val supportedOs: List<org.wip.plugintoolkit.api.OS> = emptyList(),
    val targetAppVersion: String? = null
)

sealed class InstallationSource {
    data class Local(val filePath: String) : InstallationSource()
    data class Remote(val pluginUrl: String, val fileName: String, val pkg: String) : InstallationSource()
}
