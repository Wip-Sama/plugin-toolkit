package org.wip.plugintoolkit.shared.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TooltipExtensionTest {

    private class MockLayoutCoordinates : LayoutCoordinates {
        override val isAttached: Boolean = true
        override val parentCoordinates: LayoutCoordinates? = null
        override val parentLayoutCoordinates: LayoutCoordinates? = null
        override val size: IntSize = IntSize(100, 50)
        override val providedAlignmentLines: Set<AlignmentLine> = emptySet()
        override fun windowToLocal(relativeToWindow: Offset): Offset = relativeToWindow
        override fun localToWindow(relativeToLocal: Offset): Offset = relativeToLocal
        override fun localToRoot(relativeToLocal: Offset): Offset = relativeToLocal
        override fun localPositionOf(sourceCoordinates: LayoutCoordinates, relativeToSource: Offset): Offset = relativeToSource
        override fun localBoundingBoxOf(sourceCoordinates: LayoutCoordinates, clipBounds: Boolean): Rect = Rect.Zero
        override fun get(alignmentLine: AlignmentLine): Int = 0
    }

    @Test
    fun testTooltipDataConstructors() {
        val coords = MockLayoutCoordinates()
        val textData = TooltipData("Hello", coords)
        assertEquals("Hello", textData.text)
        assertEquals(coords, textData.coordinates)
        assertNull(textData.content)
        assertEquals(false, textData.isPinned)

        val customData = TooltipData(
            coordinates = coords,
            content = {},
            isPinned = true,
            key = "info_card"
        )
        assertNull(customData.text)
        assertNotNull(customData.content)
        assertTrue(customData.isPinned)
        assertEquals("info_card", customData.key)
    }

    @Test
    fun testTooltipStateToggleAndDismiss() {
        val state = TooltipState()
        val coords = MockLayoutCoordinates()
        assertNull(state.tooltipData)

        // First toggle opens tooltip
        state.toggle(coords, "my_key") {}
        assertNotNull(state.tooltipData)
        assertEquals("my_key", state.tooltipData?.key)
        assertTrue(state.tooltipData?.isPinned == true)

        // Second toggle with same key closes tooltip
        state.toggle(coords, "my_key") {}
        assertNull(state.tooltipData)

        // Re-open and dismiss explicitly
        state.toggle(coords, "my_key") {}
        assertNotNull(state.tooltipData)
        state.dismiss()
        assertNull(state.tooltipData)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun testTooltipStateShowAndScheduleDismiss() = kotlinx.coroutines.test.runTest {
        val state = TooltipState()
        val coords = MockLayoutCoordinates()
        val data = TooltipData("Test tooltip", coords)

        state.show(data)
        assertEquals(data, state.tooltipData)

        // Schedule dismiss with 250ms
        state.scheduleDismiss(this, 250L)
        // Before delay expires, tooltip should still be active
        testScheduler.advanceTimeBy(100L)
        assertEquals(data, state.tooltipData)

        // After delay expires without popup hover, tooltip should be dismissed
        testScheduler.advanceTimeBy(160L)
        assertNull(state.tooltipData)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun testTooltipStateHoverPreservesTooltip() = kotlinx.coroutines.test.runTest {
        val state = TooltipState()
        val coords = MockLayoutCoordinates()
        val data = TooltipData("Hover tooltip", coords)

        state.show(data)
        state.scheduleDismiss(this, 250L)

        // User moves mouse into popup before 250ms passes
        testScheduler.advanceTimeBy(100L)
        state.isPopupHovered = true
        state.cancelDismiss()

        // Wait past original dismiss time
        testScheduler.advanceTimeBy(300L)
        // Remains visible because hover occurred and cancelDismiss was called
        assertEquals(data, state.tooltipData)

        // When mouse exits popup, schedule dismiss again
        state.isPopupHovered = false
        state.scheduleDismiss(this, 250L)

        testScheduler.advanceTimeBy(260L)
        assertNull(state.tooltipData)
    }
}

