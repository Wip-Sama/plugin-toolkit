package org.wip.plugintoolkit.shared.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
}
