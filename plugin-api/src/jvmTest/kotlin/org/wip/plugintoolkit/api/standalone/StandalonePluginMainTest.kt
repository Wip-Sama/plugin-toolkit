package org.wip.plugintoolkit.api.standalone

import org.wip.plugintoolkit.api.DataProcessor
import org.wip.plugintoolkit.api.PluginEntry
import org.wip.plugintoolkit.api.PluginInfo
import org.wip.plugintoolkit.api.PluginManifest
import org.wip.plugintoolkit.api.Requirements
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StandalonePluginMainTest {
    @Test
    fun `missing plugin and unknown options return non-zero`() {
        assertEquals(2, runStandalone(emptyArray(), {}, {}, loadPlugins = { emptyList() }))
        assertEquals(2, runStandalone(arrayOf("--wat"), {}, {}, loadPlugins = { error("must not load") }))
    }

    @Test
    fun `help succeeds without loading plugin services`() {
        assertEquals(0, runStandalone(arrayOf("--help"), {}, {}, loadPlugins = { error("must not load") }))
    }

    @Test
    fun `info describes a discovered plugin`() {
        val output = mutableListOf<String>()

        val exitCode = runStandalone(arrayOf("--info"), output::add, {}, loadPlugins = { listOf(plugin()) })

        assertEquals(0, exitCode)
        assertTrue(output.single().contains("Example 1.0"))
        assertTrue(output.single().contains("Capabilities: none"))
    }

    private fun plugin() = object : PluginEntry {
        override fun getManifest() = Result.success(
            PluginManifest(
                manifestVersion = "1",
                plugin = PluginInfo("example", "Example", "1.0", "Example plugin"),
                requirements = Requirements(64, 10)
            )
        )

        override fun getProcessor(): Result<DataProcessor> = Result.failure(UnsupportedOperationException())
        override fun setDebug(isDebug: Boolean) = Unit
    }
}
