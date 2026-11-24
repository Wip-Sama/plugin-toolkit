package org.wip.plugintoolkit.features.plugin.sdui

import org.wip.plugintoolkit.api.sdui.ActionMenuItem
import org.wip.plugintoolkit.api.sdui.AssetReference
import org.wip.plugintoolkit.api.sdui.ColumnNode
import org.wip.plugintoolkit.api.sdui.DatePickerNode
import org.wip.plugintoolkit.api.sdui.ErrorBoundaryNode
import org.wip.plugintoolkit.api.sdui.FabMenuNode
import org.wip.plugintoolkit.api.sdui.FabNode
import org.wip.plugintoolkit.api.sdui.FloatingToolbarNode
import org.wip.plugintoolkit.api.sdui.ImageCompareNode
import org.wip.plugintoolkit.api.sdui.MediaPlayerNode
import org.wip.plugintoolkit.api.sdui.SideSheetNode
import org.wip.plugintoolkit.api.sdui.TextNode
import org.wip.plugintoolkit.api.sdui.TimePickerNode
import org.wip.plugintoolkit.api.sdui.UiModifier
import org.wip.plugintoolkit.api.sdui.UiNodeTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class UiAstSanitizerExpressiveMediaTest {

    @Test
    fun sanitizerRejectsInvalidToolbarFabAndSideSheetFields() {
        val tree = UiNodeTree(
            root = ColumnNode(
                id = "root",
                children = listOf(
                    FloatingToolbarNode(
                        id = "bad_toolbar",
                        items = listOf(ActionMenuItem(key = "", label = "Bad"))
                    ),
                    FabNode(id = "bad_fab", iconName = "", action = "act"),
                    FabMenuNode(
                        id = "bad_fab_menu",
                        items = listOf(ActionMenuItem(key = "k", label = ""))
                    ),
                    SideSheetNode(id = "bad_sheet", title = " ")
                )
            )
        )

        val sanitized = UiAstSanitizer.sanitize(tree)
        val root = assertIs<ColumnNode>(sanitized.root)
        root.children.forEach { child ->
            assertIs<ErrorBoundaryNode>(child)
        }
    }

    @Test
    fun sanitizerClampsTimeImageCompareAndSanitizesDatesAndSideSheetChildren() {
        val tree = UiNodeTree(
            root = ColumnNode(
                id = "root",
                children = listOf(
                    DatePickerNode(
                        id = "date_node",
                        selectedDateIso = "invalid-date",
                        minDateIso = "2026-01-05",
                        maxDateIso = "2026-99-99"
                    ),
                    TimePickerNode(id = "time_node", hour = 99, minute = -5),
                    ImageCompareNode(
                        id = "compare_nan",
                        beforeAsset = AssetReference.Relative("a.png"),
                        afterAsset = AssetReference.Relative("b.png"),
                        splitPosition = Float.NaN
                    ),
                    ImageCompareNode(
                        id = "compare_over",
                        beforeAsset = AssetReference.Relative("a.png"),
                        afterAsset = AssetReference.Relative("b.png"),
                        splitPosition = 1.8f
                    ),
                    SideSheetNode(
                        id = "sheet_ok",
                        title = "Inspector",
                        isOpen = true,
                        children = listOf(
                            TextNode(id = "ok_child", text = "Valid"),
                            TextNode(id = "bad_weight", text = "Bad", modifier = UiModifier(weight = -2f))
                        )
                    ),
                    MediaPlayerNode(
                        id = "media_ok",
                        asset = AssetReference.Relative("clip.wav"),
                        title = "Track"
                    )
                )
            )
        )

        val sanitized = UiAstSanitizer.sanitize(tree)
        val root = assertIs<ColumnNode>(sanitized.root)

        val dateNode = assertIs<DatePickerNode>(root.children[0])
        assertNull(dateNode.selectedDateIso)
        assertEquals("2026-01-05", dateNode.minDateIso)
        assertNull(dateNode.maxDateIso)

        val timeNode = assertIs<TimePickerNode>(root.children[1])
        assertEquals(23, timeNode.hour)
        assertEquals(0, timeNode.minute)

        val compareNan = assertIs<ImageCompareNode>(root.children[2])
        assertEquals(0.5f, compareNan.splitPosition)

        val compareOver = assertIs<ImageCompareNode>(root.children[3])
        assertEquals(1.0f, compareOver.splitPosition)

        val sheetOk = assertIs<SideSheetNode>(root.children[4])
        assertIs<TextNode>(sheetOk.children[0])
        assertIs<ErrorBoundaryNode>(sheetOk.children[1])

        assertIs<MediaPlayerNode>(root.children[5])
    }
}
