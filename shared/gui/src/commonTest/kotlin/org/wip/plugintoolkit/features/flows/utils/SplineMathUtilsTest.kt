package org.wip.plugintoolkit.features.flows.utils

import androidx.compose.ui.geometry.Offset
import org.wip.plugintoolkit.features.settings.model.ConnectionCurveStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SplineMathUtilsTest {

    @Test
    fun testCubicSegmentEvaluation() {
        val seg = SplineMathUtils.CubicSegment(
            start = Offset(0f, 0f),
            control1 = Offset(25f, 0f),
            control2 = Offset(75f, 100f),
            end = Offset(100f, 100f)
        )

        val at0 = seg.evaluate(0f)
        assertEquals(0f, at0.x, 0.001f)
        assertEquals(0f, at0.y, 0.001f)

        val at1 = seg.evaluate(1f)
        assertEquals(100f, at1.x, 0.001f)
        assertEquals(100f, at1.y, 0.001f)

        val atMid = seg.evaluate(0.5f)
        assertEquals(50f, atMid.x, 0.001f)
        assertEquals(50f, atMid.y, 0.001f)
    }

    @Test
    fun testCardinalSplineSegmentsTwoPoints() {
        val points = listOf(Offset(0f, 0f), Offset(100f, 0f))
        val segments = SplineMathUtils.computeCardinalSplineSegments(points, tension = 0.5f)
        assertEquals(1, segments.size)
        assertEquals(Offset(0f, 0f), segments[0].start)
        assertEquals(Offset(100f, 0f), segments[0].end)
    }

    @Test
    fun testCardinalSplineSegmentsMultiplePoints() {
        val points = listOf(
            Offset(0f, 0f),
            Offset(50f, 50f),
            Offset(100f, 0f),
            Offset(150f, 50f)
        )
        val segments = SplineMathUtils.computeCardinalSplineSegments(points, tension = 0.5f)
        assertEquals(3, segments.size)
        assertEquals(points[0], segments[0].start)
        assertEquals(points[1], segments[0].end)
        assertEquals(points[1], segments[1].start)
        assertEquals(points[2], segments[1].end)
        assertEquals(points[2], segments[2].start)
        assertEquals(points[3], segments[2].end)
    }

    @Test
    fun testDistanceToSegment() {
        val a = Offset(0f, 0f)
        val b = Offset(100f, 0f)

        // Point directly on segment
        val distOnSeg = SplineMathUtils.distanceToSegment(Offset(50f, 0f), a, b)
        assertEquals(0f, distOnSeg, 0.001f)

        // Point perpendicular to segment
        val distPerp = SplineMathUtils.distanceToSegment(Offset(50f, 25f), a, b)
        assertEquals(25f, distPerp, 0.001f)

        // Point beyond segment start
        val distBefore = SplineMathUtils.distanceToSegment(Offset(-10f, 0f), a, b)
        assertEquals(10f, distBefore, 0.001f)

        // Point beyond segment end
        val distAfter = SplineMathUtils.distanceToSegment(Offset(120f, 0f), a, b)
        assertEquals(20f, distAfter, 0.001f)
    }

    @Test
    fun testDistanceToPathAndClosestPointProjection() {
        val points = listOf(
            Offset(0f, 0f),
            Offset(100f, 0f),
            Offset(100f, 100f)
        )

        // Closest to first segment
        val p1 = Offset(40f, 15f)
        val dist1 = SplineMathUtils.distanceToPath(p1, points)
        assertEquals(15f, dist1, 0.001f)
        val proj1 = SplineMathUtils.findClosestPointOnPath(p1, points)
        assertEquals(40f, proj1.x, 0.001f)
        assertEquals(0f, proj1.y, 0.001f)

        // Closest to second segment
        val p2 = Offset(110f, 60f)
        val dist2 = SplineMathUtils.distanceToPath(p2, points)
        assertEquals(10f, dist2, 0.001f)
        val proj2 = SplineMathUtils.findClosestPointOnPath(p2, points)
        assertEquals(100f, proj2.x, 0.001f)
        assertEquals(60f, proj2.y, 0.001f)
    }

    @Test
    fun testSampleConnectionPoints() {
        val points = listOf(Offset(0f, 0f), Offset(100f, 100f))

        val straightSamples = SplineMathUtils.sampleConnectionPoints(
            points,
            ConnectionCurveStyle.Straight
        )
        assertEquals(2, straightSamples.size)

        val splineSamples = SplineMathUtils.sampleConnectionPoints(
            points,
            ConnectionCurveStyle.CardinalSpline,
            tension = 0.5f,
            samplesPerSegment = 10
        )
        assertEquals(11, splineSamples.size)
        assertEquals(Offset(0f, 0f), splineSamples.first())
        assertEquals(Offset(100f, 100f), splineSamples.last())

        val orthoSamples = SplineMathUtils.sampleConnectionPoints(
            points,
            ConnectionCurveStyle.Orthogonal
        )
        assertTrue(orthoSamples.size >= 4)
        assertEquals(Offset(0f, 0f), orthoSamples.first())
        assertEquals(Offset(100f, 100f), orthoSamples.last())
    }

    @Test
    fun testSnapToStraightAngle() {
        val start = Offset(100f, 100f)

        // Horizontal right (0 degrees)
        val right = Offset(200f, 105f)
        val snappedRight = SplineMathUtils.snapToStraightAngle(start, right)
        assertEquals(200.12f, snappedRight.x, 0.5f)
        assertEquals(100f, snappedRight.y, 0.5f)

        // Vertical down (90 degrees)
        val down = Offset(104f, 200f)
        val snappedDown = SplineMathUtils.snapToStraightAngle(start, down)
        assertEquals(100f, snappedDown.x, 0.5f)
        assertEquals(200.08f, snappedDown.y, 0.5f)

        // Diagonal (45 degrees)
        val diag = Offset(195f, 205f)
        val snappedDiag = SplineMathUtils.snapToStraightAngle(start, diag)
        val dx = snappedDiag.x - start.x
        val dy = snappedDiag.y - start.y
        assertEquals(dx, dy, 0.01f)
    }

    @Test
    fun testHarmonizedSplineTangents() {
        val points = listOf(
            Offset(0f, 100f),
            Offset(100f, 200f),
            Offset(200f, 100f)
        )
        val segments = SplineMathUtils.computeHarmonizedSplineSegments(points, tension = 0.5f, startHorizontal = true, endHorizontal = true)
        assertEquals(2, segments.size)

        val seg0 = segments[0]
        val seg1 = segments[1]

        // Start must depart horizontally (y identical to start.y)
        assertEquals(seg0.start.y, seg0.control1.y, 0.001f)

        // End must arrive horizontally (y identical to end.y)
        assertEquals(seg1.end.y, seg1.control2.y, 0.001f)

        // Intermediate point must be C1 continuous: tangent vector (p2 - p0) has dy = 0, so both controls at p1 have y == p1.y
        assertEquals(points[1].y, seg0.control2.y, 0.001f)
        assertEquals(points[1].y, seg1.control1.y, 0.001f)
    }

    @Test
    fun testSnapToOrthogonal() {
        val start = Offset(50f, 50f)

        // Horizontal dominance
        val currH = Offset(120f, 60f)
        val snappedH = SplineMathUtils.snapToOrthogonal(start, currH)
        assertEquals(120f, snappedH.x)
        assertEquals(50f, snappedH.y)

        // Vertical dominance
        val currV = Offset(60f, 150f)
        val snappedV = SplineMathUtils.snapToOrthogonal(start, currV)
        assertEquals(50f, snappedV.x)
        assertEquals(150f, snappedV.y)
    }

    @Test
    fun testComputeSegmentMidpoints() {
        val points = listOf(
            Offset(0f, 0f),
            Offset(100f, 0f),
            Offset(100f, 100f)
        )

        val straightMidpoints = SplineMathUtils.computeSegmentMidpoints(points, ConnectionCurveStyle.Straight)
        assertEquals(2, straightMidpoints.size)
        assertEquals(Offset(50f, 0f), straightMidpoints[0])
        assertEquals(Offset(100f, 50f), straightMidpoints[1])

        val splineMidpoints = SplineMathUtils.computeSegmentMidpoints(points, ConnectionCurveStyle.CardinalSpline)
        assertEquals(2, splineMidpoints.size)
        assertTrue(splineMidpoints[0].x in 0f..100f)
        assertTrue(splineMidpoints[1].y in 0f..100f)
    }

    @Test
    fun testBuildRoundedPolylinePath() {
        val points = listOf(
            Offset(0f, 0f),
            Offset(100f, 0f),
            Offset(100f, 100f)
        )
        val path = SplineMathUtils.buildRoundedPolylinePath(points, cornerRadius = 8f)
        assertTrue(!path.isEmpty)
    }

    @Test
    fun testSingleMidpointCorridorRouting() {
        val pOut = Offset(100f, 100f)
        val junc = Offset(300f, 200f)
        val pIn = Offset(500f, 300f)

        // Unified 3-point orthogonal path through midpoint
        val unifiedPoints = SplineMathUtils.computeOrthogonalPoints(
            listOf(pOut, junc, pIn),
            startHorizontal = true,
            endHorizontal = true
        )
        // Must form a single vertical corridor at x = 300f:
        // (100, 100) -> (300, 100) -> (300, 300) -> (500, 300)
        assertEquals(4, unifiedPoints.size)
        assertEquals(Offset(100f, 100f), unifiedPoints[0])
        assertEquals(Offset(300f, 100f), unifiedPoints[1])
        assertEquals(Offset(300f, 300f), unifiedPoints[2])
        assertEquals(Offset(500f, 300f), unifiedPoints[3])

        // Split connections meeting at junction:
        // Connection 1: output to junction (arrives vertically)
        val c1 = SplineMathUtils.computeOrthogonalPoints(listOf(pOut, junc), startHorizontal = true, endHorizontal = false)
        assertEquals(listOf(Offset(100f, 100f), Offset(300f, 100f), Offset(300f, 200f)), c1)

        // Connection 2: junction to input (departs vertically)
        val c2 = SplineMathUtils.computeOrthogonalPoints(listOf(junc, pIn), startHorizontal = false, endHorizontal = true)
        assertEquals(listOf(Offset(300f, 200f), Offset(300f, 300f), Offset(500f, 300f)), c2)

        // Both meet seamlessly along x = 300f without any kink or S-jog
        assertEquals(c1.last(), c2.first())
        assertEquals(300f, c1[1].x)
        assertEquals(300f, c1[2].x)
        assertEquals(300f, c2[0].x)
        assertEquals(300f, c2[1].x)
    }

    @Test
    fun testZoomInvariantOrthogonalPoints() {
        val boardPoints = listOf(Offset(100f, 100f), Offset(300f, 200f), Offset(500f, 300f))
        val baseOrtho = SplineMathUtils.computeOrthogonalPoints(boardPoints, startHorizontal = true, endHorizontal = true)

        // Across scales 0.5x, 1.0x, 2.0x, the resulting board topology is identical
        for (scale in listOf(0.5f, 1.0f, 1.5f, 2.0f, 3.0f)) {
            val scaledBoardPoints = boardPoints.map { it * scale }
            val scaledOrtho = SplineMathUtils.computeOrthogonalPoints(scaledBoardPoints, startHorizontal = true, endHorizontal = true)
            assertEquals(baseOrtho.size, scaledOrtho.size)
            for (i in baseOrtho.indices) {
                assertEquals(baseOrtho[i].x * scale, scaledOrtho[i].x, 0.5f)
                assertEquals(baseOrtho[i].y * scale, scaledOrtho[i].y, 0.5f)
            }
        }
    }

    @Test
    fun testHarmonizedSplineJunctionTangentAlignment() {
        val p0 = Offset(100f, 100f)
        val junc = Offset(300f, 200f)
        val p1 = Offset(500f, 300f)

        // C1 segment approaching junction
        val segsIn = SplineMathUtils.computeHarmonizedSplineSegments(listOf(p0, junc), tension = 0.5f, startHorizontal = true, endHorizontal = false)
        // C1 segment departing junction
        val segsOut = SplineMathUtils.computeHarmonizedSplineSegments(listOf(junc, p1), tension = 0.5f, startHorizontal = false, endHorizontal = true)

        assertEquals(1, segsIn.size)
        assertEquals(1, segsOut.size)

        // Tangent approaching junction (control2 -> end)
        val inTangent = segsIn[0].end - segsIn[0].control2
        // Tangent departing junction (start -> control1)
        val outTangent = segsOut[0].control1 - segsOut[0].start

        // Both must point in the positive forward direction (x > 0 and y > 0)
        assertTrue(inTangent.x > 0f)
        assertTrue(inTangent.y > 0f)
        assertTrue(outTangent.x > 0f)
        assertTrue(outTangent.y > 0f)
    }

    @Test
    fun testOrthogonalStepModes() {
        val start = Offset(100f, 100f)
        val end = Offset(300f, 200f)
        val points = listOf(start, end)

        // Middle step mode: intermediate corner at x = 200f
        val midPoints = SplineMathUtils.computeOrthogonalPoints(points, startHorizontal = true, endHorizontal = true, stepMode = org.wip.plugintoolkit.features.settings.model.OrthogonalStepMode.Middle)
        assertEquals(4, midPoints.size)
        assertEquals(200f, midPoints[1].x)
        assertEquals(100f, midPoints[1].y)
        assertEquals(200f, midPoints[2].x)
        assertEquals(200f, midPoints[2].y)

        // Before step mode (PDF 1): moves Y first then X -> corner at (start.x, end.y) = (100, 200)
        val beforePoints = SplineMathUtils.computeOrthogonalPoints(points, startHorizontal = true, endHorizontal = true, stepMode = org.wip.plugintoolkit.features.settings.model.OrthogonalStepMode.Before)
        assertEquals(3, beforePoints.size)
        assertEquals(start, beforePoints.first())
        assertEquals(Offset(100f, 200f), beforePoints[1])
        assertEquals(end, beforePoints.last())

        // After step mode (PDF 1): moves X first then Y -> corner at (end.x, start.y) = (300, 100)
        val afterPoints = SplineMathUtils.computeOrthogonalPoints(points, startHorizontal = true, endHorizontal = true, stepMode = org.wip.plugintoolkit.features.settings.model.OrthogonalStepMode.After)
        assertEquals(3, afterPoints.size)
        assertEquals(start, afterPoints.first())
        assertEquals(Offset(300f, 100f), afterPoints[1])
        assertEquals(end, afterPoints.last())

        // Auto step mode (PDF 1): for 2 points between horizontal ports, uses smooth midpoint routing
        val autoPoints = SplineMathUtils.computeOrthogonalPoints(points, startHorizontal = true, endHorizontal = true, stepMode = org.wip.plugintoolkit.features.settings.model.OrthogonalStepMode.Auto)
        assertEquals(4, autoPoints.size)
        assertEquals(200f, autoPoints[1].x)
        assertEquals(200f, autoPoints[2].x)
    }

    @Test
    fun testOrthogonalAutoMinBreakMultiPointRouting() {
        // Multi-waypoint path as in PDF 1
        val p0 = Offset(100f, 100f)
        val p1 = Offset(200f, 300f)
        val p2 = Offset(400f, 400f)
        val p3 = Offset(600f, 200f)
        val points = listOf(p0, p1, p2, p3)

        val autoPoints = SplineMathUtils.computeOrthogonalPoints(
            points,
            startHorizontal = true,
            endHorizontal = true,
            stepMode = org.wip.plugintoolkit.features.settings.model.OrthogonalStepMode.Auto
        )

        // In Auto mode, every segment inherits the incoming direction from the preceding segment,
        // avoiding breaks at intermediate nodes (tangent continuity through p1 and p2).
        assertTrue(autoPoints.size >= 4)
        assertEquals(p0, autoPoints.first())
        assertEquals(p3, autoPoints.last())

        // Ensure all segments are strictly horizontal or vertical
        for (i in 0 until autoPoints.size - 1) {
            val a = autoPoints[i]
            val b = autoPoints[i + 1]
            val isH = abs(a.y - b.y) < 0.2f
            val isV = abs(a.x - b.x) < 0.2f
            assertTrue(isH || isV, "Segment from $a to $b must be strictly horizontal or vertical")
        }
    }

    @Test
    fun testOrthogonalNaturalHeadingVerticalRiseAndRoofLine() {
        // Vertical rise continuing straight up through intermediate waypoint to roof line (Top Red Section)
        val p0 = Offset(100f, 400f)
        val w1 = Offset(100f, 250f)
        val w2 = Offset(250f, 100f)
        val pEnd = Offset(400f, 100f)

        val pathPoints = SplineMathUtils.computeOrthogonalPoints(
            listOf(p0, w1, w2, pEnd),
            startHorizontal = false,
            endHorizontal = true,
            stepMode = org.wip.plugintoolkit.features.settings.model.OrthogonalStepMode.Auto
        )

        assertTrue(pathPoints.contains(Offset(100f, 100f)), "Must have corner at (100, 100) continuing straight rise")
        assertEquals(p0, pathPoints.first())
        assertEquals(pEnd, pathPoints.last())
    }

    @Test
    fun testOrthogonalNaturalHeadingFloorSweepRight() {
        // Lower loop dropping down, sweeping across bottom floor, then rising (Bottom Red Section)
        val p0 = Offset(300f, 300f)
        val wFloor1 = Offset(300f, 500f)
        val wFloor2 = Offset(500f, 500f)
        val pTarget = Offset(550f, 420f)

        val pathPoints = SplineMathUtils.computeOrthogonalPoints(
            listOf(p0, wFloor1, wFloor2, pTarget),
            startHorizontal = false,
            endHorizontal = false,
            stepMode = org.wip.plugintoolkit.features.settings.model.OrthogonalStepMode.Auto
        )

        // When travelling along the floor at y = 500f, it sweeps forward to x = 550f,
        // cornering at (550, 500), then rises up to pTarget without stair-stepping early
        assertTrue(pathPoints.contains(Offset(550f, 500f)), "Must sweep across floor to (550, 500) before rising")
    }

    @Test
    fun testOrthogonalHeadingUTurnDetour() {
        // Path moving RIGHT, but target is behind to the LEFT
        val p0 = Offset(100f, 100f)
        val pAhead = Offset(300f, 100f)
        val pBehind = Offset(150f, 300f)

        val pathPoints = SplineMathUtils.computeOrthogonalPoints(
            listOf(p0, pAhead, pBehind),
            startHorizontal = true,
            endHorizontal = true,
            stepMode = org.wip.plugintoolkit.features.settings.model.OrthogonalStepMode.Auto
        )

        // Preserves straight exit from pAhead (> 300f) before looping back toward pBehind
        val maxX = pathPoints.maxOf { it.x }
        assertTrue(maxX > 300f, "U-turn must project forward stub (maxX was $maxX, expected > 300)")
        assertEquals(pBehind, pathPoints.last())
    }

    @Test
    fun testCardinalSplineIntermediateSegmentsAreCurved() {
        // Multi-waypoint zigzag path like the user screenshot:
        // Source port -> w1 -> w2 -> w3 -> w4 -> Target port
        val complexPath = listOf(
            Offset(50f, 400f),
            Offset(150f, 300f),
            Offset(250f, 280f),
            Offset(300f, 100f),
            Offset(400f, 100f),
            Offset(350f, 320f),
            Offset(500f, 320f),
            Offset(600f, 200f),
            Offset(700f, 250f)
        )

        val segments = SplineMathUtils.computeHarmonizedSplineSegments(
            complexPath,
            tension = 0.5f,
            startHorizontal = true,
            endHorizontal = true
        )
        assertEquals(complexPath.size - 1, segments.size)

        // Verify that EVERY intermediate segment between waypoints has non-zero curvature
        // (i.e. control points are distinct from endpoints, not collapsed to a straight line)
        for (i in 1 until segments.size - 1) {
            val seg = segments[i]
            val c1Dist = (seg.control1 - seg.start).getDistance()
            val c2Dist = (seg.control2 - seg.end).getDistance()
            assertTrue(c1Dist > 1f, "Intermediate segment $i control1 must not collapse onto start (was $c1Dist)")
            assertTrue(c2Dist > 1f, "Intermediate segment $i control2 must not collapse onto end (was $c2Dist)")
            val mid = seg.evaluate(0.5f)
            assertTrue(mid.x.isFinite() && mid.y.isFinite())
        }
    }

    @Test
    fun testSplineResilienceToNonFiniteOrDuplicatedPoints() {
        // Duplicated points
        val dupPoints = listOf(Offset(50f, 50f), Offset(50f, 50f), Offset(150f, 150f))
        val segsDup = SplineMathUtils.computeHarmonizedSplineSegments(dupPoints, tension = 0.5f)
        assertTrue(segsDup.isNotEmpty())
        for (seg in segsDup) {
            assertTrue(seg.control1.x.isFinite())
            assertTrue(seg.control1.y.isFinite())
            assertTrue(seg.control2.x.isFinite())
            assertTrue(seg.control2.y.isFinite())
        }

        // Non-finite point input
        val invalidPoints = listOf(Offset(0f, 0f), Offset(Float.NaN, Float.POSITIVE_INFINITY), Offset(100f, 100f))
        val segsInvalid = SplineMathUtils.computeHarmonizedSplineSegments(invalidPoints, tension = 0.5f)
        assertTrue(segsInvalid.isNotEmpty())
        for (seg in segsInvalid) {
            assertTrue(seg.control1.x.isFinite())
            assertTrue(seg.control1.y.isFinite())
            assertTrue(seg.control2.x.isFinite())
            assertTrue(seg.control2.y.isFinite())
        }
    }

    @Test
    fun testBuildRoundedPolylinePathWithStartFilletLeadInPerpendicular() {
        val junc = Offset(100f, 100f)
        val leadIn = Offset(50f, 100f) // incoming moving right (1, 0)
        val target = Offset(100f, 0f)  // outgoing moving up (0, -1)
        val points = listOf(junc, target)

        val path = SplineMathUtils.buildRoundedPolylinePath(
            points = points,
            cornerRadius = 14f,
            startFilletLeadIn = leadIn
        )
        assertTrue(!path.isEmpty)

        val sampled = SplineMathUtils.sampleConnectionPoints(
            points = points,
            style = ConnectionCurveStyle.Orthogonal,
            startHorizontal = false,
            endHorizontal = false,
            startFilletLeadIn = leadIn
        )
        // Expected fillet radius = minOf(14, 50 * 0.45, 100 * 0.45) = 14f
        // Start corner = (100 - 14, 100) = (86, 100)
        // End corner = (100, 100 - 14) = (100, 86)
        assertEquals(86f, sampled.first().x, 0.5f)
        assertEquals(100f, sampled.first().y, 0.5f)
        // Final point is target (100, 0)
        assertEquals(100f, sampled.last().x, 0.5f)
        assertEquals(0f, sampled.last().y, 0.5f)
        // Point (100, 100) must NOT be in sampled points because it was filleted
        assertTrue(sampled.none { (it - junc).getDistance() < 1f })
    }

    @Test
    fun testBuildRoundedPolylinePathWithStartFilletLeadInCollinear() {
        val junc = Offset(100f, 100f)
        val leadIn = Offset(50f, 100f) // incoming moving right (1, 0)
        val target = Offset(200f, 100f) // outgoing moving right (1, 0) - straight through!
        val points = listOf(junc, target)

        val sampled = SplineMathUtils.sampleConnectionPoints(
            points = points,
            style = ConnectionCurveStyle.Orthogonal,
            startHorizontal = true,
            endHorizontal = true,
            startFilletLeadIn = leadIn
        )
        // Collinear straight through should start directly at junc (100, 100)
        assertEquals(100f, sampled.first().x, 0.5f)
        assertEquals(100f, sampled.first().y, 0.5f)
        assertEquals(200f, sampled.last().x, 0.5f)
        assertEquals(100f, sampled.last().y, 0.5f)
    }

    @Test
    fun testBuildRoundedPolylinePathWithEndTrimDistance() {
        val src = Offset(100f, 0f)
        val junc = Offset(100f, 100f)
        val points = listOf(src, junc)

        val sampled = SplineMathUtils.sampleConnectionPoints(
            points = points,
            style = ConnectionCurveStyle.Orthogonal,
            startHorizontal = false,
            endHorizontal = false,
            endTrimDistance = 14f
        )
        // Trimmed by 14 along (0, 1) -> stops at (100, 86)
        assertEquals(100f, sampled.first().x, 0.5f)
        assertEquals(0f, sampled.first().y, 0.5f)
        assertEquals(100f, sampled.last().x, 0.5f)
        assertEquals(86f, sampled.last().y, 0.5f)
    }

    @Test
    fun testZoomInvarianceAcrossScales() {
        val boardStart = Offset(50f, 100f)
        val boardEnd = Offset(300f, 250f)
        val scales = listOf(0.1f, 0.25f, 0.5f, 1.0f, 2.0f, 5.0f)
        val offsets = listOf(Offset.Zero, Offset(120f, -80f), Offset(-45.5f, 300.2f))

        for (scale in scales) {
            for (offset in offsets) {
                val screenPts = listOf(boardStart * scale + offset, boardEnd * scale + offset)
                val sampled = SplineMathUtils.sampleConnectionPoints(
                    points = screenPts,
                    style = ConnectionCurveStyle.Orthogonal,
                    scale = scale,
                    canvasOffset = offset
                )

                assertTrue(sampled.size >= 4, "Should have multiple sampled points at scale $scale")
                // Convert sampled points back to board space
                val boardSampled = sampled.map { (it - offset) / scale }

                // The normalized start and end must match boardStart and boardEnd exactly
                assertEquals(boardStart.x, boardSampled.first().x, 0.1f)
                assertEquals(boardStart.y, boardSampled.first().y, 0.1f)
                assertEquals(boardEnd.x, boardSampled.last().x, 0.1f)
                assertEquals(boardEnd.y, boardSampled.last().y, 0.1f)

                // The corner should be rounded, not a sharp point at the unfilleted waypoint
                val unfilletedCorner1 = Offset((boardStart.x + boardEnd.x) / 2f, boardStart.y)
                val unfilletedCorner2 = Offset((boardStart.x + boardEnd.x) / 2f, boardEnd.y)
                assertTrue(
                    boardSampled.none { (it - unfilletedCorner1).getDistance() < 0.5f },
                    "Filleted curve should not touch sharp corner 1 at scale $scale"
                )
                assertTrue(
                    boardSampled.none { (it - unfilletedCorner2).getDistance() < 0.5f },
                    "Filleted curve should not touch sharp corner 2 at scale $scale"
                )
            }
        }
    }

    @Test
    fun testFilletZoomInvarianceWithJunctionBranch() {
        val juncBoard = Offset(200f, 200f)
        val leadInBoard = Offset(50f, 200f) // Incoming from left (moving right)
        val targetBoard = Offset(200f, 50f)  // Outgoing branching up (moving up)
        val scales = listOf(0.1f, 0.2f, 0.5f, 1.0f, 2.5f, 5.0f)

        for (scale in scales) {
            val offset = Offset(133.7f, -42.1f)
            val screenJunc = juncBoard * scale + offset
            val screenLeadIn = leadInBoard * scale + offset
            val screenTarget = targetBoard * scale + offset

            val sampled = SplineMathUtils.sampleConnectionPoints(
                points = listOf(screenJunc, screenTarget),
                style = ConnectionCurveStyle.Orthogonal,
                startHorizontal = false,
                endHorizontal = true,
                scale = scale,
                canvasOffset = offset,
                startFilletLeadIn = screenLeadIn
            )

            assertTrue(sampled.isNotEmpty(), "Sampled points must not be empty at scale $scale")
            val boardSampled = sampled.map { (it - offset) / scale }

            // Board-space fillet radius should be 14f (minOf(14, 150 * 0.45, 150 * 0.45) = 14)
            // Fillet should start at (200 - 14, 200) = (186, 200) at every scale
            assertEquals(186f, boardSampled.first().x, 0.5f, "Fillet start X should be zoom-invariant at scale $scale")
            assertEquals(200f, boardSampled.first().y, 0.5f, "Fillet start Y should be zoom-invariant at scale $scale")

            // Filleted curve must never touch the sharp junction vertex (200, 200)
            assertTrue(
                boardSampled.none { (it - juncBoard).getDistance() < 1f },
                "Curve must smoothly bypass junction vertex at scale $scale"
            )
        }
    }

    @Test
    fun testOrthogonalRoutingIsStableUnderSubpixelJitter() {
        // Verifies that subpixel layout-rounding differences (< 1.5 board units in X or Y)
        // do NOT change the topology (bend count) of the orthogonal path.
        // Before the fix, 0.2f thresholds meant jitter of 0.3 board units could flip routing.
        val baseFrom = Offset(100f, 200f)
        val baseTo   = Offset(400f, 250f) // mostly horizontal — should always route H-V-H

        val basePoints = SplineMathUtils.computeOrthogonalPoints(
            listOf(baseFrom, baseTo),
            startHorizontal = true,
            endHorizontal = true
        )
        val baseBendCount = basePoints.size

        // Apply jitter values BELOW the 1.5f threshold
        val jitterValues = listOf(0f, 0.3f, 0.5f, 0.9f, 1.0f, 1.4f)
        for (jitterY in jitterValues) {
            val jitteredFrom = Offset(baseFrom.x, baseFrom.y + jitterY)
            val jitteredTo   = Offset(baseTo.x, baseTo.y + jitterY)
            val jitteredPoints = SplineMathUtils.computeOrthogonalPoints(
                listOf(jitteredFrom, jitteredTo),
                startHorizontal = true,
                endHorizontal = true
            )
            assertEquals(
                baseBendCount,
                jitteredPoints.size,
                "Bend count must be stable under Y-jitter of $jitterY board units"
            )
        }

        // Also test near-collinear vertical case: dx < 1.5 should NOT add a bend
        val nearVertFrom = Offset(200f, 100f)
        val nearVertTo   = Offset(201f, 400f) // dx = 1f < 1.5f threshold → should route straight
        val nearVertPoints = SplineMathUtils.computeOrthogonalPoints(
            listOf(nearVertFrom, nearVertTo),
            startHorizontal = true,
            endHorizontal = true
        )
        // With 1.5f threshold: abs(p0.x - p1.x) = 1f < 1.5f, so it returns listOf(p0, p1) directly
        assertEquals(2, nearVertPoints.size, "Near-vertical path (dx=1) must not get an extra bend")
    }
}
