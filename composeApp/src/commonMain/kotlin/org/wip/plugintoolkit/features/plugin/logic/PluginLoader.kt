package org.wip.plugintoolkit.features.plugin.logic

import org.wip.plugintoolkit.api.PluginEntry
import kotlinx.serialization.json.JsonElement

expect object PluginLoader {
    fun loadPlugin(
        jarPath: String,
        settings: Map<String, JsonElement> = emptyMap()
    ): Result<PluginEntry>

    fun unloadPlugin(jarPath: String)
    fun unloadAll()
    fun getPlugins(): List<PluginEntry>
    fun getPlugin(jarPath: String): PluginEntry?
    fun getPluginById(pluginId: String): PluginEntry?
    fun getPluginInstallPath(pluginId: String): String?
    fun getPluginJarPath(pluginId: String): String?
}
