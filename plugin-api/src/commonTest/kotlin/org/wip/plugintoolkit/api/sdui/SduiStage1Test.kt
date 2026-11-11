package org.wip.plugintoolkit.api.sdui

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SduiStage1Test {

    @Test
    fun testUiModifierBuilders() {
        val mod = UiModifier.fillMaxSize()
            .width(200)
            .height(100)
            .widthIn(min = 120, max = 320)
            .heightIn(min = 60, max = 180)
            .background(SemanticColor.SURFACE)
            .border(SemanticColor.OUTLINE)
            .clipRounded(true)
            .weight(2f)
            .align(AlignmentRole.CENTER)
            .enabled(false)
            .visible(true)

        assertEquals(1f, mod.fillMaxWidth)
        assertEquals(1f, mod.fillMaxHeight)
        assertEquals(200, mod.widthDp)
        assertEquals(100, mod.heightDp)
        assertEquals(120, mod.minWidthDp)
        assertEquals(320, mod.maxWidthDp)
        assertEquals(60, mod.minHeightDp)
        assertEquals(180, mod.maxHeightDp)
        assertEquals(SemanticColor.SURFACE, mod.backgroundColor)
        assertEquals(SemanticColor.OUTLINE, mod.borderColor)
        assertTrue(mod.clipRounded)
        assertEquals(2f, mod.weight)
        assertEquals(AlignmentRole.CENTER, mod.alignment)
        assertFalse(mod.enabled)
        assertTrue(mod.visible)

        val squareMod = UiModifier.size(64)
        assertEquals(64, squareMod.widthDp)
        assertEquals(64, squareMod.heightDp)
    }

    @Test
    fun testButtonActionAndClickEventSerialization() {
        val tree = uiTree {
            column(id = "root_col") {
                button(
                    id = "save_btn",
                    label = "Save Changes",
                    action = "save_board",
                    isPrimary = true
                )
            }
        }

        val encodedTree = Json.encodeToString(UiNodeTree.serializer(), tree)
        val decodedTree = Json.decodeFromString(UiNodeTree.serializer(), encodedTree)
        val col = assertIs<ColumnNode>(decodedTree.root)
        val btn = assertIs<ButtonNode>(col.children.single())
        assertEquals("save_btn", btn.id)
        assertEquals("Save Changes", btn.label)
        assertEquals("save_board", btn.action)

        val clickEvent: UiEvent = UiEvent.Click(nodeId = "save_btn", action = "save_board")
        val encodedEvent = Json.encodeToString(UiEvent.serializer(), clickEvent)
        val decodedEvent = Json.decodeFromString(UiEvent.serializer(), encodedEvent)
        val click = assertIs<UiEvent.Click>(decodedEvent)
        assertEquals("save_btn", click.nodeId)
        assertEquals("save_board", click.action)
    }
}
