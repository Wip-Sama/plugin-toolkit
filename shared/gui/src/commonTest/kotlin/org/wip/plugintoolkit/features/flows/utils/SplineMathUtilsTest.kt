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
        assertEquals(4, orthoSamples.size)
        assertEquals(Offset(0f, 0f), orthoSamples[0])
        assertEquals(Offset(50f, 0f), orthoSamples[1])
        assertEquals(Offset(50f, 100f), orthoSamples[2])
        assertEquals(Offset(100f, 100f), orthoSamples[3])
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
}
