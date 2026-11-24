package org.wip.minimal

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.wip.plugintoolkit.api.ApiConfig
import org.wip.plugintoolkit.api.OS
import org.wip.plugintoolkit.api.PluginContext
import org.wip.plugintoolkit.api.PluginNotificationType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MinimalPluginTest {

    @Test
    fun testGreetDefault() {
        val plugin = MinimalPlugin()
        assertEquals("Hello, World!", plugin.greet("World"))
    }

    @Test
    fun testGreetCustomName() {
        val plugin = MinimalPlugin()
        assertEquals("Hello, Alice!", plugin.greet("Alice"))
    }

    @Test
    fun testGreetEnthusiastic() {
        val plugin = MinimalPlugin()
        assertEquals("Hello, Bob!!!", plugin.greet("Bob", enthusiastic = true))
    }

    @Test
    fun testGeneratedManifestAndPluginContextDefaults() {
        assertEquals("4.1.0-alpha06", ApiConfig.VERSION)
        val manifest = MinimalPluginManifest.manifest
        assertEquals("org.wip.minimal", manifest.plugin.id)
        assertEquals("Minimal Example Plugin", manifest.plugin.name)
        assertTrue(manifest.capabilities.any { it.name == "greet" })
        assertTrue(OS.WINDOWS in manifest.plugin.supportedOs)

        val plugin = MinimalPlugin()
        val entry = MinimalPluginPluginEntry(plugin)
        assertNotNull(entry.getProcessor().getOrNull())
        assertNull(entry.getInteractiveProcessor())

        // Verify updated PluginContext default implementations remain binary-compatible for minimal plugins
        val noOpContext = object : PluginContext by StubNoOpContext() {}
        val noOpSecrets = noOpContext.secrets
        assertNotNull(noOpSecrets)
        noOpContext.showNotification("Title", "Message", PluginNotificationType.INFO)
        runTest {
            noOpContext.updateSetting("apiKey", JsonPrimitive("demo"))
            noOpContext.updateSettings(mapOf("apiKey" to JsonPrimitive("demo2")))
            noOpContext.clearSetting("apiKey")
        }
    }

    private class StubNoOpContext : PluginContext {
        override val logger get() = throw UnsupportedOperationException()
        override val progress get() = throw UnsupportedOperationException()
        override val fileSystem get() = throw UnsupportedOperationException()
        override val cacheFileSystem get() = throw UnsupportedOperationException()
        override val executionFileSystem get() = throw UnsupportedOperationException()
        override val hostFileSystem get() = throw UnsupportedOperationException()
        override val settings: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap()
        override val storage get() = throw UnsupportedOperationException()
        override val signals get() = throw UnsupportedOperationException()
        override fun setRequiredAction(actionName: String?) {}
    }
}
