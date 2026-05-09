package org.wip.plugintoolkit.features.plugin.utils

import org.wip.plugintoolkit.features.repository.model.ExtensionPlugin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PluginSearchTest {

    private val plugins = listOf(
        ExtensionPlugin(name = "Weather Widget", pkg = "org.weather.widget", version = "1.0.0", description = "Shows weather info", fileName = "w.jar"),
        ExtensionPlugin(name = "Calculator", pkg = "org.calc.app", version = "2.1.0", description = "Advanced math tool", fileName = "c.jar"),
        ExtensionPlugin(name = "Notes Pro", pkg = "com.notes.pro", version = "0.9.0", description = "Take notes easily", fileName = "n.jar"),
        ExtensionPlugin(name = "System Monitor", pkg = "org.sys.mon", version = "1.5.0", description = "Monitor CPU and RAM", fileName = "s.jar")
    )

    @Test
    fun testSearchByName() {
        val results = PluginSearchUtils.filterPlugins(plugins, "Weather", emptySet())
        assertEquals(1, results.size)
        assertEquals("Weather Widget", results[0].name)
    }

    @Test
    fun testSearchByPackage() {
        val results = PluginSearchUtils.filterPlugins(plugins, "org.sys", emptySet())
        assertEquals(1, results.size)
        assertEquals("System Monitor", results[0].name)
    }

    @Test
    fun testSearchByDescription() {
        val results = PluginSearchUtils.filterPlugins(plugins, "math", emptySet())
        assertEquals(1, results.size)
        assertEquals("Calculator", results[0].name)
    }

    @Test
    fun testCaseInsensitiveSearch() {
        val results = PluginSearchUtils.filterPlugins(plugins, "NOTES", emptySet())
        assertEquals(1, results.size)
        assertEquals("Notes Pro", results[0].name)
    }

    @Test
    fun testEmptySearchReturnsAll() {
        val results = PluginSearchUtils.filterPlugins(plugins, "", emptySet())
        assertEquals(4, results.size)
    }

    @Test
    fun testSortingInstalledToBottom() {
        val installed = setOf("org.weather.widget", "org.calc.app")
        val results = PluginSearchUtils.filterPlugins(plugins, "", installed)
        
        assertEquals(4, results.size)
        // Non-installed should be at the top
        assertTrue(results[0].pkg == "com.notes.pro" || results[0].pkg == "org.sys.mon")
        assertTrue(results[1].pkg == "com.notes.pro" || results[1].pkg == "org.sys.mon")
        // Installed should be at the bottom
        assertTrue(results[2].pkg == "org.weather.widget" || results[2].pkg == "org.calc.app")
        assertTrue(results[3].pkg == "org.weather.widget" || results[3].pkg == "org.calc.app")
    }

    @Test
    fun testAlphabeticalSortingWithinGroups() {
        val results = PluginSearchUtils.filterPlugins(plugins, "", emptySet())
        
        assertEquals(4, results.size)
        assertEquals("Calculator", results[0].name)
        assertEquals("Notes Pro", results[1].name)
        assertEquals("System Monitor", results[2].name)
        assertEquals("Weather Widget", results[3].name)
    }
}
