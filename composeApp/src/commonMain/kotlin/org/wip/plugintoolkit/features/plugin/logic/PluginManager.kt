package org.wip.plugintoolkit.features.plugin.logic

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.wip.plugintoolkit.api.PluginContext
import org.wip.plugintoolkit.api.PluginAction
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobStatus
import org.wip.plugintoolkit.features.job.model.JobType
import org.wip.plugintoolkit.features.plugin.model.InstalledPlugin
import org.wip.plugintoolkit.features.plugin.model.PluginSettingsStore
import org.wip.plugintoolkit.features.repository.logic.RepoManager
import org.wip.plugintoolkit.features.repository.model.ExtensionPlugin
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository
import kotlin.time.Clock

/**
 * Facade class that coordinates plugin management by delegating to specialized components.
 * Maintains compatibility with existing UI components while providing a cleaner internal structure.
 */
class PluginManager(
    private val settingsRepository: SettingsRepository,
    private val repoManager: RepoManager,
    private val jobManager: JobManager,
    private val registry: PluginRegistry,
    private val installer: PluginInstaller,
    private val lifecycleManager: PluginLifecycleManager,
    private val scanner: PluginScanner
) {
    val installedPlugins: StateFlow<List<InstalledPlugin>> = registry.installedPlugins
    val loadedPlugins: StateFlow<Set<String>> = lifecycleManager.loadedPlugins

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        Logger.i { "Initializing PluginManager facade" }
        
        // Observe jobs to mark plugins as validated or set up
        scope.launch {
            jobManager.jobs.collect { jobs ->
                jobs.forEach { job ->
                    if (job.status == JobStatus.Completed) {
                        if (job.type == JobType.Validation || job.type == JobType.Setup) {
                            markAsValidated(job.pluginId)
                        }
                    }
                }
            }
        }
    }

    // --- Installation & Updates ---

    suspend fun installLocal(filePath: String, targetFolderPath: String) = 
        installer.installLocal(filePath, targetFolderPath)

    suspend fun installRemote(plugin: ExtensionPlugin, targetFolderPath: String) = 
        installer.installRemote(plugin, targetFolderPath)

    suspend fun uninstall(pkg: String) = installer.uninstall(pkg)

    suspend fun updateLocal(pkg: String, newJarPath: String) = installer.updateLocal(pkg, newJarPath)

    suspend fun updateRemote(pkg: String) = installer.updateRemote(pkg)

    fun getUpdate(pkg: String) = installer.getUpdate(pkg)

    suspend fun fetchRemoteChangelog(pkg: String): String? {
        val remote = repoManager.plugins.value.values.flatten().find { it.pkg == pkg } ?: return null
        val repoUrl = remote.repoUrl ?: return null
        val pluginsFolder = repoUrl.substringBeforeLast("/") + "/plugins"
        val baseUrl = "$pluginsFolder/${remote.pkg}"
        return repoManager.fetchText("$baseUrl/changelog.txt")
    }

    // --- Lifecycle Management ---

    suspend fun loadPlugin(pkg: String) = lifecycleManager.loadPlugin(pkg)

    suspend fun unloadPlugin(pkg: String) = lifecycleManager.unloadPlugin(pkg)

    fun reloadPlugin(pkg: String) {
        scope.launch { lifecycleManager.reloadPlugin(pkg) }
    }

    fun reloadAll() {
        installedPlugins.value.forEach { reloadPlugin(it.pkg) }
    }

    // --- Scanning ---

    fun refreshInstalledPlugins() = scanner.refreshInstalledPlugins()

    fun rescanManagedFolders() {
        scope.launch {
            scanner.rescanManagedFolders()
            // Post-scan: load plugins that are enabled and validated but not yet loaded
            installedPlugins.value.filter { it.isEnabled && it.isValidated && !loadedPlugins.value.contains(it.pkg) }
                .forEach { launch { loadPlugin(it.pkg) } }
        }
    }

    // --- Folder Management ---

    fun getManagedFolders() = registry.getManagedFolders()

    suspend fun removeManagedFolder(folderPath: String): Result<Unit> {
        val defaultFolder = registry.getDefaultPluginFolder()
        if (folderPath.replace('\\', '/').removeSuffix("/") == defaultFolder.replace('\\', '/').removeSuffix("/")) {
            return Result.failure(Exception("Cannot remove default plugins folder"))
        }

        val normalizedFolder = folderPath.replace('\\', '/').removeSuffix("/")
        val pluginsInFolder = installedPlugins.value.filter { 
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

    // --- Settings ---

    fun loadPluginSettings(pkg: String) = lifecycleManager.loadPluginSettings(pkg)

    fun savePluginSettings(pkg: String, store: PluginSettingsStore) = lifecycleManager.savePluginSettings(pkg, store)

    suspend fun setEnabled(pkg: String, enabled: Boolean): Result<Unit> {
        Logger.i { "Setting plugin $pkg enabled: $enabled" }

        if (!enabled) {
            lifecycleManager.ensureSafeToUnload(listOf(pkg)).onFailure { return Result.failure(it) }
            lifecycleManager.unloadPlugin(pkg)
        }

        registry.updatePlugin(pkg) { it.copy(isEnabled = enabled) }

        if (enabled) {
            val plugin = registry.getPlugin(pkg)
            if (plugin != null) {
                if (plugin.isValidated) loadPlugin(pkg) else enqueueSetupJob(pkg)
            }
        }
        return Result.success(Unit)
    }

    // --- Context & Jobs ---

    fun createPluginContext(pkg: String, jobId: String? = null): PluginContext = 
        lifecycleManager.createPluginContext(pkg, jobId)

    suspend fun validatePluginInJob(pkg: String): Result<Unit> {
        val plugin = registry.getPlugin(pkg) ?: return Result.failure(Exception("Plugin not found"))
        val loadResult = loadPlugin(pkg)
        if (loadResult.isFailure) return Result.failure(loadResult.exceptionOrNull()!!)

        val job = BackgroundJob(
            id = "val_$pkg",
            name = "Validation: ${plugin.name}",
            type = JobType.Validation,
            pluginId = pkg,
            capabilityName = "validate",
            keepResult = false
        )
        jobManager.enqueueJob(job)
        return Result.success(Unit)
    }

    suspend fun validatePlugin(pkg: String): Result<Unit> {
        val plugin = PluginLoader.getPluginById(pkg) ?: return Result.failure(Exception("Plugin not loaded"))
        return plugin.validate(createPluginContext(pkg))
    }

    suspend fun enqueueSetupJob(pkg: String) {
        val plugin = registry.getPlugin(pkg) ?: return
        val loadResult = loadPlugin(pkg)
        if (loadResult.isFailure) {
            Logger.e { "Failed to load plugin $pkg for setup: ${loadResult.exceptionOrNull()?.message}" }
            return
        }

        val job = BackgroundJob(
            id = "setup_$pkg",
            name = "Setup: ${plugin.name}",
            type = JobType.Setup,
            pluginId = pkg,
            capabilityName = "setup",
            keepResult = false
        )
        jobManager.enqueueJob(job)
    }

    suspend fun runAction(pkg: String, action: PluginAction) {
        val plugin = registry.getPlugin(pkg) ?: return
        Logger.i { "Enqueuing custom action: ${action.name} for plugin: $pkg" }

        val loadResult = loadPlugin(pkg)
        if (loadResult.isFailure) {
            Logger.e { "Failed to load plugin $pkg for action ${action.name}" }
            return
        }

        val job = BackgroundJob(
            id = "action_${pkg}_${action.functionName}_${Clock.System.now().toEpochMilliseconds()}",
            name = "Action: ${action.name} (${plugin.name})",
            type = JobType.PluginAction,
            pluginId = pkg,
            capabilityName = action.functionName,
            keepResult = false
        )
        jobManager.enqueueJob(job)
    }

    private fun markAsValidated(pkg: String) {
        val plugin = registry.getPlugin(pkg) ?: return
        if (plugin.isValidated) return

        Logger.i { "Marking plugin $pkg as validated and activating" }
        scope.launch {
            registry.updatePlugin(pkg) { it.copy(isValidated = true) }
            loadPlugin(pkg)
        }
    }
}
