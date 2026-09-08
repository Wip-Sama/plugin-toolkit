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
}
