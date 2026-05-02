package com.wip.kpm_cpm_wotoolkit.features.plugin.logic

import co.touchlab.kermit.Logger
import com.wip.plugin.api.PluginEntry
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import org.koin.core.Koin
import org.koin.dsl.koinApplication
import java.io.File
import java.net.URLClassLoader
import java.util.ServiceLoader

private data class LoadedPlugin(
    val jarPath: String,
    val entry: PluginEntry,
    val classLoader: URLClassLoader,
    val koin: Koin
)

actual object PluginLoader {
    private val loadedPlugins = mutableMapOf<String, LoadedPlugin>()

    actual fun loadPlugin(
        jarPath: String
    ): Result<PluginEntry> {
        return try {
            val path = Path(jarPath)
            if (!SystemFileSystem.exists(path)) {
                Logger.e { "Plugin JAR file not found: $jarPath" }
                return Result.failure(Exception("File not found: $jarPath"))
            }

            // If already loaded, return it
            loadedPlugins[jarPath]?.let {
                Logger.d { "Plugin already loaded: $jarPath" }
                return Result.success(it.entry)
            }

            Logger.i { "Loading plugin from $jarPath" }
            val url = File(jarPath).toURI().toURL()
            val newClassLoader = URLClassLoader(arrayOf(url), this.javaClass.classLoader)

            // Use ServiceLoader to find the PluginEntry implementation
            val loader = ServiceLoader.load(PluginEntry::class.java, newClassLoader)
            val pluginEntryImpl =
                loader.firstOrNull() ?: throw Exception("No PluginEntry implementation found in $jarPath")

            // Get the Koin module from the entry point
            val module = pluginEntryImpl.getKoinModule()

            // Create an isolated Koin application for this plugin
            val koinApp = koinApplication {
                modules(module)
            }
            val koin = koinApp.koin
            val pluginEntry = koin.get<PluginEntry>()

            val loadedPlugin = LoadedPlugin(jarPath, pluginEntry, newClassLoader, koin)
            loadedPlugins[jarPath] = loadedPlugin

            Logger.i { "Successfully loaded plugin from $jarPath" }
            Result.success(pluginEntry)
        } catch (e: Exception) {
            Logger.e(e) { "Failed to load plugin from $jarPath" }
            Result.failure(e)
        }
    }

    actual fun unloadPlugin(jarPath: String) {
        loadedPlugins.remove(jarPath)?.let {
            Logger.i { "Unloading plugin: $jarPath" }
            it.entry.shutdown()
            it.classLoader.close()
        }
    }

    actual fun unloadAll() {
        loadedPlugins.keys.toList().forEach { unloadPlugin(it) }
    }

    actual fun getPlugins(): List<PluginEntry> = loadedPlugins.values.map { it.entry }

    actual fun getPlugin(jarPath: String): PluginEntry? = loadedPlugins[jarPath]?.entry

    actual fun getPluginById(pluginId: String): PluginEntry? {
        return loadedPlugins.values.find { it.entry.getManifest().plugin.id == pluginId }?.entry
    }

    actual fun getPluginInstallPath(pluginId: String): String? {
        val plugin = loadedPlugins.values.find { it.entry.getManifest().plugin.id == pluginId }
        if (plugin != null) {
            val file = File(plugin.jarPath)
            return file.parent
        }
        return null
    }

    actual fun getPluginJarPath(pluginId: String): String? {
        return loadedPlugins.values.find { it.entry.getManifest().plugin.id == pluginId }?.jarPath
    }
}
