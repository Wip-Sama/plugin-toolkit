package org.wip.plugintoolkit.features.plugin.logic

import org.wip.plugintoolkit.api.PluginEntry
import java.net.URL
import java.net.URLClassLoader

class ChildFirstClassLoader(
    urls: Array<URL>,
    parent: ClassLoader
) : URLClassLoader(urls, parent) {

    private val systemClassLoader = getSystemClassLoader()

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        synchronized(getClassLoadingLock(name)) {
            // First, check if the class has already been loaded
            var c = findLoadedClass(name)
            
            if (c == null) {
                // Determine if we should delegate to parent first for this class
                // Core java classes and plugin API classes MUST come from parent
                val apiPackagePrefix = PluginEntry::class.java.name.substringBeforeLast(".") + "."
                val isSharedClass = name.startsWith("java.") ||
                                    name.startsWith("javax.") ||
                                    name.startsWith("kotlin.") ||
                                    name.startsWith(apiPackagePrefix) ||
                                    name.startsWith("org.koin.") // Keep Koin shared if we want types to match, though the Koin instance is isolated
                                    //TODO: could add kotlinx. probably
                
                if (isSharedClass) {
                    try {
                        c = super.loadClass(name, resolve)
                    } catch (e: ClassNotFoundException) {
                        // Ignore, will try to find it locally below (unlikely for java/kotlin)
                    }
                }

                if (c == null) {
                    try {
                        // Try to load from the plugin JAR first
                        c = findClass(name)
                    } catch (e: ClassNotFoundException) {
                        // If not found locally, and not already checked parent, delegate to parent
                        if (!isSharedClass) {
                            c = try {
                                super.loadClass(name, resolve)
                            } catch (e2: ClassNotFoundException) {
                                // Last resort: system class loader
                                systemClassLoader.loadClass(name)
                            }
                        }
                    }
                }
            }

            if (c != null) {
                if (resolve) {
                    resolveClass(c)
                }
                return c
            }

            throw ClassNotFoundException(name)
        }
    }
}
