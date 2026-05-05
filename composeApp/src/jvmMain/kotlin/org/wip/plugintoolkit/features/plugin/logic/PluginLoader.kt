package org.wip.plugintoolkit.features.plugin.logic

import co.touchlab.kermit.Logger
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import org.koin.core.Koin
import org.koin.dsl.koinApplication
import org.wip.plugintoolkit.api.PluginEntry
import java.io.File
import java.net.URLClassLoader
import java.util.ServiceLoader
import java.util.concurrent.ConcurrentHashMap

private data class LoadedPlugin(
    val jarPath: String,
    val entry: PluginEntry,
    val classLoader: URLClassLoader,
    val koin: Koin
)

actual object PluginLoader {
    private val loadedPlugins = ConcurrentHashMap<String, LoadedPlugin>()

    private fun normalizePath(path: String): String {
        return try {
            File(path).canonicalPath
        } catch (e: Exception) {
            Logger.w { "Failed to get canonical path for $path, using absolute path instead" }
            File(path).absolutePath
        }
    }

    actual fun loadPlugin(
        jarPath: String
    ): Result<PluginEntry> {
        val normalizedPath = normalizePath(jarPath)

        // Use synchronized to prevent two threads from loading the same plugin simultaneously
        synchronized(this) {
            try {
                val path = Path(normalizedPath)
                if (!SystemFileSystem.exists(path)) {
                    Logger.e { "Plugin JAR file not found: $normalizedPath (original: $jarPath)" }
                    return Result.failure(Exception("File not found: $normalizedPath"))
                }

                // If already loaded, return it
                loadedPlugins[normalizedPath]?.let {
                    Logger.d { "Plugin already loaded: $normalizedPath" }
                    return Result.success(it.entry)
                }

                Logger.i { "Loading plugin from $normalizedPath" }
                val url = File(normalizedPath).toURI().toURL()
                val newClassLoader = ChildFirstClassLoader(arrayOf(url), this.javaClass.classLoader)

                // Use ServiceLoader to find the PluginEntry implementation
                val loader = ServiceLoader.load(PluginEntry::class.java, newClassLoader)
                val pluginEntryImpl =
                    loader.firstOrNull() ?: throw Exception("No PluginEntry implementation found in $normalizedPath")

                // Get the Koin module from the entry point
                val module = pluginEntryImpl.getKoinModule()

                // Create an isolated Koin application for this plugin
                val koinApp = koinApplication {
                    modules(module)
                }
                val koin = koinApp.koin
                val pluginEntry = koin.get<PluginEntry>()

                val loadedPlugin = LoadedPlugin(normalizedPath, pluginEntry, newClassLoader, koin)
                loadedPlugins[normalizedPath] = loadedPlugin

                Logger.i { "Successfully loaded plugin from $normalizedPath (Total tracked: ${loadedPlugins.size})" }
                return Result.success(pluginEntry)
            } catch (e: Exception) {
                Logger.e(e) { "Failed to load plugin from $normalizedPath" }
                return Result.failure(e)
            }
        }
    }

    actual fun unloadPlugin(jarPath: String) {
        val normalizedPath = normalizePath(jarPath)
        loadedPlugins.remove(normalizedPath)?.let {
            Logger.i { "Unloading plugin: $normalizedPath" }
            try {
                it.entry.shutdown()
                it.classLoader.close()
                Logger.i { "Successfully unloaded and closed classloader for $normalizedPath" }
            } catch (e: Exception) {
                Logger.e(e) { "Error during shutdown of plugin at $normalizedPath" }
            }
            return
        }
        Logger.w { "Attempted to unload plugin but it was not found in cache: $normalizedPath (original: $jarPath). Current keys: ${loadedPlugins.keys}" }
    }

    actual fun unloadAll() {
        Logger.i { "Unloading all plugins (${loadedPlugins.size} currently loaded)" }
        loadedPlugins.keys.toList().forEach { unloadPlugin(it) }
    }

    actual fun getPlugins(): List<PluginEntry> = loadedPlugins.values.map { it.entry }

    actual fun getPlugin(jarPath: String): PluginEntry? = loadedPlugins[normalizePath(jarPath)]?.entry

    actual fun getPluginById(pluginId: String): PluginEntry? {
        return loadedPlugins.values.find {
            try {
                it.entry.getManifest().plugin.id == pluginId
            } catch (t: Throwable) {
                Logger.e(t) { "Failed to get manifest for plugin at ${it.jarPath}" }
                false
            }
        }?.entry
    }

    actual fun getPluginInstallPath(pluginId: String): String? {
        val plugin = loadedPlugins.values.find {
            try {
                it.entry.getManifest().plugin.id == pluginId
            } catch (t: Throwable) {
                Logger.e(t) { "Failed to get manifest for plugin at ${it.jarPath}" }
                false
            }
        }
        if (plugin != null) {
            val file = File(plugin.jarPath)
            return file.parent
        }
        return null
    }

    actual fun getPluginJarPath(pluginId: String): String? {
        return loadedPlugins.values.find {
            try {
                it.entry.getManifest().plugin.id == pluginId
            } catch (t: Throwable) {
                Logger.e(t) { "Failed to get manifest for plugin at ${it.jarPath}" }
                false
            }
        }?.jarPath
    }
}
