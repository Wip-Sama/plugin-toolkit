package org.wip.plugintoolkit.shared.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.dragAndDrop
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.wip.plugintoolkit.core.theme.AppTheme
import org.wip.plugintoolkit.features.settings.model.AppearanceSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResizableCollapsiblePaneTest {

    @Test
    fun expandDeltaSignMatchesAllFourAttachmentEdges() {
        assertEquals(20f, ResizablePaneDragPhysics.toExpandDelta(PaneAttachmentEdge.Start, 20f, 5f))
        assertEquals(-20f, ResizablePaneDragPhysics.toExpandDelta(PaneAttachmentEdge.End, 20f, 5f))
        assertEquals(15f, ResizablePaneDragPhysics.toExpandDelta(PaneAttachmentEdge.Top, 5f, 15f))
        assertEquals(-15f, ResizablePaneDragPhysics.toExpandDelta(PaneAttachmentEdge.Bottom, 5f, 15f))
    }

    @Test
    fun computeMaxExpandedSizeProtectsBothPaneAndMainWorkspaceFromSquishing() {
        val maxByMain = ResizablePaneDragPhysics.computeMaxExpandedSizeDp(
            totalAvailableDp = 800f,
            minPaneSizeDp = 240f,
            minMainPaneSizeDp = 440f,
            handleThicknessDp = 14f,
            maxPaneFraction = 0.6f
        )
        // 800 - 440 - 14 = 346 (tighter than 800 * 0.6 = 480)
        assertEquals(346f, maxByMain)

        val maxByFraction = ResizablePaneDragPhysics.computeMaxExpandedSizeDp(
            totalAvailableDp = 1600f,
            minPaneSizeDp = 240f,
            minMainPaneSizeDp = 440f,
            handleThicknessDp = 14f,
            maxPaneFraction = 0.55f
        )
        // 1600 * 0.55 = 880 (tighter than 1600 - 440 - 14 = 1146)
        assertEquals(880f, maxByFraction)
    }

    @Test
    fun commitOnReleaseDoesNotCollapseMidDragAndRecoversWhenCursorReverses() {
        val started = ResizablePaneDragPhysics.onDragStart(
            ResizablePaneDragSnapshot(
                isExpanded = true,
                visibleSizeDp = 300f,
                lastExpandedSizeDp = 300f
            )
        )

        // Drag past minSize (240dp) by 80dp (> 64dp collapse threshold)
        val overDragged = ResizablePaneDragPhysics.onDragDelta(
            state = started,
            expandDeltaDp = -140f,
            minSizeDp = 240f,
            maxSizeDp = 500f,
            collapseThresholdDp = 64f,
            expandThresholdDp = 40f,
            collapsible = true
        )
        // Must NOT flip isExpanded mid-drag (no handle teleporting!)
        assertTrue(overDragged.isExpanded, "Pane must stay expanded until pointer release")
        assertEquals(80f, overDragged.overDragCollapseDp)
        assertTrue(overDragged.visibleSizeDp in 226f..240f, "Rubber-band pull should remain near minSize without squishing")
        assertEquals(1f, overDragged.collapseIntentProgress(64f))

        // Reversing direction back right by 100dp (rawCursor = 160 + 100 = 260dp) recovers 1:1 resizing cleanly
        val recovered = ResizablePaneDragPhysics.onDragDelta(
            state = overDragged,
            expandDeltaDp = 100f,
            minSizeDp = 240f,
            maxSizeDp = 500f,
            collapseThresholdDp = 64f,
            expandThresholdDp = 40f,
            collapsible = true
        )
        assertTrue(recovered.isExpanded)
        assertEquals(260f, recovered.visibleSizeDp)
        assertEquals(0f, recovered.overDragCollapseDp)

        // Releasing while over-dragged past threshold commits collapse
        val committedCollapse = ResizablePaneDragPhysics.onDragEnd(
            state = overDragged,
            defaultSizeDp = 300f,
            minSizeDp = 240f,
            maxSizeDp = 500f,
            magneticSnapDp = 14f,
            collapseThresholdDp = 64f,
            expandThresholdDp = 40f,
            collapsible = true
        )
        assertFalse(committedCollapse.isExpanded, "Releasing past collapse threshold must collapse pane")
        assertEquals(0f, committedCollapse.visibleSizeDp)
        assertEquals(300f, committedCollapse.lastExpandedSizeDp, "Pre-collapse size must be preserved for restore")
    }

    @Test
    fun dragToOpenUnveilsUnderCursorAndCommitsOnRelease() {
        val started = ResizablePaneDragPhysics.onDragStart(
            ResizablePaneDragSnapshot(
                isExpanded = false,
                visibleSizeDp = 0f,
                lastExpandedSizeDp = 320f
            )
        )

        // Pulling out 25dp (< 40dp expand threshold) unveils 25dp preview under cursor
        val smallPull = ResizablePaneDragPhysics.onDragDelta(
            state = started,
            expandDeltaDp = 25f,
            minSizeDp = 240f,
            maxSizeDp = 500f,
            collapseThresholdDp = 64f,
            expandThresholdDp = 40f,
            collapsible = true
        )
        assertFalse(smallPull.isExpanded, "Must not flip isExpanded mid-drag")
        assertEquals(25f, smallPull.visibleSizeDp, "Preview should track cursor 1:1")
        assertEquals(25f, smallPull.dragOpenProgressDp)

        // Releasing before expand threshold springs back closed
        val releasedEarly = ResizablePaneDragPhysics.onDragEnd(
            state = smallPull,
            defaultSizeDp = 300f,
            minSizeDp = 240f,
            maxSizeDp = 500f,
            magneticSnapDp = 14f,
            expandThresholdDp = 40f
        )
        assertFalse(releasedEarly.isExpanded)
        assertEquals(0f, releasedEarly.visibleSizeDp)

        // Pulling out past expand threshold (to 90dp) and releasing restores lastExpandedSizeDp (320dp)
        val mediumPull = ResizablePaneDragPhysics.onDragDelta(
            state = smallPull,
            expandDeltaDp = 65f,
            minSizeDp = 240f,
            maxSizeDp = 500f,
            collapseThresholdDp = 64f,
            expandThresholdDp = 40f,
            collapsible = true
        )
        assertEquals(90f, mediumPull.visibleSizeDp)
        val releasedOpen = ResizablePaneDragPhysics.onDragEnd(
            state = mediumPull,
            defaultSizeDp = 300f,
            minSizeDp = 240f,
            maxSizeDp = 500f,
            magneticSnapDp = 14f,
            expandThresholdDp = 40f
        )
        assertTrue(releasedOpen.isExpanded)
        assertEquals(320f, releasedOpen.visibleSizeDp)

        // Pulling all the way to 360dp (>= minSizeDp) settles at cursor position (360dp)
        val fullPull = ResizablePaneDragPhysics.onDragDelta(
            state = mediumPull,
            expandDeltaDp = 270f,
            minSizeDp = 240f,
            maxSizeDp = 500f,
            collapseThresholdDp = 64f,
            expandThresholdDp = 40f,
            collapsible = true
        )
        val releasedAtCursor = ResizablePaneDragPhysics.onDragEnd(
            state = fullPull,
            defaultSizeDp = 300f,
            minSizeDp = 240f,
            maxSizeDp = 500f,
            magneticSnapDp = 14f,
            expandThresholdDp = 40f
        )
        assertTrue(releasedAtCursor.isExpanded)
        assertEquals(360f, releasedAtCursor.visibleSizeDp)
    }

    @Test
    fun velocityFlingCollapsesAndExpandsSmoothly() {
        val nearMin = ResizablePaneDragSnapshot(
            isExpanded = true,
            visibleSizeDp = 250f,
            lastExpandedSizeDp = 300f,
            rawCursorSizeDp = 250f
        )
        val flungClosed = ResizablePaneDragPhysics.onDragEnd(
            state = nearMin,
            defaultSizeDp = 300f,
            minSizeDp = 240f,
            maxSizeDp = 500f,
            magneticSnapDp = 14f,
            releaseExpandVelocityDpPerSec = -900f
        )
        assertFalse(flungClosed.isExpanded)
        assertEquals(0f, flungClosed.visibleSizeDp)

        val smallOpenPull = ResizablePaneDragSnapshot(
            isExpanded = false,
            visibleSizeDp = 20f,
            lastExpandedSizeDp = 300f,
            dragOpenProgressDp = 20f,
            rawCursorSizeDp = 20f
        )
        val flungOpen = ResizablePaneDragPhysics.onDragEnd(
            state = smallOpenPull,
            defaultSizeDp = 300f,
            minSizeDp = 240f,
            maxSizeDp = 500f,
            magneticSnapDp = 14f,
            releaseExpandVelocityDpPerSec = 900f
        )
        assertTrue(flungOpen.isExpanded)
        assertEquals(300f, flungOpen.visibleSizeDp)
    }

    @Test
    fun dragEndMagneticallySnapsNearDefaultSize() {
        val nearDefault = ResizablePaneDragSnapshot(
            isExpanded = true,
            visibleSizeDp = 308f,
            lastExpandedSizeDp = 308f,
            rawCursorSizeDp = 308f
        )
        val snapped = ResizablePaneDragPhysics.onDragEnd(
            state = nearDefault,
            defaultSizeDp = 300f,
            minSizeDp = 240f,
            maxSizeDp = 500f,
            magneticSnapDp = 14f
        )
        assertEquals(300f, snapped.visibleSizeDp)
    }

    @OptIn(ExperimentalMaterial3AdaptiveApi::class)
    @Test
    fun clickHandleTogglesOpenAndClosedOnBothHorizontalAndVerticalEdges() = runComposeUiTest {
        var listExpanded by mutableStateOf(true)
        var supportingExpanded by mutableStateOf(true)
        var supportingWidth by mutableStateOf(1150.dp)

        setContent {
            AppTheme(appearance = AppearanceSettings()) {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    Box(modifier = Modifier.width(supportingWidth).height(700.dp)) {
                        ToolkitAdaptiveSupportingWorkspace(
                            showSupportingPane = supportingExpanded,
                            onShowSupportingPaneChange = { supportingExpanded = it },
                            collapseTooltip = "",
                            expandTooltip = "",
                            mainPane = {
                                ToolkitAdaptiveListDetailWorkspace<String>(
                                    listPaneWidth = 260.dp,
                                    listPaneMinWidth = 200.dp,
                                    isListPaneExpanded = listExpanded,
                                    onListPaneExpandedChange = { listExpanded = it },
                                    listPane = { Text("List Content") },
                                    detailPane = { Text("Detail Content") }
                                )
                            },
                            supportingPane = { Text("Supporting Content") }
                        )
                    }
                }
            }
        }

        waitForIdle()
        // Horizontal End handle (side supporting card): click closes, click opens
        onNodeWithTag("supporting_pane_side_handle").performClick()
        waitForIdle()
        assertFalse(supportingExpanded, "Clicking open side supporting handle should close it")
        onNodeWithTag("supporting_pane_side_handle").performClick()
        waitForIdle()
        assertTrue(supportingExpanded, "Clicking closed side supporting handle should open it")

        // Vertical Bottom handle (narrow supporting card): click closes, click opens
        supportingWidth = 520.dp
        waitForIdle()
        onNodeWithTag("supporting_pane_bottom_handle").performClick()
        waitForIdle()
        assertFalse(supportingExpanded, "Clicking open bottom supporting handle should close it")
        onNodeWithTag("supporting_pane_bottom_handle").performClick()
        waitForIdle()
        assertTrue(supportingExpanded, "Clicking closed bottom supporting handle should open it")
    }

    @OptIn(ExperimentalTestApi::class, ExperimentalMaterial3AdaptiveApi::class)
    @Test
    fun listDetailWorkspaceSidebarDragsClosedAndDragsOpen() = runComposeUiTest {
        var expandedState by mutableStateOf(true)

        setContent {
            AppTheme(appearance = AppearanceSettings()) {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    Box(modifier = Modifier.width(1200.dp).height(700.dp)) {
                        ToolkitAdaptiveListDetailWorkspace<String>(
                            listPaneWidth = 300.dp,
                            listPaneMinWidth = 240.dp,
                            isListPaneExpanded = expandedState,
                            onListPaneExpandedChange = { expandedState = it },
                            listPane = { Text("Interactive Sidebar") },
                            detailPane = { Text("Interactive Canvas") }
                        )
                    }
                }
            }
        }

        waitForIdle()
        onNodeWithText("Interactive Sidebar").assertIsDisplayed()
        onNodeWithText("Interactive Canvas").assertIsDisplayed()

        // Drag handle left across minWidth (60dp) + collapseThreshold (64dp) -> commits collapse on release
        onNodeWithTag("list_detail_pane_resize_handle").performMouseInput {
            dragAndDrop(
                start = center,
                end = Offset(center.x - 180f, center.y),
                durationMillis = 150
            )
        }
        waitForIdle()
        assertFalse(expandedState, "Dragging list sidebar left past threshold should collapse it")
        onNodeWithText("Interactive Sidebar").assertDoesNotExist()
        onNodeWithText("Interactive Canvas").assertIsDisplayed()

        // Drag collapsed left-edge handle right past expandThreshold (40dp) -> commits expand on release
        onNodeWithTag("list_detail_pane_resize_handle").performMouseInput {
            dragAndDrop(
                start = center,
                end = Offset(center.x + 120f, center.y),
                durationMillis = 150
            )
        }
        waitForIdle()
        assertTrue(expandedState, "Dragging collapsed edge handle right should expand sidebar")
        onNodeWithText("Interactive Sidebar").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun supportingWorkspaceBottomAndSidePanesDragCloseAndDragOpen() = runComposeUiTest {
        var showPane by mutableStateOf(true)
        var containerWidth by mutableStateOf(520.dp)

        setContent {
            AppTheme(appearance = AppearanceSettings()) {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    Box(modifier = Modifier.width(containerWidth).height(700.dp)) {
                        ToolkitAdaptiveSupportingWorkspace(
                            showSupportingPane = showPane,
                            onShowSupportingPaneChange = { showPane = it },
                            collapseTooltip = "",
                            expandTooltip = "",
                            mainPane = { Text("Main Panel") },
                            supportingPane = { Text("Supporting Panel") }
                        )
                    }
                }
            }
        }

        // Narrow mode (Bottom): drag down past minHeight (140dp) + vertical threshold (52dp)
        waitForIdle()
        onNodeWithText("Supporting Panel").assertIsDisplayed()
        onNodeWithTag("supporting_pane_bottom_handle").performMouseInput {
            dragAndDrop(
                start = center,
                end = Offset(center.x, center.y + 200f),
                durationMillis = 150
            )
        }
        waitForIdle()
        assertFalse(showPane, "Dragging bottom supporting handle down past threshold should collapse it")
        onNodeWithText("Supporting Panel").assertDoesNotExist()

        // Drag collapsed bottom handle upward past expand threshold (40dp)
        onNodeWithTag("supporting_pane_bottom_handle").performMouseInput {
            dragAndDrop(
                start = center,
                end = Offset(center.x, center.y - 120f),
                durationMillis = 150
            )
        }
        waitForIdle()
        assertTrue(showPane, "Dragging collapsed bottom handle up should expand it")
        onNodeWithText("Supporting Panel").assertIsDisplayed()

        // Wide mode (End / Right side): drag right past minWidth + collapse threshold
        containerWidth = 1150.dp
        waitForIdle()
        onNodeWithTag("supporting_pane_side_handle").performMouseInput {
            dragAndDrop(
                start = center,
                end = Offset(center.x + 240f, center.y),
                durationMillis = 150
            )
        }
        waitForIdle()
        assertFalse(showPane, "Dragging right supporting handle right past threshold should collapse it")
        onNodeWithText("Supporting Panel").assertDoesNotExist()

        // Drag collapsed right-edge handle left past expand threshold
        onNodeWithTag("supporting_pane_side_handle").performMouseInput {
            dragAndDrop(
                start = center,
                end = Offset(center.x - 120f, center.y),
                durationMillis = 150
            )
        }
        waitForIdle()
        assertTrue(showPane, "Dragging collapsed right-edge handle left should expand it")
        onNodeWithText("Supporting Panel").assertIsDisplayed()
    }
}
