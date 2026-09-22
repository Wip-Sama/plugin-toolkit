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
    fun testOrthogonalAutoUsesMiddleRouteOnlyForDirectConnections() {
        val directPoints = SplineMathUtils.computeOrthogonalPoints(
            listOf(Offset(0f, 0f), Offset(10f, 20f)),
            startHorizontal = true,
            endHorizontal = true,
            stepMode = OrthogonalStepMode.Auto
        )
        assertEquals(
            listOf(
                Offset(0f, 0f),
                Offset(5f, 0f),
                Offset(5f, 20f),
                Offset(10f, 20f)
            ),
            directPoints
        )

        val junctionConnectionPoints = SplineMathUtils.computeOrthogonalPoints(
            listOf(
                Offset(0f, 0f),
                Offset(10f, 20f)
            ),
            startHorizontal = true,
            endHorizontal = true,
            stepMode = OrthogonalStepMode.Auto,
            useMiddleRouteForDirectConnection = false
        )

        assertEquals(
            listOf(
                Offset(0f, 0f),
                Offset(10f, 0f),
                Offset(10f, 20f)
            ),
            junctionConnectionPoints
        )
    }

    @Test
    fun testOrthogonalAutoUsesSingleBendForJunctionConnections() {
        val junctionRoutes = listOf(
            SplineMathUtils.computeOrthogonalPoints(
                listOf(Offset(2450f, 1750f), Offset(3200f, 150f)),
                startHorizontal = false,
                endHorizontal = false,
                stepMode = OrthogonalStepMode.Auto,
                useMiddleRouteForDirectConnection = false
            ),
            SplineMathUtils.computeOrthogonalPoints(
                listOf(Offset(3750f, 2200f), Offset(4500f, -200f)),
                startHorizontal = false,
                endHorizontal = false,
                stepMode = OrthogonalStepMode.Auto,
                useMiddleRouteForDirectConnection = false
            )
        )

        assertEquals(
            listOf(
                Offset(2450f, 1750f),
                Offset(2450f, 150f),
                Offset(3200f, 150f)
            ),
            junctionRoutes.first()
        )
        assertEquals(
            listOf(
                Offset(3750f, 2200f),
                Offset(3750f, -200f),
                Offset(4500f, -200f)
            ),
            junctionRoutes.last()
        )
    }

    @Test
    fun testOrthogonalAutoJunctionContinuationIgnoresEndHeading() {
        val routes = listOf(
            SplineMathUtils.computeOrthogonalPoints(
                listOf(Offset(550f, 950f), Offset(900f, 700f)),
                startHorizontal = true,
                endHorizontal = true,
                stepMode = OrthogonalStepMode.Auto,
                useMiddleRouteForDirectConnection = false
            ),
            SplineMathUtils.computeOrthogonalPoints(
                listOf(Offset(800f, 1350f), Offset(2050f, 600f)),
                startHorizontal = true,
                endHorizontal = true,
                stepMode = OrthogonalStepMode.Auto,
                useMiddleRouteForDirectConnection = false
            )
        )

        assertEquals(
            listOf(Offset(550f, 950f), Offset(900f, 950f), Offset(900f, 700f)),
            routes.first()
        )
        assertEquals(
            listOf(Offset(800f, 1350f), Offset(2050f, 1350f), Offset(2050f, 600f)),
            routes.last()
        )
    }

    @Test
    fun testOrthogonalAutoSnapsSmallEndpointAxisDrift() {
        val route = SplineMathUtils.computeOrthogonalPoints(
            listOf(Offset(328.37332f, 1747.4736f), Offset(2450f, 1750f)),
            startHorizontal = true,
            endHorizontal = true,
            stepMode = OrthogonalStepMode.Auto
        )

        assertEquals(
            listOf(Offset(328.37332f, 1747.4736f), Offset(2450f, 1750f)),
            route
        )
    }

    @Test
    fun testLoggedLongJunctionRouteRemainsOrthogonalWhenZoomedOut() {
        val route = SplineMathUtils.computeOrthogonalPoints(
            listOf(Offset(-300f, 1850f), Offset(4300f, -1150f)),
            startHorizontal = true,
            endHorizontal = true,
            stepMode = OrthogonalStepMode.Auto,
            useMiddleRouteForDirectConnection = false
        )

        assertEquals(
            listOf(
                Offset(-300f, 1850f),
                Offset(4300f, 1850f),
                Offset(4300f, -1150f)
            ),
            route
        )

        val path = SplineMathUtils.buildConnectionPath(
            points = route.map { it * 0.05f },
            style = org.wip.plugintoolkit.features.settings.model.ConnectionCurveStyle.Orthogonal,
            scale = 0.05f
        )
        assertTrue(!path.isEmpty)
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
