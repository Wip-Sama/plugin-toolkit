package org.wip.plugintoolkit.core

import kotlinx.serialization.json.Json
import org.wip.plugintoolkit.features.settings.model.GeneralSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsModelTest {
    @Test
    fun testGeneralSettingsDefaults() {
        val settings = GeneralSettings()
        assertTrue(settings.singleInstanceLock, "Single instance lock should default to true")
        assertEquals(2048, settings.maxMemoryMb, "Max memory should default to 2048 MB")
    }

    @Test
    fun testGeneralSettingsSerialization() {
        val original = GeneralSettings(singleInstanceLock = false, maxMemoryMb = 4096)
        val json = Json { encodeDefaults = true }
        val serialized = json.encodeToString(GeneralSettings.serializer(), original)
        val deserialized = json.decodeFromString(GeneralSettings.serializer(), serialized)

        assertEquals(original, deserialized)
    }

    @Test
    fun testEffectiveMaxMemoryMb() {
        val defaultSettings = GeneralSettings()
        assertEquals(GeneralSettings.DEFAULT_MAX_MEMORY_MB, defaultSettings.effectiveMaxMemoryMb())

        val subMinSettings = GeneralSettings(maxMemoryMb = 512)
        assertEquals(GeneralSettings.MIN_MAX_MEMORY_MB, subMinSettings.effectiveMaxMemoryMb(), "Values below minimum should be clamped to 1024 MB")

        val customSettings = GeneralSettings(maxMemoryMb = 3500)
        assertEquals(3500, customSettings.effectiveMaxMemoryMb(), "Custom values >= 1024 MB should be retained")
    }
}
