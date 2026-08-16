package org.wip.plugintoolkit.features.flows.ui

import androidx.compose.ui.geometry.Offset
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.ui.canvas.BoardInteractionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BoardInteractionStateTest {

    @Test
    fun testInitialState() {
        val state = BoardInteractionState()
        assertNull(state.selectedConnection)
        assertNull(state.hoveredConnection)
        assertNull(state.hoveredConnectionIsSource)
        assertFalse(state.isCtrlModifierPressed)
        assertEquals(Offset.Zero, state.lastPointerPosition)
        assertNull(state.selectionStart)
        assertNull(state.selectionEnd)
    }

    @Test
    fun testClearHoveredConnection() {
        val state = BoardInteractionState()
        val conn = Connection(1L, "out", 2L, "in")
        state.hoveredConnection = conn
        state.hoveredConnectionIsSource = true

        state.clearHoveredConnection()

        assertNull(state.hoveredConnection)
        assertNull(state.hoveredConnectionIsSource)
    }

    @Test
    fun testClearSelectionBox() {
        val state = BoardInteractionState()
        state.selectionStart = Offset(10f, 10f)
        state.selectionEnd = Offset(50f, 50f)

        state.clearSelectionBox()

        assertNull(state.selectionStart)
        assertNull(state.selectionEnd)
    }

    @Test
    fun testSelectedConnectionIndependent() {
        val state = BoardInteractionState()
        val conn = Connection(1L, "out", 2L, "in")
        state.selectedConnection = conn

        state.clearHoveredConnection()

        assertEquals(conn, state.selectedConnection)
    }
}
