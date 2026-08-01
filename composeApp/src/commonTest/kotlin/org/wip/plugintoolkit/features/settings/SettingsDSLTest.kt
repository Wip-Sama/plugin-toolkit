package org.wip.plugintoolkit.features.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import org.wip.plugintoolkit.features.settings.model.AppSettings
import org.wip.plugintoolkit.features.settings.model.GeneralSettings
import org.wip.plugintoolkit.features.settings.model.JobSettings
import org.wip.plugintoolkit.features.settings.model.LogLevel
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

class SettingsDSLTest {

    @Test
    fun testDSLRegistrationAndIDGeneration() {
        val registry = SettingsRegistry.build {
            nav(SettingNavKey.SystemSettings) {
                section(SettingText.Raw("Logging")) {
                    bindGroup(AppSettings::logging, { copy(logging = it) }) {
                        switch(
                            LoggingSettings::compressOldLogs,
                            SettingText.Raw("Compress Old Logs"),
                            Icons.Default.Settings
                        ) { copy(compressOldLogs = it) }

                        numeric(
                            LoggingSettings::logsToKeep,
                            SettingText.Raw("Logs to Keep"),
                            Icons.Default.Settings,
                            range = 1..30
                        ) { copy(logsToKeep = it) }

                        dropdown(
                            LoggingSettings::level,
                            SettingText.Raw("Log Level"),
                            Icons.Default.Settings,
                            options = LogLevel.entries
                        ) { copy(level = it) }
                    }
                }
            }
        }

        val defs = registry.definitions.value
        assertEquals(3, defs.size)

        val switchDef = defs[0] as SettingDefinition.SwitchSetting
        assertEquals("logging.compressOldLogs", switchDef.id)

        val numericDef = defs[1] as SettingDefinition.NumericSetting
        assertEquals("logging.logsToKeep", numericDef.id)

        val dropdownDef = defs[2] as SettingDefinition.DropdownSetting<*>
        assertEquals("logging.level", dropdownDef.id)
    }

    @Test
    fun testBindGroupStateMutations() {
        var settings = AppSettings()
        assertTrue(settings.logging.compressOldLogs)

        val registry = SettingsRegistry.build {
            nav(SettingNavKey.SystemSettings) {
                section(SettingText.Raw("Logging")) {
                    bindGroup(AppSettings::logging, { copy(logging = it) }) {
                        switch(
                            LoggingSettings::compressOldLogs,
                            SettingText.Raw("Compress Old Logs"),
                            Icons.Default.Settings
                        ) { copy(compressOldLogs = it) }

                        numeric(
                            LoggingSettings::logsToKeep,
                            SettingText.Raw("Logs to Keep"),
                            Icons.Default.Settings,
                            range = 1..30
                        ) { copy(logsToKeep = it) }
                    }
                }
            }
        }

        val switchDef = registry.definitions.value[0] as SettingDefinition.SwitchSetting
        settings = switchDef.setValue(settings, false)
        assertFalse(settings.logging.compressOldLogs)

        val numericDef = registry.definitions.value[1] as SettingDefinition.NumericSetting
        settings = numericDef.setValue(settings, 14)
        assertEquals(14, settings.logging.logsToKeep)
    }

    @Test
    fun testSettingLongNumericBridging() {
        var settings = AppSettings()
        assertEquals(600000L, settings.jobs.pluginTimeoutMs)

        val registry = SettingsRegistry.build {
            nav(SettingNavKey.SystemSettings) {
                section(SettingText.Raw("Jobs")) {
                    bindGroup(AppSettings::jobs, { copy(jobs = it) }) {
                        longNumeric(
                            JobSettings::pluginTimeoutMs,
                            SettingText.Raw("Plugin Timeout"),
                            Icons.Default.Settings,
                            range = -1..3600000
                        ) { copy(pluginTimeoutMs = it) }
                    }
                }
            }
        }

        val numericDef = registry.definitions.value[0] as SettingDefinition.NumericSetting
        assertEquals(600000, numericDef.getValue(settings))

        settings = numericDef.setValue(settings, 1200000)
        assertEquals(1200000L, settings.jobs.pluginTimeoutMs)
    }

    @Test
    fun testSettingSliderInGroup() {
        var settings = AppSettings()
        assertEquals(1.0f, settings.general.scaling)

        val registry = SettingsRegistry.build {
            nav(SettingNavKey.Appearance) {
                section(SettingText.Raw("General")) {
                    bindGroup(AppSettings::general, { copy(general = it) }) {
                        slider(
                            GeneralSettings::scaling,
                            SettingText.Raw("Scaling"),
                            Icons.Default.Settings,
                            range = 0.5f..2.0f
                        ) { copy(scaling = it) }
                    }
                }
            }
        }

        val sliderDef = registry.definitions.value[0] as SettingDefinition.SliderSetting
        assertEquals(1.0f, sliderDef.getValue(settings))

        settings = sliderDef.setValue(settings, 1.5f)
        assertEquals(1.5f, settings.general.scaling)
    }
}
