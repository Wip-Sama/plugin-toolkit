package org.wip.plugintoolkit.features.colorpicker

import androidx.compose.ui.geometry.Offset
import org.wip.plugintoolkit.features.colorpicker.logic.BoundedPointStrategy
import org.wip.plugintoolkit.features.colorpicker.logic.MathHelper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MathHelperTest {

    @Test
    fun testGetLength() {
        val radius = 50f
        // Center has distance 0
        assertEquals(0f, MathHelper.getLength(50f, 50f, radius), 0.01f)

        // Point (50, 100) has distance 50
        assertEquals(50f, MathHelper.getLength(50f, 100f, radius), 0.01f)

        // Point (50 + 30, 50 + 40) has distance 50 (3-4-5 right triangle)
        assertEquals(50f, MathHelper.getLength(80f, 90f, radius), 0.01f)
    }

    @Test
    fun testGetBoundedPointEdgeStrategy() {
        val radius = 50f
        val x = 100f
        val y = 50f
        val length = MathHelper.getLength(x, y, radius) // 50f

        val bounded = MathHelper.getBoundedPointWithInRadius(
            x = x,
            y = y,
            length = length,
            radius = radius,
            strategy = BoundedPointStrategy.Edge
        )
        // Along +x direction from center (50, 50) at distance radius 50 -> (100, 50)
        assertEquals(100f, bounded.x, 0.01f)
        assertEquals(50f, bounded.y, 0.01f)
    }

    @Test
    fun testGetBoundedPointInsideStrategy() {
        val radius = 50f

        // Point already inside radius 50
        val insidePoint = MathHelper.getBoundedPointWithInRadius(
            x = 60f,
            y = 50f,
            length = 10f,
            radius = radius,
            strategy = BoundedPointStrategy.Inside
        )
        assertEquals(60f, insidePoint.x, 0.01f)
        assertEquals(50f, insidePoint.y, 0.01f)

        // Point outside radius 50
        val outsidePoint = MathHelper.getBoundedPointWithInRadius(
            x = 150f,
            y = 50f,
            length = 100f,
            radius = radius,
            strategy = BoundedPointStrategy.Inside
        )
        assertEquals(100f, outsidePoint.x, 0.01f)
        assertEquals(50f, outsidePoint.y, 0.01f)
    }

    @Test
    fun testGetBoundedPointOutsideStrategy() {
        val radius = 50f

        // Point already outside radius 50
        val outsidePoint = MathHelper.getBoundedPointWithInRadius(
            x = 150f,
            y = 50f,
            length = 100f,
            radius = radius,
            strategy = BoundedPointStrategy.Outside
        )
        assertEquals(150f, outsidePoint.x, 0.01f)
        assertEquals(50f, outsidePoint.y, 0.01f)

        // Point inside radius 50 pushed to boundary
        val insidePoint = MathHelper.getBoundedPointWithInRadius(
            x = 60f,
            y = 50f,
            length = 10f,
            radius = radius,
            strategy = BoundedPointStrategy.Outside
        )
        assertEquals(100f, insidePoint.x, 0.01f)
        assertEquals(50f, insidePoint.y, 0.01f)
    }
}
