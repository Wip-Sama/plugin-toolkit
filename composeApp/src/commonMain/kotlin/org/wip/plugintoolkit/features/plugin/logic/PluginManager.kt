package org.wip.plugintoolkit.features.plugin.logic

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.wip.plugintoolkit.api.PluginAction
import org.wip.plugintoolkit.api.PluginManifest
import org.wip.plugintoolkit.features.plugin.model.InstalledPlugin
import org.wip.plugintoolkit.features.plugin.model.PluginSettingsStore
import org.wip.plugintoolkit.features.repository.logic.RepoManager
import org.wip.plugintoolkit.features.repository.model.ExtensionPlugin

class PluginManager(
    private val repoManager: RepoManager,
    private val registry: PluginRegistry,
    private val installer: PluginInstaller,
    private val lifecycleManager: PluginLifecycleManager,
    private val scanner: PluginScanner,
    private val coordinator: PluginLifecycleCoordinator,
    private val folderManager: PluginFolderManager,
    private val scope: CoroutineScope
) {
    val installedPlugins: StateFlow<List<InstalledPlugin>> = registry.installedPlugins
    val loadedPlugins: StateFlow<Set<String>> = lifecycleManager.loadedPlugins
    val isRegistryReady: StateFlow<Boolean> = registry.isReady

    init {
        Logger.i { "Initializing PluginManager facade" }
    }

    // --- Installation & Updates ---

    suspend fun installLocal(filePath: String, targetFolderPath: String) = 
        installer.installLocal(filePath, targetFolderPath).onSuccess { manifest ->
            if (manifest != null) {
                coordinator.handlePostInstall(manifest.plugin.id, manifest)
            }
        }.map { Unit }

    suspend fun enqueueRemoteInstall(plugin: ExtensionPlugin, targetFolderPath: String) =
        installer.enqueueRemoteInstall(plugin, targetFolderPath)

    suspend fun installRemote(
        plugin: ExtensionPlugin,
        targetFolderPath: String,
        onProgress: ((Float) -> Unit)? = null
    ) = installer.installRemote(plugin, targetFolderPath, onProgress).onSuccess { manifest ->
        if (manifest != null) {
            coordinator.handlePostInstall(plugin.pkg, manifest)
        }
    }.map { Unit }

    suspend fun uninstall(pkg: String) = installer.uninstall(pkg)

    suspend fun updateLocal(pkg: String, newJarPath: String) = 
        installer.updateLocal(pkg, newJarPath).onSuccess { manifest ->
            if (manifest != null) {
                coordinator.handlePostUpdate(pkg, manifest, installer)
            }
        }.map { Unit }

    suspend fun updateRemote(pkg: String) = 
        installer.updateRemote(pkg).onSuccess { manifest ->
            if (manifest != null) {
                coordinator.handlePostUpdate(pkg, manifest, installer)
            }
        }.map { Unit }

    fun getUpdate(pkg: String) = installer.getUpdate(pkg)

    suspend fun fetchRemoteChangelog(pkg: String): String? = 
        repoManager.fetchRemoteChangelog(pkg)

    // --- Lifecycle Management ---

    suspend fun loadPlugin(pkg: String) = coordinator.loadPlugin(pkg)

    suspend fun unloadPlugin(pkg: String) = coordinator.unloadPlugin(pkg)

    fun reloadPlugin(pkg: String) {
        scope.launch { coordinator.reloadPlugin(pkg) }
    }

    fun reloadAll() {
        installedPlugins.value.forEach { reloadPlugin(it.pkg) }
    }

    // --- Scanning ---

    suspend fun refreshInstalledPlugins() = scanner.refreshInstalledPlugins()

    fun rescanManagedFolders() {
        scope.launch {
            scanner.rescanManagedFolders()
            // Post-scan: load plugins that are enabled and validated but not yet loaded
            installedPlugins.value.filter { it.isEnabled && it.isValidated && !loadedPlugins.value.contains(it.pkg) }
                .forEach { launch { loadPlugin(it.pkg) } }
        }
    }

    // --- Folder Management ---

    fun getManagedFolders() = folderManager.getManagedFolders()

    suspend fun removeManagedFolder(folderPath: String) = folderManager.removeManagedFolder(folderPath)

    // --- Settings ---

    fun loadPluginSettings(pkg: String) = lifecycleManager.loadPluginSettings(pkg)

    fun savePluginSettings(pkg: String, store: PluginSettingsStore) = lifecycleManager.savePluginSettings(pkg, store)

    fun getManifest(pkg: String): PluginManifest? = lifecycleManager.getManifest(pkg)

    suspend fun setEnabled(pkg: String, enabled: Boolean) = coordinator.setEnabled(pkg, enabled)

    suspend fun updatePlugin(pkg: String, transform: (InstalledPlugin) -> InstalledPlugin) = 
        registry.updatePlugin(pkg, transform)

    // --- Context & Jobs ---

    fun createPluginContext(pkg: String, jobId: String? = null) = 
        lifecycleManager.createPluginContext(pkg, jobId)

    suspend fun validatePluginInJob(pkg: String) = coordinator.triggerValidation(pkg)

    suspend fun validatePlugin(pkg: String) = coordinator.validatePlugin(pkg)

    suspend fun enqueueSetupJob(pkg: String) = coordinator.enqueueSetupJob(pkg)

    suspend fun rerunSetup(pkg: String) = coordinator.rerunSetup(pkg, installer)

    suspend fun runAction(pkg: String, action: PluginAction) = coordinator.runAction(pkg, action)

    suspend fun enqueueUpdateJob(pkg: String) = coordinator.enqueueUpdateJob(pkg)

    suspend fun checkAndResumeSetup(pkg: String) = coordinator.checkAndResumeSetup(pkg)
}
