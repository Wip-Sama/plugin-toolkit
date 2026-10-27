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
        println("=== EXIT TRAJECTORY ===")
        for (step in 0..15) {
            val t = step * 20
            val size = barNode.fetchSemanticsNode().size
            println("Exit t=${t}ms: size = ${size.width} x ${size.height}")
            composeTestRule.mainClock.advanceTimeBy(20)
        }

        // Settle completely
        composeTestRule.mainClock.advanceTimeBy(500)
        val finalSize = barNode.fetchSemanticsNode().size
        println("Final size: $finalSize")

        assertTrue(expandedSize.width > initialSize.width, "Expanded bar should be wider than collapsed")
        assertTrue(expandedSize.height > initialSize.height, "Expanded bar should be taller than collapsed")
        assertEquals(initialSize.width, finalSize.width, "Final settled width should match initial collapsed width")
        assertEquals(initialSize.height, finalSize.height, "Final settled height should match initial collapsed height")
    }
}

