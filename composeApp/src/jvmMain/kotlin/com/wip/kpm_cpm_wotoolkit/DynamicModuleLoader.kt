package com.wip.kpm_cpm_wotoolkit

import com.wip.plugin.api.PluginEntry
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.core.module.Module
import org.koin.mp.KoinPlatformTools
import java.io.File
import java.net.URLClassLoader

object DynamicModuleLoader {
    private var classLoader: URLClassLoader? = null
    private var currentModule: Module? = null
    private var currentPlugin: PluginEntry? = null

    fun loadPlugin(jarPath: String, moduleClassName: String, modulePropertyName: String): Result<PluginEntry> {
        return try {
            val file = File(jarPath)
            if (!file.exists()) return Result.failure(Exception("File not found: $jarPath"))

            unloadPlugin()

            val url = file.toURI().toURL()
            val newClassLoader = URLClassLoader(arrayOf(url), this.javaClass.classLoader)
            classLoader = newClassLoader

            val clazz = newClassLoader.loadClass(moduleClassName)
            val field = clazz.getDeclaredField(modulePropertyName)
            field.isAccessible = true
            val module = field.get(null) as Module

            loadKoinModules(module)
            currentModule = module
            
            val koin = KoinPlatformTools.defaultContext().get()
            val pluginEntry = koin.get<PluginEntry>()
            currentPlugin = pluginEntry
            
            Result.success(pluginEntry)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    fun unloadPlugin() {
        currentPlugin?.shutdown()
        currentPlugin = null
        currentModule?.let {
            unloadKoinModules(it)
            currentModule = null
        }
        classLoader?.close()
        classLoader = null
    }
    
    fun getPlugin(): PluginEntry? = currentPlugin
}
