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

        // Middle step mode (default): intermediate corner at x = 200f
        val midPoints = SplineMathUtils.computeOrthogonalPoints(points, startHorizontal = true, endHorizontal = true, stepMode = org.wip.plugintoolkit.features.settings.model.OrthogonalStepMode.Middle)
        assertEquals(4, midPoints.size)
        assertEquals(200f, midPoints[1].x)
        assertEquals(100f, midPoints[1].y)
        assertEquals(200f, midPoints[2].x)
        assertEquals(200f, midPoints[2].y)

        // Before step mode: step occurs early, near start
        val beforePoints = SplineMathUtils.computeOrthogonalPoints(points, startHorizontal = true, endHorizontal = true, stepMode = org.wip.plugintoolkit.features.settings.model.OrthogonalStepMode.Before)
        assertTrue(beforePoints.size >= 3)
        assertEquals(start, beforePoints.first())
        assertEquals(end, beforePoints.last())

        // After step mode: step occurs late, curving directly up/down near end
        val afterPoints = SplineMathUtils.computeOrthogonalPoints(points, startHorizontal = true, endHorizontal = true, stepMode = org.wip.plugintoolkit.features.settings.model.OrthogonalStepMode.After)
        assertTrue(afterPoints.size >= 3)
        assertEquals(start, afterPoints.first())
        assertEquals(end, afterPoints.last())
    }

    @Test
    fun testHarmonizedSplineMonotonicityNoOvershoot() {
        // Monotonically increasing points (e.g. 90-degree bend or stair-step)
        val points = listOf(
            Offset(0f, 0f),
            Offset(100f, 0f),
            Offset(100f, 100f)
        )
        val segments = SplineMathUtils.computeHarmonizedSplineSegments(points, tension = 0.5f)
        assertEquals(2, segments.size)

        for (seg in segments) {
            val minX = minOf(seg.start.x, seg.end.x) - 0.01f
            val maxX = maxOf(seg.start.x, seg.end.x) + 0.01f
            val minY = minOf(seg.start.y, seg.end.y) - 0.01f
            val maxY = maxOf(seg.start.y, seg.end.y) + 0.01f

            // Fritsch-Carlson monotone slope clamping guarantees control points stay bounded
            assertTrue(seg.control1.x in minX..maxX, "control1.x ${seg.control1.x} must be within [$minX, $maxX]")
            assertTrue(seg.control2.x in minX..maxX, "control2.x ${seg.control2.x} must be within [$minX, $maxX]")
            assertTrue(seg.control1.y in minY..maxY, "control1.y ${seg.control1.y} must be within [$minY, $maxY]")
            assertTrue(seg.control2.y in minY..maxY, "control2.y ${seg.control2.y} must be within [$minY, $maxY]")
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
}
