package org.wip.plugintoolkit.features.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import kotlinx.coroutines.test.runTest
import org.wip.plugintoolkit.features.settings.model.AppSettings
import org.wip.plugintoolkit.features.settings.model.LoggingSettings
import org.wip.plugintoolkit.features.settings.model.SettingDefinition
import org.wip.plugintoolkit.features.settings.ui.SettingNavKey
import org.wip.plugintoolkit.features.settings.utils.SettingText
import org.wip.plugintoolkit.features.settings.utils.SettingsRegistry
import org.wip.plugintoolkit.features.settings.utils.bindGroup
import org.wip.plugintoolkit.features.settings.utils.build
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsRegistryTest {

    @Test
    fun testRegisterAndUnregister() {
        val registry = SettingsRegistry()
        assertEquals(0, registry.definitions.value.size)

        val def = SettingDefinition.SwitchSetting(
            id = "test.setting",
            title = SettingText.Raw("Test"),
            subtitle = null,
            icon = Icons.Default.Settings,
            sectionTitle = SettingText.Raw("Test Section"),
            navKey = SettingNavKey.SystemSettings,
            getValue = { false },
            setValue = { s, _ -> s }
        )

        registry.register(listOf(def))
        assertEquals(1, registry.definitions.value.size)

        registry.unregister(setOf("test.setting"))
        assertEquals(0, registry.definitions.value.size)
    }

    @Test
    fun testGetDefinitionsForPage() {
        val registry = SettingsRegistry.build {
            nav(SettingNavKey.SystemSettings) {
                section(SettingText.Raw("System")) {
                    bindGroup(AppSettings::logging, { copy(logging = it) }) {
                        switch(
                            LoggingSettings::compressOldLogs,
                            SettingText.Raw("Compress Old Logs"),
                            Icons.Default.Settings
                        ) { copy(compressOldLogs = it) }
                    }
                }
            }
            nav(SettingNavKey.Appearance) {
                section(SettingText.Raw("Appearance")) {
                    // empty section for test
                }
            }
        }

        val systemDefs = registry.getDefinitionsForPage(SettingNavKey.SystemSettings)
        assertEquals(1, systemDefs.size)
        assertEquals(1, systemDefs.values.first().size)

        val appearanceDefs = registry.getDefinitionsForPage(SettingNavKey.Appearance)
        assertEquals(0, appearanceDefs.size)
    }

    @Test
    fun testTriggerSideEffects() = runTest {
        var sideEffectFired = false

        val registry = SettingsRegistry.build {
            nav(SettingNavKey.SystemSettings) {
                section(SettingText.Raw("System")) {
                    SettingSwitch(
                        p1 = AppSettings::logging,
                        p2 = LoggingSettings::compressOldLogs,
                        title = SettingText.Raw("Compress"),
                        icon = Icons.Default.Settings,
                        sideEffect = { sideEffectFired = true },
                        setValue = { s, v -> s.copy(logging = s.logging.copy(compressOldLogs = v)) }
                    )
                }
            }
        }

        val oldSettings = AppSettings()
        val newSettings = oldSettings.copy(logging = oldSettings.logging.copy(compressOldLogs = false))

        // Trigger when value changes
        registry.triggerSideEffects(oldSettings, newSettings)
        assertTrue(sideEffectFired)

        // Reset and trigger when value does not change
        sideEffectFired = false
        registry.triggerSideEffects(newSettings, newSettings)
        assertFalse(sideEffectFired)
    }
}
