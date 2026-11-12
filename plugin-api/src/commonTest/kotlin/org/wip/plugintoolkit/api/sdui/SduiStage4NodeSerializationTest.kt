package org.wip.plugintoolkit.api.sdui

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SduiStage4NodeSerializationTest {

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }

    @Test
    fun testProgressBarNodeSerialization() {
        val node = ProgressBarNode(
            id = "prog_1",
            progress = 0.45f,
            secondaryProgress = 0.60f,
            label = "Uploading",
            statusText = "45% (4.5/10 MB)",
            isCircular = false,
            color = SemanticColor.PRIMARY
        )
        val serialized = json.encodeToString(UiNodeSerializer, node)
        assertTrue(serialized.contains("\"type\":\"progress_bar\""))
        assertTrue(serialized.contains("\"progress\":0.45"))
        assertTrue(serialized.contains("\"secondaryProgress\":0.6"))

        val deserialized = json.decodeFromString(UiNodeSerializer, serialized)
        assertIs<ProgressBarNode>(deserialized)
        assertEquals(0.45f, deserialized.progress)
        assertEquals(0.60f, deserialized.secondaryProgress)
        assertEquals("Uploading", deserialized.label)
        assertEquals("45% (4.5/10 MB)", deserialized.statusText)
        assertEquals(SemanticColor.PRIMARY, deserialized.color)
    }

    @Test
    fun testTabsNodeSerialization() {
        val node = TabsNode(
            id = "tabs_1",
            selectedTabKey = "queue",
            tabs = listOf(
                TabItem(key = "chapters", label = "Chapters", iconName = "book", badgeText = "12"),
                TabItem(key = "queue", label = "Active Jobs", iconName = "sync", badgeText = "1")
            ),
            content = TextNode(id = "txt_content", text = "Tab body")
        )
        val serialized = json.encodeToString(UiNodeSerializer, node)
        assertTrue(serialized.contains("\"type\":\"tabs\""))
        assertTrue(serialized.contains("\"selectedTabKey\":\"queue\""))

        val deserialized = json.decodeFromString(UiNodeSerializer, serialized)
        assertIs<TabsNode>(deserialized)
        assertEquals("queue", deserialized.selectedTabKey)
        assertEquals(2, deserialized.tabs.size)
        assertEquals("Chapters", deserialized.tabs[0].label)
        assertEquals("12", deserialized.tabs[0].badgeText)
        assertIs<TextNode>(deserialized.content)
    }

    @Test
    fun testListItemNodeSerialization() {
        val node = ListItemNode(
            id = "item_1",
            title = "Chapter 42: The Awakening",
            subtitle = "Status: Draft | Size: 14.2 MB",
            leadingIcon = "book",
            badgeText = "DRAFT",
            badgeColor = SemanticColor.WARNING,
            selected = true,
            selectable = true,
            trailingContent = listOf(
                ButtonNode(id = "push_btn", label = "Push", action = "push_ch_42")
            )
        )
        val serialized = json.encodeToString(UiNodeSerializer, node)
        assertTrue(serialized.contains("\"type\":\"list_item\""))
        assertTrue(serialized.contains("\"badgeText\":\"DRAFT\""))

        val deserialized = json.decodeFromString(UiNodeSerializer, serialized)
        assertIs<ListItemNode>(deserialized)
        assertEquals("Chapter 42: The Awakening", deserialized.title)
        assertEquals(true, deserialized.selected)
        assertEquals(true, deserialized.selectable)
        assertEquals(1, deserialized.trailingContent.size)
        assertIs<ButtonNode>(deserialized.trailingContent[0])
    }

    @Test
    fun testCheckboxAndChipGroupNodeSerialization() {
        val chk = CheckboxNode(id = "chk_1", checked = true, label = "Select all drafts")
        val chkSerialized = json.encodeToString(UiNodeSerializer, chk)
        val chkDeserialized = json.decodeFromString(UiNodeSerializer, chkSerialized)
        assertIs<CheckboxNode>(chkDeserialized)
        assertEquals(true, chkDeserialized.checked)
        assertEquals("Select all drafts", chkDeserialized.label)

        val chips = ChipGroupNode(
            id = "chips_1",
            chips = listOf(
                ChipItem(key = "ALL", label = "All"),
                ChipItem(key = "PUBLISHED", label = "Published", iconName = "check"),
                ChipItem(key = "DRAFT", label = "Draft", iconName = "edit")
            ),
            selectedKeys = setOf("DRAFT"),
            multiSelect = false
        )
        val chipsSerialized = json.encodeToString(UiNodeSerializer, chips)
        val chipsDeserialized = json.decodeFromString(UiNodeSerializer, chipsSerialized)
        assertIs<ChipGroupNode>(chipsDeserialized)
        assertEquals(3, chipsDeserialized.chips.size)
        assertTrue("DRAFT" in chipsDeserialized.selectedKeys)
    }

    @Test
    fun testIconButtonAndAlertBannerSerialization() {
        val iconBtn = IconButtonNode(
            id = "btn_sync",
            iconName = "sync",
            action = "refresh_catalog",
            tooltip = "Sync catalog",
            tint = SemanticColor.PRIMARY,
            isTonal = true
        )
        val iconBtnSerialized = json.encodeToString(UiNodeSerializer, iconBtn)
        val iconBtnDeserialized = json.decodeFromString(UiNodeSerializer, iconBtnSerialized)
        assertIs<IconButtonNode>(iconBtnDeserialized)
        assertEquals("sync", iconBtnDeserialized.iconName)
        assertEquals("refresh_catalog", iconBtnDeserialized.action)
        assertEquals(true, iconBtnDeserialized.isTonal)

        val banner = AlertBannerNode(
            id = "alert_1",
            title = "Remote Synchronization Notice",
            message = "3 local drafts require remote authorization",
            severity = SemanticColor.WARNING,
            iconName = "warning",
            actionLabel = "Authorize Now"
        )
        val bannerSerialized = json.encodeToString(UiNodeSerializer, banner)
        val bannerDeserialized = json.decodeFromString(UiNodeSerializer, bannerSerialized)
        assertIs<AlertBannerNode>(bannerDeserialized)
        assertEquals("Remote Synchronization Notice", bannerDeserialized.title)
        assertEquals(SemanticColor.WARNING, bannerDeserialized.severity)
        assertEquals("Authorize Now", bannerDeserialized.actionLabel)
    }

    @Test
    fun testDialogAndSplitPaneSerialization() {
        val dialog = DialogNode(
            id = "dlg_confirm",
            title = "Confirm Publishing Batch",
            isOpen = true,
            confirmLabel = "Publish 4 Chapters",
            dismissLabel = "Cancel",
            isDestructive = true,
            children = listOf(
                TextNode(id = "dlg_txt", text = "This action will overwrite live chapters.")
            )
        )
        val dialogSerialized = json.encodeToString(UiNodeSerializer, dialog)
        val dialogDeserialized = json.decodeFromString(UiNodeSerializer, dialogSerialized)
        assertIs<DialogNode>(dialogDeserialized)
        assertEquals(true, dialogDeserialized.isOpen)
        assertEquals(true, dialogDeserialized.isDestructive)
        assertEquals(1, dialogDeserialized.children.size)

        val split = SplitPaneNode(
            id = "split_1",
            first = TextNode(id = "pane_1", text = "Master View"),
            second = TextNode(id = "pane_2", text = "Detail View"),
            splitFraction = 0.40f,
            isHorizontal = true
        )
        val splitSerialized = json.encodeToString(UiNodeSerializer, split)
        val splitDeserialized = json.decodeFromString(UiNodeSerializer, splitSerialized)
        assertIs<SplitPaneNode>(splitDeserialized)
        assertEquals(0.40f, splitDeserialized.splitFraction)
        assertEquals(true, splitDeserialized.isHorizontal)
        assertIs<TextNode>(splitDeserialized.first)
        assertIs<TextNode>(splitDeserialized.second)
    }

    @Test
    fun testSpacerAndScrollableRowSerialization() {
        val spacer = SpacerNode(id = "spacer_1", size = SpacingSize.LARGE)
        val spacerSerialized = json.encodeToString(UiNodeSerializer, spacer)
        val spacerDeserialized = json.decodeFromString(UiNodeSerializer, spacerSerialized)
        assertIs<SpacerNode>(spacerDeserialized)
        assertEquals(SpacingSize.LARGE, spacerDeserialized.size)

        val scrollRow = ScrollableRowNode(
            id = "srow_1",
            children = listOf(
                TextNode(id = "t1", text = "Item 1"),
                TextNode(id = "t2", text = "Item 2")
            ),
            spacing = SpacingSize.SMALL
        )
        val scrollRowSerialized = json.encodeToString(UiNodeSerializer, scrollRow)
        val scrollRowDeserialized = json.decodeFromString(UiNodeSerializer, scrollRowSerialized)
        assertIs<ScrollableRowNode>(scrollRowDeserialized)
        assertEquals(2, scrollRowDeserialized.children.size)
        assertEquals(SpacingSize.SMALL, scrollRowDeserialized.spacing)
    }

    @Test
    fun testStage4EventsSerialization() {
        val tabEvent = UiEvent.TabSelected(nodeId = "tabs_1", tabKey = "queue")
        val tabSerialized = json.encodeToString(UiEventSerializer, tabEvent)
        val tabDeserialized = json.decodeFromString(UiEventSerializer, tabSerialized)
        assertIs<UiEvent.TabSelected>(tabDeserialized)
        assertEquals("tabs_1", tabDeserialized.nodeId)
        assertEquals("queue", tabDeserialized.tabKey)

        val chipEvent = UiEvent.ChipSelected(nodeId = "chips_1", chipKey = "DRAFT", isSelected = true)
        val chipSerialized = json.encodeToString(UiEventSerializer, chipEvent)
        val chipDeserialized = json.decodeFromString(UiEventSerializer, chipSerialized)
        assertIs<UiEvent.ChipSelected>(chipDeserialized)
        assertEquals("chips_1", chipDeserialized.nodeId)
        assertEquals("DRAFT", chipDeserialized.chipKey)
        assertEquals(true, chipDeserialized.isSelected)

        val dialogEvent = UiEvent.DialogAction(nodeId = "dlg_confirm", isConfirmed = true)
        val dialogSerialized = json.encodeToString(UiEventSerializer, dialogEvent)
        val dialogDeserialized = json.decodeFromString(UiEventSerializer, dialogSerialized)
        assertIs<UiEvent.DialogAction>(dialogDeserialized)
        assertEquals("dlg_confirm", dialogDeserialized.nodeId)
        assertEquals(true, dialogDeserialized.isConfirmed)
    }

    @Test
    fun testStage4DslBuilders() {
        val tree: UiNodeTree = uiTree {
            tabs(
                id = "tabs_root",
                selectedTabKey = "tab_1",
                tabs = listOf(TabItem(key = "tab_1", label = "Overview"))
            ) {
                splitPane(
                    id = "split_main",
                    splitFraction = 0.30f,
                    first = {
                        listItem(
                            id = "item_demo",
                            title = "Chapter Demo",
                            subtitle = "Subtitle",
                            leadingIcon = "book",
                            badgeText = "NEW",
                            trailingBlock = {
                                iconButton(id = "btn_play", iconName = "play", action = "play_action")
                            }
                        )
                        checkbox(id = "chk_demo", checked = false, label = "Auto-sync")
                        chipGroup(
                            id = "chip_demo",
                            chips = listOf(ChipItem(key = "opt1", label = "Option 1"))
                        )
                    },
                    second = {
                        progressBar(id = "prog_demo", progress = 0.75f, label = "Progress")
                        alertBanner(id = "alert_demo", message = "All systems operational", severity = SemanticColor.SUCCESS)
                        emptyState(id = "empty_demo", title = "No active errors", iconName = "check_circle")
                        spacer(size = SpacingSize.LARGE)
                        scrollableRow(id = "row_demo", spacing = SpacingSize.SMALL) {
                            icon(id = "ic_1", name = "star", tint = SemanticColor.HIGHLIGHT, size = ControlSize.LARGE)
                        }
                    }
                )
            }
        }

        assertNotNull(tree)
        assertIs<TabsNode>(tree.root)
        val tabs = tree.root as TabsNode
        assertIs<BoxNode>(tabs.content)
        val splitBox = tabs.content as BoxNode
        assertEquals(1, splitBox.children.size)
        assertIs<SplitPaneNode>(splitBox.children[0])
    }
}
