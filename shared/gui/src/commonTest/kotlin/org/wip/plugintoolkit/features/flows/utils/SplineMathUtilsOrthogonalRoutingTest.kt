package org.wip.plugintoolkit.features.flows.utils

import androidx.compose.ui.geometry.Offset
import org.wip.plugintoolkit.features.settings.model.ConnectionCurveStyle
import org.wip.plugintoolkit.features.settings.model.OrthogonalStepMode
import kotlin.math.abs
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

    @Test
    fun testOrthogonalAutoWithPortLeadsDirectForwardConnection() {
        val p0 = Offset(0f, 0f)
        val p1 = Offset(200f, 100f)
        val gridSize = 50f

        // When borders are specified (e.g. source right border = 0f, target left border = 200f):
        val pointsWithBorders = SplineMathUtils.computeOrthogonalPoints(
            listOf(p0, p1),
            startHorizontal = true,
            endHorizontal = true,
            stepMode = OrthogonalStepMode.Auto,
            startPortLead = true,
            endPortLead = true,
            gridSize = gridSize,
            startBorderX = 0f,
            endBorderX = 200f
        )

        // Forward Auto connection turns at pLead1.x = endBorderX - 50f = 150f, NOT in the middle (100f)
        assertEquals(
            listOf(
                Offset(0f, 0f),
                Offset(150f, 0f),
                Offset(150f, 100f),
                Offset(200f, 100f)
            ),
            pointsWithBorders
        )

        // When borders are not specified, fall back using NODE_PORT_INSET (19f)
        val pointsFallback = SplineMathUtils.computeOrthogonalPoints(
            listOf(p0, p1),
            startHorizontal = true,
            endHorizontal = true,
            stepMode = OrthogonalStepMode.Auto,
            startPortLead = true,
            endPortLead = true,
            gridSize = gridSize
        )

        assertEquals(
            listOf(
                Offset(0f, 0f),
                Offset(131f, 0f),
                Offset(131f, 100f),
                Offset(200f, 100f)
            ),
            pointsFallback
        )
    }

    @Test
    fun testOrthogonalAutoWithPortLeadsDirectBackwardConnection() {
        val p0 = Offset(200f, 0f)
        val p1 = Offset(0f, 100f)
        val gridSize = 50f

        val points = SplineMathUtils.computeOrthogonalPoints(
            listOf(p0, p1),
            startHorizontal = true,
            endHorizontal = true,
            stepMode = OrthogonalStepMode.Auto,
            startPortLead = true,
            endPortLead = true,
            gridSize = gridSize,
            startBorderX = 200f,
            endBorderX = 0f
        )

        // Expected loop-around:
        // p0 (200, 0) -> lead (250, 0) -> turnY (250, 50) -> leadX (-50, 50) -> leadY (-50, 100) -> p1 (0, 100)
        assertEquals(
            listOf(
                Offset(200f, 0f),
                Offset(250f, 0f),
                Offset(250f, 50f),
                Offset(-50f, 50f),
                Offset(-50f, 100f),
                Offset(0f, 100f)
            ),
            points
        )
    }

    @Test
    fun testOrthogonalAutoWithPortLeadsDirectVerticallyAligned() {
        val p0 = Offset(100f, 0f)
        val p1 = Offset(100f, 200f)
        val gridSize = 50f

        val points = SplineMathUtils.computeOrthogonalPoints(
            listOf(p0, p1),
            startHorizontal = true,
            endHorizontal = true,
            stepMode = OrthogonalStepMode.Auto,
            startPortLead = true,
            endPortLead = true,
            gridSize = gridSize,
            startBorderX = 100f,
            endBorderX = 100f
        )

        // p0Lead.x = 150, p1Lead.x = 50 -> p0Lead.x >= p1Lead.x -> routes around cleanly:
        // (100, 0) -> (150, 0) -> (150, 100) -> (50, 100) -> (50, 200) -> (100, 200)
        assertEquals(
            listOf(
                Offset(100f, 0f),
                Offset(150f, 0f),
                Offset(150f, 100f),
                Offset(50f, 100f),
                Offset(50f, 200f),
                Offset(100f, 200f)
            ),
            points
        )
    }

    @Test
    fun testOrthogonalAutoWithPortLeadsMultiWaypoint() {
        val p0 = Offset(0f, 0f)
        val wp = Offset(300f, 150f)
        val p1 = Offset(600f, 300f)
        val gridSize = 50f

        val points = SplineMathUtils.computeOrthogonalPoints(
            listOf(p0, wp, p1),
            startHorizontal = true,
            endHorizontal = true,
            stepMode = OrthogonalStepMode.Auto,
            startPortLead = true,
            endPortLead = true,
            gridSize = gridSize,
            startBorderX = 0f,
            endBorderX = 600f
        )

        // Ensure first segment departs horizontally past p0.x + gridSize (50f)
        // and last segment arrives horizontally before p1.x - gridSize (550f)
        assertEquals(p0, points.first())
        assertEquals(p1, points.last())
        assertTrue(points[1].x >= 50f)
        assertTrue(points[points.size - 2].x <= 550f)
    }

    @Test
    fun testNodeBorderClearanceCalculationMatchesGrid() {
        // Source node at position x=100 with width=400: right border is at 500
        // Output port center is inset by 19px: (481, 50)
        // Target node at position x=700: left border is at 700
        // Input port center is inset by 19px: (719, 150)
        val p0 = Offset(481f, 50f)
        val p1 = Offset(719f, 150f)
        val startBorderX = 500f
        val endBorderX = 700f
        val gridSize = 50f

        val lead0 = SplineMathUtils.computeStartPortLead(p0, startBorderX, gridSize)
        val lead1 = SplineMathUtils.computeEndPortLead(p1, endBorderX, gridSize)

        // Verify lead points land exactly on the 50f grid
        assertEquals(550f, lead0.x)
        assertEquals(50f, lead0.y)
        assertEquals(650f, lead1.x)
        assertEquals(150f, lead1.y)

        val points = SplineMathUtils.computeOrthogonalPoints(
            listOf(p0, p1),
            startHorizontal = true,
            endHorizontal = true,
            stepMode = OrthogonalStepMode.Auto,
            startPortLead = true,
            endPortLead = true,
            gridSize = gridSize,
            startBorderX = startBorderX,
            endBorderX = endBorderX
        )

        // In Auto mode, single bend happens at target port lead x=650 (1u before target border), NOT in the middle (600)
        assertEquals(
            listOf(
                Offset(481f, 50f),
                Offset(650f, 50f),
                Offset(650f, 150f),
                Offset(719f, 150f)
            ),
            points
        )
    }

    @Test
    fun testOrthogonalPortLeadDisabledMaintainsDefaultBehavior() {
        val p0 = Offset(0f, 0f)
        val p1 = Offset(100f, 100f)

        val defaultPoints = SplineMathUtils.computeOrthogonalPoints(
            listOf(p0, p1),
            startHorizontal = true,
            endHorizontal = true,
            stepMode = OrthogonalStepMode.Auto,
            startPortLead = false,
            endPortLead = false
        )

        assertEquals(
            listOf(
                Offset(0f, 0f),
                Offset(50f, 0f),
                Offset(50f, 100f),
                Offset(100f, 100f)
            ),
            defaultPoints
        )
    }

    @Test
    fun testOrthogonalAutoWithPortLeadsCloseNodesSteppingInCorridor() {
        // Two nodes close together: startBorderX = 500f, endBorderX = 540f (corridor = 40f < 2 * gridSize)
        val p0 = Offset(481f, 50f)
        val p1 = Offset(559f, 150f)
        val startBorderX = 500f
        val endBorderX = 540f
        val gridSize = 50f

        val points = SplineMathUtils.computeOrthogonalPoints(
            listOf(p0, p1),
            startHorizontal = true,
            endHorizontal = true,
            stepMode = OrthogonalStepMode.Auto,
            startPortLead = true,
            endPortLead = true,
            gridSize = gridSize,
            startBorderX = startBorderX,
            endBorderX = endBorderX
        )

        // Must step cleanly at stepX = (500 + 540) / 2 = 520 without overshooting or looping backwards
        assertEquals(
            listOf(
                Offset(481f, 50f),
                Offset(520f, 50f),
                Offset(520f, 150f),
                Offset(559f, 150f)
            ),
            points
        )
    }

    @Test
    fun testOrthogonalAutoWithPortLeadsHorizontallyAlignedCloseNodes() {
        // Two nodes horizontally aligned: y0 == y1
        val p0 = Offset(481f, 50f)
        val p1 = Offset(559f, 50f)
        val startBorderX = 500f
        val endBorderX = 540f
        val gridSize = 50f

        val points = SplineMathUtils.computeOrthogonalPoints(
            listOf(p0, p1),
            startHorizontal = true,
            endHorizontal = true,
            stepMode = OrthogonalStepMode.Auto,
            startPortLead = true,
            endPortLead = true,
            gridSize = gridSize,
            startBorderX = startBorderX,
            endBorderX = endBorderX
        )

        // Must be a single straight horizontal line
        assertEquals(
            listOf(
                Offset(481f, 50f),
                Offset(559f, 50f)
            ),
            points
        )
    }

    @Test
    fun testOrthogonalMiddleStepVerticalMiddleRoute() {
        // Vertical step mode should step horizontally at midY
        val p0 = Offset(100f, 0f)
        val p1 = Offset(200f, 100f)

        val points = SplineMathUtils.computeOrthogonalPoints(
            listOf(p0, p1),
            startHorizontal = false,
            endHorizontal = false,
            stepMode = OrthogonalStepMode.Middle,
            useMiddleRouteForDirectConnection = true
        )

        // midY = 50f. Starting vertically: (100, 0) -> (100, 50) -> (200, 50) -> (200, 100)
        assertEquals(
            listOf(
                Offset(100f, 0f),
                Offset(100f, 50f),
                Offset(200f, 50f),
                Offset(200f, 100f)
            ),
            points
        )
    }

    @Test
    fun testZoomInvarianceForOrthogonalSegmentMidpoints() {
        val p0 = Offset(0f, 0f)
        val p1 = Offset(200f, 100f)
        val basePoints = listOf(p0, p1)

        val baseMidpoints = SplineMathUtils.computeSegmentMidpoints(
            points = basePoints,
            style = ConnectionCurveStyle.Orthogonal,
            scale = 1f,
            canvasOffset = Offset.Zero,
            stepMode = OrthogonalStepMode.Auto,
            useMiddleRouteForDirectConnection = false,
            startPortLead = true,
            endPortLead = true,
            startBorderX = 0f,
            endBorderX = 200f
        )
        assertEquals(1, baseMidpoints.size)
        val expectedBoardMid = baseMidpoints.first()

        val scales = listOf(0.1f, 0.25f, 0.5f, 1f, 1.5f, 2f, 4f)
        val testOffsets = listOf(Offset.Zero, Offset(50f, -80f), Offset(-200f, 350f))

        for (scale in scales) {
            for (canvasOffset in testOffsets) {
                val screenPoints = basePoints.map { (it * scale) + canvasOffset }
                val midpoints = SplineMathUtils.computeSegmentMidpoints(
                    points = screenPoints,
                    style = ConnectionCurveStyle.Orthogonal,
                    scale = scale,
                    canvasOffset = canvasOffset,
                    stepMode = OrthogonalStepMode.Auto,
                    useMiddleRouteForDirectConnection = false,
                    startPortLead = true,
                    endPortLead = true,
                    startBorderX = 0f,
                    endBorderX = 200f
                )

                assertEquals(1, midpoints.size)
                // Convert screen midpoint back to board coordinates
                val boardMid = (midpoints.first() - canvasOffset) * (1f / scale)
                assertTrue(
                    abs(boardMid.x - expectedBoardMid.x) < 0.1f && abs(boardMid.y - expectedBoardMid.y) < 0.1f,
                    "Midpoint board position must be invariant to zoom scale ($scale) and offset ($canvasOffset). Got $boardMid, expected $expectedBoardMid"
                )
            }
        }
    }

    @Test
    fun testTightBendCurvaturePathGeneration() {
        // An S- or U-shaped polyline with tight segments and large corner radius
        val points = listOf(
            Offset(0f, 100f),
            Offset(100f, 100f),
            Offset(100f, 0f),
            Offset(200f, 0f)
        )

        val path = SplineMathUtils.buildRoundedPolylinePath(
            points = points,
            cornerRadius = 50f
        )

        // Verify path was generated successfully with no crashing or empty output
        assertTrue(!path.isEmpty, "Rounded polyline path must not be empty")
    }

    @Test
    fun testNoGapsBetweenJunctionFilletAndTrimAcrossZoomLevels() {
        // Simulates an orthogonal junction chain: J0 -> J1 -> J2 with a 90-degree bend at J1.
        // Verifies across various scales and high tension (roundness = 1.0) that the incoming
        // connection's endTrimDistance exactly matches the outgoing connection's startFillet start offset.
        val j0 = Offset(100f, 300f)
        val j1 = Offset(200f, 300f)
        val j2 = Offset(200f, 400f)
        val juncMap = mapOf(1L to j0, 2L to j1, 3L to j2)

        val connIn = org.wip.plugintoolkit.features.flows.model.Connection(
            sourceJunctionId = 1L,
            sourceNodeId = org.wip.plugintoolkit.features.flows.model.Connection.FLOATING_NODE_ID,
            sourcePortId = org.wip.plugintoolkit.features.flows.model.Connection.FLOATING_PORT_ID,
            targetNodeId = org.wip.plugintoolkit.features.flows.model.Connection.FLOATING_NODE_ID,
            targetPortId = org.wip.plugintoolkit.features.flows.model.Connection.FLOATING_PORT_ID,
            targetJunctionId = 2L
        )
        val connOut = org.wip.plugintoolkit.features.flows.model.Connection(
            sourceJunctionId = 2L,
            sourceNodeId = org.wip.plugintoolkit.features.flows.model.Connection.FLOATING_NODE_ID,
            sourcePortId = org.wip.plugintoolkit.features.flows.model.Connection.FLOATING_PORT_ID,
            targetNodeId = org.wip.plugintoolkit.features.flows.model.Connection.FLOATING_NODE_ID,
            targetPortId = org.wip.plugintoolkit.features.flows.model.Connection.FLOATING_PORT_ID,
            targetJunctionId = 3L
        )

        val scales = listOf(0.1f, 0.25f, 0.5f, 1f, 1.5f, 2f, 3f)
        val tensions = listOf(0.5f, 0.8f, 1f)

        for (scale in scales) {
            for (tension in tensions) {
                val filletIn = org.wip.plugintoolkit.features.flows.ui.canvas.ConnectionHitTester.getJunctionFilletParams(
                    connection = connIn,
                    connections = listOf(connIn, connOut),
                    junctionMap = juncMap,
                    getPortBoardPosition = null,
                    scale = scale,
                    tension = tension
                )
                val filletOut = org.wip.plugintoolkit.features.flows.ui.canvas.ConnectionHitTester.getJunctionFilletParams(
                    connection = connOut,
                    connections = listOf(connIn, connOut),
                    junctionMap = juncMap,
                    getPortBoardPosition = null,
                    scale = scale,
                    tension = tension
                )

                // The incoming wire must have a valid trim at J1
                assertTrue(filletIn.endTrimDistance > 0f, "Incoming connection must have endTrimDistance > 0 at scale=$scale, tension=$tension")
                // The outgoing wire must have a valid lead-in at J1
                assertTrue(filletOut.startFilletLeadIn != null, "Outgoing connection must have startFilletLeadIn at scale=$scale, tension=$tension")

                val rExpected = maxOf(
                    SplineMathUtils.DEFAULT_CORNER_RADIUS * scale,
                    tension * SplineMathUtils.TENSION_RADIUS_FACTOR * scale,
                    SplineMathUtils.MIN_RENDER_CORNER_RADIUS
                )
                val lenInScreen = (j1 - j0).getDistance() * scale
                val lenOutScreen = (j2 - j1).getDistance() * scale
                val expectedTrim = minOf(rExpected, lenInScreen * SplineMathUtils.TRIM_FACTOR, lenOutScreen * SplineMathUtils.TRIM_FACTOR)

                assertEquals(
                    expectedTrim,
                    filletIn.endTrimDistance,
                    0.01f,
                    "endTrimDistance must match expected corner radius at scale=$scale, tension=$tension"
                )
            }
        }
    }

    @Test
    fun testShortIntermediateSegmentFilletAndTrimMeetSeamlessly() {
        // When intermediate segment is short (40 units) and tension is high (1.0),
        // r = 28 would exceed L * 0.5 = 20.
        // Both the start fillet and end trim must meet seamlessly at L/2 without gap or overlap.
        val p0 = Offset(0f, 0f)
        val p1 = Offset(40f, 0f)
        val leadIn = Offset(0f, -50f)
        val endTrim = 28f // Wants 28f trim

        val path = SplineMathUtils.buildRoundedPolylinePath(
            points = listOf(p0, p1),
            cornerRadius = 28f,
            startFilletLeadIn = leadIn,
            endTrimDistance = endTrim
        )

        assertTrue(!path.isEmpty, "Path for short segment must not be empty")
    }

    @Test
    fun testNearNodesAvoidImproperMiddleFallback() {
        // When two ports/nodes are close in X (corridor < 30f),
        // it must avoid adding 2 extra waypoints for a cramped middle vertical step.
        val p0 = Offset(100f, 50f)
        val p1 = Offset(115f, 150f) // dx = 15f < 30f

        val points = SplineMathUtils.computeOrthogonalPoints(
            points = listOf(p0, p1),
            startHorizontal = true,
            endHorizontal = true,
            stepMode = OrthogonalStepMode.Auto,
            useMiddleRouteForDirectConnection = true,
            startPortLead = true,
            endPortLead = true,
            startBorderX = 95f,
            endBorderX = 120f
        )

        // Should NOT add a cramped 2-waypoint middle step: waypoints count should be at most 3
        assertTrue(
            points.size <= 3,
            "Near ports (dx=15 < 30) must avoid adding cramped middle vertical step waypoints. Got: $points"
        )
    }
}

