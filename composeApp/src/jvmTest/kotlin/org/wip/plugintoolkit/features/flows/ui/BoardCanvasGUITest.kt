package org.wip.plugintoolkit.features.flows.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BoardCanvasGUITest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testBoardCanvasRenders() {
        composeTestRule.setContent {
            RenderTestBoardCanvas()
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("board_canvas").assertIsDisplayed()
        composeTestRule.onNodeWithTag("zoom_controls").assertIsDisplayed()
    }

    @Test
    fun testZoomInButtonTriggersZoomEvent() {
        var zoomDeltaReceived = 0f
        composeTestRule.setContent {
            RenderTestBoardCanvas(
                onZoom = { delta, _, _ -> zoomDeltaReceived = delta }
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("zoom_in_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(-1f, zoomDeltaReceived, 0.01f)
    }

    @Test
    fun testZoomOutButtonTriggersZoomEvent() {
        var zoomDeltaReceived = 0f
        composeTestRule.setContent {
            RenderTestBoardCanvas(
                onZoom = { delta, _, _ -> zoomDeltaReceived = delta }
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("zoom_out_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1f, zoomDeltaReceived, 0.01f)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testDeleteKeyDeletesSelectedNodes() {
        var deleteCalled = false
        composeTestRule.setContent {
            RenderTestBoardCanvas(
                selectedNodeIds = setOf(1L),
                onDeleteSelectedNodes = { deleteCalled = true }
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("board_canvas").performKeyInput {
            pressKey(Key.Delete)
        }
        composeTestRule.waitForIdle()
        assertTrue(deleteCalled)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testCtrlZTriggersUndo() {
        var undoCalled = false
        composeTestRule.setContent {
            RenderTestBoardCanvas(
                onUndo = { undoCalled = true }
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("board_canvas").performKeyInput {
            withKeyDown(Key.CtrlLeft) {
                pressKey(Key.Z)
            }
        }
        composeTestRule.waitForIdle()
        assertTrue(undoCalled)
    }
}
