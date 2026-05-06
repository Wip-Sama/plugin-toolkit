package org.wip.plugintoolkit.features.plugin.logic

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.wip.plugintoolkit.core.KeepTrack
import org.wip.plugintoolkit.core.utils.PlatformUtils
import org.wip.plugintoolkit.features.plugin.model.InstalledPlugin
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository

/**
 * Handles the state and persistence of installed plugins across managed folders.
 * Ensures atomic updates to the plugin list and disk synchronization.
 */
class PluginRegistry(
    private val settingsRepository: SettingsRepository
) {
    private val _installedPlugins = MutableStateFlow<List<InstalledPlugin>>(emptyList())
    val installedPlugins: StateFlow<List<InstalledPlugin>> = _installedPlugins.asStateFlow()

    private val mutex = Mutex()
    private val json = kotlinx.serialization.json.Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val defaultPluginFolder = settingsRepository.getSettingsDir() + "/" + KeepTrack.PLUGINS_DIR_NAME

    init {
        Logger.i { "Initializing PluginRegistry" }
        PlatformUtils.mkdirs(defaultPluginFolder)
        loadFromManagedFolders()
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
    fun loadFromManagedFolders() {
        val allPlugins = mutableListOf<InstalledPlugin>()
        getManagedFolders().forEach { folderPath ->
            val file = "${normalizePath(folderPath)}/${KeepTrack.INSTALLED_PLUGINS_FILE_NAME}"
            val content = PlatformUtils.readFile(file)
            if (content != null) {
                try {
                    val folderPlugins = json.decodeFromString<List<InstalledPlugin>>(content)
                    allPlugins.addAll(folderPlugins)
                } catch (t: Throwable) {
                    Logger.e(t) { "Failed to parse $file" }
                }
            }
        }
        _installedPlugins.update { allPlugins }
        Logger.d { "Loaded ${_installedPlugins.value.size} plugins from managed folders" }
    }

    /**
     * Atomically updates the plugin list and saves it to disk.
     */
    suspend fun updatePlugins(transform: (List<InstalledPlugin>) -> List<InstalledPlugin>) {
        mutex.withLock {
            val current = _installedPlugins.value
            val updated = transform(current)
            if (current != updated) {
                _installedPlugins.value = updated
                saveToManagedFolders(updated)
                Logger.d { "Plugin list updated and persisted (Total: ${updated.size})" }
            }
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

    private fun saveToManagedFolders(plugins: List<InstalledPlugin>) {
        val folders = getManagedFolders()
        folders.forEach { folderPath ->
            val normalizedFolder = normalizePath(folderPath)
            val folderPlugins = plugins.filter { normalizePath(it.installPath).startsWith(normalizedFolder) }
            val file = "$normalizedFolder/${KeepTrack.INSTALLED_PLUGINS_FILE_NAME}"

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
