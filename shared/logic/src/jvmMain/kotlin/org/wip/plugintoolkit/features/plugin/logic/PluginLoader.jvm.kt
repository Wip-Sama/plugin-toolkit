package org.wip.plugintoolkit.features.plugin.logic

import co.touchlab.kermit.Logger
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.json.JsonElement
import org.koin.dsl.koinApplication
import org.wip.plugintoolkit.api.PluginEntry
import org.wip.plugintoolkit.api.PluginModuleProvider
import java.io.File
import java.net.URLClassLoader
import java.util.ServiceLoader
import java.util.concurrent.ConcurrentHashMap

private data class LoadedPlugin(
    val id: String,
    val jarPath: String,
    val entry: PluginEntry,
    val classLoader: URLClassLoader,
    val koinApp: org.koin.core.KoinApplication,
    val lastModified: Long
)

actual object PluginLoader {
    private val loadedPlugins = ConcurrentHashMap<String, LoadedPlugin>()
    private val idToJarPath = ConcurrentHashMap<String, String>()
    private class RefCountedLock(var count: Int = 0)
    private val jarLocks = ConcurrentHashMap<String, RefCountedLock>()
    private val unloadedClassLoaders = ConcurrentHashMap<String, java.lang.ref.WeakReference<ClassLoader>>()

    private fun acquireJarLock(path: String): RefCountedLock {
        return synchronized(jarLocks) {
            val lock = jarLocks.getOrPut(path) { RefCountedLock() }
            lock.count++
            lock
        }
    }

    private fun releaseJarLock(path: String) {
        synchronized(jarLocks) {
            val lock = jarLocks[path] ?: return
            lock.count--
            if (lock.count <= 0) {
                jarLocks.remove(path)
            }
        }
    }

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

        val lock = acquireJarLock(normalizedPath)
        try {
            synchronized(lock) {
                val path = Path(normalizedPath)
                val file = File(normalizedPath)
                val currentLastModified = if (file.exists()) file.lastModified() else 0L

                // If already loaded, return it (unless it was modified)
                loadedPlugins[normalizedPath]?.let {
                    if (it.lastModified == currentLastModified) {
                        Logger.d { "Plugin already loaded and up to date: $normalizedPath" }
                        return Result.success(it.entry)
                    } else {
                        Logger.i { "Plugin jar was modified, reloading: $normalizedPath" }
                        unloadPlugin(jarPath) // Unload the old version first
                    }
                }

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
                    loader.firstOrNull()
                        ?: throw Exception("No PluginModuleProvider implementation found in $normalizedPath")

                // Create an isolated Koin application for this plugin
                val koinApp = koinApplication {
                    modules(moduleProvider.getKoinModule(settings))
                }

                // Retrieve the real PluginEntry from Koin
                val pluginEntry = koinApp.koin.get<PluginEntry>()

                // Cache the ID for O(1) lookups
                val manifest = pluginEntry.getManifest().getOrThrow()
                val pluginId = manifest.plugin.id

                val loadedPlugin =
                    LoadedPlugin(pluginId, normalizedPath, pluginEntry, newClassLoader, koinApp, currentLastModified)
                loadedPlugins[normalizedPath] = loadedPlugin
                idToJarPath[pluginId] = normalizedPath
                unloadedClassLoaders.remove(normalizedPath)

                Logger.i { "Successfully loaded plugin $pluginId from $normalizedPath (Total tracked: ${loadedPlugins.size})" }
                return Result.success(pluginEntry)
            }
        } catch (e: Throwable) {
            Logger.e(e) { "Failed to load plugin from $normalizedPath" }
            return Result.failure(Exception(e.message, e))
        } finally {
            releaseJarLock(normalizedPath)
        }
    }

    actual fun unloadPlugin(jarPath: String) {
        val normalizedPath = normalizePath(jarPath)
        val lock = acquireJarLock(normalizedPath)
        try {
            synchronized(lock) {
                loadedPlugins.remove(normalizedPath)?.let {
                    Logger.i { "Unloading plugin ${it.id}: $normalizedPath" }
                    idToJarPath.remove(it.id)
                    try {
                        it.entry.shutdown()
                        it.koinApp.close()
                        it.classLoader.close()
                        unloadedClassLoaders[normalizedPath] = java.lang.ref.WeakReference(it.classLoader)
                        Logger.i { "Successfully unloaded, closed Koin app and classloader for $normalizedPath" }
                    } catch (e: Throwable) {
                        Logger.e(e) { "Error during shutdown of plugin at $normalizedPath" }
                    }
                    return
                }
                Logger.w { "Attempted to unload plugin but it was not found in cache: $normalizedPath (original: $jarPath). Current keys: ${loadedPlugins.keys}" }
            }
        } finally {
            releaseJarLock(normalizedPath)
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
