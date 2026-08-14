package org.wip.plugintoolkit.features.plugin.model

import kotlinx.serialization.json.JsonPrimitive
import org.wip.plugintoolkit.api.Capability
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PluginInfo
import org.wip.plugintoolkit.api.PluginManifest
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.api.Requirements
import org.wip.plugintoolkit.api.SettingMetadata
import org.wip.plugintoolkit.features.plugin.utils.CapabilityLockStatus
import org.wip.plugintoolkit.features.plugin.utils.CapabilityLockUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PluginSettingDefaultsTest {
    private val manifest = PluginManifest(
        manifestVersion = "1",
        plugin = PluginInfo("example", "Example", "1.0", "Example plugin"),
        requirements = Requirements(128, 10),
        settings = mapOf(
            "endpoint" to SettingMetadata(
                defaultValue = JsonPrimitive("https://example.test"),
                description = "Endpoint",
                type = DataType.Primitive(PrimitiveType.STRING)
            ),
            "enabled" to SettingMetadata(
                description = "Enabled",
                type = DataType.Primitive(PrimitiveType.BOOLEAN)
            )
        )
    )

    @Test
    fun `manifest defaults are available before a user saves settings`() {
        val resolved = PluginSettingsStore().resolveCustomSettings(manifest)

        assertEquals(JsonPrimitive("https://example.test"), resolved["endpoint"])
        assertFalse(resolved.containsKey("enabled"))
    }

    @Test
    fun `user values override defaults and global values are exposed separately`() {
        val store = PluginSettingsStore(
            settings = mapOf("endpoint" to JsonPrimitive("https://custom.test")),
            globalParams = mapOf("region" to JsonPrimitive("eu"))
        )

        val custom = store.resolveCustomSettings(manifest)
        val provided = store.resolveProvidedValues(manifest)

        assertEquals(JsonPrimitive("https://custom.test"), custom["endpoint"])
        assertFalse(custom.containsKey("enabled"))
        assertEquals(JsonPrimitive("eu"), provided["region"])
    }

    @Test
    fun `global parameters cannot shadow custom settings in custom setting resolution`() {
        val store = PluginSettingsStore(
            settings = mapOf("endpoint" to JsonPrimitive("https://custom.test")),
            globalParams = mapOf("endpoint" to JsonPrimitive("global-collision"))
        )

        assertEquals(JsonPrimitive("https://custom.test"), store.resolveCustomSettings(manifest)["endpoint"])
        assertEquals(JsonPrimitive("global-collision"), store.resolveProvidedValues(manifest)["endpoint"])
    }

    @Test
    fun `manifest defaults unlock capability gates before settings are persisted`() {
        val capability = Capability(
            name = "call",
            description = "Call the configured endpoint",
            returnType = DataType.Primitive(PrimitiveType.STRING),
            requiresSettings = listOf("endpoint")
        )
        val provided = PluginSettingsStore().resolveProvidedValues(manifest)

        assertTrue(capability.isReady(provided, manifest.settings))
        assertTrue(
            CapabilityLockUtils.checkCapabilityLockStatus(capability, emptyMap(), provided) is
                CapabilityLockStatus.Unlocked
        )
    }
}
