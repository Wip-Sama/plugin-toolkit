package com.wip.kpm_cpm_wotoolkit.features.plugin.logic

import com.wip.plugin.api.PluginEntry

expect object ModuleLoader {
    fun loadPlugin(
        jarPath: String
    ): Result<PluginEntry>

    fun unloadPlugin(jarPath: String)
    fun unloadAll()
    fun getPlugins(): List<PluginEntry>
    fun getPlugin(jarPath: String): PluginEntry?
}
