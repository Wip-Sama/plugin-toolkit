package org.wip.plugintoolkit.features.plugin.sdui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.junit.Rule
import org.wip.plugintoolkit.api.sdui.ActionMenuItem
import org.wip.plugintoolkit.api.sdui.BreadcrumbItem
import org.wip.plugintoolkit.api.sdui.BreadcrumbsNode
import org.wip.plugintoolkit.api.sdui.ColorPickerNode
import org.wip.plugintoolkit.api.sdui.ColumnNode
import org.wip.plugintoolkit.api.sdui.FilePickerMode
import org.wip.plugintoolkit.api.sdui.FilePickerNode
import org.wip.plugintoolkit.api.sdui.MenuNode
import org.wip.plugintoolkit.api.sdui.PaginationNode
import org.wip.plugintoolkit.api.sdui.ScrollEdge
import org.wip.plugintoolkit.api.sdui.ScrollableColumnNode
import org.wip.plugintoolkit.api.sdui.SpacingSize
import org.wip.plugintoolkit.api.sdui.StepStatus
import org.wip.plugintoolkit.api.sdui.StepperItem
import org.wip.plugintoolkit.api.sdui.StepperNode
import org.wip.plugintoolkit.api.sdui.TextNode
import org.wip.plugintoolkit.api.sdui.TreeNodeItem
import org.wip.plugintoolkit.api.sdui.TreeViewNode
import org.wip.plugintoolkit.api.sdui.UiEvent
import org.wip.plugintoolkit.api.sdui.UiModifier
import org.wip.plugintoolkit.core.theme.AppTheme
import org.wip.plugintoolkit.features.settings.model.AppearanceSettings
import org.wip.plugintoolkit.shared.components.ToolkitAccordion
import org.wip.plugintoolkit.shared.components.ToolkitConfirmDialog
import org.wip.plugintoolkit.shared.components.ToolkitEmptyState
import org.wip.plugintoolkit.shared.components.ToolkitSearchBar
import org.wip.plugintoolkit.shared.components.ToolkitSideSheet
import org.wip.plugintoolkit.shared.components.ToolkitStatusBadge
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.action_close
import plugintoolkit.composeapp.generated.resources.sdui_file_clear
import plugintoolkit.composeapp.generated.resources.sdui_pagination_next
import plugintoolkit.composeapp.generated.resources.sdui_pagination_of
import plugintoolkit.composeapp.generated.resources.sdui_pagination_page_size
import plugintoolkit.composeapp.generated.resources.sdui_tree_expand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ToolkitWorkflowAndControlsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun treeViewBreadcrumbsPaginationStepperAndMenuEmitEvents() {
        var expandDesc = ""
        var nextDesc = ""
        var pageLabel = ""
        var pageSizeLabel = ""
        val events = mutableListOf<UiEvent>()

        val root = ColumnNode(
            id = "wf_controls_col",
            spacing = SpacingSize.SMALL,
            children = listOf(
                BreadcrumbsNode(
                    id = "crumbs",
                    maxVisibleItems = 3,
                    items = listOf(
                        BreadcrumbItem(key = "home", label = "Workspace"),
                        BreadcrumbItem(key = "sub1", label = "Archive"),
                        BreadcrumbItem(key = "sub2", label = "Series"),
                        BreadcrumbItem(key = "ch_4", label = "Chapter 4")
                    )
                ),
                StepperNode(
                    id = "wizard_steps",
                    horizontal = true,
                    steps = listOf(
                        StepperItem(key = "s1", label = "Scan", status = StepStatus.COMPLETED),
                        StepperItem(key = "s2", label = "Review", status = StepStatus.ACTIVE),
                        StepperItem(key = "s3", label = "Publish", status = StepStatus.PENDING, enabled = false)
                    )
                ),
                TreeViewNode(
                    id = "repo_tree",
                    selectedKey = "leaf_a",
                    items = listOf(
                        TreeNodeItem(
                            key = "dir_1",
                            label = "Chapters",
                            expanded = false,
                            children = listOf(TreeNodeItem(key = "leaf_a", label = "Ch 01.webp"))
                        )
                    )
                ),
                PaginationNode(
                    id = "pager",
                    currentPage = 2,
                    totalPages = 5,
                    pageSize = 25,
                    pageSizeOptions = listOf(10, 25, 50)
                ),
                MenuNode(
                    id = "actions_menu",
                    triggerLabel = "More Actions",
                    items = listOf(
                        ActionMenuItem(key = "sync", label = "Force Sync", action = "force_sync")
                    )
                )
            )
        )

        composeTestRule.setContent {
            AppTheme(appearance = AppearanceSettings()) {
                expandDesc = stringResource(Res.string.sdui_tree_expand)
                nextDesc = stringResource(Res.string.sdui_pagination_next)
                pageLabel = stringResource(Res.string.sdui_pagination_of, 2, 5)
                pageSizeLabel = stringResource(Res.string.sdui_pagination_page_size, 50)
                Box(modifier = Modifier.size(760.dp, 620.dp)) {
                    RenderUiNode(node = root, onEvent = events::add)
                }
            }
        }

        composeTestRule.onNodeWithText("Workspace").performClick()
        assertEquals(UiEvent.OptionSelected("crumbs", "home"), events.last())

        composeTestRule.onNodeWithText("Scan").performClick()
        assertEquals(UiEvent.OptionSelected("wizard_steps", "s1"), events.last())

        composeTestRule.onNodeWithContentDescription(expandDesc).performClick()
        assertEquals(UiEvent.TreeNodeToggled("repo_tree", "dir_1", true), events.last())
        composeTestRule.onNodeWithText("Ch 01.webp").assertIsDisplayed().performClick()
        assertEquals(UiEvent.OptionSelected("repo_tree", "leaf_a"), events.last())

        composeTestRule.onNodeWithText(pageLabel).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(nextDesc).performClick()
        assertEquals(UiEvent.PageChanged("pager", 3, 25), events.last())

        composeTestRule.onNodeWithText(pageSizeLabel.replace("50", "25")).performClick()
        composeTestRule.onNodeWithText(pageSizeLabel).performClick()
        assertEquals(UiEvent.PageChanged("pager", 1, 50), events.last())

        composeTestRule.onNodeWithText("More Actions").performClick()
        composeTestRule.onNodeWithText("Force Sync").performClick()
        assertEquals(UiEvent.Click("actions_menu", "force_sync"), events.last())
    }

    @Test
    fun fileAndColorPickersAndScrollAnchoringWorkCleanly() {
        var clearPathDesc = ""
        val events = mutableListOf<UiEvent>()
        val items = (0..30).map { idx ->
            TextNode(id = "log_item_$idx", text = "Log entry #$idx")
        }

        val root = ColumnNode(
            id = "pickers_host",
            spacing = SpacingSize.SMALL,
            children = listOf(
                FilePickerNode(
                    id = "src_picker",
                    value = "C:/workspace/input.cbz",
                    paths = listOf("C:/workspace/input.cbz"),
                    mode = FilePickerMode.FILE,
                    label = "Source Archive",
                    action = "source_chosen"
                ),
                ColorPickerNode(
                    id = "theme_accent",
                    colorHex = "#6750A4",
                    label = "Theme Color",
                    presets = listOf("#6750A4", "#006A6A")
                ),
                ScrollableColumnNode(
                    id = "anchored_logs",
                    scrollToEdge = ScrollEdge.BOTTOM,
                    scrollRevision = 1,
                    modifier = UiModifier.fillMaxWidth().height(160),
                    children = items
                )
            )
        )

        composeTestRule.setContent {
            AppTheme(appearance = AppearanceSettings()) {
                clearPathDesc = stringResource(Res.string.sdui_file_clear)
                Box(modifier = Modifier.size(720.dp, 540.dp)) {
                    RenderUiNode(node = root, onEvent = events::add)
                }
            }
        }

        composeTestRule.onNodeWithText("Source Archive").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(clearPathDesc).performClick()
        assertEquals(
            UiEvent.FilesPicked("src_picker", "source_chosen", emptyList()),
            events.last()
        )

        composeTestRule.onNodeWithText("#6750A4").performTextReplacement("#112233")
        assertEquals(UiEvent.ColorChanged("theme_accent", "#112233"), events.last())

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Log entry #30").assertIsDisplayed()
    }

    @Test
    fun standardizedToolkitHostControlsRenderAndInteract() {
        var searchQuery by mutableStateOf("")
        var expanded by mutableStateOf(false)
        var dialogOpen by mutableStateOf(true)
        var sheetOpen by mutableStateOf(true)
        var confirmed by mutableStateOf(false)
        var closeLabel = ""

        composeTestRule.setContent {
            AppTheme(appearance = AppearanceSettings()) {
                closeLabel = stringResource(Res.string.action_close)
                Box(modifier = Modifier.size(920.dp, 640.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToolkitStatusBadge(text = "Healthy")
                        ToolkitSearchBar(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            placeholder = "Filter items",
                            resultCountText = "3"
                        )
                        ToolkitAccordion(
                            title = "Advanced Options",
                            expanded = expanded,
                            onExpandedChange = { expanded = it }
                        ) {
                            Text("Accordion Body Text")
                        }
                        ToolkitEmptyState(
                            title = "Nothing queued",
                            description = "Add an item to begin",
                            icon = Icons.Default.Info
                        )
                        ToolkitSideSheet(
                            isOpen = sheetOpen,
                            title = "Inspector Drawer",
                            onDismiss = { sheetOpen = false }
                        ) {
                            Text("Inspector Details")
                        }
                    }

                    ToolkitConfirmDialog(
                        isOpen = dialogOpen,
                        title = "Confirm Reset",
                        message = "Proceed with reset?",
                        confirmLabel = "Confirm Now",
                        dismissLabel = "Cancel Now",
                        onConfirm = {
                            confirmed = true
                            dialogOpen = false
                        },
                        onDismiss = { dialogOpen = false }
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Confirm Reset").assertIsDisplayed()
        composeTestRule.onNodeWithText("Confirm Now").performClick()
        assertTrue(confirmed)

        composeTestRule.onNodeWithText("Healthy").assertIsDisplayed()
        composeTestRule.onNodeWithText("Advanced Options").performClick()
        composeTestRule.onNodeWithText("Accordion Body Text").assertIsDisplayed()

        composeTestRule.onNodeWithText("Inspector Drawer").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(closeLabel).performClick()
        composeTestRule.onNodeWithText("Nothing queued").assertIsDisplayed()
    }
}
