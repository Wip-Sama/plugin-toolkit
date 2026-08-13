package org.wip.plugintoolkit.features.plugin.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * GUI tests for the locked click interceptor modifier.
 *
 * These tests MUST FAIL against the current standard implementation because:
 * - Test A: disabled buttons silently eat clicks; no navigation is triggered.
 * - Test B: no modifier guards against firing navigation on enabled buttons.
 */
@OptIn(ExperimentalTestApi::class)
class LockedClickInterceptorTest {

    // ─── Test A: Locked State ───────────────────────────────────────────────

    /**
     * A visually DISABLED (locked) button with the interceptor modifier must:
     * 1. NOT propagate to the real onClick (the button is disabled).
     * 2. Fire the navigation callback when clicked.
     *
     * FAILS against current code: disabled buttons swallow all events,
     * so no navigation is ever triggered.
     */
    @Test
    fun testLockedButton_interceptsClick_andFiresNavigation() = runDesktopComposeUiTest {
        var navFired by mutableStateOf<String?>(null)

        setContent {
            Button(
                onClick = { /* intentionally empty – disabled */ },
                enabled = false,
                modifier = Modifier
                    .testTag("locked-btn")
                    .lockedClickInterceptor(
                        isLocked = true,
                        hasUnsavedChanges = false,
                        targetSettingKey = "secretToken",
                        onNavigateToPluginSetting = { _, key -> navFired = key }
                    )
            ) {
                Text("Execute")
            }
        }

        // The button appears disabled to native Compose...
        onNodeWithTag("locked-btn").assertIsNotEnabled()
        // ...but the interceptor must still handle the tap.
        onNodeWithTag("locked-btn").performClick()

        assertEquals("secretToken", navFired, "Navigation should have fired on locked click")
    }

    /**
     * When the locked element is clicked and there IS unsaved data,
     * the warning dialog must appear BEFORE navigation fires.
     *
     * FAILS against current code for the same reason as above.
     */
    @Test
    fun testLockedButton_withUnsavedData_showsWarningDialog() = runDesktopComposeUiTest {
        var navFired by mutableStateOf<String?>(null)

        setContent {
            Button(
                onClick = { /* disabled */ },
                enabled = false,
                modifier = Modifier
                    .testTag("locked-btn-dirty")
                    .lockedClickInterceptor(
                        isLocked = true,
                        hasUnsavedChanges = true,
                        targetSettingKey = "secretToken",
                        onNavigateToPluginSetting = { _, key -> navFired = key }
                    )
            ) {
                Text("Execute")
            }
        }

        onNodeWithTag("locked-btn-dirty").performClick()

        // Navigation must NOT have fired yet – dialog guards it
        assertNull(navFired, "Navigation must not fire before user confirms the warning")

        // The warning dialog text from dialog_unsaved_changes + the loss message
        onNodeWithText(
            "All the unsaved data will be lost. Are you sure you want to exit?"
        ).assertExists()
    }

    // ─── Test B: Enabled State ──────────────────────────────────────────────

    /**
     * When isLocked = false the interceptor must be a no-op:
     * - The real onClick fires normally.
     * - The navigation callback is NEVER invoked.
     *
     * FAILS against a naive implementation that always intercepts clicks.
     */
    @Test
    fun testUnlockedButton_normalClickFires_navigationNeverFires() = runDesktopComposeUiTest {
        var normalClicked by mutableStateOf(false)
        var navFired by mutableStateOf<String?>(null)

        setContent {
            Button(
                onClick = { normalClicked = true },
                enabled = true,
                modifier = Modifier
                    .testTag("unlocked-btn")
                    .lockedClickInterceptor(
                        isLocked = false,
                        hasUnsavedChanges = false,
                        targetSettingKey = "secretToken",
                        onNavigateToPluginSetting = { _, key -> navFired = key }
                    )
            ) {
                Text("Execute")
            }
        }

        onNodeWithTag("unlocked-btn").assertIsEnabled()
        onNodeWithTag("unlocked-btn").performClick()

        kotlin.test.assertTrue(normalClicked, "Normal onClick must fire on an unlocked button")
        assertNull(navFired, "Navigation must NEVER fire when the element is unlocked")
    }
}
