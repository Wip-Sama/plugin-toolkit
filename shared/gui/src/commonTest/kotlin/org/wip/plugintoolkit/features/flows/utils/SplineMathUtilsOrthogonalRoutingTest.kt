package org.wip.plugintoolkit.features.flows.utils

import androidx.compose.ui.geometry.Offset
import org.wip.plugintoolkit.features.settings.model.OrthogonalStepMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SplineMathUtilsOrthogonalRoutingTest {

    @Test
    fun testOrthogonalAutoMinBreakMultiPointRouting() {
        val p0 = Offset(0f, 0f)
        val p1 = Offset(10f, 10f)
        val p2 = Offset(12f, 10f)
        val points = listOf(p0, p1, p2)

        val autoPoints = SplineMathUtils.computeOrthogonalPoints(
            points,
            startHorizontal = true,
            endHorizontal = true,
            stepMode = OrthogonalStepMode.Auto
        )

        // With the new DP algorithm, this avoids putting double curves at p1
        // It optimally arrives at p1 vertically from the bottom, and leaves horizontally to the right.
        assertEquals(4, autoPoints.size)
        assertEquals(Offset(0f, 0f), autoPoints[0])
        assertEquals(Offset(10f, 0f), autoPoints[1]) // STEP AFTER
        assertEquals(Offset(10f, 10f), autoPoints[2])
        assertEquals(Offset(12f, 10f), autoPoints[3])
    }

    @Test
    fun testOrthogonalNaturalHeadingVerticalRiseAndRoofLine() {
        // Vertical rise continuing straight up through intermediate waypoint to roof line
        val p0 = Offset(100f, 400f)
        val w1 = Offset(100f, 250f)
        val w2 = Offset(250f, 100f)
        val pEnd = Offset(400f, 100f)

        val pathPoints = SplineMathUtils.computeOrthogonalPoints(
            listOf(p0, w1, w2, pEnd),
            startHorizontal = false,
            endHorizontal = true,
            stepMode = OrthogonalStepMode.Auto
        )

        assertTrue(pathPoints.contains(Offset(100f, 100f)), "Must have corner at (100, 100) continuing straight rise")
        assertEquals(p0, pathPoints.first())
        assertEquals(pEnd, pathPoints.last())
    }
}
