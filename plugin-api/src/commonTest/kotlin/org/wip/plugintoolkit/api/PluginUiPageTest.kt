package org.wip.plugintoolkit.api

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class PluginUiPageTest {
    @Test
    fun `plugin pages round trip through the manifest`() {
        val manifest = PluginManifest(
            manifestVersion = "1",
            plugin = PluginInfo("example", "Example", "1", "Example"),
            requirements = Requirements(64, 10),
            uiPages = listOf(
                PluginUiPage("home", "Home", "Common actions", listOf("convert"))
            )
        )

        val json = Json.encodeToString(manifest)
        val restored = Json.decodeFromString<PluginManifest>(json)

        assertEquals(manifest.uiPages, restored.uiPages)
    }
}
