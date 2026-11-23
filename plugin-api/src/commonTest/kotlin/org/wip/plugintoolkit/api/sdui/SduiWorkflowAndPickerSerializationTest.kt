package org.wip.plugintoolkit.api.sdui

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SduiWorkflowAndPickerSerializationTest {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Test
    fun `workflow navigation and picker nodes round-trip through JSON`() {
        val tree = uiTree {
            breadcrumbs(id = "repo_crumbs", separator = "/", maxVisibleItems = 4) {
                item(key = "root", label = "Workspace", iconName = "folder")
                item(key = "series", label = "Series", iconName = "book")
                item(key = "ch_12", label = "Chapter 12")
            }
            stepper(id = "publish_steps", horizontal = true) {
                step(key = "select", label = "Select Files", status = StepStatus.COMPLETED)
                step(key = "validate", label = "Validate", subtitle = "Checking frames", status = StepStatus.ACTIVE)
                step(key = "upload", label = "Publish", status = StepStatus.PENDING, enabled = false)
            }
            treeView(id = "archive_tree", selectedKey = "ch_01_p1", maxHeightDp = 280, action = "open_entry") {
                node(key = "ch_01", label = "Chapter 1", iconName = "folder_open", expanded = true) {
                    node(key = "ch_01_p1", label = "001.webp", iconName = "image")
                }
            }
            filePicker(
                id = "cover_picker",
                value = "C:/manga/cover.png",
                paths = listOf("C:/manga/cover.png"),
                mode = FilePickerMode.FILE,
                allowedExtensions = listOf("png", "webp"),
                label = "Cover Image",
                action = "cover_picked"
            )
            colorPicker(
                id = "accent_color",
                colorHex = "#6750A4",
                label = "Reader Accent",
                presets = listOf("#6750A4", "#006A6A", "#B3261E"),
                showHexInput = true
            )
            pagination(
                id = "catalog_pages",
                currentPage = 2,
                totalPages = 12,
                pageSize = 25,
                pageSizeOptions = listOf(10, 25, 50)
            )
            menu(id = "row_actions_menu", triggerLabel = "Actions", triggerIconName = "more_vert", tooltip = "Row menu") {
                item(key = "export", label = "Export JSON", action = "export_json", iconName = "download")
                item(key = "delete", label = "Remove", action = "remove_item", iconName = "delete", isDestructive = true, startsSection = true)
            }
            scrollableColumn(
                id = "log_scroll",
                scrollToIndex = 15,
                scrollToEdge = ScrollEdge.BOTTOM,
                scrollRevision = 3
            ) {
                text(id = "log_line_1", text = "Log initialized")
            }
            grid(
                id = "page_grid",
                columns = 4,
                scrollToIndex = 8,
                scrollToEdge = ScrollEdge.TOP,
                scrollRevision = 2
            ) {
                badge(id = "grid_badge_1", text = "P1")
            }
        }

        val encoded = json.encodeToString(UiNodeTree.serializer(), tree)
        val decoded = json.decodeFromString(UiNodeTree.serializer(), encoded)

        assertEquals(tree, decoded)

        val root = assertIs<ColumnNode>(decoded.root)
        assertEquals(9, root.children.size)

        val crumbs = assertIs<BreadcrumbsNode>(root.children[0])
        assertEquals(4, crumbs.maxVisibleItems)
        assertEquals(3, crumbs.items.size)

        val stepper = assertIs<StepperNode>(root.children[1])
        assertEquals(StepStatus.ACTIVE, stepper.steps[1].status)

        val treeView = assertIs<TreeViewNode>(root.children[2])
        assertTrue(treeView.items.single().expanded)
        assertEquals("ch_01_p1", treeView.items.single().children.single().key)

        val filePicker = assertIs<FilePickerNode>(root.children[3])
        assertEquals(FilePickerMode.FILE, filePicker.mode)

        val colorPicker = assertIs<ColorPickerNode>(root.children[4])
        assertEquals("#6750A4", colorPicker.colorHex)

        val pagination = assertIs<PaginationNode>(root.children[5])
        assertEquals(25, pagination.pageSize)

        val menu = assertIs<MenuNode>(root.children[6])
        assertTrue(menu.items[1].isDestructive)
        assertTrue(menu.items[1].startsSection)

        val scrollCol = assertIs<ScrollableColumnNode>(root.children[7])
        assertEquals(15, scrollCol.scrollToIndex)
        assertEquals(ScrollEdge.BOTTOM, scrollCol.scrollToEdge)
        assertEquals(3, scrollCol.scrollRevision)

        val grid = assertIs<GridNode>(root.children[8])
        assertEquals(8, grid.scrollToIndex)
        assertEquals(ScrollEdge.TOP, grid.scrollToEdge)
        assertEquals(2, grid.scrollRevision)
    }

    @Test
    fun `workflow and picker events round-trip through JSON`() {
        val events: List<UiEvent> = listOf(
            UiEvent.TreeNodeToggled(nodeId = "archive_tree", itemKey = "ch_01", expanded = false),
            UiEvent.PageChanged(nodeId = "catalog_pages", page = 4, pageSize = 50),
            UiEvent.FilesPicked(nodeId = "cover_picker", action = "cover_picked", paths = listOf("C:/tmp/a.webp")),
            UiEvent.ColorChanged(nodeId = "accent_color", colorHex = "#006A6A")
        )

        events.forEach { original ->
            val encoded = json.encodeToString(UiEvent.serializer(), original)
            val decoded = json.decodeFromString(UiEvent.serializer(), encoded)
            assertEquals(original, decoded)
        }
    }
}
