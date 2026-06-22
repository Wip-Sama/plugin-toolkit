package org.wip.plugintoolkit.features.flows.utils

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BoardMathUtilsTest {

    @Test
    fun testSplitCubicBezierInHalf() {
        // Given a horizontal line segment representing a bezier curve
        val start = Offset(0f, 0f)
        val end = Offset(100f, 0f)

        // When splitting it in half
        val (left, right) = BoardMathUtils.splitCubicBezierInHalf(start, end)

        // Then the left curve should end at the exact midpoint
        assertEquals(Offset(50f, 0f), left.p3, "Left curve should end at midpoint")
        
        // And the right curve should start at the exact midpoint
        assertEquals(Offset(50f, 0f), right.p0, "Right curve should start at midpoint")

        // And the control points should be correctly bounded
        assertTrue(left.p1.x > start.x && left.p1.x < left.p3.x, "Left CP1 should be bounded")
        assertTrue(right.p2.x > right.p0.x && right.p2.x < end.x, "Right CP2 should be bounded")
    }

    @Test
    fun testGetDistanceToBezier_pointOnCurve() {
        val start = Offset(0f, 0f)
        val end = Offset(100f, 100f)

        // Midpoint of the bezier curve
        val midpoint = BoardMathUtils.getBezierMidpoint(start, end)

        // When testing distance to a point exactly on the curve
        val distance = BoardMathUtils.getDistanceToBezier(midpoint, start, end)

        // Then distance should be very close to 0
        assertTrue(distance < 1f, "Distance to a point on the curve should be near zero (was $distance)")
    }

    @Test
    fun testGetDistanceToBezier_pointFarAway() {
        val start = Offset(0f, 0f)
        val end = Offset(100f, 0f)

        val farPoint = Offset(50f, 500f)

        val distance = BoardMathUtils.getDistanceToBezier(farPoint, start, end)

        // The point is 500 units away vertically
        assertTrue(distance > 450f, "Distance to a far point should be large (was $distance)")
    }
}
