package org.wip.plugintoolkit.shared.components.sidebar

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.wip.plugintoolkit.core.theme.AppTheme
import org.wip.plugintoolkit.features.settings.model.AppearanceSettings
import org.wip.plugintoolkit.shared.components.ToolkitAdaptiveListDetailWorkspace
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
}
