package org.wip.plugintoolkit.api.sdui

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UiExpressiveAndMediaSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun batch6NodesRoundTripThroughUiNodeSerializer() {
        val tree = uiTree {
            floatingToolbar(
                id = "toolbar_1",
                orientation = ToolbarOrientation.HORIZONTAL,
                vibrant = true,
                expanded = true,
                primaryAction = ActionMenuItem(key = "run", label = "Run", action = "run_all", iconName = "play_arrow")
            ) {
                item(key = "inspect", label = "Inspect", action = "open_sheet", iconName = "info")
            }
            fab(
                id = "fab_1",
                iconName = "add",
                action = "create_item",
                label = "Create",
                size = FabSize.EXTENDED,
                tooltip = "Create entry"
            )
            fabMenu(id = "fab_menu_1", iconName = "add", expanded = true, label = "Quick actions") {
                item(key = "a", label = "Action A", action = "act_a", iconName = "bolt")
                item(key = "b", label = "Action B", action = "act_b", iconName = "check")
            }
            sideSheet(
                id = "sheet_1",
                title = "Inspector",
                isOpen = true,
                attachPoint = SideSheetAttachPoint.START,
                dismissAction = "close_sheet"
            ) {
                text("Details inside side sheet", id = "sheet_txt")
            }
            datePicker(
                id = "date_1",
                selectedDateIso = "2026-11-24",
                minDateIso = "2026-01-01",
                maxDateIso = "2026-12-31",
                label = "Publish Date"
            )
            timePicker(
                id = "time_1",
                hour = 14,
                minute = 30,
                is24Hour = true,
                label = "Publish Time"
            )
            imageCompare(
                id = "compare_1",
                beforeAsset = AssetReference.Relative("before.png"),
                afterAsset = AssetReference.Relative("after.png"),
                beforeLabel = "Raw",
                afterLabel = "Edited",
                splitPosition = 0.35f
            )
            mediaPlayer(
                id = "media_1",
                asset = AssetReference.Uri("https://example.com/clip.wav"),
                title = "Voiceover",
                kind = MediaKind.AUDIO,
                autoPlay = false,
                showControls = true
            )
        }

        val encoded = json.encodeToString(UiNodeTree.serializer(), tree)
        val decoded = json.decodeFromString(UiNodeTree.serializer(), encoded)
        val root = assertIs<ColumnNode>(decoded.root)
        assertEquals(8, root.children.size)

        val toolbar = assertIs<FloatingToolbarNode>(root.children[0])
        assertEquals(ToolbarOrientation.HORIZONTAL, toolbar.orientation)
        assertTrue(toolbar.vibrant)
        assertEquals("run_all", toolbar.primaryAction?.action)

        val fab = assertIs<FabNode>(root.children[1])
        assertEquals(FabSize.EXTENDED, fab.size)
        assertEquals("create_item", fab.action)

        val fabMenu = assertIs<FabMenuNode>(root.children[2])
        assertTrue(fabMenu.expanded)
        assertEquals(2, fabMenu.items.size)

        val sheet = assertIs<SideSheetNode>(root.children[3])
        assertEquals(SideSheetAttachPoint.START, sheet.attachPoint)
        assertEquals("close_sheet", sheet.dismissAction)
        assertEquals(1, sheet.children.size)

        val date = assertIs<DatePickerNode>(root.children[4])
        assertEquals("2026-11-24", date.selectedDateIso)

        val time = assertIs<TimePickerNode>(root.children[5])
        assertEquals(14, time.hour)
        assertEquals(30, time.minute)

        val compare = assertIs<ImageCompareNode>(root.children[6])
        assertEquals(0.35f, compare.splitPosition)

        val media = assertIs<MediaPlayerNode>(root.children[7])
        assertEquals(MediaKind.AUDIO, media.kind)
        assertEquals("Voiceover", media.title)
    }
}
