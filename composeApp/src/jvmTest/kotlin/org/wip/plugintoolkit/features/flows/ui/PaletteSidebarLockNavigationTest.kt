package org.wip.plugintoolkit.features.flows.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.wip.plugintoolkit.api.Capability
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PluginInfo
import org.wip.plugintoolkit.api.PrimitiveType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * GUI tests for PaletteItem locked capability navigation.
 *
 * Test B (Locked): Clicking a LOCKED capability in the Flow Editor palette must fire
 *                  onNavigateToPluginSetting with (pluginId, settingKey) and must NOT
 *                  add the node to the canvas (onClick is not called).
 *
 * Test C (Enabled): Clicking an ENABLED capability must call onClick (add to canvas)
 *                   and must NOT fire onNavigateToPluginSetting.
 */
@OptIn(ExperimentalTestApi::class)
class PaletteSidebarLockNavigationTest {

    private val lockedCapability = Capability(
        name = "capabilityWithLockRequirement",
        description = "Requires apiKey setting",
        returnType = DataType.Primitive(PrimitiveType.STRING),
        requiresSettings = listOf("apiKey")
    )

    private val enabledCapability = Capability(
        name = "capabilityWithPause",
        description = "No settings required",
        returnType = DataType.Primitive(PrimitiveType.STRING),
        requiresSettings = emptyList()
    )

    private val pluginInfo = PluginInfo(
        id = "completeExamplePlugin",
        name = "Complete Example Plugin",
        version = "1.0.0",
        description = "Test Plugin Description"
    )

    /**
     * Test B: Clicking a LOCKED PaletteItem intercepts the click and fires onNavigateToPluginSetting.
     * The normal onClick (add-to-canvas) must NOT be called.
     */
    @Test
    fun testLockedPaletteItem_interceptsClick_firesNavigation() = runDesktopComposeUiTest {
        var onClickCalled by mutableStateOf(false)
        var navigatedPluginId by mutableStateOf<String?>(null)
        var navigatedSettingKey by mutableStateOf<String?>(null)

        setContent {
            MaterialTheme {
                // Test PaletteItem directly with isLocked = true (enabled = false)
                PaletteItem(
                    text = lockedCapability.name,
                    enabled = false,
                    targetSettingKey = lockedCapability.requiresSettings.firstOrNull() ?: "",
                    pluginId = pluginInfo.id,
                    hasUnsavedChanges = false,
                    onNavigateToPluginSetting = { pluginId, settingKey ->
                        navigatedPluginId = pluginId
                        navigatedSettingKey = settingKey
                    },
                    rootLayoutCoordinates = null,
                    onDragStart = { _, _ -> },
                    onDrag = { },
                    onDragEnd = { },
                    onClick = { onClickCalled = true }
                )
            }
        }

        onNodeWithText(lockedCapability.name).performClick()

        assertFalse(onClickCalled, "Normal onClick (add to canvas) must NOT fire for locked items")
        assertEquals(pluginInfo.id, navigatedPluginId)
        assertEquals("apiKey", navigatedSettingKey)
    }

    /**
     * Test C: Clicking an ENABLED PaletteItem calls onClick normally.
     * onNavigateToPluginSetting must NOT be called.
     */
    @Test
    fun testEnabledPaletteItem_normalClickFires_noNavigation() = runDesktopComposeUiTest {
        var onClickCalled by mutableStateOf(false)
        var navFired by mutableStateOf(false)

        setContent {
            MaterialTheme {
                PaletteItem(
                    text = enabledCapability.name,
                    enabled = true,
                    targetSettingKey = "",
                    pluginId = pluginInfo.id,
                    hasUnsavedChanges = false,
                    onNavigateToPluginSetting = { _, _ -> navFired = true },
                    rootLayoutCoordinates = null,
                    onDragStart = { _, _ -> },
                    onDrag = { },
                    onDragEnd = { },
                    onClick = { onClickCalled = true }
                )
            }
        }

        onNodeWithText(enabledCapability.name).performClick()

        assertTrue(onClickCalled, "Normal onClick must fire for enabled items")
        assertFalse(navFired, "Navigation must NOT fire for enabled items")
    }

    /**
     * Test B2: Clicking a LOCKED PaletteItem when there are unsaved changes must show
     * the unsaved changes warning dialog before navigation fires.
     */
    @Test
    fun testLockedPaletteItem_withUnsavedChanges_showsWarningDialog() = runDesktopComposeUiTest {
        var navFired by mutableStateOf(false)

        setContent {
            MaterialTheme {
                PaletteItem(
                    text = lockedCapability.name,
                    enabled = false,
                    targetSettingKey = "apiKey",
                    pluginId = pluginInfo.id,
                    hasUnsavedChanges = true,
                    onNavigateToPluginSetting = { _, _ -> navFired = true },
                    rootLayoutCoordinates = null,
                    onDragStart = { _, _ -> },
                    onDrag = { },
                    onDragEnd = { },
                    onClick = { }
                )
            }
        }

        onNodeWithText(lockedCapability.name).performClick()

        // Warning dialog must appear; navigation must NOT have fired yet
        onNodeWithText("All the unsaved data will be lost. Are you sure you want to exit?").assertExists()
        assertFalse(navFired, "Navigation must not fire before user confirms")
    }
}
