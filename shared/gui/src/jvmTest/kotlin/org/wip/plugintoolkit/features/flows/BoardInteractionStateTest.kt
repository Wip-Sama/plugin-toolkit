package org.wip.plugintoolkit.features.flows

import androidx.compose.ui.geometry.Offset
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.ui.canvas.BoardInteractionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BoardInteractionStateTest {

    @Test
    fun testHoveredNodeStateTransitions() {
        val state = BoardInteractionState()
        assertNull(state.hoveredNodeId)

        state.hoveredNodeId = 42L
        assertEquals(42L, state.hoveredNodeId)

        state.clearHoveredNode()
        assertNull(state.hoveredNodeId)
    }

    @Test
    fun testClearAllInteractionsClearsHoveredNode() {
        val state = BoardInteractionState()
        val dummyConnection = Connection(1L, "out", 2L, "in")

        state.selectedConnection = dummyConnection
        state.hoveredConnection = dummyConnection
        state.hoveredConnectionIsSource = true
        state.hoveredNodeId = 99L
        state.selectionStart = Offset(10f, 10f)
        state.selectionEnd = Offset(50f, 50f)

        state.clearAllInteractions()

        assertNull(state.selectedConnection)
        assertNull(state.hoveredConnection)
        assertNull(state.hoveredConnectionIsSource)
        assertNull(state.hoveredNodeId)
        assertNull(state.selectionStart)
        assertNull(state.selectionEnd)
    }
}
