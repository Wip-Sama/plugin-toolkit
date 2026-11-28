package org.wip.plugintoolkit.shared.components.sidebar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.dragAndDrop
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.wip.plugintoolkit.core.theme.AppTheme
import org.wip.plugintoolkit.features.settings.model.AppearanceSettings
import org.wip.plugintoolkit.shared.components.ToolkitAdaptiveListDetailWorkspace
import org.wip.plugintoolkit.shared.components.computeMaxAdaptiveListPaneWidth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ToolkitFloatingSidebarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun sequelSidebarModeResolvesAndTogglesCorrectly() {
        assertTrue(resolveSequelExpanded(SequelSidebarMode.Auto, isWideViewport = true))
        assertFalse(resolveSequelExpanded(SequelSidebarMode.Auto, isWideViewport = false))

        assertTrue(resolveSequelExpanded(SequelSidebarMode.ForcedExpanded, isWideViewport = false))
        assertTrue(resolveSequelExpanded(SequelSidebarMode.ForcedExpanded, isWideViewport = true))

        assertFalse(resolveSequelExpanded(SequelSidebarMode.ForcedCompact, isWideViewport = true))
        assertFalse(resolveSequelExpanded(SequelSidebarMode.ForcedCompact, isWideViewport = false))

        assertEquals(SequelSidebarMode.ForcedCompact, toggleSequelSidebarMode(isCurrentlyExpanded = true))
        assertEquals(SequelSidebarMode.ForcedExpanded, toggleSequelSidebarMode(isCurrentlyExpanded = false))
    }

    @Test
    fun floatingSidebarNavigatesPrimaryContentAndTogglesSequelPane() {
        var navKey by mutableStateOf("plugins")
        var sequelMode by mutableStateOf(SequelSidebarMode.ForcedCompact)

        composeTestRule.setContent {
            AppTheme(appearance = AppearanceSettings()) {
                val isSequelVisible = resolveSequelExpanded(sequelMode, isWideViewport = false)
                ToolkitFloatingSidebar(
                    navigationKey = navKey,
                    modifier = Modifier.width(560.dp),
                    sequelVisible = isSequelVisible,
                    sequelContent = {
                        Text("Sequel Capabilities")
                    }
                ) { key ->
                    ToolkitFloatingSidebarScaffold(
                        title = "Sidebar Title",
                        headerActions = {
                            ToolkitSequelSidebarToggleButton(
                                isExpanded = isSequelVisible,
                                onToggle = { sequelMode = toggleSequelSidebarMode(isSequelVisible) }
                            )
                        }
                    ) {
                        Text("Primary: $key")
                    }
                }
            }
        }

        composeTestRule.onNodeWithTag("toolkit_floating_sidebar").assertIsDisplayed()
        composeTestRule.onNodeWithText("Primary: plugins").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sequel Capabilities").assertDoesNotExist()

        // Navigate primary content
        navKey = "details"
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Primary: details").assertIsDisplayed()

        // Toggle sequel pane ON via header button
        composeTestRule.onNodeWithTag("sequel_sidebar_toggle").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("toolkit_floating_sidebar_sequel").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sequel Capabilities").assertIsDisplayed()

        // Toggle sequel pane OFF again
        composeTestRule.onNodeWithTag("sequel_sidebar_toggle").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Sequel Capabilities").assertDoesNotExist()
    }

    @OptIn(ExperimentalMaterial3AdaptiveApi::class)
    @Test
    fun adaptiveListDetailWorkspaceRendersFloatingListAndDetailPanes() {
        composeTestRule.setContent {
            AppTheme(appearance = AppearanceSettings()) {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    ToolkitAdaptiveListDetailWorkspace<String>(
                        modifier = Modifier.fillMaxSize(),
                        listPane = {
                            ToolkitFloatingSidebar(navigationKey = "root") {
                                Text("Workspace List Pane")
                            }
                        },
                        detailPane = {
                            Text("Workspace Detail Pane")
                        }
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Workspace List Pane").assertIsDisplayed()
        composeTestRule.onNodeWithText("Workspace Detail Pane").assertIsDisplayed()
    }

    @Test
    fun sequelSidebarHysteresisAppliesCollapseAndExpandThresholds() {
        // Collapses to single-column below 440dp
        assertFalse(SequelSidebarHysteresis.resolveFromDraggedWidth(isCurrentlySplit = true, draggedWidth = 439.dp))
        // Stays split inside the hysteresis band [440dp, 500dp)
        assertTrue(SequelSidebarHysteresis.resolveFromDraggedWidth(isCurrentlySplit = true, draggedWidth = 440.dp))
        assertTrue(SequelSidebarHysteresis.resolveFromDraggedWidth(isCurrentlySplit = true, draggedWidth = 480.dp))

        // Stays single-column inside the hysteresis band until reaching 500dp
        assertFalse(SequelSidebarHysteresis.resolveFromDraggedWidth(isCurrentlySplit = false, draggedWidth = 450.dp))
        assertFalse(SequelSidebarHysteresis.resolveFromDraggedWidth(isCurrentlySplit = false, draggedWidth = 499.dp))
        // Restores two-column at >= 500dp
        assertTrue(SequelSidebarHysteresis.resolveFromDraggedWidth(isCurrentlySplit = false, draggedWidth = 500.dp))
    }

    @OptIn(ExperimentalMaterial3AdaptiveApi::class)
    @Test
    fun externallyAnimatedFloatingSidebarPreservesPrimaryAndTogglesSequelSmoothly() {
        var isSplit by mutableStateOf(false)
        var selectedPlugin by mutableStateOf<String?>(null)
        var primaryRecomposeMounts = 0

        composeTestRule.setContent {
            AppTheme(appearance = AppearanceSettings()) {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    ToolkitAdaptiveListDetailWorkspace<String>(
                        modifier = Modifier.fillMaxSize(),
                        listPaneWidth = if (isSplit) 540.dp else 300.dp,
                        listPane = {
                            val navState = Pair(if (isSplit) null else selectedPlugin, isSplit)
                            ToolkitFloatingSidebar(
                                navigationKey = navState,
                                navigationContentKey = { it.first },
                                animateSequelWidth = false,
                                sequelVisible = isSplit,
                                sequelContent = {
                                    Text("Sequel: ${selectedPlugin ?: "none"}")
                                }
                            ) { state ->
                                if (state.first == null) {
                                    LaunchedEffect(Unit) {
                                        primaryRecomposeMounts++
                                    }
                                    Text("Loaded Plugins")
                                } else {
                                    Text("Drilldown: ${state.first}")
                                }
                            }
                        },
                        detailPane = {
                            Text("Detail Workspace")
                        }
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Loaded Plugins").assertIsDisplayed()
        assertEquals(1, primaryRecomposeMounts)

        // Selecting a plugin directly in wide Auto mode expands split immediately without remounting Loaded Plugins
        selectedPlugin = "plugin-alpha"
        isSplit = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Loaded Plugins").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sequel: plugin-alpha").assertIsDisplayed()
        assertEquals(1, primaryRecomposeMounts)

        // Switching from two-column back to single-column shows drill-down pane cleanly
        isSplit = false
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Drilldown: plugin-alpha").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sequel: plugin-alpha").assertDoesNotExist()
    }

    @Test
    fun adaptiveSequelAvailabilityBlocksTwoColumnsWhenWorkspaceCapacityIsTooNarrow() {
        // Workspace too narrow (maxListPaneWidth = 360dp < 480dp): NEVER allows split, even ForcedExpanded
        assertFalse(
            resolveAdaptiveSequelExpanded(
                maxListPaneWidth = 360.dp,
                minTwoColumnWidth = 480.dp,
                mode = SequelSidebarMode.ForcedExpanded,
                isWideViewport = true,
                hasSelection = true,
                dragOverride = true
            )
        )

        // Workspace wide enough (maxListPaneWidth = 540dp >= 480dp): allows split in Auto with selection
        assertTrue(
            resolveAdaptiveSequelExpanded(
                maxListPaneWidth = 540.dp,
                minTwoColumnWidth = 480.dp,
                mode = SequelSidebarMode.Auto,
                isWideViewport = true,
                hasSelection = true
            )
        )

        // Standard 1280dp window with 240dp nav sidebar + 28dp workspace padding -> 1012dp inner width
        val maxPaneAt1280 = computeMaxAdaptiveListPaneWidth(
            workspaceInnerWidth = 1012.dp,
            listPaneMinWidth = 240.dp,
            handleThickness = 14.dp
        )
        assertTrue(
            maxPaneAt1280 >= 480.dp,
            "1280dp window with expanded nav sidebar should fit two-column sidebar (got $maxPaneAt1280)"
        )

        // Narrower 860dp inner workspace -> 860 - 400 - 14 = 446dp < 480dp -> cleanly blocks two-column sliver
        val maxPaneAtNarrow = computeMaxAdaptiveListPaneWidth(
            workspaceInnerWidth = 860.dp,
            listPaneMinWidth = 240.dp,
            handleThickness = 14.dp
        )
        assertTrue(
            maxPaneAtNarrow < 480.dp,
            "Narrow workspace should report maxPane < 480dp (got $maxPaneAtNarrow)"
        )
    }

    @OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalTestApi::class)
    @Test
    fun draggingSidebarNarrowThenReEnablingTwoColumnModeExpandsPaneToFullTwoColumnWidth() {
        var sequelMode by mutableStateOf(SequelSidebarMode.Auto)
        var dragSequelOverride by mutableStateOf<Boolean?>(null)
        var widthRequestKey by mutableStateOf(0)

        composeTestRule.setContent {
            AppTheme(appearance = AppearanceSettings()) {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    Box(modifier = Modifier.width(1200.dp).height(700.dp)) {
                        val targetSequel = resolveAdaptiveSequelExpanded(
                            maxListPaneWidth = 600.dp,
                            minTwoColumnWidth = 480.dp,
                            mode = sequelMode,
                            isWideViewport = true,
                            hasSelection = true,
                            dragOverride = null
                        )
                        val isSequelExpanded = resolveAdaptiveSequelExpanded(
                            maxListPaneWidth = 600.dp,
                            minTwoColumnWidth = 480.dp,
                            mode = sequelMode,
                            isWideViewport = true,
                            hasSelection = true,
                            dragOverride = dragSequelOverride
                        )
                        ToolkitAdaptiveListDetailWorkspace<String>(
                            listPaneWidth = if (targetSequel) 540.dp else 300.dp,
                            listPaneWidthRequestKey = widthRequestKey,
                            onLiveListPaneWidthChanged = { draggedWidth ->
                                val nextSplit = SequelSidebarHysteresis.resolveFromDraggedWidth(
                                    isCurrentlySplit = isSequelExpanded,
                                    draggedWidth = draggedWidth
                                )
                                if (nextSplit != isSequelExpanded) {
                                    dragSequelOverride = nextSplit
                                }
                            },
                            listPane = {
                                ToolkitFloatingSidebar(
                                    navigationKey = isSequelExpanded,
                                    animateSequelWidth = false,
                                    sequelVisible = isSequelExpanded,
                                    sequelContent = {
                                        Text("Capabilities Column")
                                    }
                                ) {
                                    ToolkitFloatingSidebarScaffold(
                                        title = "Loaded Plugins",
                                        headerActions = {
                                            ToolkitSequelSidebarToggleButton(
                                                isExpanded = isSequelExpanded,
                                                onToggle = {
                                                    dragSequelOverride = null
                                                    sequelMode = toggleSequelSidebarMode(isSequelExpanded)
                                                    widthRequestKey++
                                                }
                                            )
                                        }
                                    ) {
                                        Text("Plugin List")
                                    }
                                }
                            },
                            detailPane = {
                                Text("Detail Area")
                            }
                        )
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Capabilities Column").assertIsDisplayed()

        // Step 1: Drag sidebar narrower by 260dp (540 -> ~280dp), collapsing 2-column to 1-column via hysteresis
        composeTestRule.onNodeWithTag("list_detail_pane_resize_handle").performMouseInput {
            dragAndDrop(
                start = center,
                end = Offset(center.x - 260f, center.y),
                durationMillis = 150
            )
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Capabilities Column").assertDoesNotExist()

        // Step 2: Re-enable 2-column mode via toggle button -> pane MUST expand back to >= 480dp (no clipped sliver!)
        composeTestRule.onNodeWithTag("sequel_sidebar_toggle").performClick()
        composeTestRule.waitForIdle()

        val sidebarBounds = composeTestRule.onNodeWithTag("toolkit_floating_sidebar").getUnclippedBoundsInRoot()
        val sidebarWidthDp = sidebarBounds.right.value - sidebarBounds.left.value
        assertTrue(
            sidebarWidthDp >= 480f,
            "Re-enabling 2-column mode after dragging narrow must expand sidebar to >= 480dp, got $sidebarWidthDp"
        )
        composeTestRule.onNodeWithText("Capabilities Column").assertIsDisplayed()
    }

    @Test
    fun primaryColumnWidthStaysAnchoredDuringOneToTwoColumnExpansion() {
        var containerWidth by mutableStateOf(300.dp)
        var sequelVisible by mutableStateOf(false)

        composeTestRule.setContent {
            AppTheme(appearance = AppearanceSettings()) {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    ToolkitFloatingSidebar(
                        navigationKey = "plugins",
                        animateSequelWidth = false,
                        sequelVisible = sequelVisible,
                        sequelContent = { Text("Sequel Column") },
                        modifier = Modifier.width(containerWidth).height(500.dp)
                    ) {
                        Text("Primary Column")
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        val initialPrimaryBounds = composeTestRule.onNodeWithTag("toolkit_floating_sidebar_primary").getUnclippedBoundsInRoot()
        assertEquals(300f, initialPrimaryBounds.right.value - initialPrimaryBounds.left.value, 1f)

        // Frame 1 of expansion: sequel becomes visible while container is still 300dp -> primary MUST stay 300dp (not jump to 240dp!)
        sequelVisible = true
        composeTestRule.waitForIdle()
        val frame1PrimaryBounds = composeTestRule.onNodeWithTag("toolkit_floating_sidebar_primary").getUnclippedBoundsInRoot()
        assertEquals(300f, frame1PrimaryBounds.right.value - frame1PrimaryBounds.left.value, 1f)

        // Mid-expansion (430dp) and full split (560dp): primary stays anchored at 300dp while sequel reveals on the right
        containerWidth = 430.dp
        composeTestRule.waitForIdle()
        val midPrimaryBounds = composeTestRule.onNodeWithTag("toolkit_floating_sidebar_primary").getUnclippedBoundsInRoot()
        assertEquals(300f, midPrimaryBounds.right.value - midPrimaryBounds.left.value, 1f)

        containerWidth = 560.dp
        composeTestRule.waitForIdle()
        val settledPrimaryBounds = composeTestRule.onNodeWithTag("toolkit_floating_sidebar_primary").getUnclippedBoundsInRoot()
        assertEquals(300f, settledPrimaryBounds.right.value - settledPrimaryBounds.left.value, 1f)
        composeTestRule.onNodeWithText("Sequel Column").assertIsDisplayed()
    }
}
