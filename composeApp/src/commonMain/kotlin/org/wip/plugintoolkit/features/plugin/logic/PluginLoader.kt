package org.wip.plugintoolkit.features.plugin.logic

import org.wip.plugintoolkit.api.PluginEntry

expect object PluginLoader {
    fun loadPlugin(
        jarPath: String
    ): Result<PluginEntry>

    fun unloadPlugin(jarPath: String)
    fun unloadAll()
    fun getPlugins(): List<PluginEntry>
    fun getPlugin(jarPath: String): PluginEntry?
    fun getPluginById(pluginId: String): PluginEntry?
    fun getPluginInstallPath(pluginId: String): String?
    fun getPluginJarPath(pluginId: String): String?
}
