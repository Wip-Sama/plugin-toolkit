package org.wip.plugintoolkit.features.plugin.utils

import org.wip.plugintoolkit.features.repository.model.ExtensionPlugin

object PluginSearchUtils {
    fun filterPlugins(
        plugins: List<ExtensionPlugin>,
        query: String,
        installedPackageNames: Set<String>
    ): List<ExtensionPlugin> {
        return plugins.filter { 
            it.name.contains(query, ignoreCase = true) || 
            (it.description?.contains(query, ignoreCase = true) == true) ||
            it.pkg.contains(query, ignoreCase = true)
        }.sortedWith(
            compareBy<ExtensionPlugin> { installedPackageNames.contains(it.pkg) }
                .thenBy { it.name }
        )
    }
}
