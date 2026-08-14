package org.wip.plugintoolkit.features.plugin.model

import kotlinx.serialization.json.JsonPrimitive
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PluginInfo
import org.wip.plugintoolkit.api.PluginManifest
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.api.Requirements
import org.wip.plugintoolkit.api.SettingMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

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
}
