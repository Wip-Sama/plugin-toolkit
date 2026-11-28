package org.wip.plugintoolkit.features.flows.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.wip.plugintoolkit.core.theme.AppTheme
import org.wip.plugintoolkit.features.settings.model.AppearanceSettings
import org.wip.plugintoolkit.features.settings.model.ConnectionCurveStyle
import org.wip.plugintoolkit.features.settings.model.OrthogonalStepMode
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FlowFloatingAppBarTest {

    @Test
    fun toolbarControlsShareAlignedVerticalCenterlineAndHorizontalCenter() = runComposeUiTest {
        setContent {
            AppTheme(appearance = AppearanceSettings()) {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    Box(modifier = Modifier.fillMaxSize().testTag("canvas_host")) {
                        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                            FlowFloatingAppBar(
                                scale = 1.0f,
                                isReadOnly = false,
                                isPaintToolActive = false,
                                isWashToolActive = false,
                                isEyedropperActive = false,
                                isAdvancedConnectionMode = false,
                                hideConnectionPointsUnlessHovered = false,
                                activePaintColor = "#4CAF50",
                                colorsInFlow = listOf("#4CAF50", "#2196F3"),
                                connectionStyle = ConnectionCurveStyle.CardinalSpline,
                                connectionRoundness = 0.5f,
                                orthogonalStepMode = OrthogonalStepMode.Auto,
                                orthogonalPortLead = false,
                                onTogglePaintTool = {},
                                onToggleWashTool = {},
                                onToggleEyedropper = {},
                                onToggleAdvancedConnectionMode = {},
                                onToggleHideConnectionPoints = {},
                                onSelectPaintColor = {},
                                onAddGroup = {},
                                onAddLabel = {},
                                onChangeConnectionStyle = {},
                                onChangeConnectionRoundness = {},
                                onZoomIn = {},
                                onZoomOut = {},
                                onExportImage = {}
                            )
                        }
                    }
                }
            }
        }

        waitForIdle()

        val hostBounds = onNodeWithTag("canvas_host").getUnclippedBoundsInRoot()
        val hostCenterX = (hostBounds.left.value + hostBounds.right.value) / 2f
        val barBounds = onNodeWithTag("flow_floating_app_bar").getUnclippedBoundsInRoot()
        val barCenterX = (barBounds.left.value + barBounds.right.value) / 2f
        assertTrue(
            abs(barCenterX - hostCenterX) <= 2f,
            "Expected toolbar horizontal center near ${hostCenterX}dp, got $barCenterX"
        )

        val controlTags = listOf(
            "toolbar_add_group",
            "toolbar_add_label",
            "toolbar_paint_tool",
            "toolbar_color_swatch",
            "toolbar_eyedropper_tool",
            "toolbar_wash_tool",
            "toolbar_spline_settings",
            "toolbar_advanced_connection_mode",
            "toolbar_hide_connection_points",
            "flow_controls_info_button",
            "toolbar_export_image",
            "zoom_controls"
        )

        val referenceBounds = onNodeWithTag("zoom_controls", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val referenceCenterY = (referenceBounds.top.value + referenceBounds.bottom.value) / 2f

        controlTags.forEach { tag ->
            val bounds = onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()
            val centerY = (bounds.top.value + bounds.bottom.value) / 2f
            assertTrue(
                abs(centerY - referenceCenterY) <= 2f,
                "Expected control '$tag' centerY ($centerY) to match zoom_controls centerY ($referenceCenterY) within 2dp"
            )
        }
    }

    @Test
    fun toolbarTogglesAndAnchoredPopoversWorkCleanly() = runComposeUiTest {
        var paintActive by mutableStateOf(false)
        var washActive by mutableStateOf(false)
        var eyedropperActive by mutableStateOf(false)
        var advancedMode by mutableStateOf(false)
        var hidePorts by mutableStateOf(false)
        var selectedStyle by mutableStateOf(ConnectionCurveStyle.CardinalSpline)

        setContent {
            AppTheme(appearance = AppearanceSettings()) {
                Box(modifier = Modifier.size(1200.dp, 600.dp)) {
                    Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                        FlowFloatingAppBar(
                            scale = 1.0f,
                            isReadOnly = false,
                            isPaintToolActive = paintActive,
                            isWashToolActive = washActive,
                            isEyedropperActive = eyedropperActive,
                            isAdvancedConnectionMode = advancedMode,
                            hideConnectionPointsUnlessHovered = hidePorts,
                            activePaintColor = "#4CAF50",
                            colorsInFlow = listOf("#4CAF50"),
                            connectionStyle = selectedStyle,
                            connectionRoundness = 0.5f,
                            onTogglePaintTool = { paintActive = !paintActive },
                            onToggleWashTool = { washActive = !washActive },
                            onToggleEyedropper = { eyedropperActive = !eyedropperActive },
                            onToggleAdvancedConnectionMode = { advancedMode = !advancedMode },
                            onToggleHideConnectionPoints = { hidePorts = !hidePorts },
                            onSelectPaintColor = {},
                            onAddGroup = {},
                            onAddLabel = {},
                            onChangeConnectionStyle = { selectedStyle = it },
                            onChangeConnectionRoundness = {},
                            onZoomIn = {},
                            onZoomOut = {},
                            onExportImage = {}
                        )
                    }
                }
            }
        }

        waitForIdle()

        onNodeWithTag("toolbar_paint_tool").performClick()
        onNodeWithTag("toolbar_wash_tool").performClick()
        onNodeWithTag("toolbar_eyedropper_tool").performClick()
        onNodeWithTag("toolbar_advanced_connection_mode").performClick()
        onNodeWithTag("toolbar_hide_connection_points").performClick()
        waitForIdle()

        assertTrue(paintActive)
        assertTrue(washActive)
        assertTrue(eyedropperActive)
        assertTrue(advancedMode)
        assertTrue(hidePorts)

        // Open Quick Palette popover on click
        onNodeWithTag("toolbar_color_swatch").performClick()
        waitForIdle()
        onNodeWithTag("quick_palette_card").assertIsDisplayed()

        // First outside click dismisses the focusable Quick Palette popup; clicking spline button opens SplineSettingsCard
        onNodeWithTag("toolbar_spline_settings").performClick()
        waitForIdle()
        onNodeWithTag("quick_palette_card").assertDoesNotExist()
        onNodeWithTag("toolbar_spline_settings").performClick()
        waitForIdle()
        onNodeWithTag("spline_settings_card").assertIsDisplayed()
        val splineBounds = onNodeWithTag("spline_settings_card").getUnclippedBoundsInRoot()
        val splineWidth = splineBounds.right.value - splineBounds.left.value
        assertTrue(
            splineWidth in 280f..360f,
            "Expected SplineSettingsCard to be compactly bounded (<= 360dp), got $splineWidth"
        )
        assertEquals(ConnectionCurveStyle.CardinalSpline, selectedStyle)
    }

    @Test
    fun flowEditorTopBarCentersAtTopAndMatchesToolbarStyle() = runComposeUiTest {
        setContent {
            AppTheme(appearance = AppearanceSettings()) {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    Box(modifier = Modifier.fillMaxSize().testTag("editor_canvas_host")) {
                        FlowEditorTopBar(
                            flowName = "Sample Pipeline",
                            isReadOnly = false,
                            readOnlyReasons = emptyList(),
                            hasUnsavedChanges = true,
                            onExportImage = {},
                            onSave = {},
                            onExit = {},
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }
                }
            }
        }

        waitForIdle()
        val hostBounds = onNodeWithTag("editor_canvas_host").getUnclippedBoundsInRoot()
        val hostCenterX = (hostBounds.left.value + hostBounds.right.value) / 2f
        val topBarBounds = onNodeWithTag("flow_editor_top_bar").getUnclippedBoundsInRoot()
        val topBarCenterX = (topBarBounds.left.value + topBarBounds.right.value) / 2f
        assertTrue(
            abs(topBarCenterX - hostCenterX) <= 2f,
            "Expected FlowEditorTopBar horizontal center near ${hostCenterX}dp, got $topBarCenterX"
        )
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun toolbarTooltipsAreDelayedAndNotInstant() = runComposeUiTest {
        mainClock.autoAdvance = false

        setContent {
            AppTheme(appearance = AppearanceSettings()) {
                Box(modifier = Modifier.size(1200.dp, 600.dp)) {
                    Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                        FlowFloatingAppBar(
                            scale = 1.0f,
                            isReadOnly = false,
                            isPaintToolActive = false,
                            isWashToolActive = false,
                            activePaintColor = "#4CAF50",
                            connectionStyle = ConnectionCurveStyle.CardinalSpline,
                            connectionRoundness = 0.5f,
                            onTogglePaintTool = {},
                            onToggleWashTool = {},
                            onSelectPaintColor = {},
                            onAddGroup = {},
                            onAddLabel = {},
                            onChangeConnectionStyle = {},
                            onChangeConnectionRoundness = {},
                            onZoomIn = {},
                            onZoomOut = {},
                            onExportImage = {}
                        )
                    }
                }
            }
        }

        mainClock.advanceTimeByFrame()

        // Hover over Color Swatch and Info button: neither should flash an instant popover at 0ms
        onNodeWithTag("toolbar_color_swatch").performMouseInput { enter(center) }
        mainClock.advanceTimeBy(100)
        onNodeWithTag("quick_palette_card").assertDoesNotExist()

        onNodeWithTag("flow_controls_info_button").performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(100)
        onNodeWithTag("quick_palette_card").assertDoesNotExist()
    }
}
