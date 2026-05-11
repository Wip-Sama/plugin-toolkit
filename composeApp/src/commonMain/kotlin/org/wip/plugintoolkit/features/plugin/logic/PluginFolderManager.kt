package org.wip.plugintoolkit.features.plugin.logic

import co.touchlab.kermit.Logger
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository

/**
 * Handles the management of folders containing plugins.
 * Coordinates with Registry and Lifecycle components to ensure safe folder removal.
 */
class PluginFolderManager(
    private val registry: PluginRegistry,
    private val lifecycleManager: PluginLifecycleManager,
    private val settingsRepository: SettingsRepository
) {
    /**
     * Safely removes a managed folder.
     * Unloads any active plugins in that folder before removing it from management.
     */
    suspend fun removeManagedFolder(folderPath: String): Result<Unit> {
        val defaultFolder = registry.getDefaultPluginFolder()
        if (folderPath.replace('\\', '/').removeSuffix("/") == defaultFolder.replace('\\', '/').removeSuffix("/")) {
            return Result.failure(Exception("Cannot remove default plugins folder"))
        }

        val normalizedFolder = folderPath.replace('\\', '/').removeSuffix("/")
        val pluginsInFolder = registry.installedPlugins.value.filter { 
            it.installPath.replace('\\', '/').removeSuffix("/").startsWith(normalizedFolder) 
        }
        val pkgs = pluginsInFolder.map { it.pkg }

        // Safety check and unload
        lifecycleManager.ensureSafeToUnload(pkgs).onFailure { return Result.failure(it) }
        pkgs.forEach { lifecycleManager.unloadPlugin(it) }

        // Remove from settings
        settingsRepository.updateSettings { settings ->
            val updatedFolders = settings.extensions.pluginFolders.filter { 
                it.replace('\\', '/').removeSuffix("/") != normalizedFolder 
            }
            settings.copy(extensions = settings.extensions.copy(pluginFolders = updatedFolders))
        }

        // Remove from registry
        pkgs.forEach { registry.removePlugin(it) }

        Logger.i { "Removed managed folder: $folderPath. ${pkgs.size} plugins removed from management." }
        return Result.success(Unit)
    }

    fun getManagedFolders() = registry.getManagedFolders()
}
