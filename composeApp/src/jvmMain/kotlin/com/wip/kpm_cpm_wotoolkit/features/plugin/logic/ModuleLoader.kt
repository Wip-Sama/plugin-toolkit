package com.wip.kpm_cpm_wotoolkit.features.plugin.logic

import com.wip.plugin.api.PluginEntry
import org.koin.core.Koin
import org.koin.dsl.koinApplication
import org.koin.core.module.Module
import java.io.File
import java.net.URLClassLoader

private data class LoadedPlugin(
    val jarPath: String,
    val entry: PluginEntry,
    val classLoader: URLClassLoader,
    val koin: Koin
)

actual object ModuleLoader {
    private val loadedPlugins = mutableMapOf<String, LoadedPlugin>()

    actual fun loadPlugin(
        jarPath: String, 
        moduleClassName: String, 
        modulePropertyName: String
    ): Result<PluginEntry> {
        return try {
            val file = File(jarPath)
            if (!file.exists()) return Result.failure(Exception("File not found: $jarPath"))

            // If already loaded, return it
            loadedPlugins[jarPath]?.let { return Result.success(it.entry) }

            val url = file.toURI().toURL()
            val newClassLoader = URLClassLoader(arrayOf(url), this.javaClass.classLoader)

            val clazz = newClassLoader.loadClass(moduleClassName)
            val field = clazz.getDeclaredField(modulePropertyName)
            field.isAccessible = true
            val module = field.get(null) as Module

            // Create an isolated Koin application for this plugin
            val koinApp = koinApplication {
                modules(module)
            }
            val koin = koinApp.koin
            val pluginEntry = koin.get<PluginEntry>()
            
            val loadedPlugin = LoadedPlugin(jarPath, pluginEntry, newClassLoader, koin)
            loadedPlugins[jarPath] = loadedPlugin
            
            Result.success(pluginEntry)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    actual fun unloadPlugin(jarPath: String) {
        loadedPlugins.remove(jarPath)?.let { 
            it.entry.shutdown()
            it.classLoader.close()
        }
    }

    actual fun unloadAll() {
        loadedPlugins.keys.toList().forEach { unloadPlugin(it) }
    }

    actual fun getPlugins(): List<PluginEntry> = loadedPlugins.values.map { it.entry }
    
    actual fun getPlugin(jarPath: String): PluginEntry? = loadedPlugins[jarPath]?.entry
}
