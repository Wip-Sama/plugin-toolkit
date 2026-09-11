package org.wip.plugintoolkit.features.flows.utils

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import org.wip.plugintoolkit.features.settings.model.ConnectionCurveStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Geometric and mathematical utilities for flow connections, splines, Bézier curves,
 * universal hit-testing, and branch projection.
 */
object SplineMathUtils {

    /**
     * Represents a single cubic Bézier segment defined by start, two control points, and end.
     */
    data class CubicSegment(
        val start: Offset,
        val control1: Offset,
        val control2: Offset,
        val end: Offset
    ) {
        /**
         * Evaluates position along this cubic Bézier curve at parameter [t] in [0, 1].
         */
        fun evaluate(t: Float): Offset {
            val clampedT = t.coerceIn(0f, 1f)
            val mt = 1f - clampedT
            val mt2 = mt * mt
            val mt3 = mt2 * mt
            val t2 = clampedT * clampedT
            val t3 = t2 * clampedT

            val x = start.x * mt3 + control1.x * (3f * mt2 * clampedT) + control2.x * (3f * mt * t2) + end.x * t3
            val y = start.y * mt3 + control1.y * (3f * mt2 * clampedT) + control2.y * (3f * mt * t2) + end.y * t3
            return Offset(x, y)
        }
    }

    /**
     * Computes cubic Bézier segments for a Cardinal / Catmull-Rom spline passing through [points].
     *
     * @param points Ordered list of points along the curve.
     * @param tension Roundness / tension parameter in [0.0, 1.0].
     *                0.0 produces straight linear segments; 0.5 is standard Catmull-Rom; 1.0 produces high curvature.
     */
    fun computeCardinalSplineSegments(
        points: List<Offset>,
        tension: Float = 0.5f
    ): List<CubicSegment> {
        return computeHarmonizedSplineSegments(points, tension, startHorizontal = true, endHorizontal = true)
    }

