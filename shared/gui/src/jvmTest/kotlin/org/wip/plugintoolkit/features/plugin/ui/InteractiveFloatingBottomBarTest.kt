package org.wip.plugintoolkit.features.plugin.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import org.junit.Rule
import org.wip.plugintoolkit.core.theme.AppTheme
import org.wip.plugintoolkit.features.settings.model.AppearanceSettings
import kotlin.test.Test

class InteractiveFloatingBottomBarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testBottomBarRendersInCollapsedState() {
        var restartClicked = false
        var closeClicked = false

        composeTestRule.setContent {
            AppTheme(appearance = AppearanceSettings()) {
                InteractiveFloatingBottomBar(
                    toolName = "Test Interactive Tool",
                    pluginName = "Test Plugin v1.0.0",
                    onRestart = { restartClicked = true },
                    onClose = { closeClicked = true },
                    modifier = Modifier
                )
            }
        }
        composeTestRule.waitForIdle()

        // Bar container and tool title should be visible in collapsed state
        composeTestRule.onNodeWithTag("interactive_floating_bottom_bar").assertIsDisplayed()
        composeTestRule.onNodeWithText("Test Interactive Tool").assertIsDisplayed()
    }

    @Test
    fun testBottomBarHoverAndUnhoverAnimationLifecycle() {
        composeTestRule.setContent {
            AppTheme(appearance = AppearanceSettings()) {
                InteractiveFloatingBottomBar(
                    toolName = "Interactive Canvas",
                    pluginName = "Sample Plugin v1.0",
                    onRestart = {},
                    onClose = {},
                    modifier = Modifier
                )
            }
        }
        composeTestRule.waitForIdle()

        // Hover over the bar
        val barNode = composeTestRule.onNodeWithTag("interactive_floating_bottom_bar")
        barNode.performMouseInput {
            enter()
        }
        composeTestRule.waitForIdle()

        // Unhover from the bar
        barNode.performMouseInput {
            exit()
        }
        // Advance clock to allow debounce and spring transitions to settle
        composeTestRule.mainClock.advanceTimeBy(1000)
        composeTestRule.waitForIdle()

        // Collapsed tool text remains displayed and bar settles cleanly
        composeTestRule.onNodeWithText("Interactive Canvas").assertIsDisplayed()
    }
}
