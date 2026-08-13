package org.wip.plugintoolkit.shared.components.plugin.inputs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.ParameterMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * GUI tests for EnumDropdownInput locked option navigation.
 *
 * Test A: Clicking a locked dropdown option must fire onNavigateToPluginSetting with the correct
 *         (pluginId, settingKey) pair, NOT select the option.
 */
@OptIn(ExperimentalTestApi::class)
class EnumDropdownLockNavigationTest {

    /**
     * Test A1: Clicking a LOCKED enum option ("ONLINE") with mouse click intercepts the click
     * and fires onNavigateToPluginSetting with the correct pluginId and settingKey.
     */
    @Test
    fun testLockedEnumOption_firesNavigation() = runDesktopComposeUiTest {
        var navigatedPluginId by mutableStateOf<String?>(null)
        var navigatedSettingKey by mutableStateOf<String?>(null)

        val options = listOf("SECURE", "ONLINE", "LOCAL")
        val optionLockRequirements = mapOf("ONLINE" to listOf("apiKey"))

        val metadata = ParameterMetadata(
            description = "Test dropdown parameter",
            type = DataType.Enum(
                className = "TestEnum",
                options = options,
                optionLockRequirements = optionLockRequirements
            )
        )

        setContent {
            MaterialTheme {
                EnumDropdownInput(
                    name = "Input Data",
                    metadata = metadata,
                    value = "SECURE",
                    onValueChange = { },
                    providedLocks = emptyMap(),
                    onNavigateToPluginSetting = { pluginId, settingKey ->
                        navigatedPluginId = pluginId
                        navigatedSettingKey = settingKey
                    },
                    pluginId = "completeExamplePlugin"
                )
            }
        }

        // Open the dropdown
        onNodeWithText("SECURE").performClick()

        // Perform real mouse click on the locked ONLINE option
        onNodeWithText("ONLINE").performMouseInput { click() }

        // Navigation must fire — not normal selection
        assertEquals("completeExamplePlugin", navigatedPluginId)
        assertEquals("apiKey", navigatedSettingKey)
    }

    /**
     * Test A2 (Negative): Clicking an ENABLED enum option must select it normally,
     * navigation must NOT fire.
     */
    @Test
    fun testEnabledEnumOption_selectsNormally_noNavigation() = runDesktopComposeUiTest {
        var selectedValue by mutableStateOf("SECURE")
        var navFired by mutableStateOf(false)

        val options = listOf("SECURE", "ONLINE", "LOCAL")
        val optionLockRequirements = mapOf("ONLINE" to listOf("apiKey"))

        val metadata = ParameterMetadata(
            description = "Test dropdown parameter",
            type = DataType.Enum(
                className = "TestEnum",
                options = options,
                optionLockRequirements = optionLockRequirements
            )
        )

        setContent {
            MaterialTheme {
                EnumDropdownInput(
                    name = "Input Data",
                    metadata = metadata,
                    value = selectedValue,
                    onValueChange = { selectedValue = it },
                    providedLocks = emptyMap(),
                    onNavigateToPluginSetting = { _, _ -> navFired = true },
                    pluginId = "completeExamplePlugin"
                )
            }
        }

        // Open the dropdown
        onNodeWithText("SECURE").performClick()

        // Click the ENABLED LOCAL option
        onNodeWithText("LOCAL").performMouseInput { click() }

        assertEquals("LOCAL", selectedValue, "Enabled option must be selected")
        kotlin.test.assertFalse(navFired, "Navigation must NOT fire for enabled options")
    }
}