    /**
     * Computes a single harmonized $C^1$ continuous spline connecting [points].
     * Terminal ends depart/arrive horizontally (0° output exit, 180° input entry), while all
     * intermediate waypoints smoothly flow through continuous tangent angles determined by their neighbors,
     * completely eliminating unnatural 0°/180° intermediate ripples.
     */
    fun computeHarmonizedSplineSegments(
        points: List<Offset>,
        tension: Float = 0.5f,
        startHorizontal: Boolean = true,
        endHorizontal: Boolean = true
    ): List<CubicSegment> {
        if (points.size < 2) return emptyList()

        val n = points.size
        val segments = ArrayList<CubicSegment>(n - 1)
        val clampedTension = tension.coerceIn(0f, 1f)

        if (n == 2) {
            val p0 = points[0]
            val p1 = points[1]
            val dx = maxOf(abs(p1.x - p0.x) * 0.5f, 40f) * clampedTension
            val c1 = if (startHorizontal) Offset(p0.x + dx, p0.y) else Offset(p0.x + (p1.x - p0.x) * 0.33f, p0.y + (p1.y - p0.y) * 0.33f)
            val c2 = if (endHorizontal) Offset(p1.x - dx, p1.y) else Offset(p1.x - (p1.x - p0.x) * 0.33f, p1.y - (p1.y - p0.y) * 0.33f)
            segments.add(CubicSegment(p0, c1, c2, p1))
            return segments
        }

        // Compute tangents at each intermediate point with chord-length distance weighting
        // to ensure harmonious curves without overshoot on unequal segment lengths.
        val tangents = Array(n) { Offset.Zero }
        for (i in 1 until n - 1) {
            val prev = points[i - 1]
            val curr = points[i]
            val next = points[i + 1]

            val d1 = sqrt((curr.x - prev.x) * (curr.x - prev.x) + (curr.y - prev.y) * (curr.y - prev.y))
            val d2 = sqrt((next.x - curr.x) * (next.x - curr.x) + (next.y - curr.y) * (next.y - curr.y))
            val dSum = d1 + d2

            if (dSum > 0.001f) {
                tangents[i] = Offset(
                    (next.x - prev.x) * clampedTension,
                    (next.y - prev.y) * clampedTension
                )
            }
        }

        for (i in 0 until n - 1) {
            val pCurr = points[i]
            val pNext = points[i + 1]
            val chordDist = sqrt((pNext.x - pCurr.x) * (pNext.x - pCurr.x) + (pNext.y - pCurr.y) * (pNext.y - pCurr.y))

            // Control point 1 (departing pCurr)
            val c1 = if (i == 0) {
                if (startHorizontal) {
                    val dx = maxOf(abs(pNext.x - pCurr.x) * 0.5f, 40f) * clampedTension
                    Offset(pCurr.x + dx, pCurr.y)
                } else {
                    Offset(pCurr.x + (pNext.x - pCurr.x) * (clampedTension / 3f), pCurr.y + (pNext.y - pCurr.y) * (clampedTension / 3f))
                }
            } else {
                val dPrev = sqrt((pCurr.x - points[i - 1].x) * (pCurr.x - points[i - 1].x) + (pCurr.y - points[i - 1].y) * (pCurr.y - points[i - 1].y))
                val dNext = chordDist
                val weight = if (dPrev + dNext > 0.001f) dNext / (dPrev + dNext) else 0.5f
                Offset(
                    pCurr.x + tangents[i].x * weight * 0.66f,
                    pCurr.y + tangents[i].y * weight * 0.66f
                )
            }

            // Control point 2 (approaching pNext)
            val c2 = if (i + 1 == n - 1) {
                if (endHorizontal) {
                    val dx = maxOf(abs(pNext.x - pCurr.x) * 0.5f, 40f) * clampedTension
                    Offset(pNext.x - dx, pNext.y)
                } else {
                    Offset(pNext.x - (pNext.x - pCurr.x) * (clampedTension / 3f), pNext.y - (pNext.y - pCurr.y) * (clampedTension / 3f))
                }
            } else {
                val dCurr = chordDist
                val dAfter = sqrt((points[i + 2].x - pNext.x) * (points[i + 2].x - pNext.x) + (points[i + 2].y - pNext.y) * (points[i + 2].y - pNext.y))
                val weight = if (dCurr + dAfter > 0.001f) dCurr / (dCurr + dAfter) else 0.5f
                Offset(
                    pNext.x - tangents[i + 1].x * weight * 0.66f,
                    pNext.y - tangents[i + 1].y * weight * 0.66f
                )
            }

            segments.add(CubicSegment(pCurr, c1, c2, pNext))
        }

        return segments
    }

