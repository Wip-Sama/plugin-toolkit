package org.wip.plugintoolkit.features.flows

import androidx.compose.ui.geometry.Offset as ComposeOffset
import org.wip.plugintoolkit.features.flows.model.Offset as ModelOffset
import org.wip.plugintoolkit.features.flows.ui.snapToGrid
import kotlin.test.Test
import kotlin.test.assertEquals

class FlowNodeSnappingTest {

    @Test
    fun testModelOffsetSnapToGrid() {
        // Exact multiples of 50f
        assertEquals(ModelOffset(0f, 0f), ModelOffset(0f, 0f).snapToGrid(50f))
        assertEquals(ModelOffset(50f, 100f), ModelOffset(50f, 100f).snapToGrid(50f))

        // Values rounding down
        assertEquals(ModelOffset(50f, 50f), ModelOffset(62f, 74f).snapToGrid(50f))

        // Values rounding up
        assertEquals(ModelOffset(100f, 100f), ModelOffset(76f, 88f).snapToGrid(50f))

        // Negative coordinates and zero sign normalization
        assertEquals(ModelOffset(0f, 0f), ModelOffset(-10f, 15f).snapToGrid(50f))
        assertEquals(ModelOffset(-50f, -100f), ModelOffset(-45f, -98f).snapToGrid(50f))
    }

    @Test
    fun testComposeOffsetSnapToGrid() {
        // Exact multiples
        assertEquals(ComposeOffset(0f, 0f), ComposeOffset(0f, 0f).snapToGrid(50f))
        assertEquals(ComposeOffset(100f, 200f), ComposeOffset(100f, 200f).snapToGrid(50f))

        // Rounding
        assertEquals(ComposeOffset(50f, 100f), ComposeOffset(48f, 112f).snapToGrid(50f))

        // Negative values
        assertEquals(ComposeOffset(0f, 0f), ComposeOffset(-5f, 4f).snapToGrid(50f))
        assertEquals(ComposeOffset(-100f, -50f), ComposeOffset(-110f, -40f).snapToGrid(50f))
    }
}
