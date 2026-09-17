package org.wip.plugintoolkit.features.plugin.logic

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.wip.plugintoolkit.api.PluginAction
import org.wip.plugintoolkit.api.PluginManifest
import org.wip.plugintoolkit.api.PluginMigration
import org.wip.plugintoolkit.features.plugin.model.InstalledPlugin
import org.wip.plugintoolkit.features.plugin.model.PluginSettingsStore
import org.wip.plugintoolkit.features.repository.logic.RepoManager
import org.wip.plugintoolkit.features.repository.model.ExtensionPlugin

class PluginManager(
    private val repoManager: RepoManager? = null,
    private val registry: PluginRegistry,
    private val installer: PluginInstaller? = null,
    private val lifecycleManager: PluginLifecycleManager,
    private val scanner: PluginScanner? = null,
    private val coordinator: PluginLifecycleCoordinator,
    private val folderManager: PluginFolderManager? = null,
    private val scope: CoroutineScope
) {
    val installedPlugins: StateFlow<List<InstalledPlugin>> = registry.installedPlugins
    val loadedPlugins: StateFlow<Set<String>> = lifecycleManager.loadedPlugins
    val loadingPlugins: StateFlow<Set<String>> = lifecycleManager.loadingPlugins
    val pluginLoadingSteps: StateFlow<Map<String, String>> = lifecycleManager.pluginLoadingSteps
    val isRegistryReady: StateFlow<Boolean> = registry.isReady
    val pluginLocksState: StateFlow<Map<String, Map<String, Boolean>>> = lifecycleManager.pluginLocksState
    val pluginSettingsState: StateFlow<Map<String, PluginSettingsStore>> = lifecycleManager.pluginSettingsState

    init {
        Logger.i { "Initializing PluginManager facade" }
    }

    // --- Installation & Updates ---

    suspend fun installLocal(filePath: String, targetFolderPath: String): Result<Unit> {
        val inst = installer ?: return Result.failure(UnsupportedOperationException("Installer not configured in standalone mode"))
        return inst.installLocal(filePath, targetFolderPath).onSuccess { manifest ->
            if (manifest != null) {
                coordinator.handlePostInstall(manifest.plugin.id, manifest)
            }
        }.map { }
    }

    suspend fun enqueueRemoteInstall(plugin: ExtensionPlugin, targetFolderPath: String) {
        installer?.enqueueRemoteInstall(plugin, targetFolderPath)
    }

    suspend fun installRemote(
        plugin: ExtensionPlugin,
        targetFolderPath: String,
        onDownloadProgress: ((bytesRead: Long, totalBytes: Long?, fraction: Float) -> Unit)? = null,
        onProgress: ((Float) -> Unit)? = null
    ): Result<Unit> {
        val inst = installer ?: return Result.failure(UnsupportedOperationException("Installer not configured in standalone mode"))
        return inst.installRemote(plugin, targetFolderPath, onDownloadProgress, onProgress).onSuccess { manifest ->
            if (manifest != null) {
                coordinator.handlePostInstall(plugin.pkg, manifest)
            }
        }.map { }
    }

    suspend fun uninstall(pkg: String): Result<Unit> {
        val inst = installer ?: return Result.failure(UnsupportedOperationException("Installer not configured in standalone mode"))
        return inst.uninstall(pkg)
    }

    suspend fun updateLocal(pkg: String, newJarPath: String): Result<Unit> {
        val inst = installer ?: return Result.failure(UnsupportedOperationException("Installer not configured in standalone mode"))
        return inst.updateLocal(pkg, newJarPath).onSuccess { manifest ->
            if (manifest != null) {
                coordinator.handlePostUpdate(pkg, manifest, inst)
            }
        }.map { }
    }

    suspend fun updateRemote(pkg: String): Result<Unit> {
        val inst = installer ?: return Result.failure(UnsupportedOperationException("Installer not configured in standalone mode"))
        return inst.updateRemote(pkg).onSuccess { manifest ->
            if (manifest != null) {
                coordinator.handlePostUpdate(pkg, manifest, inst)
            }
        }.map { }
    }

    fun getUpdate(pkg: String) = installer?.getUpdate(pkg)

    suspend fun fetchRemoteChangelog(pkg: String): String? =
        repoManager?.fetchRemoteChangelog(pkg)

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

    suspend fun refreshInstalledPlugins() = scanner?.refreshInstalledPlugins()

    fun rescanManagedFolders() {
        val sc = scanner ?: return
        scope.launch {
            sc.rescanManagedFolders()
            // Post-scan: load plugins that are enabled, validated, and don't require setup/action
            installedPlugins.value.filter {
                it.isEnabled && it.isValidated && it.requiredAction == null && !loadedPlugins.value.contains(
                    it.pkg
                )
            }
                .forEach { launch { loadPlugin(it.pkg) } }
        }
    }

    // --- Folder Management ---

    fun getManagedFolders(): List<String> = folderManager?.getManagedFolders() ?: emptyList()

    suspend fun removeManagedFolder(folderPath: String): Result<Unit> {
        val fm = folderManager ?: return Result.failure(UnsupportedOperationException("FolderManager not configured in standalone mode"))
        return fm.removeManagedFolder(folderPath)
    }

    // --- Settings ---

    fun loadPluginSettings(pkg: String) = lifecycleManager.loadPluginSettings(pkg)

    fun savePluginSettings(pkg: String, store: PluginSettingsStore) {
        lifecycleManager.savePluginSettings(pkg, store)
        scope.launch {
            try {
                lifecycleManager.refreshLocks(pkg)
            } catch (t: Throwable) {
                Logger.e(t) { "Failed to refresh locks after saving settings for $pkg" }
            }
        }
    }

    suspend fun refreshLocks(pkg: String, overriddenSettings: org.wip.plugintoolkit.features.plugin.model.PluginSettingsStore? = null) =
        lifecycleManager.refreshLocks(pkg, overriddenSettings)

    suspend fun refreshAllLocks(): Map<String, Map<String, Boolean>> =
        lifecycleManager.refreshAllLocks()

    fun getManifest(pkg: String): PluginManifest? = lifecycleManager.getManifest(pkg)

    fun getMigrations(pkg: String): List<PluginMigration> = lifecycleManager.getMigrations(pkg)

    suspend fun setEnabled(pkg: String, enabled: Boolean) = coordinator.setEnabled(pkg, enabled)

    suspend fun updatePlugin(pkg: String, transform: (InstalledPlugin) -> InstalledPlugin) =
        registry.updatePlugin(pkg, transform)

    // --- Context & Jobs ---

    fun createPluginContext(
        pkg: String,
        jobId: String? = null,
        capabilityName: String? = null,
        manifest: org.wip.plugintoolkit.api.PluginManifest? = null,
        allowedPaths: List<String> = emptyList(),
        isDestructiveAllowed: Boolean = false,
        executionFileSystem: org.wip.plugintoolkit.api.ExecutionFileSystem? = null
    ) =
        lifecycleManager.createPluginContext(pkg, jobId, capabilityName, manifest, allowedPaths, isDestructiveAllowed, executionFileSystem)

    suspend fun validatePluginInJob(pkg: String) = coordinator.triggerValidation(pkg)

    suspend fun validatePlugin(pkg: String) = coordinator.validatePlugin(pkg)

    suspend fun enqueueSetupJob(pkg: String) = coordinator.enqueueSetupJob(pkg)

    suspend fun rerunSetup(pkg: String) {
        val inst = installer
        if (inst != null) {
            coordinator.rerunSetup(pkg, inst)
        } else {
            coordinator.enqueueSetupJob(pkg)
        }
    }

    suspend fun runAction(
        pkg: String,
        action: PluginAction,
        parameters: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap()
    ) = coordinator.runAction(pkg, action, parameters)

    suspend fun enqueueUpdateJob(pkg: String) = coordinator.enqueueUpdateJob(pkg)

    suspend fun checkAndResumeSetup(pkg: String) = coordinator.checkAndResumeSetup(pkg)
}
