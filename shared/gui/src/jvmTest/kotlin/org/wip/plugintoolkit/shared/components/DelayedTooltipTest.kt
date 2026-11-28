package org.wip.plugintoolkit.shared.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.wip.plugintoolkit.core.theme.AppTheme
import org.wip.plugintoolkit.features.settings.model.AppearanceSettings
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class DelayedTooltipTest {

    @Test
    fun transientHoverDoesNotShowTooltip() = runComposeUiTest {
        mainClock.autoAdvance = false

        setContent {
            AppTheme(appearance = AppearanceSettings()) {
                ToolkitTooltipBox(
                    tooltip = "Transient Hint",
                    delay = DEFAULT_TOOLTIP_DELAY
                ) {
                    Box(modifier = Modifier.size(48.dp).testTag("tooltip_target"))
                }
            }
        }
        mainClock.advanceTimeBy(100)
        waitForIdle()

        // Enter and leave before the 500ms delay expires
        onNodeWithTag("tooltip_target").performMouseInput {
            enter(Offset(10f, 10f))
        }
        mainClock.advanceTimeBy(200)
        waitForIdle()

        onNodeWithText("Transient Hint").assertDoesNotExist()

        onNodeWithTag("tooltip_target").performMouseInput {
            exit(Offset(100f, 100f))
        }
        mainClock.advanceTimeBy(600)
        waitForIdle()

        onNodeWithText("Transient Hint").assertDoesNotExist()
    }

    @Test
    fun sustainedHoverShowsAndDismissesTooltip() = runComposeUiTest {
        mainClock.autoAdvance = false

        setContent {
            AppTheme(appearance = AppearanceSettings()) {
                ToolkitTooltipBox(
                    tooltip = "Sustained Hint",
                    delay = DEFAULT_TOOLTIP_DELAY
                ) {
                    Box(modifier = Modifier.size(48.dp).testTag("tooltip_target"))
                }
            }
        }
        mainClock.advanceTimeBy(100)
        waitForIdle()

        onNodeWithTag("tooltip_target").performMouseInput {
            enter(Offset(10f, 10f))
        }
        mainClock.advanceTimeBy(DEFAULT_TOOLTIP_DELAY.inWholeMilliseconds - 100)
        waitForIdle()
        onNodeWithText("Sustained Hint").assertDoesNotExist()

        mainClock.advanceTimeBy(200)
        waitForIdle()
        onNodeWithText("Sustained Hint").assertIsDisplayed()

        onNodeWithTag("tooltip_target").performMouseInput {
            exit(Offset(100f, 100f))
        }
        mainClock.advanceTimeBy(500)
        waitForIdle()
        onNodeWithText("Sustained Hint").assertDoesNotExist()
    }

    @Test
    fun longDelayTooltipWaitsForConfiguredDuration() = runComposeUiTest {
        mainClock.autoAdvance = false

        setContent {
            AppTheme(appearance = AppearanceSettings()) {
                ToolkitTooltipBox(
                    tooltip = "Chrome Action",
                    delay = LONG_TOOLTIP_DELAY
                ) {
                    Box(modifier = Modifier.size(48.dp).testTag("chrome_target"))
                }
            }
        }
        mainClock.advanceTimeBy(100)
        waitForIdle()

        onNodeWithTag("chrome_target").performMouseInput {
            enter(Offset(10f, 10f))
        }
        mainClock.advanceTimeBy(600)
        waitForIdle()
        onNodeWithText("Chrome Action").assertDoesNotExist()

        mainClock.advanceTimeBy(500)
        waitForIdle()
        onNodeWithText("Chrome Action").assertIsDisplayed()
    }

    @Test
    fun richTooltipRespectsHoverDelay() = runComposeUiTest {
        mainClock.autoAdvance = false

        setContent {
            AppTheme(appearance = AppearanceSettings()) {
                ToolkitRichTooltipBox(
                    tooltipContent = { Text("Rich Details") },
                    delay = 300.milliseconds
                ) {
                    Box(modifier = Modifier.size(48.dp).testTag("rich_target"))
                }
            }
        }
        mainClock.advanceTimeBy(100)
        waitForIdle()

        onNodeWithTag("rich_target").performMouseInput {
            enter(Offset(10f, 10f))
        }
        mainClock.advanceTimeBy(150)
        waitForIdle()
        onNodeWithText("Rich Details").assertDoesNotExist()

        mainClock.advanceTimeBy(250)
        waitForIdle()
        onNodeWithText("Rich Details").assertIsDisplayed()
    }

    @Test
    fun clickingToggleButtonDoesNotFlashInstantTooltipOnFocusOrLabelSwap() = runComposeUiTest {
        mainClock.autoAdvance = false
        var isCollapsed by mutableStateOf(false)
        val pointerInputModeManager = object : InputModeManager {
            override val inputMode: InputMode = InputMode.Touch
            override fun requestInputMode(inputMode: InputMode): Boolean = true
        }

        setContent {
            AppTheme(appearance = AppearanceSettings()) {
                CompositionLocalProvider(LocalInputModeManager provides pointerInputModeManager) {
                    ToolkitTooltipBox(
                        tooltip = if (isCollapsed) "Expand navigation" else "Collapse navigation",
                        delay = DEFAULT_TOOLTIP_DELAY
                    ) {
                        IconButton(
                            onClick = { isCollapsed = !isCollapsed },
                            modifier = Modifier.size(48.dp).testTag("nav_toggle_btn")
                        ) {
                            Text("Toggle")
                        }
                    }
                }
            }
        }
        mainClock.advanceTimeBy(100)
        waitForIdle()

        // Hover briefly and click (simulating clicking Collapse navigation -> becomes Expand navigation)
        onNodeWithTag("nav_toggle_btn").performMouseInput {
            enter(Offset(10f, 10f))
        }
        onNodeWithTag("nav_toggle_btn").performClick()
        mainClock.advanceTimeBy(100)
        waitForIdle()

        // Must NOT flash either tooltip immediately after click
        onNodeWithText("Collapse navigation").assertDoesNotExist()
        onNodeWithText("Expand navigation").assertDoesNotExist()

        // Even while pointer stays resting on the clicked button, remains suppressed until exit+re-enter
        mainClock.advanceTimeBy(DEFAULT_TOOLTIP_DELAY.inWholeMilliseconds + 200)
        waitForIdle()
        onNodeWithText("Expand navigation").assertDoesNotExist()

        // Exit and re-enter: must respect full hover delay before showing
        onNodeWithTag("nav_toggle_btn").performMouseInput {
            exit(Offset(100f, 100f))
        }
        mainClock.advanceTimeBy(100)
        waitForIdle()

        onNodeWithTag("nav_toggle_btn").performMouseInput {
            enter(Offset(10f, 10f))
        }
        mainClock.advanceTimeBy(DEFAULT_TOOLTIP_DELAY.inWholeMilliseconds - 100)
        waitForIdle()
        onNodeWithText("Expand navigation").assertDoesNotExist()

        mainClock.advanceTimeBy(200)
        waitForIdle()
        onNodeWithText("Expand navigation").assertIsDisplayed()
    }

    @Test
    fun keyboardFocusTooltipIsDebouncedAndNotInstant() = runComposeUiTest {
        mainClock.autoAdvance = false
        val focusRequester = FocusRequester()
        val keyboardInputModeManager = object : InputModeManager {
            override val inputMode: InputMode = InputMode.Keyboard
            override fun requestInputMode(inputMode: InputMode): Boolean = true
        }

        setContent {
            AppTheme(appearance = AppearanceSettings()) {
                CompositionLocalProvider(LocalInputModeManager provides keyboardInputModeManager) {
                    ToolkitTooltipBox(
                        tooltip = "Hide sidebar",
                        delay = DEFAULT_TOOLTIP_DELAY
                    ) {
                        IconButton(
                            onClick = {},
                            modifier = Modifier
                                .size(48.dp)
                                .focusRequester(focusRequester)
                                .testTag("sidebar_toggle_btn")
                        ) {
                            Text("Hide")
                        }
                    }
                }
            }
        }
        mainClock.advanceTimeBy(50)
        waitForIdle()

        runOnIdle { focusRequester.requestFocus() }
        mainClock.advanceTimeBy(50)
        waitForIdle()

        // Must NOT appear instantaneously at 50ms (< KEYBOARD_TOOLTIP_DELAY)
        onNodeWithText("Hide sidebar").assertDoesNotExist()

        // Appears once KEYBOARD_TOOLTIP_DELAY has elapsed
        mainClock.advanceTimeBy(KEYBOARD_TOOLTIP_DELAY.inWholeMilliseconds + 50)
        waitForIdle()
        onNodeWithText("Hide sidebar").assertIsDisplayed()
    }
}
