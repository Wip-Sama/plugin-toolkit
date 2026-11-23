package org.wip.plugintoolkit.features.plugin.sdui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.junit.Rule
import org.wip.plugintoolkit.api.sdui.BadgeNode
import org.wip.plugintoolkit.api.sdui.ColumnNode
import org.wip.plugintoolkit.api.sdui.FlowRowNode
import org.wip.plugintoolkit.api.sdui.KeyValueEntry
import org.wip.plugintoolkit.api.sdui.KeyValueListNode
import org.wip.plugintoolkit.api.sdui.MarkdownTextNode
import org.wip.plugintoolkit.api.sdui.MultiSelectDropdownNode
import org.wip.plugintoolkit.api.sdui.RangeSliderNode
import org.wip.plugintoolkit.api.sdui.SemanticColor
import org.wip.plugintoolkit.api.sdui.SpacingSize
import org.wip.plugintoolkit.api.sdui.TableColumn
import org.wip.plugintoolkit.api.sdui.TableNode
import org.wip.plugintoolkit.api.sdui.TableRow
import org.wip.plugintoolkit.api.sdui.UiEvent
import org.wip.plugintoolkit.core.theme.AppTheme
import org.wip.plugintoolkit.features.settings.model.AppearanceSettings
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.sdui_kv_copied
import plugintoolkit.composeapp.generated.resources.sdui_kv_copy_value
import plugintoolkit.composeapp.generated.resources.sdui_multi_select_all
import plugintoolkit.composeapp.generated.resources.sdui_multi_select_clear
import plugintoolkit.composeapp.generated.resources.sdui_multi_select_filter_placeholder
import plugintoolkit.composeapp.generated.resources.sdui_multi_select_placeholder
import plugintoolkit.composeapp.generated.resources.sdui_table_empty
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ToolkitDataTableAndRichControlsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun tableRendersRowsEmitsSortAndRowClickEvents() {
        val events = mutableListOf<UiEvent>()
        val root = ColumnNode(
            id = "table_host",
            children = listOf(
                TableNode(
                    id = "chapters_table",
                    sortColumnKey = "pages",
                    sortAscending = true,
                    action = "select_chapter",
                    columns = listOf(
                        TableColumn(key = "title", title = "Chapter Title", weight = 1.5f, sortable = true),
                        TableColumn(key = "pages", title = "Pages", widthDp = 96, sortable = true),
                        TableColumn(key = "state", title = "Status", widthDp = 120)
                    ),
                    rows = listOf(
                        TableRow(
                            key = "ch_1",
                            cells = mapOf("title" to "Prologue", "pages" to "24", "state" to "Synced"),
                            badgeColumnKey = "state",
                            badgeColor = SemanticColor.SUCCESS
                        ),
                        TableRow(
                            key = "ch_2",
                            cells = mapOf("title" to "Awakening", "pages" to "32", "state" to "Queued"),
                            badgeColumnKey = "state",
                            badgeColor = SemanticColor.WARNING
                        )
                    )
                )
            )
        )

        composeTestRule.setContent {
            AppTheme(appearance = AppearanceSettings()) {
                Box(modifier = Modifier.size(720.dp, 420.dp)) {
                    RenderUiNode(node = root, onEvent = events::add)
                }
            }
        }

        composeTestRule.onNodeWithText("Chapter Title").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithText("Pages").performClick()
        composeTestRule.onNodeWithText("Awakening").performClick()

        val sortEvents = events.filterIsInstance<UiEvent.TableSorted>()
        assertEquals(2, sortEvents.size)
        assertEquals(UiEvent.TableSorted("chapters_table", "title", true), sortEvents[0])
        assertEquals(UiEvent.TableSorted("chapters_table", "pages", false), sortEvents[1])

        val clickEvent = assertIs<UiEvent.Click>(events.last())
        assertEquals("chapters_table", clickEvent.nodeId)
        assertEquals("select_chapter:ch_2", clickEvent.action)
    }

    @Test
    fun emptyTableDisplaysLocalizedPlaceholderAndFlowRowWrapsChildren() {
        var emptyLabel = ""
        val root = ColumnNode(
            id = "empty_flow_host",
            spacing = SpacingSize.SMALL,
            children = listOf(
                TableNode(
                    id = "empty_table",
                    columns = listOf(TableColumn(key = "name", title = "Name")),
                    rows = emptyList()
                ),
                FlowRowNode(
                    id = "tag_flow",
                    children = listOf(
                        BadgeNode(id = "t1", text = "Action", color = SemanticColor.PRIMARY),
                        BadgeNode(id = "t2", text = "Drama", color = SemanticColor.SECONDARY),
                        BadgeNode(id = "t3", text = "Verified", color = SemanticColor.SUCCESS)
                    )
                )
            )
        )

        composeTestRule.setContent {
            AppTheme(appearance = AppearanceSettings()) {
                emptyLabel = stringResource(Res.string.sdui_table_empty)
                Box(modifier = Modifier.size(600.dp, 360.dp)) {
                    RenderUiNode(node = root, onEvent = {})
                }
            }
        }

        composeTestRule.onNodeWithText(emptyLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText("Action").assertIsDisplayed()
        composeTestRule.onNodeWithText("Drama").assertIsDisplayed()
        composeTestRule.onNodeWithText("Verified").assertIsDisplayed()
    }

    @Test
    fun keyValueListMarkdownTextAndRangeSliderRenderCleanly() {
        var copyContentDesc = ""
        var copiedContentDesc = ""
        val events = mutableListOf<UiEvent>()

        val root = ColumnNode(
            id = "rich_host",
            spacing = SpacingSize.SMALL,
            children = listOf(
                KeyValueListNode(
                    id = "meta_list",
                    title = "Build Details",
                    entries = listOf(
                        KeyValueEntry(key = "sha", label = "Checksum", value = "deadbeef01", monospace = true, copyable = true),
                        KeyValueEntry(key = "status", label = "Status", value = "Published", badgeColor = SemanticColor.SUCCESS)
                    )
                ),
                MarkdownTextNode(
                    id = "md_notes",
                    markdown = "## Release Overview\n- **Fast** processing with `webp`\n- Read [docs](https://example.org/docs)",
                    linkAction = "open_docs"
                ),
                RangeSliderNode(
                    id = "chapter_window",
                    startValue = 2.0,
                    endValue = 18.0,
                    min = 1.0,
                    max = 50.0,
                    label = "Batch Range",
                    unitSuffix = "ch"
                )
            )
        )

        composeTestRule.setContent {
            AppTheme(appearance = AppearanceSettings()) {
                copyContentDesc = stringResource(Res.string.sdui_kv_copy_value)
                copiedContentDesc = stringResource(Res.string.sdui_kv_copied)
                Box(modifier = Modifier.size(720.dp, 540.dp)) {
                    RenderUiNode(node = root, onEvent = events::add)
                }
            }
        }

        composeTestRule.onNodeWithText("Build Details").assertIsDisplayed()
        composeTestRule.onNodeWithText("deadbeef01").assertIsDisplayed()
        composeTestRule.onNodeWithText("Published").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(copyContentDesc).performClick()
        composeTestRule.onNodeWithContentDescription(copiedContentDesc).assertIsDisplayed()

        composeTestRule.onNodeWithText("Batch Range").assertIsDisplayed()
        composeTestRule.onNodeWithText("2 – 18 ch").assertIsDisplayed()
        composeTestRule.onNodeWithText("Release Overview", substring = true).assertIsDisplayed()
        assertTrue(events.isEmpty())
    }

    @Test
    fun multiSelectDropdownFiltersSelectsAllAndClears() {
        var placeholder = ""
        var filterPlaceholder = ""
        var selectAllText = ""
        var clearText = ""
        val events = mutableListOf<UiEvent>()

        val root = ColumnNode(
            id = "multi_host",
            children = listOf(
                MultiSelectDropdownNode(
                    id = "format_selector",
                    options = mapOf(
                        "webp" to "WebP Lossless",
                        "png" to "PNG Standard",
                        "avif" to "AVIF Compact"
                    ),
                    selectedKeys = setOf("webp"),
                    label = "Output Formats",
                    searchable = true,
                    showSelectAll = true
                )
            )
        )

        composeTestRule.setContent {
            AppTheme(appearance = AppearanceSettings()) {
                placeholder = stringResource(Res.string.sdui_multi_select_placeholder)
                filterPlaceholder = stringResource(Res.string.sdui_multi_select_filter_placeholder)
                selectAllText = stringResource(Res.string.sdui_multi_select_all)
                clearText = stringResource(Res.string.sdui_multi_select_clear)
                Box(modifier = Modifier.size(640.dp, 480.dp)) {
                    RenderUiNode(node = root, onEvent = events::add)
                }
            }
        }

        composeTestRule.onNodeWithText("Output Formats").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithText(filterPlaceholder).performTextInput("AVIF")
        composeTestRule.onNodeWithText("AVIF Compact").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("PNG Standard").fetchSemanticsNodes().let {
            assertEquals(0, it.size, "Filtered menu should hide non-matching unselected options")
        }

        composeTestRule.onNodeWithText("AVIF").performTextReplacement("")
        composeTestRule.onNodeWithText(selectAllText).performClick()
        assertEquals(setOf("webp", "png", "avif"), assertIs<UiEvent.MultiOptionSelected>(events.last()).selectedKeys)

        composeTestRule.onNodeWithText(clearText).performClick()
        assertEquals(emptySet(), assertIs<UiEvent.MultiOptionSelected>(events.last()).selectedKeys)
        composeTestRule.onNodeWithText(placeholder).assertIsDisplayed()
    }
}
