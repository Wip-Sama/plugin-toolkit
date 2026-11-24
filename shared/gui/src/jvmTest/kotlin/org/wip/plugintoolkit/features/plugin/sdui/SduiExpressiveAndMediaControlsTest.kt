package org.wip.plugintoolkit.features.plugin.sdui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import org.jetbrains.compose.resources.stringResource
import org.junit.Rule
import org.wip.plugintoolkit.api.sdui.ActionMenuItem
import org.wip.plugintoolkit.api.sdui.AssetReference
import org.wip.plugintoolkit.api.sdui.ColumnNode
import org.wip.plugintoolkit.api.sdui.DatePickerNode
import org.wip.plugintoolkit.api.sdui.FabMenuNode
import org.wip.plugintoolkit.api.sdui.FabNode
import org.wip.plugintoolkit.api.sdui.FabSize
import org.wip.plugintoolkit.api.sdui.FloatingToolbarNode
import org.wip.plugintoolkit.api.sdui.ImageCompareNode
import org.wip.plugintoolkit.api.sdui.MediaKind
import org.wip.plugintoolkit.api.sdui.MediaPlayerNode
import org.wip.plugintoolkit.api.sdui.SideSheetAttachPoint
import org.wip.plugintoolkit.api.sdui.SideSheetNode
import org.wip.plugintoolkit.api.sdui.TextNode
import org.wip.plugintoolkit.api.sdui.TimePickerNode
import org.wip.plugintoolkit.api.sdui.ToolbarOrientation
import org.wip.plugintoolkit.api.sdui.UiEvent
import org.wip.plugintoolkit.core.theme.AppTheme
import org.wip.plugintoolkit.features.settings.model.AppearanceSettings
import org.wip.plugintoolkit.shared.components.ToolkitEmptyState
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.action_close
import plugintoolkit.composeapp.generated.resources.sdui_date_picker_confirm
import plugintoolkit.composeapp.generated.resources.sdui_image_compare_slider_desc
import plugintoolkit.composeapp.generated.resources.sdui_media_play
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SduiExpressiveAndMediaControlsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun floatingToolbarFabAndFabMenuEmitClickEvents() {
        val events = mutableListOf<UiEvent>()

        composeTestRule.setContent {
            AppTheme(appearance = AppearanceSettings()) {
                RenderUiNode(
                    node = ColumnNode(
                        id = "root",
                        children = listOf(
                            FloatingToolbarNode(
                                id = "tb_1",
                                orientation = ToolbarOrientation.HORIZONTAL,
                                vibrant = true,
                                expanded = true,
                                items = listOf(
                                    ActionMenuItem(key = "info", label = "InspectItem", action = "tb_inspect", iconName = "info")
                                ),
                                primaryAction = ActionMenuItem(
                                    key = "run",
                                    label = "PrimaryRun",
                                    action = "tb_primary_run",
                                    iconName = "play_arrow"
                                )
                            ),
                            FabNode(
                                id = "fab_ext",
                                iconName = "add",
                                action = "fab_create",
                                label = "NewRelease",
                                size = FabSize.EXTENDED
                            ),
                            FabMenuNode(
                                id = "fab_menu",
                                iconName = "bolt",
                                expanded = false,
                                label = "FabMenuTrigger",
                                items = listOf(
                                    ActionMenuItem(key = "quick", label = "QuickStage", action = "fab_menu_stage", iconName = "check")
                                )
                            )
                        )
                    ),
                    onEvent = { events.add(it) }
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("InspectItem").performClick()
        composeTestRule.onNodeWithContentDescription("PrimaryRun").performClick()
        composeTestRule.onNodeWithText("NewRelease", useUnmergedTree = true).performClick()
        composeTestRule.onNodeWithContentDescription("FabMenuTrigger").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("QuickStage").performClick()

        val clicks = events.filterIsInstance<UiEvent.Click>().map { it.nodeId to it.action }
        assertTrue(("tb_1" to "tb_inspect") in clicks)
        assertTrue(("tb_1" to "tb_primary_run") in clicks)
        assertTrue(("fab_ext" to "fab_create") in clicks)
        assertTrue(("fab_menu" to "fab_menu_stage") in clicks)
    }

    @Test
    fun sideSheetDateAndTimePickerRenderAndEmitEvents() {
        val events = mutableListOf<UiEvent>()
        var confirmLabel = ""
        var closeLabel = ""

        composeTestRule.setContent {
            AppTheme(appearance = AppearanceSettings()) {
                confirmLabel = stringResource(Res.string.sdui_date_picker_confirm)
                closeLabel = stringResource(Res.string.action_close)
                RenderUiNode(
                    node = ColumnNode(
                        id = "root",
                        children = listOf(
                            DatePickerNode(
                                id = "dp_1",
                                selectedDateIso = "2026-11-24",
                                label = "Release Date"
                            ),
                            TimePickerNode(
                                id = "tp_1",
                                hour = 10,
                                minute = 15,
                                inline = true,
                                label = "Release Time"
                            ),
                            SideSheetNode(
                                id = "sheet_1",
                                title = "Schedule Inspector",
                                isOpen = true,
                                attachPoint = SideSheetAttachPoint.END,
                                dismissAction = "close_inspector",
                                children = listOf(
                                    TextNode(id = "sheet_body", text = "Ready for publication")
                                )
                            )
                        )
                    ),
                    onEvent = { events.add(it) }
                )
            }
        }

        composeTestRule.onNodeWithText("Release Date").assertIsDisplayed()
        composeTestRule.onNodeWithText("2026-11-24").assertIsDisplayed()
        composeTestRule.onNodeWithText("Schedule Inspector").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ready for publication").assertIsDisplayed()

        composeTestRule.onNodeWithText(confirmLabel).performClick()
        composeTestRule.onNodeWithContentDescription(closeLabel).performClick()

        assertTrue(events.any { it is UiEvent.TextChanged && it.nodeId == "tp_1" && it.text == "10:15" })
        assertTrue(events.any { it is UiEvent.Click && it.nodeId == "sheet_1" && it.action == "close_inspector" })
    }

    @Test
    fun imageCompareAndMediaPlayerEmitEventsAndEmptyStateRenders() {
        val events = mutableListOf<UiEvent>()
        var playLabel = ""
        var compareSliderDesc = ""
        var emptyActionClicked = false

        val tinyPngDataUri =
            "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="

        composeTestRule.setContent {
            AppTheme(appearance = AppearanceSettings()) {
                playLabel = stringResource(Res.string.sdui_media_play)
                compareSliderDesc = stringResource(Res.string.sdui_image_compare_slider_desc)

                CompositionLocalProvider(LocalPluginAssetContext provides PluginAssetContext()) {
                    RenderUiNode(
                        node = ColumnNode(
                            id = "root",
                            children = listOf(
                                ImageCompareNode(
                                    id = "cmp_1",
                                    beforeAsset = AssetReference.Uri(tinyPngDataUri),
                                    afterAsset = AssetReference.Uri(tinyPngDataUri),
                                    beforeLabel = "RawInput",
                                    afterLabel = "Upscaled",
                                    splitPosition = 0.5f
                                ),
                                MediaPlayerNode(
                                    id = "mp_1",
                                    asset = AssetReference.Relative("missing.wav"),
                                    title = "Preview Clip",
                                    kind = MediaKind.AUDIO
                                )
                            )
                        ),
                        onEvent = { events.add(it) }
                    )
                }
                ToolkitEmptyState(
                    title = "Empty Workspace",
                    icon = Icons.Default.Inbox,
                    actionLabel = "Add Item",
                    onAction = { emptyActionClicked = true }
                )
            }
        }

        composeTestRule.onNodeWithText("RawInput").assertIsDisplayed()
        composeTestRule.onNodeWithText("Upscaled").assertIsDisplayed()
        composeTestRule.onNodeWithText("Preview Clip").assertIsDisplayed()

        val compareSlider = composeTestRule.onNodeWithContentDescription(compareSliderDesc)
        compareSlider.performSemanticsAction(SemanticsActions.RequestFocus)
        compareSlider.performKeyInput {
            pressKey(Key.DirectionRight)
        }
        composeTestRule.onNodeWithContentDescription(playLabel).performClick()
        composeTestRule.onNodeWithText("Add Item").performClick()

        assertTrue(events.any { it is UiEvent.ValueChanged && it.nodeId == "cmp_1" && it.value > 0.5 })
        assertTrue(events.any { it is UiEvent.Click && it.nodeId == "mp_1" && it.action == "play" })
        assertEquals(true, emptyActionClicked)
    }
}
