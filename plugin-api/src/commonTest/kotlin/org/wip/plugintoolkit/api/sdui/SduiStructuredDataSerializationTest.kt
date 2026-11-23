package org.wip.plugintoolkit.api.sdui

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SduiStructuredDataSerializationTest {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Test
    fun `structured data and rich control nodes round-trip through JSON`() {
        val tree = uiTree {
            table(
                id = "chapters_table",
                selectedRowKeys = setOf("ch_1"),
                sortColumnKey = "pages",
                sortAscending = false,
                maxHeightDp = 320,
                action = "select_chapter"
            ) {
                column(key = "chapter", title = "Chapter", weight = 1.5f, sortable = true)
                column(key = "pages", title = "Pages", widthDp = 90, sortable = true)
                column(key = "status", title = "Status", widthDp = 120)
                row(
                    key = "ch_1",
                    cells = mapOf("chapter" to "Chapter 1", "pages" to "24", "status" to "Ready"),
                    badgeColumnKey = "status",
                    badgeColor = SemanticColor.SUCCESS
                )
            }
            flowRow(
                id = "tag_flow",
                horizontalSpacing = SpacingSize.SMALL,
                verticalSpacing = SpacingSize.EXTRA_SMALL,
                maxItemsInEachRow = 4
            ) {
                badge(text = "Manga", id = "badge_manga", color = SemanticColor.PRIMARY)
                badge(text = "HQ", id = "badge_hq", color = SemanticColor.SECONDARY)
            }
            keyValueList(id = "chapter_meta", title = "Metadata", showDividers = true) {
                entry(key = "sha", label = "Checksum", value = "deadbeef", monospace = true, copyable = true)
                entry(key = "status", label = "State", value = "Synced", badgeColor = SemanticColor.SUCCESS)
            }
            markdownText(
                id = "release_notes",
                markdown = "## Notes\n- **Verified** [changelog](https://example.com)",
                selectable = true,
                maxLines = 8,
                linkAction = "open_release_link"
            )
            multiSelectDropdown(
                id = "formats_filter",
                selectedKeys = setOf("webp", "avif"),
                options = mapOf("webp" to "WebP", "avif" to "AVIF", "png" to "PNG"),
                label = "Export Formats",
                placeholder = "Choose formats",
                searchable = true,
                showSelectAll = true
            )
            rangeSlider(
                id = "chapter_range",
                startValue = 10.0,
                endValue = 45.0,
                min = 1.0,
                max = 100.0,
                steps = 9,
                label = "Chapter Window",
                unitSuffix = "ch"
            )
        }

        val encoded = json.encodeToString(UiNodeTree.serializer(), tree)
        val decoded = json.decodeFromString(UiNodeTree.serializer(), encoded)

        assertEquals(tree, decoded)

        val root = assertIs<ColumnNode>(decoded.root)
        assertEquals(6, root.children.size)

        val table = assertIs<TableNode>(root.children[0])
        assertEquals("chapters_table", table.id)
        assertEquals(3, table.columns.size)
        assertEquals("status", table.rows.single().badgeColumnKey)

        val flow = assertIs<FlowRowNode>(root.children[1])
        assertEquals(2, flow.children.size)
        assertEquals(4, flow.maxItemsInEachRow)

        val kvList = assertIs<KeyValueListNode>(root.children[2])
        assertEquals(2, kvList.entries.size)
        assertTrue(kvList.entries.first().copyable)

        val markdown = assertIs<MarkdownTextNode>(root.children[3])
        assertEquals("open_release_link", markdown.linkAction)
        assertEquals(8, markdown.maxLines)

        val multiSelect = assertIs<MultiSelectDropdownNode>(root.children[4])
        assertEquals(setOf("webp", "avif"), multiSelect.selectedKeys)

        val range = assertIs<RangeSliderNode>(root.children[5])
        assertEquals(10.0, range.startValue)
        assertEquals(45.0, range.endValue)
        assertEquals("ch", range.unitSuffix)
    }

    @Test
    fun `structured data and range events round-trip through JSON`() {
        val events: List<UiEvent> = listOf(
            UiEvent.TableSorted(nodeId = "chapters_table", columnKey = "pages", ascending = false),
            UiEvent.RangeChanged(nodeId = "chapter_range", startValue = 12.0, endValue = 64.0),
            UiEvent.MultiOptionSelected(nodeId = "formats_filter", selectedKeys = setOf("webp", "png"))
        )

        events.forEach { original ->
            val encoded = json.encodeToString(UiEvent.serializer(), original)
            val decoded = json.decodeFromString(UiEvent.serializer(), encoded)
            assertEquals(original, decoded)
        }
    }
}
