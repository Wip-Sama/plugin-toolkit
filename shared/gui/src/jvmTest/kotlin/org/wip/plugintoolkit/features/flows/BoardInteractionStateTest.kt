package org.wip.plugintoolkit.features.flows

import androidx.compose.ui.geometry.Offset
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.ui.canvas.BoardInteractionState
import org.wip.plugintoolkit.features.flows.ui.canvas.CanvasInputMode
import org.wip.plugintoolkit.features.flows.ui.canvas.DraggingSegmentInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        assertEquals(CanvasInputMode.Idle, state.inputMode)
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

    @Test
    fun testFsmMutualExclusionAndTransitions() {
        val state = BoardInteractionState()
        assertEquals(CanvasInputMode.Idle, state.inputMode)

        // 1. Panning transition
        state.startPanning(Offset(15f, 25f))
        assertIs<CanvasInputMode.Panning>(state.inputMode)
        assertEquals(Offset(15f, 25f), (state.inputMode as CanvasInputMode.Panning).startPointer)

        // 2. Transition to Marquee clears Panning
        state.startMarquee(Offset(50f, 50f))
        assertIs<CanvasInputMode.MarqueeSelecting>(state.inputMode)
        assertEquals(Offset(50f, 50f), state.selectionStart)
        state.updateMarquee(Offset(150f, 200f))
        assertEquals(Offset(150f, 200f), state.selectionEnd)

        // 3. Transition to Dragging Junction clears Marquee
        state.startDraggingJunction(junctionId = 101L, startPointer = Offset(150f, 200f))
        assertIs<CanvasInputMode.DraggingJunction>(state.inputMode)
        assertEquals(101L, state.draggingJunctionId)
        assertNull(state.selectionStart)
        assertNull(state.selectionEnd)

        // 4. Transition to ConnectingWire clears dragging junction
        state.startConnectingWire(
            sourceNodeId = 1L,
            sourcePortId = "out",
            isOutput = true,
            sourceJunctionId = null,
            initialWaypoints = listOf(Offset(10f, 20f)),
            livePos = Offset(30f, 40f)
        )
        assertIs<CanvasInputMode.ConnectingWire>(state.inputMode)
        assertTrue(state.isDrawingStructuredConnection)
        assertEquals(1L, state.structuredConnectionStartNodeId)
        assertEquals("out", state.structuredConnectionStartPortId)
        assertNull(state.draggingJunctionId)

        // 5. Transition to DraggingWaypoint clears connecting wire
        val dummyConn = Connection(1L, "out", 2L, "in")
        state.startDraggingWaypoint(dummyConn, waypointIndex = 2)
        assertIs<CanvasInputMode.DraggingWaypoint>(state.inputMode)
        assertEquals(Pair(dummyConn, 2), state.draggingWaypoint)
        assertNull(state.structuredConnectionStartNodeId)

        // 6. Transition to DraggingSegment clears dragging waypoint
        val segInfo = DraggingSegmentInfo(connection = dummyConn, segmentIndex = 0)
        state.startDraggingSegment(segInfo)
        assertIs<CanvasInputMode.DraggingSegment>(state.inputMode)
        assertEquals(segInfo, state.draggingSegment)
        assertNull(state.draggingWaypoint)

        // 7. Reset to Idle restores clean state
        state.resetToIdle()
        assertEquals(CanvasInputMode.Idle, state.inputMode)
        assertNull(state.draggingSegment)
    }
}
