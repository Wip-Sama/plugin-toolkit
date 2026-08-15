package org.wip.plugintoolkit.features.plugin.logic

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import org.wip.plugintoolkit.api.PluginContext
import org.wip.plugintoolkit.api.PluginFileSystem
import org.wip.plugintoolkit.api.PluginLogger
import org.wip.plugintoolkit.api.PluginManifest
import org.wip.plugintoolkit.api.PluginMigration
import org.wip.plugintoolkit.api.PluginSignal
import org.wip.plugintoolkit.api.PluginSignalManager
import org.wip.plugintoolkit.api.ProgressReporter
import org.wip.plugintoolkit.core.utils.FileSystem
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.job.model.JobStatus
import org.wip.plugintoolkit.features.plugin.model.PluginSettingsStore
import org.wip.plugintoolkit.features.plugin.model.resolveCustomSettings
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository
import org.wip.plugintoolkit.features.settings.model.PluginUnplugBehavior
import org.wip.plugintoolkit.features.plugin.utils.PluginCompatibilityUtils
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.collections.immutable.persistentMapOf

/**
 * Manages the runtime lifecycle of plugins (loading, unloading, context creation).
 * Ensures sequential operations per plugin to avoid race conditions during rapid reloads.
 */
class PluginLifecycleManager(
    private val registry: PluginRegistry,
    private val jobManager: JobManager,
    private val settingsRepository: SettingsRepository,
    private val fileSystem: FileSystem
) {
    private val _loadedPlugins = MutableStateFlow<Set<String>>(emptySet())
    val loadedPlugins: StateFlow<Set<String>> = _loadedPlugins.asStateFlow()

    private val _pluginLocksState = MutableStateFlow<Map<String, Map<String, Boolean>>>(emptyMap())
    val pluginLocksState: StateFlow<Map<String, Map<String, Boolean>>> = _pluginLocksState.asStateFlow()

    // Cache for decrypted plugin settings to avoid redundant IO and decryption
    private val _pluginSettingsState = MutableStateFlow<Map<String, PluginSettingsStore>>(emptyMap())
    val pluginSettingsState: StateFlow<Map<String, PluginSettingsStore>> = _pluginSettingsState.asStateFlow()

    private val json = kotlinx.serialization.json.Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val pluginLocks = atomic(persistentMapOf<String, Mutex>())
    private fun getPluginLock(pkg: String): Mutex {
        pluginLocks.value[pkg]?.let { return it }
        val newMutex = Mutex()
        pluginLocks.update { map ->
            if (map.containsKey(pkg)) map else map.put(pkg, newMutex)
        }
        return pluginLocks.value.getValue(pkg)
    }

    /**
     * Loads a plugin into the JVM and initializes it.
     */
    suspend fun loadPlugin(pkg: String): Result<Unit> = getPluginLock(pkg).withLock {
        loadPluginInternal(pkg)
    }

    private suspend fun loadPluginInternal(pkg: String): Result<Unit> {
        Logger.i { "Loading plugin: $pkg" }
        val plugin = registry.getPlugin(pkg) ?: return Result.failure(Exception("Plugin $pkg not found in registry"))

        if (!plugin.isEnabled) {
            Logger.d { "Plugin $pkg is disabled, skipping runtime load" }
            return Result.success(Unit)
        }

        val (isComp, compError) = PluginCompatibilityUtils.checkCompatibility(plugin)
        if (!isComp || !plugin.isCompatible) {
            val errorMsg = compError ?: plugin.compatibilityError ?: "Plugin is incompatible with the current app version"
            Logger.w { "Cannot load plugin $pkg: $errorMsg" }
            registry.updatePlugin(pkg) { it.copy(isCompatible = false, compatibilityError = compError ?: it.compatibilityError) }
            return Result.failure(Exception(errorMsg))
        }

        val jarFileName = plugin.jarFileName ?: (plugin.pkg.substringAfterLast(".") + ".jar")
        val jarFile = "${plugin.installPath}/$jarFileName"

        // Runtime signature verification for remote plugins
        if (plugin.repoUrl != null) {
            val repo = settingsRepository.loadSettings().extensions.repositories.find { it.url == plugin.repoUrl }
            val publicKey = repo?.signPublicKey
            val strictChecking = settingsRepository.loadSettings().extensions.strictSignatureChecking

            if (publicKey != null) {
                val isSignatureValid = PluginSecurity.verify(jarFile, publicKey)
                if (!isSignatureValid) {
                    val msg = "Plugin signature verification failed for ${plugin.pkg}"
                    Logger.w { msg }
                    if (strictChecking || plugin.requiredAction == "CONFIRM_SIGNATURE") {
                        updateLoadError(pkg, msg)
                        return Result.failure(Exception(msg))
                    }
                }
            }
        }

        Logger.d { "Requesting PluginLoader to load JAR: $jarFile" }
        val settings = loadPluginSettings(pkg)
        val result = try {
            PluginLoader.loadPlugin(jarFile, settings.settings)
        } catch (t: Throwable) {
            Result.failure(Exception("Fatal error loading plugin classes", t))
        }

        return if (result.isSuccess) {
            val entry = result.getOrThrow()
            try {
                // Initialize with context
                val manifest = entry.getManifest().getOrThrow()
                val initResult = entry.initialize(createPluginContext(pkg, manifest = manifest))
                if (initResult.isFailure) {
                    val error = initResult.exceptionOrNull() ?: Exception("Initialization failed")
                    Logger.e(error) { "Initialization failed for $pkg" }
                    updateLoadError(pkg, error.message)
                    return Result.failure(error)
                }

                // Perform load step only if already validated.
                // For new installations/updates, this is handled by the JobWorker after setup/update.
                if (plugin.isValidated) {
                    val loadResult = entry.performLoad(createPluginContext(pkg, manifest = manifest))
                    if (loadResult.isFailure) {
                        val error = loadResult.exceptionOrNull() ?: Exception("Load failed")
                        Logger.e(error) { "Load failed for $pkg" }
                        updateLoadError(pkg, error.message)
                        return Result.failure(error)
                    }
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
    suspend fun unloadPlugin(pkg: String) = getPluginLock(pkg).withLock {
        unloadPluginInternal(pkg)
    }

    private suspend fun unloadPluginInternal(pkg: String) {
        Logger.i { "Unloading plugin: $pkg" }

        // Safety check for running jobs
        ensureSafeToUnload(listOf(pkg)).onFailure { throw it }

        val plugin = registry.getPlugin(pkg)
        if (plugin == null) {
            Logger.w { "Cannot unload $pkg: not found in registry" }
            return
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
    suspend fun reloadPlugin(pkg: String) = getPluginLock(pkg).withLock {
        unloadPluginInternal(pkg)
        loadPluginInternal(pkg)
    }

    /**
     * Executes the setup phase for a plugin.
     */
    suspend fun performSetup(pkg: String): Result<Unit> {
        val entry = PluginLoader.getPluginById(pkg) ?: return Result.failure(Exception("Plugin $pkg not loaded"))
        return entry.performSetup(createPluginContext(pkg))
    }

    /**
     * Executes the update phase for a plugin.
     */
    suspend fun performUpdate(pkg: String): Result<Unit> {
        val entry = PluginLoader.getPluginById(pkg) ?: return Result.failure(Exception("Plugin $pkg not loaded"))
        return entry.performUpdate(createPluginContext(pkg))
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
                val msg =
                    "Cannot proceed: ${runningJobs.size} jobs are still running for plugins: ${pkgs.joinToString()}"
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
        // Return from cache if available
        _pluginSettingsState.value[pkg]?.let { return it }

        val plugin = registry.getPlugin(pkg) ?: return PluginSettingsStore()
        val settingsFile = "${plugin.installPath}/settings.json"
        val content = fileSystem.readFile(settingsFile)
        val store = if (content != null) {
            try {
                json.decodeFromString<PluginSettingsStore>(content)
            } catch (e: Exception) {
                Logger.e(e) { "Failed to parse settings for $pkg" }
                PluginSettingsStore()
            }
        } else {
            PluginSettingsStore()
        }

        val manifest = getManifest(pkg)

        val decryptedSettings = store.settings.mapValues { (key, value) ->
            val isSecret = manifest?.settings?.get(key)?.secret == true
            if (isSecret && value is kotlinx.serialization.json.JsonPrimitive && value.isString) {
                val content = value.content
                if (content.startsWith("ENC[") && content.endsWith("]")) {
                    val encryptedPart = content.substring(4, content.length - 1)
                    kotlinx.serialization.json.JsonPrimitive(
                        org.wip.plugintoolkit.core.utils.SecureStorage.decrypt(
                            encryptedPart
                        )
                    )
                } else {
                    value
                }
            } else {
                value
            }
        }

        val decryptedStore = store.copy(settings = decryptedSettings)
            .withResolvedAutogeneratedSettings(manifest?.settings.orEmpty())
        _pluginSettingsState.update { it + (pkg to decryptedStore) }
        return decryptedStore
    }

    fun savePluginSettings(pkg: String, store: PluginSettingsStore) {
        val plugin = registry.getPlugin(pkg) ?: return
        val settingsFile = "${plugin.installPath}/settings.json"

        val manifest = getManifest(pkg)
        val resolvedStore = store.withResolvedAutogeneratedSettings(manifest?.settings.orEmpty())
        val encryptedSettings = resolvedStore.settings.mapValues { (key, value) ->
            val isSecret = manifest?.settings?.get(key)?.secret == true
            if (isSecret && value is kotlinx.serialization.json.JsonPrimitive && value.isString) {
                val encrypted = org.wip.plugintoolkit.core.utils.SecureStorage.encrypt(value.content)
                kotlinx.serialization.json.JsonPrimitive("ENC[$encrypted]")
            } else {
                value
            }
        }
        val storeToSave = resolvedStore.copy(settings = encryptedSettings)

        try {
            fileSystem.writeFile(settingsFile, json.encodeToString(storeToSave))
            // Update cache with the decrypted store
            _pluginSettingsState.update { it + (pkg to resolvedStore) }
        } catch (t: Throwable) {
            Logger.e(t) { "Failed to save settings for $pkg" }
        }
    }

    fun createPluginContext(
        pkg: String,
        jobId: String? = null,
        capabilityName: String? = null,
        manifest: PluginManifest? = null,
        allowedPaths: List<String> = emptyList(),
        isDestructiveAllowed: Boolean = false,
        executionFileSystem: org.wip.plugintoolkit.api.ExecutionFileSystem? = null,
        overriddenSettings: PluginSettingsStore? = null
    ): PluginContext {
        val plugin = registry.getPlugin(pkg)
        val installPath = plugin?.installPath ?: ""
        val jarFullPath = plugin?.let { "${it.installPath}/${it.jarFileName}" }

        val actualManifest = manifest ?: getManifest(pkg)
        val storedSettings = (overriddenSettings ?: loadPluginSettings(pkg))
            .withResolvedAutogeneratedSettings(actualManifest?.settings.orEmpty())
        val mergedSettings = storedSettings.resolveCustomSettings(actualManifest)

        val pluginLogger = jobManager.getPluginLogger(pkg, jobId)
        val progressReporter = object : ProgressReporter {
            override fun report(progress: Float) {
                if (jobId != null) {
                    if (capabilityName != null) {
                        jobManager.updateCapabilityProgress(jobId, capabilityName, progress)
                    } else {
                        jobManager.updateJobProgress(jobId, progress)
                    }
                }
            }
        }

        return DefaultPluginContext(
            logger = pluginLogger,
            progress = progressReporter,
            fileSystem = DefaultPluginFileSystem(installPath, jarFullPath),
            cacheFileSystem = DefaultPluginFileSystem.createCacheOnly(installPath, jarFullPath),
            executionFileSystem = executionFileSystem ?: DefaultExecutionFileSystem("${installPath}/temp_execution"),
            hostFileSystem = HostFileSystemImpl(allowedPaths, isDestructiveAllowed),
            settings = mergedSettings,
            storage = DefaultPluginStorage(installPath, fileSystem),
            onRequiredActionChange = { actionName ->
                registry.scope.launch {
                    registry.updatePlugin(pkg) { it.copy(requiredAction = actionName) }
                }
            }
        )
    }

    fun getManifest(pkg: String): PluginManifest? {
        val plugin = registry.getPlugin(pkg) ?: return null
        val jarFileName = plugin.jarFileName ?: (plugin.pkg.substringAfterLast(".") + ".jar")
        val jarFile = "${plugin.installPath}/$jarFileName"

        val manifestContent = fileSystem.readFileFromZip(jarFile, "manifest.json")
            ?: fileSystem.readFileFromZip(jarFile, "META-INF/manifest.json")

        return manifestContent?.let {
            try {
                json.decodeFromString<PluginManifest>(it)
            } catch (e: Exception) {
                Logger.w { "Failed to parse manifest for $pkg: ${e.message}" }
                null
            }
        }
    }

    fun getMigrations(pkg: String): List<PluginMigration> {
        val plugin = registry.getPlugin(pkg) ?: return emptyList()
        val jarFileName = plugin.jarFileName ?: (plugin.pkg.substringAfterLast(".") + ".jar")
        val jarFile = "${plugin.installPath}/$jarFileName"

        val content = fileSystem.readFileFromZip(jarFile, "migrations.json")
            ?: fileSystem.readFileFromZip(jarFile, "META-INF/migrations.json")

        return content?.let {
            try {
                json.decodeFromString<List<PluginMigration>>(it)
            } catch (e: Exception) {
                Logger.w { "Failed to parse migrations for $pkg: ${e.message}" }
                emptyList()
            }
        } ?: emptyList()
    }

    suspend fun refreshLocks(
        pkg: String,
        overriddenSettings: PluginSettingsStore? = null
    ): Map<String, Boolean> {
        val entry = PluginLoader.getPluginById(pkg) ?: return emptyMap()
        val processor = entry.getProcessor().getOrNull() ?: return emptyMap()
        val context = createPluginContext(pkg, overriddenSettings = overriddenSettings)
        return try {
            val locks = processor.refreshLocks(context)
            _pluginLocksState.update { current ->
                current + (pkg to locks)
            }
            locks
        } catch (e: Exception) {
            Logger.e(e) { "Failed to evaluate checkLocks for $pkg" }
            emptyMap()
        }
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
    override val fileSystem: PluginFileSystem,
    override val cacheFileSystem: PluginFileSystem,
    override val executionFileSystem: org.wip.plugintoolkit.api.ExecutionFileSystem,
    override val hostFileSystem: org.wip.plugintoolkit.api.HostFileSystem,
    override val settings: Map<String, JsonElement>,
    override val storage: org.wip.plugintoolkit.api.PluginStorage,
    override val signals: PluginSignalManager = DefaultPluginSignalManager(),
    private val onRequiredActionChange: (String?) -> Unit = {}
) : PluginContext {
    override fun setRequiredAction(actionName: String?) {
        onRequiredActionChange(actionName)
    }
}
