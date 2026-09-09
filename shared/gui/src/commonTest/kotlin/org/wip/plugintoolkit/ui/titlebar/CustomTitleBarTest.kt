package org.wip.plugintoolkit.ui.titlebar

import kotlinx.serialization.json.Json
import org.wip.plugintoolkit.features.settings.definitions.appearanceDefinitions
import org.wip.plugintoolkit.features.settings.model.AppSettings
import org.wip.plugintoolkit.features.settings.model.AppearanceSettings
import org.wip.plugintoolkit.features.settings.model.SettingDefinition
import org.wip.plugintoolkit.features.settings.utils.SettingsRegistry
import org.wip.plugintoolkit.features.settings.utils.build
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CustomTitleBarTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun testAppearanceSettingsDefaultAndSerialization() {
        val defaultSettings = AppearanceSettings()
        assertTrue(defaultSettings.useCustomTitleBar, "useCustomTitleBar should default to true")

        // Serialize and deserialize
        val encoded = json.encodeToString(AppearanceSettings.serializer(), defaultSettings)
        val decoded = json.decodeFromString(AppearanceSettings.serializer(), encoded)
        assertTrue(decoded.useCustomTitleBar)

        // Test with false value
        val disabledSettings = defaultSettings.copy(useCustomTitleBar = false)
        val encodedDisabled = json.encodeToString(AppearanceSettings.serializer(), disabledSettings)
        val decodedDisabled = json.decodeFromString(AppearanceSettings.serializer(), encodedDisabled)
        assertFalse(decodedDisabled.useCustomTitleBar)

        // Test backward compatibility (JSON missing useCustomTitleBar key)
        val legacyJson = """{"theme":"System","accentColor":6422766,"followSystemAccent":true,"useAccentInTheme":false}"""
        val decodedLegacy = json.decodeFromString(AppearanceSettings.serializer(), legacyJson)
        assertTrue(decodedLegacy.useCustomTitleBar, "Missing key should fallback to default true")
    }

    @Test
    fun testCustomTitleBarSettingRegistration() {
        val registry = SettingsRegistry.build {
            appearanceDefinitions()
        }

        val definitions = registry.definitions.value
        val titleBarSetting = definitions.find { it.id == "appearance.useCustomTitleBar" }
        assertNotNull(titleBarSetting, "Setting 'appearance.useCustomTitleBar' should be registered")
        assertTrue(titleBarSetting is SettingDefinition.SwitchSetting)

        val initialAppSettings = AppSettings()
        assertTrue(titleBarSetting.getValue(initialAppSettings))

        val updatedAppSettings = titleBarSetting.setValue(initialAppSettings, false)
        assertFalse(updatedAppSettings.appearance.useCustomTitleBar)
    }

    @Test
    fun testWindowControllerActions() {
        var minimized = false
        var maximizeToggled = false
        var closed = false

        val controller = WindowController(
            isMaximized = false,
            onMinimize = { minimized = true },
            onMaximizeToggle = { maximizeToggled = true },
            onClose = { closed = true }
        )

        assertFalse(controller.isMaximized)

        controller.onMinimize()
        assertTrue(minimized, "onMinimize should be called")

        controller.onMaximizeToggle()
        assertTrue(maximizeToggled, "onMaximizeToggle should be called")

        controller.onClose()
        assertTrue(closed, "onClose should be called")

        // Test maximized state
        val maximizedController = controller.copy(isMaximized = true)
        assertTrue(maximizedController.isMaximized)
    }
}
