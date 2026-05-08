package org.wip.plugintoolkit.features.plugin.logic

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.component.KoinComponent
import org.wip.plugintoolkit.api.*
import org.wip.plugintoolkit.core.utils.PlatformUtils
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.job.model.JobStatus
import org.wip.plugintoolkit.features.plugin.model.PluginSettingsStore
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository
import org.wip.plugintoolkit.features.settings.model.PluginUnplugBehavior
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages the runtime lifecycle of plugins (loading, unloading, context creation).
 * Ensures sequential operations per plugin to avoid race conditions during rapid reloads.
 */
class PluginLifecycleManager(
    private val registry: PluginRegistry,
    private val jobManager: JobManager,
    private val settingsRepository: SettingsRepository
) {
    private val _loadedPlugins = MutableStateFlow<Set<String>>(emptySet())
    val loadedPlugins: StateFlow<Set<String>> = _loadedPlugins.asStateFlow()

    // Mutex per plugin ID to prevent concurrent load/unload/reload for the same plugin
    private val pluginMutexes = ConcurrentHashMap<String, Mutex>()
    
    private val json = kotlinx.serialization.json.Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun getMutex(pkg: String) = pluginMutexes.getOrPut(pkg) { Mutex() }

    /**
     * Loads a plugin into the JVM and initializes it.
     */
    suspend fun loadPlugin(pkg: String): Result<Unit> = getMutex(pkg).withLock {
        Logger.i { "Loading plugin: $pkg" }
        val plugin = registry.getPlugin(pkg) ?: return Result.failure(Exception("Plugin $pkg not found in registry"))
        
        if (!plugin.isEnabled) {
            Logger.d { "Plugin $pkg is disabled, skipping runtime load" }
            return Result.success(Unit)
        }

        val jarFileName = plugin.jarFileName ?: (plugin.pkg.substringAfterLast(".") + ".jar")
        val jarFile = "${plugin.installPath}/$jarFileName"

        Logger.d { "Requesting PluginLoader to load JAR: $jarFile" }
        val result = PluginLoader.loadPlugin(jarFile)
        
        return if (result.isSuccess) {
            val entry = result.getOrThrow()
            try {
                // Validate manifest can be read
                entry.getManifest()
                
                // Initialize with context
                val initResult = entry.initialize(createPluginContext(pkg))
                if (initResult.isFailure) {
                    val error = initResult.exceptionOrNull() ?: Exception("Initialization failed")
                    Logger.e(error) { "Initialization failed for $pkg" }
                    updateLoadError(pkg, error.message)
                    return Result.failure(error)
                }

                // Perform load step
                val loadResult = entry.performLoad(createPluginContext(pkg))
                if (loadResult.isFailure) {
                    val error = loadResult.exceptionOrNull() ?: Exception("Load failed")
                    Logger.e(error) { "Load failed for $pkg" }
                    updateLoadError(pkg, error.message)
                    return Result.failure(error)
                }

                if (plugin.isValidated) {
                    _loadedPlugins.update { it + pkg }
                    updateLoadError(pkg, null) // Clear errors on success
                    Logger.i { "Plugin $pkg successfully loaded and activated" }
                } else {
                    Logger.i { "Plugin $pkg loaded but waiting for validation/activation" }
                }
                Result.success(Unit)
            } catch (t: Throwable) {
                val msg = "Fatal error during initialization of $pkg: ${t.message}"
                Logger.e(t) { msg }
                updateLoadError(pkg, msg)
                Result.failure(Exception(msg, t))
            }
        } else {
            val error = result.exceptionOrNull() ?: Exception("Unknown load error")
            Logger.e(error) { "PluginLoader failed for $pkg" }
            updateLoadError(pkg, error.message)
            Result.failure(error)
        }
    }

    /**
     * Unloads a plugin from the runtime.
     */
    suspend fun unloadPlugin(pkg: String) = getMutex(pkg).withLock {
        Logger.i { "Unloading plugin: $pkg" }
        
        // Safety check for running jobs
        ensureSafeToUnload(listOf(pkg)).onFailure { throw it }

        val plugin = registry.getPlugin(pkg)
        if (plugin == null) {
            Logger.w { "Cannot unload $pkg: not found in registry" }
            return@withLock
        }
        
        val jarFileName = plugin.jarFileName ?: (plugin.pkg.substringAfterLast(".") + ".jar")
        val jarFile = "${plugin.installPath}/$jarFileName"
        
        PluginLoader.unloadPlugin(jarFile)
        _loadedPlugins.update { it - pkg }
        Logger.d { "Plugin $pkg unloaded and removed from active set" }
    }

    /**
     * Sequential unload and load.
     */
    suspend fun reloadPlugin(pkg: String) {
        unloadPlugin(pkg)
        loadPlugin(pkg)
    }

    /**
     * Ensures that no jobs are running for the specified plugins before unloading.
     * Blocks or cancels jobs based on user settings.
     */
    suspend fun ensureSafeToUnload(pkgs: List<String>): Result<Unit> {
        val runningJobs = jobManager.jobs.value.filter { it.pluginId in pkgs && it.status == JobStatus.Running }
        if (runningJobs.isNotEmpty()) {
            val settings = settingsRepository.loadSettings()
            if (settings.extensions.pluginUnplugBehavior == PluginUnplugBehavior.Block) {
                val msg = "Cannot proceed: ${runningJobs.size} jobs are still running for plugins: ${pkgs.joinToString()}"
                Logger.w { msg }
                return Result.failure(Exception(msg))
            } else {
                Logger.i { "Stopping ${runningJobs.size} jobs before unloading plugins: ${pkgs.joinToString()}" }
                runningJobs.forEach { jobManager.cancelJob(it.id) }
            }
        }
        return Result.success(Unit)
    }

    private suspend fun updateLoadError(pkg: String, error: String?) {
        registry.updatePlugin(pkg) { 
            it.copy(
                loadError = error,
                // Fatal load errors invalidate the plugin state
                isValidated = if (error != null) false else it.isValidated
            )
        }
    }

    fun loadPluginSettings(pkg: String): PluginSettingsStore {
        val plugin = registry.getPlugin(pkg) ?: return PluginSettingsStore()
        val settingsFile = "${plugin.installPath}/settings.json"
        val content = PlatformUtils.readFile(settingsFile)
        return if (content != null) {
            try {
                json.decodeFromString<PluginSettingsStore>(content)
            } catch (e: Exception) {
                Logger.e(e) { "Failed to parse settings for $pkg" }
                PluginSettingsStore()
            }
        } else {
            PluginSettingsStore()
        }
    }

    fun savePluginSettings(pkg: String, store: PluginSettingsStore) {
        val plugin = registry.getPlugin(pkg) ?: return
        val settingsFile = "${plugin.installPath}/settings.json"
        try {
            PlatformUtils.writeFile(settingsFile, json.encodeToString(store))
        } catch (t: Throwable) {
            Logger.e(t) { "Failed to save settings for $pkg" }
        }
    }

    fun createPluginContext(pkg: String, jobId: String? = null): PluginContext {
        val plugin = registry.getPlugin(pkg)
        val installPath = plugin?.installPath ?: ""
        val jarFullPath = plugin?.let { "${it.installPath}/${it.jarFileName}" }

        val storedSettings = loadPluginSettings(pkg)
        val manifest = PluginLoader.getPluginById(pkg)?.getManifest()
        val mergedSettings = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
        
        // 1. Manifest defaults
        manifest?.settings?.forEach { (key, meta) ->
            meta.defaultValue?.let { mergedSettings[key] = it }
        }
        
        // 2. User overrides
        mergedSettings.putAll(storedSettings.settings)

        val pluginLogger = jobManager.getPluginLogger(pkg, jobId)
        val progressReporter = object : ProgressReporter {
            override fun report(progress: Float) {
                if (jobId != null) jobManager.updateJobProgress(jobId, progress)
            }
        }

        return DefaultPluginContext(
            logger = pluginLogger,
            progress = progressReporter,
            fileSystem = DefaultPluginFileSystem(installPath, jarFullPath),
            cacheFileSystem = DefaultPluginFileSystem.createCacheOnly(installPath),
            settings = mergedSettings,
            onRequiredActionChange = { actionName ->
                registry.scope.launch {
                    registry.updatePlugin(pkg) { it.copy(requiredAction = actionName) }
                }
            }
        )
    }


}

/**
 * Concrete implementation of PluginSignalManager.
 */
class DefaultPluginSignalManager : PluginSignalManager {
    private val signalHandlers = mutableListOf<suspend (PluginSignal) -> Unit>()

    override fun onSignal(handler: suspend (PluginSignal) -> Unit) {
        signalHandlers.add(handler)
    }

    override suspend fun sendSignal(signal: PluginSignal) {
        signalHandlers.forEach { it(signal) }
    }
}

/**
 * Concrete implementation of PluginContext.
 */
class DefaultPluginContext(
    override val logger: PluginLogger,
    override val progress: ProgressReporter,
    override val fileSystem: org.wip.plugintoolkit.api.PluginFileSystem,
    override val cacheFileSystem: org.wip.plugintoolkit.api.PluginFileSystem,
    override val settings: Map<String, kotlinx.serialization.json.JsonElement>,
    override val signals: PluginSignalManager = DefaultPluginSignalManager(),
    private val onRequiredActionChange: (String?) -> Unit = {}
) : PluginContext {
    override fun setRequiredAction(actionName: String?) {
        onRequiredActionChange(actionName)
    }
}
