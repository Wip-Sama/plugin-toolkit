package org.wip.plugintoolkit.features.plugin

import org.wip.plugintoolkit.core.utils.VersionUtils
import org.wip.plugintoolkit.features.plugin.model.InstalledPlugin
import org.wip.plugintoolkit.features.plugin.model.PluginManagerChipFilter
import org.wip.plugintoolkit.features.plugin.model.PluginManagerSortMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PluginFilterAndSortTest {

    private val pluginA = InstalledPlugin(
        pkg = "org.example.alpha",
        name = "Alpha Tool",
        version = "1.0.0",
        installPath = "/plugins/alpha",
        isEnabled = true,
        isValidated = true,
        description = "Image processing tool"
    )

    private val pluginB = InstalledPlugin(
        pkg = "org.example.beta",
        name = "Beta Utility",
        version = "2.5.0",
        installPath = "/plugins/beta",
        isEnabled = false,
        isValidated = true,
        description = "Audio converter"
    )

    private val pluginC = InstalledPlugin(
        pkg = "org.example.gamma",
        name = "Gamma Processor",
        version = "0.9.1",
        installPath = "/plugins/gamma",
        isEnabled = true,
        isValidated = false,
        description = "Video filter"
    )

    private val allPlugins = listOf(pluginA, pluginB, pluginC)

    @Test
    fun testSearchFiltering() {
        val searchByName = allPlugins.filter { it.name.contains("Beta", ignoreCase = true) }
        assertEquals(listOf(pluginB), searchByName)

        val searchByDescription = allPlugins.filter {
            it.name.contains("filter", ignoreCase = true) ||
                (it.description?.contains("filter", ignoreCase = true) == true)
        }
        assertEquals(listOf(pluginC), searchByDescription)

        val searchByPkg = allPlugins.filter { it.pkg.contains("alpha", ignoreCase = true) }
        assertEquals(listOf(pluginA), searchByPkg)
    }

    @Test
    fun testChipFiltering() {
        // Enabled filter
        val enabledPlugins = allPlugins.filter { plugin ->
            when (PluginManagerChipFilter.Enabled) {
                PluginManagerChipFilter.All -> true
                PluginManagerChipFilter.Enabled -> plugin.isEnabled
                PluginManagerChipFilter.Disabled -> !plugin.isEnabled
                PluginManagerChipFilter.UpdateAvailable -> false
                PluginManagerChipFilter.RequiresSetup -> !plugin.isValidated
            }
        }
        assertEquals(listOf(pluginA, pluginC), enabledPlugins)

        // Disabled filter
        val disabledPlugins = allPlugins.filter { plugin ->
            when (PluginManagerChipFilter.Disabled) {
                PluginManagerChipFilter.All -> true
                PluginManagerChipFilter.Enabled -> plugin.isEnabled
                PluginManagerChipFilter.Disabled -> !plugin.isEnabled
                PluginManagerChipFilter.UpdateAvailable -> false
                PluginManagerChipFilter.RequiresSetup -> !plugin.isValidated
            }
        }
        assertEquals(listOf(pluginB), disabledPlugins)

        // RequiresSetup filter
        val requiresSetupPlugins = allPlugins.filter { plugin ->
            when (PluginManagerChipFilter.RequiresSetup) {
                PluginManagerChipFilter.All -> true
                PluginManagerChipFilter.Enabled -> plugin.isEnabled
                PluginManagerChipFilter.Disabled -> !plugin.isEnabled
                PluginManagerChipFilter.UpdateAvailable -> false
                PluginManagerChipFilter.RequiresSetup -> !plugin.isValidated
            }
        }
        assertEquals(listOf(pluginC), requiresSetupPlugins)

        // UpdateAvailable filter
        val updates = mapOf("org.example.alpha" to "1.1.0")
        val updatePlugins = allPlugins.filter { plugin ->
            val hasUpdate = updates.containsKey(plugin.pkg)
            hasUpdate
        }
        assertEquals(listOf(pluginA), updatePlugins)
    }

    @Test
    fun testSortingModes() {
        // NameAsc
        val sortedAsc = allPlugins.sortedBy { it.name.lowercase() }
        assertEquals(listOf(pluginA, pluginB, pluginC), sortedAsc)

        // NameDesc
        val sortedDesc = allPlugins.sortedByDescending { it.name.lowercase() }
        assertEquals(listOf(pluginC, pluginB, pluginA), sortedDesc)

        // StatusEnabledFirst
        val sortedEnabled = allPlugins.sortedWith(
            compareByDescending<InstalledPlugin> { it.isEnabled }.thenBy { it.name.lowercase() }
        )
        assertEquals(listOf(pluginA, pluginC, pluginB), sortedEnabled)

        // LatestVersion
        val sortedVersion = allPlugins.sortedWith { a, b ->
            VersionUtils.compare(b.version, a.version)
        }
        assertEquals(listOf(pluginB, pluginA, pluginC), sortedVersion)
    }
}
