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

    @Test
    fun testSelectionBoxProjectionBetweenModelAndScreenSpace() {
        val state = BoardInteractionState()
        val boardStart = Offset(100f, 200f)
        val boardEnd = Offset(300f, 400f)
        state.selectionStart = boardStart
        state.selectionEnd = boardEnd

        // 1. Identity transform (scale=1, offset=0)
        val scale1 = 1f
        val offset1 = Offset.Zero
        val screenStart1 = (state.selectionStart!! * scale1) + offset1
        val screenEnd1 = (state.selectionEnd!! * scale1) + offset1
        assertEquals(Offset(100f, 200f), screenStart1)
        assertEquals(Offset(300f, 400f), screenEnd1)

        // 2. Zoomed & panned transform (scale=2.0, offset=(50f, -30f))
        val scale2 = 2f
        val offset2 = Offset(50f, -30f)
        val screenStart2 = (state.selectionStart!! * scale2) + offset2
        val screenEnd2 = (state.selectionEnd!! * scale2) + offset2
        assertEquals(Offset(250f, 370f), screenStart2)
        assertEquals(Offset(650f, 770f), screenEnd2)

        // 3. Inverting screen cursor back to board space matches original model coordinates
        val cursorScreen = Offset(650f, 770f)
        val invertedModel = (cursorScreen - offset2) / scale2
        assertEquals(boardEnd, invertedModel)

        state.clearSelectionBox()
        assertNull(state.selectionStart)
        assertNull(state.selectionEnd)
    }
}

