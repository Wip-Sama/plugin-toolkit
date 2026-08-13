package org.wip.plugintoolkit.features.settings

import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.wip.plugintoolkit.core.ui.DialogService
import org.wip.plugintoolkit.features.settings.definitions.pluginDefinitions
import org.wip.plugintoolkit.features.settings.model.AppSettings
import org.wip.plugintoolkit.features.settings.model.ExtensionSettings
import org.wip.plugintoolkit.features.settings.model.SettingDefinition
import org.wip.plugintoolkit.features.settings.utils.SettingsRegistry
import org.wip.plugintoolkit.features.settings.utils.build
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PluginSettingsInPlaceWarningTest {

    @Test
    fun testPluginSettingsInPlace_toggleTrue_triggersWarningDialog() {
        val dialogService = DialogService()
        val registry = SettingsRegistry.build {
            pluginDefinitions(dialogService)
        }

        val settingDef = registry.definitions.value.filterIsInstance<SettingDefinition.SwitchSetting>()
            .find { it.id == "extensions.pluginSettingsInPlace" }

        assertNotNull(settingDef, "extensions.pluginSettingsInPlace switch setting should be registered")

        val initialSettings = AppSettings(extensions = ExtensionSettings(pluginSettingsInPlace = false))
        assertFalse(settingDef.getValue(initialSettings))

        var appliedSettings: AppSettings? = null
        val onBeforeChange = settingDef.onBeforeChange
        assertNotNull(onBeforeChange, "onBeforeChange should be provided for confirmation")

        // User attempts to turn it ON
        onBeforeChange(true) {
            appliedSettings = settingDef.setValue(initialSettings, true)
        }

        // Dialog state should be set to Confirmation / Warning
        val dialogState = dialogService.dialogState.value
        assertNotNull(dialogState, "Warning dialog should be displayed when turning in-place setting on")

        // Confirm dialog
        if (dialogState is org.wip.plugintoolkit.core.ui.DialogData.Confirmation) {
            dialogState.onConfirm()
        } else if (dialogState is org.wip.plugintoolkit.core.ui.DialogData.Warning) {
            dialogState.onConfirm()
        }

        assertNotNull(appliedSettings)
        assertTrue(appliedSettings!!.extensions.pluginSettingsInPlace)
    }

    @Test
    fun testPluginSettingsInPlace_toggleFalse_noWarningNeeded() {
        val dialogService = DialogService()
        val registry = SettingsRegistry.build {
            pluginDefinitions(dialogService)
        }

        val settingDef = registry.definitions.value.filterIsInstance<SettingDefinition.SwitchSetting>()
            .find { it.id == "extensions.pluginSettingsInPlace" }

        assertNotNull(settingDef)

        val initialSettings = AppSettings(extensions = ExtensionSettings(pluginSettingsInPlace = true))
        var appliedSettings: AppSettings? = null

        val onBeforeChange = settingDef.onBeforeChange
        assertNotNull(onBeforeChange)

        // User turns it OFF -> should apply immediately without warning dialog
        onBeforeChange(false) {
            appliedSettings = settingDef.setValue(initialSettings, false)
        }

        assertEquals(null, dialogService.dialogState.value)
        assertNotNull(appliedSettings)
        assertFalse(appliedSettings!!.extensions.pluginSettingsInPlace)
    }
}
