package com.wip.kpm_cpm_wotoolkit

import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.core.module.Module
import java.io.File
import java.net.URLClassLoader

object DynamicModuleLoader {
    private var classLoader: URLClassLoader? = null
    private var currentModule: Module? = null

    fun loadModule(jarPath: String, moduleClassName: String, modulePropertyName: String): Result<Unit> {
        return try {
            val file = File(jarPath)
            if (!file.exists()) return Result.failure(Exception("File not found: $jarPath"))

            // Unload previous if exists
            unloadModule()

            val url = file.toURI().toURL()
            val newClassLoader = URLClassLoader(arrayOf(url), this.javaClass.classLoader)
            classLoader = newClassLoader

            val clazz = newClassLoader.loadClass(moduleClassName)
            // Kotlin top-level properties are accessible via a static getter in the Kt class
            // or directly as a field if it's a val.
            val field = clazz.getDeclaredField(modulePropertyName)
            field.isAccessible = true
            val module = field.get(null) as Module

            loadKoinModules(module)
            currentModule = module
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    fun unloadModule(): Result<Unit> {
        return try {
            currentModule?.let {
                unloadKoinModules(it)
                currentModule = null
            }
            classLoader?.close()
            classLoader = null
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun getClassLoader(): ClassLoader? = classLoader
}
