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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    @Test
    fun testBottomBarWidthDuringExitAnimation() {
        composeTestRule.mainClock.autoAdvance = false
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

        val barNode = composeTestRule.onNodeWithTag("interactive_floating_bottom_bar")
        val initialSize = barNode.fetchSemanticsNode().size
        println("Initial size (collapsed): $initialSize")

        // Hover
        barNode.performMouseInput { enter() }
        composeTestRule.mainClock.advanceTimeBy(50)
        val sizeAtEnter50ms = barNode.fetchSemanticsNode().size
        println("Size at 50ms into enter: $sizeAtEnter50ms")

        composeTestRule.mainClock.advanceTimeBy(50)
        val sizeAtEnter100ms = barNode.fetchSemanticsNode().size
        println("Size at 100ms into enter: $sizeAtEnter100ms")

        // Advance time to fully expand
        composeTestRule.mainClock.advanceTimeBy(400)
        val expandedSize = barNode.fetchSemanticsNode().size
        println("Expanded size: $expandedSize")

        // Unhover
        barNode.performMouseInput { exit() }
        // Debounce is 120ms
        composeTestRule.mainClock.advanceTimeBy(120)
        val sizeAtExitStart = barNode.fetchSemanticsNode().size
        println("Size at exit start (after debounce): $sizeAtExitStart")

        // 50ms into exit animation
        composeTestRule.mainClock.advanceTimeBy(50)
        val sizeAt50ms = barNode.fetchSemanticsNode().size
        println("Size at 50ms into exit: $sizeAt50ms")

        // 100ms into exit animation
        composeTestRule.mainClock.advanceTimeBy(50)
        val sizeAt100ms = barNode.fetchSemanticsNode().size
        println("Size at 100ms into exit: $sizeAt100ms")

        // Settle completely
        composeTestRule.mainClock.advanceTimeBy(1000)
        val finalSize = barNode.fetchSemanticsNode().size
        println("Final size: $finalSize")

        assertTrue(expandedSize.width > initialSize.width, "Expanded bar should be wider than collapsed")
        assertTrue(sizeAt50ms.width < sizeAtExitStart.width, "Width should actively shrink during exit animation rather than staying full width")
        assertTrue(sizeAt100ms.width < sizeAt50ms.width, "Width should continuously shrink towards collapsed size")
        assertEquals(initialSize.width, finalSize.width, "Final settled width should match initial collapsed width")
    }
}

