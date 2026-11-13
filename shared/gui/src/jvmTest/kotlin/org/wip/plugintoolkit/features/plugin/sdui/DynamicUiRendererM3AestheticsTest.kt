package org.wip.plugintoolkit.features.plugin.sdui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.wip.plugintoolkit.api.sdui.BoardLayer
import org.wip.plugintoolkit.api.sdui.BoardNode
import org.wip.plugintoolkit.api.sdui.BoardTool
import org.wip.plugintoolkit.api.sdui.BoardViewportConfig
import org.wip.plugintoolkit.api.sdui.ButtonNode
import org.wip.plugintoolkit.api.sdui.ColumnNode
import org.wip.plugintoolkit.api.sdui.FloatingAnchor
import org.wip.plugintoolkit.api.sdui.FloatingPanelNode
import org.wip.plugintoolkit.api.sdui.IconButtonNode
import org.wip.plugintoolkit.api.sdui.ListItemNode
import org.wip.plugintoolkit.api.sdui.NormalizedPoint
import org.wip.plugintoolkit.api.sdui.NormalizedSize
import org.wip.plugintoolkit.api.sdui.RowNode
import org.wip.plugintoolkit.api.sdui.ScaffoldNode
import org.wip.plugintoolkit.api.sdui.SemanticColor
import org.wip.plugintoolkit.api.sdui.SidebarAttachPoint
import org.wip.plugintoolkit.api.sdui.SidebarNode
import org.wip.plugintoolkit.api.sdui.SpacingSize
import org.wip.plugintoolkit.api.sdui.SplitPaneNode
import org.wip.plugintoolkit.api.sdui.TextInputNode
import org.wip.plugintoolkit.api.sdui.TextNode
import org.wip.plugintoolkit.api.sdui.TypographyRole
import org.wip.plugintoolkit.api.sdui.UiEvent
import org.wip.plugintoolkit.api.sdui.UiModifier
import org.wip.plugintoolkit.core.theme.AppTheme
import org.wip.plugintoolkit.features.settings.model.AppearanceSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DynamicUiRendererM3AestheticsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testHostEnforcesMinimumSpacingWhenPluginPassesNone() {
        val treeRoot = ColumnNode(
            id = "col_no_spacing",
            spacing = SpacingSize.NONE,
            children = listOf(
                ButtonNode(id = "btn_first", label = "First Action"),
                ButtonNode(id = "btn_second", label = "Second Action"),
                TextInputNode(id = "field_first", label = "Field One"),
                TextInputNode(id = "field_second", label = "Field Two")
            )
        )

        composeTestRule.setContent {
            AppTheme(appearance = AppearanceSettings()) {
                Box(modifier = Modifier.size(width = 800.dp, height = 600.dp)) {
                    SafeUiNodeRenderer(node = treeRoot, onEvent = {})
                }
            }
        }
        composeTestRule.waitForIdle()

        val btn1Bounds = composeTestRule.onNodeWithText("First Action").getUnclippedBoundsInRoot()
        val btn2Bounds = composeTestRule.onNodeWithText("Second Action").getUnclippedBoundsInRoot()
        val inputGap = composeTestRule.onNodeWithText("Field Two").getUnclippedBoundsInRoot().top -
            composeTestRule.onNodeWithText("Field One").getUnclippedBoundsInRoot().bottom

        val buttonVerticalGap = btn2Bounds.top - btn1Bounds.bottom
        assertTrue(
            buttonVerticalGap >= 7.dp,
            "Host must enforce M3 minimum spacing even with SpacingSize.NONE; actual gap=$buttonVerticalGap"
        )
        assertTrue(
            inputGap >= 11.dp,
            "Block input fields must receive >= 12dp M3 spacing; actual gap=$inputGap"
        )
    }

    @Test
    fun testSpacingSizeMapsToMaterial3EightDpGrid() {
        val rowSmall = RowNode(
            id = "row_small",
            spacing = SpacingSize.SMALL,
            children = listOf(
                ButtonNode(id = "s1", label = "S1"),
                ButtonNode(id = "s2", label = "S2")
            )
        )
        val rowMedium = RowNode(
            id = "row_medium",
            spacing = SpacingSize.MEDIUM,
            children = listOf(
                ButtonNode(id = "m1", label = "M1"),
                ButtonNode(id = "m2", label = "M2")
            )
        )

        composeTestRule.setContent {
            AppTheme(appearance = AppearanceSettings()) {
                Box(modifier = Modifier.size(width = 800.dp, height = 400.dp)) {
                    Column {
                        SafeUiNodeRenderer(node = rowSmall, onEvent = {})
                        SafeUiNodeRenderer(node = rowMedium, onEvent = {})
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        val s1Bounds = composeTestRule.onNodeWithText("S1").getUnclippedBoundsInRoot()
        val s2Bounds = composeTestRule.onNodeWithText("S2").getUnclippedBoundsInRoot()
        val m1Bounds = composeTestRule.onNodeWithText("M1").getUnclippedBoundsInRoot()
        val m2Bounds = composeTestRule.onNodeWithText("M2").getUnclippedBoundsInRoot()

        val smallGap = s2Bounds.left - s1Bounds.right
        val mediumGap = m2Bounds.left - m1Bounds.right
        assertTrue(smallGap >= 7.5.dp, "SpacingSize.SMALL must map to >= 8dp; actual=$smallGap")
        assertTrue(mediumGap >= 15.dp, "SpacingSize.MEDIUM must map to >= 16dp; actual=$mediumGap")
        assertTrue(mediumGap > smallGap, "MEDIUM ($mediumGap) must strictly exceed SMALL ($smallGap)")
    }

    @Test
    fun testFloatingPanelStaysScopedInsideWorkspaceWithoutCoveringTopBarOrSidebar() {
        val dispatched = mutableListOf<UiEvent>()
        val scaffold = ScaffoldNode(
            id = "test_scaffold",
            topBar = RowNode(
                id = "top_row",
                spacing = SpacingSize.SMALL,
                children = listOf(
                    TextNode(id = "top_title", text = "Page 01 Compositor", modifier = UiModifier.weight(1f)),
                    ButtonNode(id = "save_btn", label = "Save & Render Page", action = "export")
                )
            ),
            content = BoardNode(
                id = "canvas_board",
                viewportConfig = BoardViewportConfig(),
                showToolbar = true,
                showZoomControls = true,
                modifier = UiModifier.fillMaxSize()
            ),
            rightSidebar = SidebarNode(
                id = "right_layers",
                attachPoint = SidebarAttachPoint.RIGHT,
                title = "Layers & Export",
                isCollapsible = true,
                isCollapsed = false,
                modifier = UiModifier.width(320),
                children = listOf(
                    TextNode(id = "layers_hdr", text = "Board Layers (1)")
                )
            ),
            floatingPanels = listOf(
                FloatingPanelNode(
                    id = "controls_panel",
                    anchor = FloatingAnchor.TOP_END,
                    title = "Board Controls",
                    isCollapsible = true,
                    isCollapsed = false,
                    children = listOf(
                        TextInputNode(id = "board_title_field", label = "Board Title", text = "Demo")
                    )
                )
            )
        )

        composeTestRule.setContent {
            AppTheme(appearance = AppearanceSettings()) {
                Box(modifier = Modifier.size(width = 1100.dp, height = 720.dp)) {
                    SafeUiNodeRenderer(node = scaffold, onEvent = { dispatched.add(it) })
                }
            }
        }
        composeTestRule.waitForIdle()

        val saveBtnBounds = composeTestRule.onNodeWithText("Save & Render Page").getUnclippedBoundsInRoot()
        val panelTitleBounds = composeTestRule.onNodeWithText("Board Controls").getUnclippedBoundsInRoot()
        val panelFieldBounds = composeTestRule.onNodeWithText("Board Title").getUnclippedBoundsInRoot()
        val sidebarTitleBounds = composeTestRule.onNodeWithText("Layers & Export").getUnclippedBoundsInRoot()

        assertTrue(
            panelTitleBounds.top > saveBtnBounds.bottom,
            "FloatingPanel top (${panelTitleBounds.top}) must be cleanly below topBar bottom (${saveBtnBounds.bottom})"
        )
        assertTrue(
            panelFieldBounds.right < sidebarTitleBounds.left,
            "FloatingPanel right (${panelFieldBounds.right}) must not overlap rightSidebar left (${sidebarTitleBounds.left})"
        )

        composeTestRule.onNodeWithText("Save & Render Page").performClick()
        assertEquals(listOf<UiEvent>(UiEvent.Click("save_btn", "export")), dispatched)
    }

    @Test
    fun testRightSidebarHonorsWidthAndListItemPreventsVerticalTextCrushing() {
        val clickedActions = mutableListOf<String>()
        val sidebar = SidebarNode(
            id = "inspectorSidebar",
            attachPoint = SidebarAttachPoint.RIGHT,
            title = "Layers & Export",
            isCollapsible = true,
            isCollapsed = false,
            modifier = UiModifier.width(320),
            children = listOf(
                ListItemNode(
                    id = "layer_callout",
                    title = "Class-S Spatial Gate Opening!",
                    subtitle = "callout_1 • z=1",
                    leadingIcon = "chat_bubble",
                    selected = true,
                    trailingContent = listOf(
                        IconButtonNode(id = "sel_btn", iconName = "select", action = "sel", tooltip = "Select layer"),
                        IconButtonNode(id = "vis_btn", iconName = "visibility", action = "vis", tooltip = "Toggle visibility"),
                        IconButtonNode(id = "lock_btn", iconName = "lock", action = "lock", tooltip = "Toggle lock"),
                        IconButtonNode(
                            id = "del_btn",
                            iconName = "delete",
                            action = "del",
                            tint = SemanticColor.ERROR,
                            tooltip = "Delete layer"
                        )
                    )
                )
            )
        )

        composeTestRule.setContent {
            AppTheme(appearance = AppearanceSettings()) {
                Box(modifier = Modifier.size(width = 800.dp, height = 600.dp)) {
                    SafeUiNodeRenderer(
                        node = sidebar,
                        onEvent = { event ->
                            if (event is UiEvent.Click && event.action != null) {
                                clickedActions.add(event.action!!)
                            }
                        }
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        val titleBounds = composeTestRule.onNodeWithText("Class-S Spatial Gate Opening!").getUnclippedBoundsInRoot()
        val titleWidth = titleBounds.right - titleBounds.left
        val titleHeight = titleBounds.bottom - titleBounds.top
        assertTrue(
            titleWidth >= 150.dp,
            "List item title must preserve generous horizontal width without character wrapping; actual=$titleWidth"
        )
        assertTrue(
            titleHeight <= 45.dp,
            "List item title must fit within 1-2 lines instead of wrapping vertically; actual=$titleHeight"
        )

        composeTestRule.onNodeWithContentDescription("Select layer").performClick()
        composeTestRule.onNodeWithContentDescription("Toggle visibility").performClick()
        composeTestRule.onNodeWithContentDescription("Toggle lock").performClick()
        composeTestRule.onNodeWithContentDescription("Delete layer").performClick()
        assertEquals(listOf("sel", "vis", "lock", "del"), clickedActions)
    }

    @Test
    fun testTextInputWithoutExplicitWidthRespectsDesktopFormCap() {
        val wideForm = ColumnNode(
            id = "wide_form",
            children = listOf(
                TextInputNode(id = "wide_input", label = "Wide Field", text = "Example")
            )
        )

        composeTestRule.setContent {
            AppTheme(appearance = AppearanceSettings()) {
                Box(modifier = Modifier.size(width = 1000.dp, height = 300.dp)) {
                    SafeUiNodeRenderer(node = wideForm, onEvent = {})
                }
            }
        }
        composeTestRule.waitForIdle()

        val fieldBounds = composeTestRule.onNodeWithText("Example").getUnclippedBoundsInRoot()
        val fieldWidth = fieldBounds.right - fieldBounds.left
        assertTrue(
            fieldWidth in 500.dp..685.dp,
            "Desktop unconstrained form input should fill up to 680dp M3 cap; actual=$fieldWidth"
        )
    }

    @Test
    fun testSplitPaneSeparatesPanesWithMaterial3Gutter() {
        val split = SplitPaneNode(
            id = "master_detail",
            splitFraction = 0.5f,
            isHorizontal = true,
            first = TextNode(id = "left_txt", text = "LeftMasterPane"),
            second = TextNode(id = "right_txt", text = "RightDetailPane")
        )

        composeTestRule.setContent {
            AppTheme(appearance = AppearanceSettings()) {
                Box(modifier = Modifier.size(width = 900.dp, height = 400.dp)) {
                    SafeUiNodeRenderer(node = split, onEvent = {})
                }
            }
        }
        composeTestRule.waitForIdle()

        val leftBounds = composeTestRule.onNodeWithText("LeftMasterPane").getUnclippedBoundsInRoot()
        val rightBounds = composeTestRule.onNodeWithText("RightDetailPane").getUnclippedBoundsInRoot()
        assertTrue(
            rightBounds.left - leftBounds.right >= 16.dp,
            "Split panes must separate content with >= 16dp M3 gutter & inner padding"
        )
    }

    @Test
    fun testCompactSpatialWidgetPreservesExtraSmallSpacingInsideBoard() {
        val clicks = mutableListOf<UiEvent>()
        val board = BoardNode(
            id = "board_with_widget",
            viewportConfig = BoardViewportConfig(),
            activeTool = BoardTool.Select(),
            showToolbar = true,
            showZoomControls = true,
            modifier = UiModifier.fillMaxSize(),
            layers = listOf(
                BoardLayer.ComponentLayer(
                    id = "qc_widget",
                    position = NormalizedPoint(0.08f, 0.55f),
                    size = NormalizedSize(0.38f, 0.22f),
                    content = ColumnNode(
                        id = "qc_col",
                        spacing = SpacingSize.EXTRA_SMALL,
                        children = listOf(
                            TextNode(id = "qc_title", text = "Page QC Stamp", role = TypographyRole.TITLE_MEDIUM),
                            ButtonNode(id = "qc_btn", label = "Approve Page (0)", action = "approve")
                        )
                    )
                )
            )
        )

        composeTestRule.setContent {
            AppTheme(appearance = AppearanceSettings()) {
                Box(modifier = Modifier.size(width = 960.dp, height = 720.dp)) {
                    SafeUiNodeRenderer(node = board, onEvent = { clicks.add(it) })
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Page QC Stamp").assertIsDisplayed()
        composeTestRule.onNodeWithText("Approve Page (0)").assertIsDisplayed().performClick()
        val qcTitleBounds = composeTestRule.onNodeWithText("Page QC Stamp").getUnclippedBoundsInRoot()
        val qcBtnBounds = composeTestRule.onNodeWithText("Approve Page (0)").getUnclippedBoundsInRoot()
        val compactGap = qcBtnBounds.top - qcTitleBounds.bottom
        assertTrue(
            compactGap in 3.dp..5.dp,
            "Compact spatial widget should preserve 4dp EXTRA_SMALL spacing; actual=$compactGap"
        )
        assertEquals(listOf<UiEvent>(UiEvent.Click("qc_btn", "approve")), clicks)
    }
}
