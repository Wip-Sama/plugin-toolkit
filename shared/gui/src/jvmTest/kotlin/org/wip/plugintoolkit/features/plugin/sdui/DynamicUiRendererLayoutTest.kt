package org.wip.plugintoolkit.features.plugin.sdui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.wip.plugintoolkit.api.sdui.BoardLayer
import org.wip.plugintoolkit.api.sdui.BoardTool
import org.wip.plugintoolkit.api.sdui.FloatingAnchor
import org.wip.plugintoolkit.api.sdui.NormalizedPoint
import org.wip.plugintoolkit.api.sdui.NormalizedSize
import org.wip.plugintoolkit.api.sdui.SemanticColor
import org.wip.plugintoolkit.api.sdui.SpacingSize
import org.wip.plugintoolkit.api.sdui.TabItem
import org.wip.plugintoolkit.api.sdui.TypographyRole
import org.wip.plugintoolkit.api.sdui.UiModifier
import org.wip.plugintoolkit.api.sdui.uiTree
import org.wip.plugintoolkit.core.theme.AppTheme
import org.wip.plugintoolkit.features.settings.model.AppearanceSettings
import kotlin.test.Test

class DynamicUiRendererLayoutTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testBoardCompositorScaffoldWithScrollableColumnInRightSidebarDoesNotCrash() {
        val tree = uiTree {
            scaffold(id = "interactiveScaffold", modifier = UiModifier.fillMaxSize()) {
                topBar {
                    row(id = "topRow", spacing = SpacingSize.SMALL) {
                        text(id = "topTitle", text = "Compositor Top Title", role = TypographyRole.TITLE_MEDIUM)
                    }
                }
                content {
                    board(
                        id = "drawingBoard",
                        activeTool = BoardTool.Select(),
                        showToolbar = true,
                        showZoomControls = true,
                        modifier = UiModifier.fillMaxSize()
                    ) {
                        textLayer(
                            id = "text_1",
                            text = "Overlay Caption",
                            position = NormalizedPoint(0.1f, 0.2f),
                            color = SemanticColor.PRIMARY
                        )
                        componentLayer(
                            id = "inspector_widget",
                            position = NormalizedPoint(0.1f, 0.6f),
                            size = NormalizedSize(0.35f, 0.2f)
                        ) {
                            text(id = "widgetText", text = "QC Widget")
                        }
                    }
                }
                rightSidebar(
                    id = "inspectorSidebar",
                    title = "Layers & Inspector",
                    isCollapsible = true,
                    isCollapsed = false
                ) {
                    text(id = "sidebarHeader", text = "Board Layers (2)", role = TypographyRole.TITLE_MEDIUM)
                    scrollableColumn(
                        id = "layersListScroll",
                        spacing = SpacingSize.EXTRA_SMALL,
                        modifier = UiModifier.fillMaxWidth().weight(1f)
                    ) {
                        listItem(id = "layer_row_text_1", title = "Overlay Caption")
                        listItem(id = "layer_row_widget", title = "QC Widget")
                    }
                }
                floatingPanel(
                    id = "controlsPanel",
                    anchor = FloatingAnchor.TOP_END,
                    title = "Board Controls"
                ) {
                    button(id = "clearBtn", label = "Clear Board", action = "clear_canvas")
                }
            }
        }

        composeTestRule.setContent {
            AppTheme(appearance = AppearanceSettings()) {
                Box(modifier = Modifier.size(1200.dp, 800.dp)) {
                    SafeUiNodeRenderer(
                        node = tree.root,
                        onEvent = {},
                        modifier = Modifier.size(1200.dp, 800.dp)
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Compositor Top Title").assertIsDisplayed()
        composeTestRule.onNodeWithText("Board Layers (2)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Board Controls").assertIsDisplayed()
    }

    @Test
    fun testTabsWithSplitPaneAndScrollableColumnsRenderWithinBoundedHeight() {
        val tree = uiTree {
            scaffold(id = "pubScaffold", modifier = UiModifier.fillMaxSize()) {
                content {
                    column(id = "pubBody", spacing = SpacingSize.SMALL, modifier = UiModifier.fillMaxSize()) {
                        tabs(
                            id = "pubTabs",
                            selectedTabKey = "chapters",
                            tabs = listOf(
                                TabItem(key = "chapters", label = "Chapters", badgeText = "4"),
                                TabItem(key = "queue", label = "Queue")
                            ),
                            modifier = UiModifier.fillMaxWidth().weight(1f)
                        ) {
                            splitPane(
                                id = "pubSplit",
                                splitFraction = 0.5f,
                                isHorizontal = true,
                                modifier = UiModifier.fillMaxSize(),
                                first = {
                                    column(id = "leftCol", modifier = UiModifier.fillMaxSize()) {
                                        scrollableColumn(
                                            id = "leftScroll",
                                            modifier = UiModifier.fillMaxWidth().weight(1f)
                                        ) {
                                            listItem(id = "row_ch_1", title = "Ch. 41: Crimson Gate")
                                        }
                                    }
                                },
                                second = {
                                    scrollableColumn(
                                        id = "rightScroll",
                                        modifier = UiModifier.fillMaxSize()
                                    ) {
                                        card(id = "inspCard", title = "Chapter Inspector") {
                                            text(id = "inspText", text = "Revision 3")
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        composeTestRule.setContent {
            AppTheme(appearance = AppearanceSettings()) {
                Box(modifier = Modifier.size(1200.dp, 800.dp)) {
                    SafeUiNodeRenderer(
                        node = tree.root,
                        onEvent = {},
                        modifier = Modifier.size(1200.dp, 800.dp)
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Ch. 41: Crimson Gate").assertIsDisplayed()
        composeTestRule.onNodeWithText("Chapter Inspector").assertIsDisplayed()
    }

    @Test
    fun testNestedScrollableColumnInsideDialogDoesNotThrow() {
        val tree = uiTree {
            dialog(
                id = "scrollDialog",
                title = "Nested Scroll Guard",
                isOpen = true,
                confirmLabel = "OK"
            ) {
                scrollableColumn(id = "nestedScroll") {
                    text(id = "dialogBodyText", text = "Inside nested scrollable column")
                }
            }
        }

        composeTestRule.setContent {
            AppTheme(appearance = AppearanceSettings()) {
                SafeUiNodeRenderer(node = tree.root, onEvent = {})
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Inside nested scrollable column").assertIsDisplayed()
    }
}
