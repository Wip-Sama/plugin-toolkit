package org.wip.plugintoolkit.features.plugin.logic

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.wip.plugintoolkit.core.SystemConfig
import org.wip.plugintoolkit.core.utils.PlatformUtils
import org.wip.plugintoolkit.api.PluginManifest
import org.wip.plugintoolkit.features.plugin.model.InstalledPlugin
import org.wip.plugintoolkit.features.plugin.utils.PluginCompatibilityUtils
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository

/**
 * Handles the state and persistence of installed plugins across managed folders.
 * Ensures atomic updates to the plugin list and disk synchronization.
 */
class PluginRegistry(
    private val settingsRepository: SettingsRepository,
    val scope: CoroutineScope,
    private val loomDispatcher: CoroutineDispatcher,
    private val appConfig: SystemConfig
) {
    private val _installedPlugins = MutableStateFlow<List<InstalledPlugin>>(emptyList())
    val installedPlugins: StateFlow<List<InstalledPlugin>> = _installedPlugins.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val mutex = Mutex()
    private val ioMutex = Mutex()
    private var currentVersion = 0L
    private var lastWrittenVersion = 0L

    private val json = kotlinx.serialization.json.Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val defaultPluginFolder = settingsRepository.getSettingsDir() + "/" + appConfig.PLUGINS_DIR_NAME

    init {
        Logger.i { "Initializing PluginRegistry" }
    }

    /**
     * Initializes the registry by loading plugins from managed folders.
     * This should be called asynchronously during application startup.
     */
    suspend fun initialize() = withContext(loomDispatcher) {
        Logger.i { "Starting PluginRegistry initialization" }
        try {
            PlatformUtils.mkdirs(defaultPluginFolder)
            loadFromManagedFolders()
            _isReady.value = true
            Logger.i { "PluginRegistry initialization complete. Ready." }
        } catch (t: Throwable) {
            Logger.e(t) { "PluginRegistry initialization failed" }
            throw t
        }
    }

    /**
     * Returns all unique managed folders, including the default one.
     */
    fun getManagedFolders(): List<String> {
        val savedFolders = settingsRepository.loadSettings().extensions.pluginFolders
        return (listOf(defaultPluginFolder) + savedFolders).distinct()
    }

    private fun normalizePath(path: String): String {
        return path.replace('\\', '/').removeSuffix("/")
    }

    /**
     * Refreshes the in-memory state from the installed_plugins.json files in all managed folders.
     */
    suspend fun loadFromManagedFolders() = withContext(loomDispatcher) {
        val allPlugins = mutableListOf<InstalledPlugin>()
        var diskUpdateNeeded = false

        getManagedFolders().forEach { folderPath ->
            val file = "${normalizePath(folderPath)}/${appConfig.INSTALLED_PLUGINS_FILE_NAME}"
            val content = PlatformUtils.readFile(file)
            if (content != null) {
                try {
                    val folderPlugins = json.decodeFromString<List<InstalledPlugin>>(content)
                    val validatedPlugins = folderPlugins.map { plugin ->
                        val jarFileName = plugin.jarFileName ?: (plugin.pkg.substringAfterLast(".") + ".jar")
                        val jarFile = "${plugin.installPath}/$jarFileName"

                        val manifest = if (plugin.targetAppVersion == null && PlatformUtils.exists(jarFile)) {
                            val manifestContent = PlatformUtils.readFileFromZip(jarFile, "manifest.json")
                                ?: PlatformUtils.readFileFromZip(jarFile, "META-INF/manifest.json")
                            manifestContent?.let {
                                try { json.decodeFromString<PluginManifest>(it) } catch (e: Exception) { null }
                            }
                        } else null

                        val updatedTargetAppVersion = plugin.targetAppVersion ?: manifest?.requirements?.targetAppVersion
                        val updatedSupportedOs = if (plugin.supportedOs.isEmpty() && manifest != null) manifest.plugin.supportedOs else plugin.supportedOs

                        val (isComp, compError) = PluginCompatibilityUtils.checkCompatibility(
                            plugin.copy(targetAppVersion = updatedTargetAppVersion, supportedOs = updatedSupportedOs),
                            manifest
                        )

                        if (plugin.isCompatible != isComp || plugin.compatibilityError != compError || plugin.targetAppVersion != updatedTargetAppVersion) {
                            diskUpdateNeeded = true
                            plugin.copy(
                                isCompatible = isComp,
                                compatibilityError = compError,
                                targetAppVersion = updatedTargetAppVersion,
                                supportedOs = updatedSupportedOs
                            )
                        } else {
                            plugin
                        }
                    }
                    allPlugins.addAll(validatedPlugins)
                } catch (t: Throwable) {
                    Logger.e(t) { "Failed to parse $file" }
                }
            }
        }
        _installedPlugins.update { allPlugins }
        if (diskUpdateNeeded) {
            saveToManagedFolders(allPlugins)
        }
        Logger.d { "Loaded ${_installedPlugins.value.size} plugins from managed folders" }
    }

    /**
     * Atomically updates the plugin list and saves it to disk.
     */
    suspend fun updatePlugins(transform: (List<InstalledPlugin>) -> List<InstalledPlugin>) {
        var myVersion = 0L
        val updated = mutex.withLock {
            val current = _installedPlugins.value
            val next = transform(current)
            if (current != next) {
                _installedPlugins.value = next
                myVersion = ++currentVersion
                next
            } else {
                null
            }
        }

        if (updated != null) {
            ioMutex.withLock {
                if (lastWrittenVersion < myVersion) {
                    saveToManagedFolders(_installedPlugins.value)
                    lastWrittenVersion = currentVersion
                }
            }
            Logger.d { "Plugin list updated and persisted (Total: ${updated.size})" }
        }
    }

    /**
     * Updates a single plugin in the list.
     */
    suspend fun updatePlugin(pkg: String, transform: (InstalledPlugin) -> InstalledPlugin) {
        updatePlugins { current ->
            current.map { if (it.pkg == pkg) transform(it) else it }
        }
    }

    /**
     * Adds or updates a plugin in the list.
     */
    suspend fun addOrUpdatePlugin(plugin: InstalledPlugin) {
        updatePlugins { current ->
            current.filter { it.pkg != plugin.pkg } + plugin
        }
    }

    /**
     * Removes a plugin from the list.
     */
    suspend fun removePlugin(pkg: String) {
        updatePlugins { current ->
            current.filter { it.pkg != pkg }
        }
    }

    private suspend fun saveToManagedFolders(plugins: List<InstalledPlugin>) = withContext(loomDispatcher) {
        val folders = getManagedFolders()
        folders.forEach { folderPath ->
            val normalizedFolder = normalizePath(folderPath)
            val folderPlugins = plugins.filter { normalizePath(it.installPath).startsWith(normalizedFolder) }
            val file = "$normalizedFolder/${appConfig.INSTALLED_PLUGINS_FILE_NAME}"

            try {
                PlatformUtils.writeFile(file, json.encodeToString(folderPlugins))
            } catch (t: Throwable) {
                Logger.e(t) { "Failed to save plugins to $file" }
            }
        }
    }

    fun getPlugin(pkg: String): InstalledPlugin? {
        return _installedPlugins.value.find { it.pkg == pkg }
    }

    fun getDefaultPluginFolder(): String = defaultPluginFolder
}
