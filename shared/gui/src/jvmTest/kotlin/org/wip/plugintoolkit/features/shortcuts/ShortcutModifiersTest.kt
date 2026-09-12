package org.wip.plugintoolkit.features.shortcuts

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import org.wip.plugintoolkit.features.shortcuts.logic.ShortcutManager
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutActionId
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutGesture
import org.wip.plugintoolkit.features.shortcuts.ui.eatPointerEvent
import org.wip.plugintoolkit.features.shortcuts.ui.matchesKey
import org.wip.plugintoolkit.features.shortcuts.ui.matchesPointerEvent
import org.wip.plugintoolkit.features.shortcuts.ui.onShortcutKey
import org.wip.plugintoolkit.features.shortcuts.ui.onShortcutPointer
import org.wip.plugintoolkit.features.shortcuts.ui.shortcutDrag
import org.wip.plugintoolkit.features.shortcuts.ui.shortcutHandler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ShortcutModifiersTest {

    @Test
    fun testKeyMatchingWithShortcutManager() {
        val manager = ShortcutManager()

        // FLOW_UNDO default is Ctrl + Z
        assertTrue(
            matchesKey(
                shortcutManager = manager,
                actionId = ShortcutActionId.FLOW_UNDO,
                key = Key.Z,
                isCtrl = true,
                isShift = false,
                isAlt = false,
                isMeta = false
            )
        )

        // Without Ctrl, FLOW_UNDO should not match
        assertFalse(
            matchesKey(
                shortcutManager = manager,
                actionId = ShortcutActionId.FLOW_UNDO,
                key = Key.Z,
                isCtrl = false,
                isShift = false,
                isAlt = false,
                isMeta = false
            )
        )

        // FLOW_PAINT_TOOL matches P or B without Ctrl
        assertTrue(
            matchesKey(
                shortcutManager = manager,
                actionId = ShortcutActionId.FLOW_PAINT_TOOL,
                key = Key.P,
                isCtrl = false,
                isShift = false,
                isAlt = false,
                isMeta = false
            )
        )
        assertTrue(
            matchesKey(
                shortcutManager = manager,
                actionId = ShortcutActionId.FLOW_PAINT_TOOL,
                key = Key.B,
                isCtrl = false,
                isShift = false,
                isAlt = false,
                isMeta = false
            )
        )
        assertFalse(
            matchesKey(
                shortcutManager = manager,
                actionId = ShortcutActionId.FLOW_PAINT_TOOL,
                key = Key.P,
                isCtrl = true,
                isShift = false,
                isAlt = false,
                isMeta = false
            )
        )
    }

    @Test
    fun testKeyMatchingCatalogFallbackWhenManagerIsNull() {
        // When manager is null, it should fall back to DefaultShortcutCatalog
        assertTrue(
            matchesKey(
                shortcutManager = null,
                actionId = ShortcutActionId.FLOW_DELETE_SELECTED,
                key = Key.Delete,
                isCtrl = false,
                isShift = false,
                isAlt = false,
                isMeta = false
            )
        )
        assertTrue(
            matchesKey(
                shortcutManager = null,
                actionId = ShortcutActionId.FLOW_DELETE_SELECTED,
                key = Key.Backspace,
                isCtrl = false,
                isShift = false,
                isAlt = false,
                isMeta = false
            )
        )
        assertFalse(
            matchesKey(
                shortcutManager = null,
                actionId = ShortcutActionId.FLOW_DELETE_SELECTED,
                key = Key.A,
                isCtrl = false,
                isShift = false,
                isAlt = false,
                isMeta = false
            )
        )
    }

    @Test
    fun testPointerEventMatchingAndEating() {
        val manager = ShortcutManager()
        val change = PointerInputChange(
            id = PointerId(1),
            uptimeMillis = 100L,
            position = Offset(10f, 10f),
            pressed = true,
            pressure = 1f,
            previousUptimeMillis = 90L,
            previousPosition = Offset(10f, 10f),
            previousPressed = true,
            isInitiallyConsumed = false
        )
        val event = PointerEvent(listOf(change))

        // Left drag does not match FLOW_PAN_CANVAS (which requires Right or Middle drag)
        assertFalse(
            matchesPointerEvent(
                shortcutManager = manager,
                actionId = ShortcutActionId.FLOW_PAN_CANVAS,
                event = event,
                gesture = ShortcutGesture.Drag
            )
        )

        // Test eating event updates live telemetry on manager
        eatPointerEvent(
            shortcutManager = manager,
            event = event,
            actionId = ShortcutActionId.FLOW_PAN_CANVAS
        )

        val eaten = manager.lastEatenEvent.value
        assertNotNull(eaten)
        assertEquals(ShortcutActionId.FLOW_PAN_CANVAS, eaten.actionId)
        assertTrue(change.isConsumed)
    }

    @Test
    fun testModifierChainingDoesNotThrow() {
        val manager = ShortcutManager()
        var triggeredAction = false
        var draggedDelta = Offset.Zero

        val modifier = Modifier
            .shortcutHandler(manager) {
                onKey(ShortcutActionId.FLOW_UNDO) {
                    triggeredAction = true
                }
                onPointer(ShortcutActionId.SKIP_CONFIRMATION, ShortcutGesture.Click) {
                    triggeredAction = true
                }
                onDrag(ShortcutActionId.FLOW_PAN_CANVAS) { delta ->
                    draggedDelta = delta
                }
                onRawKey { false }
            }
            .shortcutDrag(
                shortcutManager = manager,
                actionId = ShortcutActionId.FLOW_PAN_CANVAS,
                onDrag = { delta -> draggedDelta = delta }
            )
            .onShortcutKey(manager, ShortcutActionId.FLOW_REDO) {
                triggeredAction = true
            }
            .onShortcutPointer(manager, ShortcutActionId.SKIP_CONFIRMATION) {
                triggeredAction = true
            }

        assertNotNull(modifier)
        assertFalse(triggeredAction)
        assertEquals(Offset.Zero, draggedDelta)
    }
}
