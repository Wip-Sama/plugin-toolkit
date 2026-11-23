package org.wip.plugintoolkit.features.plugin.sdui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.junit.Rule
import org.wip.plugintoolkit.api.sdui.AccordionNode
import org.wip.plugintoolkit.api.sdui.ActionMenuItem
import org.wip.plugintoolkit.api.sdui.AssetReference
import org.wip.plugintoolkit.api.sdui.CodeBlockNode
import org.wip.plugintoolkit.api.sdui.ColumnNode
import org.wip.plugintoolkit.api.sdui.ImageNode
import org.wip.plugintoolkit.api.sdui.NumberInputNode
import org.wip.plugintoolkit.api.sdui.RadioGroupNode
import org.wip.plugintoolkit.api.sdui.RadioOptionItem
import org.wip.plugintoolkit.api.sdui.SearchBarNode
import org.wip.plugintoolkit.api.sdui.SemanticColor
import org.wip.plugintoolkit.api.sdui.ShapeRole
import org.wip.plugintoolkit.api.sdui.TextNode
import org.wip.plugintoolkit.api.sdui.UiEvent
import org.wip.plugintoolkit.api.sdui.UiModifier
import org.wip.plugintoolkit.core.theme.AppTheme
import org.wip.plugintoolkit.features.settings.model.AppearanceSettings
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.sdui_accordion_expand
import plugintoolkit.composeapp.generated.resources.sdui_code_copied
import plugintoolkit.composeapp.generated.resources.sdui_code_copy
import plugintoolkit.composeapp.generated.resources.sdui_image_zoom_close
import plugintoolkit.composeapp.generated.resources.sdui_image_zoom_in
import plugintoolkit.composeapp.generated.resources.sdui_image_zoom_open
import plugintoolkit.composeapp.generated.resources.sdui_number_decrement
import plugintoolkit.composeapp.generated.resources.sdui_number_increment
import plugintoolkit.composeapp.generated.resources.sdui_search_clear
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SduiFormAndCodeControlsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun radioGroupHorizontalAndVerticalEmitsOptionSelectedAndRespectsDisabled() {
        val events = mutableListOf<UiEvent>()
        val treeRoot = ColumnNode(
            id = "col_radio",
            children = listOf(
                RadioGroupNode(
                    id = "rg_mode",
                    label = "Publish Mode",
                    selectedKey = "fast",
                    horizontal = false,
                    options = listOf(
                        RadioOptionItem(key = "fast", label = "Fast", subtitle = "Concurrent upload"),
                        RadioOptionItem(key = "verified", label = "Verified", subtitle = "Checksum check"),
                        RadioOptionItem(key = "archived", label = "Archived", enabled = false)
                    )
                )
            )
        )

        composeTestRule.setContent {
            AppTheme(appearance = AppearanceSettings()) {
                Box(modifier = Modifier.size(640.dp, 480.dp)) {
                    SafeUiNodeRenderer(node = treeRoot, onEvent = { events.add(it) })
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Publish Mode").assertIsDisplayed()
        composeTestRule.onNodeWithText("Checksum check").assertIsDisplayed()
        composeTestRule.onNodeWithText("Verified").performClick()
        assertTrue(events.contains(UiEvent.OptionSelected("rg_mode", "verified")))

        val eventCountBeforeDisabled = events.size
        composeTestRule.onNodeWithText("Archived").performClick()
        assertEquals(eventCountBeforeDisabled, events.size)
    }

    @Test
    fun numberInputStepperButtonsBoundsAndArrowKeysWork() {
        val events = mutableListOf<UiEvent>()
        val treeRoot = NumberInputNode(
            id = "num_workers",
            value = 2.0,
            min = 1.0,
            max = 3.0,
            step = 1.0,
            unitSuffix = "jobs",
            label = "Parallel Jobs"
        )
        var incrementDesc = ""
        var decrementDesc = ""

        composeTestRule.setContent {
            AppTheme(appearance = AppearanceSettings()) {
                incrementDesc = stringResource(Res.string.sdui_number_increment)
                decrementDesc = stringResource(Res.string.sdui_number_decrement)
                Box(modifier = Modifier.size(640.dp, 240.dp)) {
                    SafeUiNodeRenderer(node = treeRoot, onEvent = { events.add(it) })
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("jobs").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(incrementDesc).performClick()
        assertEquals(UiEvent.ValueChanged("num_workers", 3.0), events.last())
        composeTestRule.onNodeWithContentDescription(incrementDesc).assertIsNotEnabled()

        composeTestRule.onNodeWithContentDescription(decrementDesc).performClick()
        assertEquals(UiEvent.ValueChanged("num_workers", 2.0), events.last())

        composeTestRule.onNode(hasSetTextAction()).performClick()
        composeTestRule.onNode(hasSetTextAction()).performKeyInput { pressKey(Key.DirectionDown) }
        assertEquals(UiEvent.ValueChanged("num_workers", 1.0), events.last())
    }

    @Test
    fun searchBarClearAndEnterSubmitWork() {
        val events = mutableListOf<UiEvent>()
        val treeRoot = SearchBarNode(
            id = "sb_filter",
            query = "alpha",
            suggestions = listOf("alpha-01", "beta-02"),
            placeholder = "Search items...",
            action = "submit_search"
        )
        var clearDesc = ""

        composeTestRule.setContent {
            AppTheme(appearance = AppearanceSettings()) {
                clearDesc = stringResource(Res.string.sdui_search_clear)
                Box(modifier = Modifier.size(640.dp, 320.dp)) {
                    SafeUiNodeRenderer(node = treeRoot, onEvent = { events.add(it) })
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription(clearDesc).performClick()
        assertTrue(events.contains(UiEvent.TextChanged("sb_filter", "")))

        composeTestRule.onNode(hasSetTextAction()).performClick()
        composeTestRule.onNode(hasSetTextAction()).performTextInput("chapt")
        assertEquals(UiEvent.TextChanged("sb_filter", "chapt"), events.last())

        composeTestRule.onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Enter) }
        assertTrue(events.contains(UiEvent.Click("sb_filter", "submit_search")))
    }

    @Test
    fun accordionTogglesVisibilityAndEmitsToggleChanged() {
        val events = mutableListOf<UiEvent>()
        val treeRoot = AccordionNode(
            id = "acc_meta",
            title = "Webhook Diagnostics",
            subtitle = "Click to inspect",
            expanded = false,
            leadingIcon = "code",
            badgeText = "DEBUG",
            badgeColor = SemanticColor.INFO,
            children = listOf(
                TextNode(
                    id = "acc_child_txt",
                    text = "Endpoint healthy",
                    selectable = true,
                    modifier = UiModifier.cornerRadius(ShapeRole.SMALL)
                )
            )
        )
        var expandDesc = ""

        composeTestRule.setContent {
            AppTheme(appearance = AppearanceSettings()) {
                expandDesc = stringResource(Res.string.sdui_accordion_expand)
                Box(modifier = Modifier.size(640.dp, 320.dp)) {
                    SafeUiNodeRenderer(node = treeRoot, onEvent = { events.add(it) })
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Webhook Diagnostics").assertIsDisplayed()
        composeTestRule.onNodeWithText("Endpoint healthy").assertDoesNotExist()

        composeTestRule.onNodeWithContentDescription(expandDesc).performClick()
        composeTestRule.waitForIdle()

        assertEquals(UiEvent.ToggleChanged("acc_meta", true), events.single())
        composeTestRule.onNodeWithText("Endpoint healthy").assertIsDisplayed()
    }

    @Test
    fun codeBlockRendersLinesAndShowsCopiedFeedback() {
        val treeRoot = CodeBlockNode(
            id = "cb_json",
            code = "{\n  \"app\": \"4.1.0-alpha01\"\n}",
            language = "json",
            title = "version.json",
            showLineNumbers = true,
            showCopyButton = true
        )
        var copyDesc = ""
        var copiedDesc = ""

        composeTestRule.setContent {
            AppTheme(appearance = AppearanceSettings()) {
                copyDesc = stringResource(Res.string.sdui_code_copy)
                copiedDesc = stringResource(Res.string.sdui_code_copied)
                Box(modifier = Modifier.size(640.dp, 320.dp)) {
                    SafeUiNodeRenderer(node = treeRoot, onEvent = {})
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("version.json").assertIsDisplayed()
        composeTestRule.onNodeWithText("JSON").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(copyDesc).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription(copiedDesc).assertIsDisplayed()
    }

    @Test
    fun zoomableImageAndUniversalModifiersRenderAndOpenZoomDialog() {
        val treeRoot = ColumnNode(
            id = "col_media",
            children = listOf(
                TextNode(
                    id = "txt_decorated",
                    text = "Long selectable summary line that truncates nicely when constrained",
                    selectable = true,
                    maxLines = 1,
                    overflowEllipsis = true,
                    modifier = UiModifier
                        .tooltip("Helpful summary tooltip")
                        .cornerRadius(ShapeRole.MEDIUM)
                        .contextMenu(listOf(ActionMenuItem(key = "inspect", label = "Inspect Item")))
                ),
                ImageNode(
                    id = "img_preview",
                    asset = AssetReference.Relative("missing_preview.png"),
                    contentDescription = "Sample Cover",
                    zoomable = true,
                    modifier = UiModifier
                        .width(180)
                        .height(120)
                        .cornerRadius(ShapeRole.LARGE)
                )
            )
        )
        var zoomOpenDesc = ""
        var zoomInDesc = ""
        var zoomCloseDesc = ""

        composeTestRule.setContent {
            AppTheme(appearance = AppearanceSettings()) {
                zoomOpenDesc = stringResource(Res.string.sdui_image_zoom_open)
                zoomInDesc = stringResource(Res.string.sdui_image_zoom_in)
                zoomCloseDesc = stringResource(Res.string.sdui_image_zoom_close)
                Box(modifier = Modifier.size(720.dp, 540.dp)) {
                    SafeUiNodeRenderer(node = treeRoot, onEvent = {})
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription(zoomOpenDesc).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("100%").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(zoomInDesc).performClick()
        composeTestRule.onNodeWithText("125%").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(zoomCloseDesc).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription(zoomCloseDesc).assertDoesNotExist()
    }
}