    /**
     * Builds a polyline path with small rounded corners at bends, ideal for structured / schematic wires.
     */
    fun buildRoundedPolylinePath(
        points: List<Offset>,
        cornerRadius: Float = 8f
    ): Path {
        val path = Path()
        if (points.isEmpty()) return path
        if (points.size == 1) {
            path.moveTo(points[0].x, points[0].y)
            return path
        }
        if (points.size == 2) {
            path.moveTo(points[0].x, points[0].y)
            path.lineTo(points[1].x, points[1].y)
            return path
        }

        path.moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size - 1) {
            val pPrev = points[i - 1]
            val pCurr = points[i]
            val pNext = points[i + 1]

            val vIn = Offset(pPrev.x - pCurr.x, pPrev.y - pCurr.y)
            val vOut = Offset(pNext.x - pCurr.x, pNext.y - pCurr.y)
            val lenIn = sqrt(vIn.x * vIn.x + vIn.y * vIn.y)
            val lenOut = sqrt(vOut.x * vOut.x + vOut.y * vOut.y)

            val r = minOf(cornerRadius, lenIn * 0.45f, lenOut * 0.45f)
            if (r < 1f || lenIn == 0f || lenOut == 0f) {
                path.lineTo(pCurr.x, pCurr.y)
            } else {
                val startCorner = Offset(pCurr.x + (vIn.x / lenIn) * r, pCurr.y + (vIn.y / lenIn) * r)
                val endCorner = Offset(pCurr.x + (vOut.x / lenOut) * r, pCurr.y + (vOut.y / lenOut) * r)
                path.lineTo(startCorner.x, startCorner.y)
                path.quadraticBezierTo(pCurr.x, pCurr.y, endCorner.x, endCorner.y)
            }
        }
        path.lineTo(points.last().x, points.last().y)
        return path
    }

    /**
     * Evaluates parametric midpoints (t = 0.5) for each segment of the connection path.
     */
    fun computeSegmentMidpoints(
        points: List<Offset>,
        style: ConnectionCurveStyle = ConnectionCurveStyle.CardinalSpline,
        tension: Float = 0.5f
    ): List<Offset> {
        if (points.size < 2) return emptyList()

        return when (style) {
            ConnectionCurveStyle.Straight -> {
                (0 until points.size - 1).map { i ->
                    Offset((points[i].x + points[i + 1].x) * 0.5f, (points[i].y + points[i + 1].y) * 0.5f)
                }
            }
            ConnectionCurveStyle.Orthogonal -> {
                (0 until points.size - 1).map { i ->
                    val p0 = points[i]
                    val p1 = points[i + 1]
                    val midX = (p0.x + p1.x) * 0.5f
                    Offset(midX, (p0.y + p1.y) * 0.5f)
                }
            }
            ConnectionCurveStyle.Bezier,
            ConnectionCurveStyle.CardinalSpline -> {
                val segments = computeHarmonizedSplineSegments(points, tension)
                segments.map { it.evaluate(0.5f) }
            }
        }
    }

    /**
     * Snaps [current] relative to [start] to be strictly horizontal or strictly vertical.
     */
    fun snapToOrthogonal(start: Offset, current: Offset): Offset {
        val dx = current.x - start.x
        val dy = current.y - start.y
        return if (abs(dx) >= abs(dy)) {
            Offset(current.x, start.y)
        } else {
            Offset(start.x, current.y)
        }
    }

    /**
     * Builds a Compose [Path] connecting [points] according to [style] and [tension].
     */
    fun buildConnectionPath(
        points: List<Offset>,
        style: ConnectionCurveStyle = ConnectionCurveStyle.CardinalSpline,
        tension: Float = 0.5f
    ): Path {
        val path = Path()
        if (points.isEmpty()) return path
        if (points.size == 1) {
            path.moveTo(points[0].x, points[0].y)
            return path
        }

        when (style) {
            ConnectionCurveStyle.Straight -> {
                path.moveTo(points[0].x, points[0].y)
                for (i in 1 until points.size) {
                    val p = points[i]
                    path.lineTo(p.x, p.y)
                }
            }

            ConnectionCurveStyle.Bezier,
            ConnectionCurveStyle.CardinalSpline -> {
                path.moveTo(points[0].x, points[0].y)
                val segments = computeHarmonizedSplineSegments(points, tension)
                for (seg in segments) {
                    path.cubicTo(seg.control1.x, seg.control1.y, seg.control2.x, seg.control2.y, seg.end.x, seg.end.y)
                }
            }

            ConnectionCurveStyle.Orthogonal -> {
                if (points.size == 2) {
                    path.moveTo(points[0].x, points[0].y)
                    val p0 = points[0]
                    val p1 = points[1]
                    val midX = (p0.x + p1.x) / 2f
                    path.lineTo(midX, p0.y)
                    path.lineTo(midX, p1.y)
                    path.lineTo(p1.x, p1.y)
                } else {
                    return buildRoundedPolylinePath(points, cornerRadius = 8f)
                }
            }
        }

        return path
    }

    /**
     * Samples points along the connection path for precise distance calculations and hit-testing.
     */
    fun sampleConnectionPoints(
        points: List<Offset>,
        style: ConnectionCurveStyle = ConnectionCurveStyle.CardinalSpline,
        tension: Float = 0.5f,
        samplesPerSegment: Int = 20
    ): List<Offset> {
        if (points.isEmpty()) return emptyList()
        if (points.size == 1) return points

        val sampled = mutableListOf<Offset>()

        when (style) {
            ConnectionCurveStyle.Straight -> {
                sampled.addAll(points)
            }

            ConnectionCurveStyle.Orthogonal -> {
                if (points.size == 2) {
                    sampled.add(points[0])
                    val p0 = points[0]
                    val p1 = points[1]
                    val midX = (p0.x + p1.x) / 2f
                    sampled.add(Offset(midX, p0.y))
                    sampled.add(Offset(midX, p1.y))
                    sampled.add(p1)
                } else {
                    sampled.addAll(points)
                }
            }

            ConnectionCurveStyle.Bezier,
            ConnectionCurveStyle.CardinalSpline -> {
                val segments = computeHarmonizedSplineSegments(points, tension)
                if (segments.isEmpty()) {
                    sampled.addAll(points)
                } else {
                    sampled.add(segments.first().start)
                    for (seg in segments) {
                        for (s in 1..samplesPerSegment) {
                            val t = s.toFloat() / samplesPerSegment
                            sampled.add(seg.evaluate(t))
                        }
                    }
                }
            }
        }

        return sampled
    }

    /**
     * Calculates the minimum perpendicular distance from [point] to the line segment [segStart] - [segEnd].
     */
    fun distanceToSegment(point: Offset, segStart: Offset, segEnd: Offset): Float {
        val dx = segEnd.x - segStart.x
        val dy = segEnd.y - segStart.y
        val l2 = dx * dx + dy * dy

        if (l2 == 0f) {
            val px = point.x - segStart.x
            val py = point.y - segStart.y
            return sqrt(px * px + py * py)
        }

        val t = ((point.x - segStart.x) * dx + (point.y - segStart.y) * dy) / l2
        val clampedT = t.coerceIn(0f, 1f)
        val projX = segStart.x + clampedT * dx
        val projY = segStart.y + clampedT * dy
        val diffX = point.x - projX
        val diffY = point.y - projY
        return sqrt(diffX * diffX + diffY * diffY)
    }

    /**
     * Computes the minimum distance from [point] to a polyline formed by [sampledPoints].
     */
    fun distanceToPath(point: Offset, sampledPoints: List<Offset>): Float {
        if (sampledPoints.isEmpty()) return Float.MAX_VALUE
        if (sampledPoints.size == 1) {
            val dx = point.x - sampledPoints[0].x
            val dy = point.y - sampledPoints[0].y
            return sqrt(dx * dx + dy * dy)
        }

        var minDistance = Float.MAX_VALUE
        for (i in 0 until sampledPoints.size - 1) {
            val dist = distanceToSegment(point, sampledPoints[i], sampledPoints[i + 1])
            if (dist < minDistance) {
                minDistance = dist
            }
        }
        return minDistance
    }

    /**
     * Finds the closest point on the polyline formed by [sampledPoints] to [point].
     * Useful for wire branching to drop a new junction exactly on the wire.
     */
    fun findClosestPointOnPath(point: Offset, sampledPoints: List<Offset>): Offset {
        if (sampledPoints.isEmpty()) return point
        if (sampledPoints.size == 1) return sampledPoints[0]

        var minDistance = Float.MAX_VALUE
        var closestPoint = sampledPoints[0]

        for (i in 0 until sampledPoints.size - 1) {
            val a = sampledPoints[i]
            val b = sampledPoints[i + 1]
            val dx = b.x - a.x
            val dy = b.y - a.y
            val l2 = dx * dx + dy * dy

            val proj = if (l2 == 0f) {
                a
            } else {
                val t = (((point.x - a.x) * dx + (point.y - a.y) * dy) / l2).coerceIn(0f, 1f)
                Offset(a.x + t * dx, a.y + t * dy)
            }

            val diffX = point.x - proj.x
            val diffY = point.y - proj.y
            val dist = sqrt(diffX * diffX + diffY * diffY)

            if (dist < minDistance) {
                minDistance = dist
                closestPoint = proj
            }
        }

        return closestPoint
    }

    /**
     * Snaps the angle between [start] and [current] to the nearest 45-degree angle
     * (horizontal, vertical, or diagonal), preserving radial distance.
     */
    fun snapToStraightAngle(start: Offset, current: Offset): Offset {
        val dx = current.x - start.x
        val dy = current.y - start.y
        val distance = sqrt(dx * dx + dy * dy)
        if (distance == 0f) return current

        val angle = atan2(dy, dx)
        val step = (PI / 4.0).toFloat()
        val snappedAngle = round(angle / step) * step

        return Offset(
            start.x + distance * cos(snappedAngle),
            start.y + distance * sin(snappedAngle)
        )
    }
}
