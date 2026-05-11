package org.wip.plugintoolkit.features.plugin.logic

import co.touchlab.kermit.Logger
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import org.koin.core.Koin
import org.koin.dsl.koinApplication
import org.wip.plugintoolkit.api.PluginEntry
import org.wip.plugintoolkit.api.PluginModuleProvider
import kotlinx.serialization.json.JsonElement
import java.io.File
import java.net.URLClassLoader
import java.util.ServiceLoader
import java.util.concurrent.ConcurrentHashMap

private data class LoadedPlugin(
    val id: String,
    val jarPath: String,
    val entry: PluginEntry,
    val classLoader: URLClassLoader,
    val koinApp: org.koin.core.KoinApplication
)

actual object PluginLoader {
    private val loadedPlugins = ConcurrentHashMap<String, LoadedPlugin>()
    private val idToJarPath = ConcurrentHashMap<String, String>()
    private val jarLocks = ConcurrentHashMap<String, Any>()

    private fun getJarLock(path: String): Any = jarLocks.getOrPut(path) { Any() }

    private fun normalizePath(path: String): String {
        return try {
            File(path).canonicalPath
        } catch (e: Exception) {
            Logger.w { "Failed to get canonical path for $path, using absolute path instead" }
            File(path).absolutePath
        }
    }

    actual fun loadPlugin(
        jarPath: String,
        settings: Map<String, JsonElement>
    ): Result<PluginEntry> {
        val normalizedPath = normalizePath(jarPath)

        // Use per-JAR lock to prevent two threads from loading the same plugin simultaneously
        // while allowing different plugins to load in parallel.
        synchronized(getJarLock(normalizedPath)) {
            try {
                // If already loaded, return it
                loadedPlugins[normalizedPath]?.let {
                    Logger.d { "Plugin already loaded: $normalizedPath" }
                    return Result.success(it.entry)
                }

                val path = Path(normalizedPath)
                if (!SystemFileSystem.exists(path)) {
                    Logger.e { "Plugin JAR file not found: $normalizedPath (original: $jarPath)" }
                    return Result.failure(Exception("File not found: $normalizedPath"))
                }

                Logger.i { "Loading plugin from $normalizedPath" }
                val url = File(normalizedPath).toURI().toURL()
                val newClassLoader = ChildFirstClassLoader(arrayOf(url), this.javaClass.classLoader)

                // Use ServiceLoader to find the PluginModuleProvider implementation
                val loader = ServiceLoader.load(PluginModuleProvider::class.java, newClassLoader)
                val moduleProvider =
                    loader.firstOrNull() ?: throw Exception("No PluginModuleProvider implementation found in $normalizedPath")

                // Create an isolated Koin application for this plugin
                val koinApp = koinApplication {
                    modules(moduleProvider.getKoinModule(settings))
                }

                // Retrieve the real PluginEntry from Koin
                val pluginEntry = koinApp.koin.get<PluginEntry>()

                // Cache the ID for O(1) lookups
                val manifest = pluginEntry.getManifest()
                val pluginId = manifest.plugin.id

                val loadedPlugin = LoadedPlugin(pluginId, normalizedPath, pluginEntry, newClassLoader, koinApp)
                loadedPlugins[normalizedPath] = loadedPlugin
                idToJarPath[pluginId] = normalizedPath

                Logger.i { "Successfully loaded plugin $pluginId from $normalizedPath (Total tracked: ${loadedPlugins.size})" }
                return Result.success(pluginEntry)
            } catch (e: Exception) {
                Logger.e(e) { "Failed to load plugin from $normalizedPath" }
                return Result.failure(e)
            }
        }
    }

    actual fun unloadPlugin(jarPath: String) {
        val normalizedPath = normalizePath(jarPath)
        synchronized(getJarLock(normalizedPath)) {
            loadedPlugins.remove(normalizedPath)?.let {
                Logger.i { "Unloading plugin ${it.id}: $normalizedPath" }
                idToJarPath.remove(it.id)
                try {
                    it.entry.shutdown()
                    it.koinApp.close()
                    it.classLoader.close()
                    Logger.i { "Successfully unloaded, closed Koin app and classloader for $normalizedPath" }
                } catch (e: Exception) {
                    Logger.e(e) { "Error during shutdown of plugin at $normalizedPath" }
                }
                return@synchronized
            }
            Logger.w { "Attempted to unload plugin but it was not found in cache: $normalizedPath (original: $jarPath). Current keys: ${loadedPlugins.keys}" }
        }
    }

    actual fun unloadAll() {
        Logger.i { "Unloading all plugins (${loadedPlugins.size} currently loaded)" }
        loadedPlugins.keys.toList().forEach { unloadPlugin(it) }
    }

    actual fun getPlugins(): List<PluginEntry> = loadedPlugins.values.map { it.entry }

    actual fun getPlugin(jarPath: String): PluginEntry? = loadedPlugins[normalizePath(jarPath)]?.entry

    actual fun getPluginById(pluginId: String): PluginEntry? {
        val jarPath = idToJarPath[pluginId] ?: return null
        return loadedPlugins[jarPath]?.entry
    }

    actual fun getPluginInstallPath(pluginId: String): String? {
        val jarPath = idToJarPath[pluginId] ?: return null
        val plugin = loadedPlugins[jarPath]
        if (plugin != null) {
            val file = File(plugin.jarPath)
            return file.parent
        }
        return null
    }

    actual fun getPluginJarPath(pluginId: String): String? {
        return idToJarPath[pluginId]
    }
}
