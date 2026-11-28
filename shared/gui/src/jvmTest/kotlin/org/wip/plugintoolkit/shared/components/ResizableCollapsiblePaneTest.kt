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
    fun twoStageDragToCollapseHoldsAtMinSizeThenAnimatesShutPastThreshold() {
        val initial = ResizablePaneDragSnapshot(
            isExpanded = true,
            visibleSizeDp = 300f,
            lastExpandedSizeDp = 300f
        )

        // Drag to minSize (240dp) + 30dp over-drag (< 64dp collapse threshold)
        val stage1 = ResizablePaneDragPhysics.onDragDelta(
            state = initial,
            expandDeltaDp = -90f,
            minSizeDp = 240f,
            maxSizeDp = 500f,
            collapseThresholdDp = 64f,
            expandThresholdDp = 40f,
            collapsible = true
        )
        assertTrue(stage1.isExpanded, "Pane must stay open in Stage 1 resistance zone")
        assertEquals(240f, stage1.visibleSizeDp, "Visible size must clamp at minSize without squishing")
        assertEquals(30f, stage1.overDragCollapseDp)
        assertTrue(stage1.collapseIntentProgress(64f) in 0.45f..0.48f)

        // Releasing in Stage 1 keeps pane open at minSize
        val releasedInStage1 = ResizablePaneDragPhysics.onDragEnd(
            state = stage1,
            defaultSizeDp = 300f,
            minSizeDp = 240f,
            maxSizeDp = 500f,
            magneticSnapDp = 14f
        )
        assertTrue(releasedInStage1.isExpanded)
        assertEquals(240f, releasedInStage1.visibleSizeDp)
        assertEquals(0f, releasedInStage1.overDragCollapseDp)

        // Continuing over-drag past 64dp triggers Stage 2 collapse
        val stage2 = ResizablePaneDragPhysics.onDragDelta(
            state = stage1,
            expandDeltaDp = -40f,
            minSizeDp = 240f,
            maxSizeDp = 500f,
            collapseThresholdDp = 64f,
            expandThresholdDp = 40f,
            collapsible = true
        )
        assertFalse(stage2.isExpanded, "Crossing collapse threshold must collapse pane")
        assertEquals(0f, stage2.visibleSizeDp)
        assertEquals(300f, stage2.lastExpandedSizeDp, "Pre-collapse size must be preserved for restore")
    }

    @Test
    fun dragToOpenFromCollapsedWallTransitionsContinuouslyIntoResizing() {
        val collapsed = ResizablePaneDragSnapshot(
            isExpanded = false,
            visibleSizeDp = 0f,
            lastExpandedSizeDp = 320f
        )

        // Small pull (< 40dp expand threshold) keeps pane collapsed
        val partialPull = ResizablePaneDragPhysics.onDragDelta(
            state = collapsed,
            expandDeltaDp = 25f,
            minSizeDp = 240f,
            maxSizeDp = 500f,
            collapseThresholdDp = 64f,
            expandThresholdDp = 40f,
            collapsible = true
        )
        assertFalse(partialPull.isExpanded)
        assertEquals(25f, partialPull.dragOpenProgressDp)

        // Pulling past 40dp opens the pane at minSize
        val opened = ResizablePaneDragPhysics.onDragDelta(
            state = partialPull,
            expandDeltaDp = 20f,
            minSizeDp = 240f,
            maxSizeDp = 500f,
            collapseThresholdDp = 64f,
            expandThresholdDp = 40f,
            collapsible = true
        )
        assertTrue(opened.isExpanded)
        assertEquals(240f, opened.visibleSizeDp)

        // Continued drag in the same gesture grows the newly opened pane seamlessly
        val resized = ResizablePaneDragPhysics.onDragDelta(
            state = opened,
            expandDeltaDp = 80f,
            minSizeDp = 240f,
            maxSizeDp = 500f,
            collapseThresholdDp = 64f,
            expandThresholdDp = 40f,
            collapsible = true
        )
        assertTrue(resized.isExpanded)
        assertEquals(320f, resized.visibleSizeDp)
    }

    @Test
    fun dragEndMagneticallySnapsNearDefaultSize() {
        val nearDefault = ResizablePaneDragSnapshot(
            isExpanded = true,
            visibleSizeDp = 308f,
            lastExpandedSizeDp = 308f
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

        // Drag handle left across minWidth (60dp) + collapseThreshold (64dp) -> collapses sidebar
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

        // Drag collapsed left-edge handle right past expandThreshold (40dp) -> restores sidebar
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
