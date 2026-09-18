package org.wip.plugintoolkit.features.flows.utils

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import org.wip.plugintoolkit.features.settings.model.ConnectionCurveStyle
import org.wip.plugintoolkit.features.settings.model.OrthogonalStepMode
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
    /**
     * Filters out non-finite offsets and removes duplicate/near-identical consecutive points.
     */
    fun sanitizePoints(points: List<Offset>): List<Offset> {
        if (points.isEmpty()) return emptyList()
        val valid = points.filter { it.x.isFinite() && it.y.isFinite() }
        if (valid.size < 2) return valid
        val result = ArrayList<Offset>(valid.size)
        result.add(valid[0])
        for (i in 1 until valid.size) {
            val prev = result.last()
            val curr = valid[i]
            if ((curr - prev).getDistance() >= 0.1f) {
                result.add(curr)
            }
        }
        return if (result.size == 1 && valid.size >= 2) listOf(valid.first(), valid.last()) else result
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
     * Computes a harmonized, monotonic $C^1$ continuous spline connecting [points] using
     * Centripetal Catmull-Rom parameterization ($\alpha = 0.5$) combined with Fritsch-Carlson
     * monotone slope clamping.
     *
     * This eliminates unnatural overshoot, retrograde loops, and S-bend ripples (Image 3) while
     * ensuring that corners (Image 4) round smoothly without bulging outwards.
     */
    fun computeHarmonizedSplineSegments(
        points: List<Offset>,
        tension: Float = 0.5f,
        startHorizontal: Boolean = true,
        endHorizontal: Boolean = true,
        scale: Float = 1f
    ): List<CubicSegment> {
        val pts = sanitizePoints(points)
        if (pts.size < 2) return emptyList()

        val n = pts.size
        val segments = ArrayList<CubicSegment>(n - 1)
        val clampedTension = tension.coerceIn(0f, 1f)

        fun computeEndpointControl(
            pFrom: Offset,
            pTo: Offset,
            isHorizontal: Boolean
        ): Offset {
            val chord = pTo - pFrom
            val dist = chord.getDistance()
            if (dist < 0.001f) return pFrom

            return if (isHorizontal) {
                val baseDx = abs(chord.x) * 0.5f
                val softenedDx = if (abs(chord.y) > abs(chord.x)) {
                    minOf(baseDx, abs(chord.y) * 0.5f + 40f * scale)
                } else {
                    baseDx
                }
                val dx = maxOf(softenedDx, 30f * scale) * clampedTension
                val signX = if (chord.x >= 0f) 1f else -1f
                val clampedDx = minOf(dx, abs(chord.x) * 0.85f)
                Offset(pFrom.x + signX * clampedDx, pFrom.y)
            } else {
                val len = minOf(dist * 0.38f, 150f * scale) * clampedTension
                val dirX = chord.x / dist
                val dirY = chord.y / dist
                Offset(pFrom.x + dirX * len, pFrom.y + dirY * len)
            }
        }

        if (n == 2) {
            val p0 = pts[0]
            val p1 = pts[1]
            val c1Raw = computeEndpointControl(p0, p1, startHorizontal)
            val c2Raw = computeEndpointControl(p1, p0, endHorizontal)
            val minX = minOf(p0.x, p1.x)
            val maxX = maxOf(p0.x, p1.x)
            val minY = minOf(p0.y, p1.y)
            val maxY = maxOf(p0.y, p1.y)
            val c1 = Offset(c1Raw.x.coerceIn(minX, maxX), c1Raw.y.coerceIn(minY, maxY))
            val c2 = Offset(c2Raw.x.coerceIn(minX, maxX), c2Raw.y.coerceIn(minY, maxY))
            segments.add(CubicSegment(p0, c1, c2, p1))
            return segments
        }

        // Chords and centripetal distances (alpha = 0.5) between consecutive points
        val chords = Array(n - 1) { i -> pts[i + 1] - pts[i] }
        val chordDistances = Array(n - 1) { i -> chords[i].getDistance() }
        val centripetalDistances = Array(n - 1) { i -> sqrt(maxOf(0.001f, chordDistances[i])) }

        // Compute centripetal tangents at each intermediate point
        val tangents = Array(n) { Offset.Zero }
        for (i in 1 until n - 1) {
            val sPrev = chords[i - 1]
            val sNext = chords[i]
            val dtPrev = centripetalDistances[i - 1]
            val dtNext = centripetalDistances[i]
            val dtSum = dtPrev + dtNext

            if (dtSum > 0.001f) {
                // Centripetal Catmull-Rom weighting: dtNext weights sPrev, dtPrev weights sNext
                val wPrev = dtNext / dtSum
                val wNext = dtPrev / dtSum
                var tx = (sPrev.x * wPrev + sNext.x * wNext) * clampedTension
                var ty = (sPrev.y * wPrev + sNext.y * wNext) * clampedTension

                // Fritsch-Carlson Monotone Slope Clamping:
                // If adjacent segments change direction in an axis, tangent on that axis MUST be 0 (local extremum).
                // Otherwise, limit tangent magnitude to prevent overshoot beyond the interval.
                if (sPrev.x * sNext.x <= 0f) {
                    tx = 0f
                } else {
                    val maxTx = 3f * minOf(abs(sPrev.x), abs(sNext.x))
                    tx = tx.coerceIn(-maxTx, maxTx)
                }

                if (sPrev.y * sNext.y <= 0f) {
                    ty = 0f
                } else {
                    val maxTy = 3f * minOf(abs(sPrev.y), abs(sNext.y))
                    ty = ty.coerceIn(-maxTy, maxTy)
                }

                tangents[i] = Offset(tx, ty)
            }
        }

        for (i in 0 until n - 1) {
            val pCurr = pts[i]
            val pNext = pts[i + 1]
            val chord = chords[i]

            // Control point 1 (departing pCurr)
            val c1Raw = if (i == 0) {
                computeEndpointControl(pCurr, pNext, startHorizontal)
            } else {
                Offset(
                    pCurr.x + tangents[i].x * 0.333f,
                    pCurr.y + tangents[i].y * 0.333f
                )
            }

            // Control point 2 (approaching pNext)
            val c2Raw = if (i + 1 == n - 1) {
                computeEndpointControl(pNext, pCurr, endHorizontal)
            } else {
                Offset(
                    pNext.x - tangents[i + 1].x * 0.333f,
                    pNext.y - tangents[i + 1].y * 0.333f
                )
            }

            // Coordinate bounding box coercion: ensures the cubic curve stays strictly monotonic
            // and within the convex interval of the endpoints without any retrograde bulging.
            val minX = minOf(pCurr.x, pNext.x)
            val maxX = maxOf(pCurr.x, pNext.x)
            val minY = minOf(pCurr.y, pNext.y)
            val maxY = maxOf(pCurr.y, pNext.y)

            val c1 = Offset(
                c1Raw.x.coerceIn(minX, maxX),
                c1Raw.y.coerceIn(minY, maxY)
            )
            val c2 = Offset(
                c2Raw.x.coerceIn(minX, maxX),
                c2Raw.y.coerceIn(minY, maxY)
            )

            segments.add(CubicSegment(pCurr, c1, c2, pNext))
        }

        return segments
    }

    /**
     * Builds a polyline path with small rounded corners at bends, ideal for structured / schematic wires.
     */
    fun buildRoundedPolylinePath(
        points: List<Offset>,
        cornerRadius: Float = 14f
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

    enum class Orientation { Horizontal, Vertical }

    /**
     * Evaluates parametric midpoints (t = 0.5) for each segment of the connection path.
     */
    fun computeSegmentMidpoints(
        points: List<Offset>,
        style: ConnectionCurveStyle = ConnectionCurveStyle.CardinalSpline,
        tension: Float = 0.5f,
        startHorizontal: Boolean = true,
        endHorizontal: Boolean = true,
        scale: Float = 1f,
        stepMode: OrthogonalStepMode = OrthogonalStepMode.Middle
    ): List<Offset> {
        val pts = sanitizePoints(points)
        if (pts.size < 2) return emptyList()

        return when (style) {
            ConnectionCurveStyle.Straight -> {
                (0 until pts.size - 1).map { i ->
                    Offset((pts[i].x + pts[i + 1].x) * 0.5f, (pts[i].y + pts[i + 1].y) * 0.5f)
                }
            }
            ConnectionCurveStyle.Orthogonal -> {
                val orthoPoints = computeOrthogonalPoints(pts, startHorizontal, endHorizontal, stepMode)
                (0 until pts.size - 1).map { i ->
                    val p0 = pts[i]
                    val p1 = pts[i + 1]
                    val idx0 = orthoPoints.indexOfFirst { (it - p0).getDistance() < 0.5f }
                    val idx1 = orthoPoints.indexOfLast { (it - p1).getDistance() < 0.5f }
                    if (idx0 != -1 && idx1 != -1 && idx1 > idx0) {
                        val subPoints = orthoPoints.subList(idx0, idx1 + 1)
                        var totalLen = 0f
                        for (k in 0 until subPoints.size - 1) {
                            totalLen += (subPoints[k + 1] - subPoints[k]).getDistance()
                        }
                        val halfLen = totalLen * 0.5f
                        var accumulated = 0f
                        var foundMid: Offset? = null
                        for (k in 0 until subPoints.size - 1) {
                            val segLen = (subPoints[k + 1] - subPoints[k]).getDistance()
                            if (accumulated + segLen >= halfLen && segLen > 0f) {
                                val t = (halfLen - accumulated) / segLen
                                foundMid = Offset(
                                    subPoints[k].x + (subPoints[k + 1].x - subPoints[k].x) * t,
                                    subPoints[k].y + (subPoints[k + 1].y - subPoints[k].y) * t
                                )
                                break
                            }
                            accumulated += segLen
                        }
                        foundMid ?: Offset((p0.x + p1.x) * 0.5f, (p0.y + p1.y) * 0.5f)
                    } else {
                        Offset((p0.x + p1.x) * 0.5f, (p0.y + p1.y) * 0.5f)
                    }
                }
            }
            ConnectionCurveStyle.Bezier,
            ConnectionCurveStyle.CardinalSpline -> {
                val segments = computeHarmonizedSplineSegments(pts, tension, startHorizontal, endHorizontal, scale)
                segments.map { it.evaluate(0.5f) }
            }
        }
    }

    /**
     * Expands a sequence of points into a single continuous orthogonal (horizontal and vertical) path.
     * Respects port launch and arrival directions, preserves directional continuity, and supports
     * configurable [stepMode] (Middle, Before, After) to prioritize minimizing sharp corners (Image 2).
     */
    fun computeOrthogonalPoints(
        points: List<Offset>,
        startHorizontal: Boolean = true,
        endHorizontal: Boolean = true,
        stepMode: OrthogonalStepMode = OrthogonalStepMode.Middle
    ): List<Offset> {
        val pts = sanitizePoints(points)
        if (pts.size < 2) return pts
        if (pts.size == 2) {
            val p0 = pts[0]
            val p1 = pts[1]
            if (abs(p0.x - p1.x) < 0.2f || abs(p0.y - p1.y) < 0.2f) {
                return listOf(p0, p1)
            }
            return when {
                startHorizontal && endHorizontal -> {
                    val midX = when (stepMode) {
                        OrthogonalStepMode.Middle -> (p0.x + p1.x) / 2f
                        OrthogonalStepMode.Before -> {
                            val lead = minOf(36f, abs(p1.x - p0.x) * 0.25f)
                            if (p1.x >= p0.x) p0.x + lead else p0.x - lead
                        }
                        OrthogonalStepMode.After -> {
                            val lead = minOf(36f, abs(p1.x - p0.x) * 0.25f)
                            if (p1.x >= p0.x) p1.x - lead else p1.x + lead
                        }
                    }
                    listOf(p0, Offset(midX, p0.y), Offset(midX, p1.y), p1)
                }
                startHorizontal && !endHorizontal -> {
                    listOf(p0, Offset(p1.x, p0.y), p1)
                }
                !startHorizontal && endHorizontal -> {
                    listOf(p0, Offset(p0.x, p1.y), p1)
                }
                else -> {
                    val midY = when (stepMode) {
                        OrthogonalStepMode.Middle -> (p0.y + p1.y) / 2f
                        OrthogonalStepMode.Before -> {
                            val lead = minOf(36f, abs(p1.y - p0.y) * 0.25f)
                            if (p1.y >= p0.y) p0.y + lead else p0.y - lead
                        }
                        OrthogonalStepMode.After -> {
                            val lead = minOf(36f, abs(p1.y - p0.y) * 0.25f)
                            if (p1.y >= p0.y) p1.y - lead else p1.y + lead
                        }
                    }
                    listOf(p0, Offset(p0.x, midY), Offset(p1.x, midY), p1)
                }
            }
        }

        val result = mutableListOf<Offset>()
        result.add(pts[0])

        var currentIsHorizontal = startHorizontal

        for (i in 0 until pts.size - 1) {
            val pA = pts[i]
            val pB = pts[i + 1]
            val isLastSegment = (i == pts.size - 2)

            if (abs(pA.x - pB.x) < 0.2f) {
                result.add(pB)
                currentIsHorizontal = false
            } else if (abs(pA.y - pB.y) < 0.2f) {
                result.add(pB)
                currentIsHorizontal = true
            } else if (isLastSegment) {
                when {
                    currentIsHorizontal && endHorizontal -> {
                        val midX = when (stepMode) {
                            OrthogonalStepMode.Middle -> (pA.x + pB.x) / 2f
                            OrthogonalStepMode.Before -> {
                                val lead = minOf(36f, abs(pB.x - pA.x) * 0.25f)
                                if (pB.x >= pA.x) pA.x + lead else pA.x - lead
                            }
                            OrthogonalStepMode.After -> {
                                val lead = minOf(36f, abs(pB.x - pA.x) * 0.25f)
                                if (pB.x >= pA.x) pB.x - lead else pB.x + lead
                            }
                        }
                        result.add(Offset(midX, pA.y))
                        result.add(Offset(midX, pB.y))
                        result.add(pB)
                        currentIsHorizontal = true
                    }
                    !currentIsHorizontal && endHorizontal -> {
                        // Vertical travel entering horizontal port: 1 smooth corner
                        result.add(Offset(pA.x, pB.y))
                        result.add(pB)
                        currentIsHorizontal = true
                    }
                    currentIsHorizontal && !endHorizontal -> {
                        // Horizontal travel entering vertical port: 1 smooth corner
                        result.add(Offset(pB.x, pA.y))
                        result.add(pB)
                        currentIsHorizontal = false
                    }
                    else -> {
                        val midY = when (stepMode) {
                            OrthogonalStepMode.Middle -> (pA.y + pB.y) / 2f
                            OrthogonalStepMode.Before -> {
                                val lead = minOf(36f, abs(pB.y - pA.y) * 0.25f)
                                if (pB.y >= pA.y) pA.y + lead else pA.y - lead
                            }
                            OrthogonalStepMode.After -> {
                                val lead = minOf(36f, abs(pB.y - pA.y) * 0.25f)
                                if (pB.y >= pA.y) pB.y - lead else pB.y + lead
                            }
                        }
                        result.add(Offset(pA.x, midY))
                        result.add(Offset(pB.x, midY))
                        result.add(pB)
                        currentIsHorizontal = false
                    }
                }
            } else {
                // Intermediate segment routing between waypoints / junctions:
                // Prioritize minimizing sharp corners based on stepMode
                when (stepMode) {
                    OrthogonalStepMode.Before -> {
                        if (currentIsHorizontal) {
                            result.add(Offset(pA.x, pB.y))
                            result.add(pB)
                            currentIsHorizontal = true
                        } else {
                            result.add(Offset(pB.x, pA.y))
                            result.add(pB)
                            currentIsHorizontal = false
                        }
                    }
                    OrthogonalStepMode.After -> {
                        // Step after: keep current direction to target coordinate, then turn (minimizes sharp corners, Image 2)
                        if (currentIsHorizontal) {
                            result.add(Offset(pB.x, pA.y))
                            result.add(pB)
                            currentIsHorizontal = false
                        } else {
                            result.add(Offset(pA.x, pB.y))
                            result.add(pB)
                            currentIsHorizontal = true
                        }
                    }
                    OrthogonalStepMode.Middle -> {
                        if (currentIsHorizontal) {
                            result.add(Offset(pB.x, pA.y))
                            result.add(pB)
                            currentIsHorizontal = false
                        } else {
                            result.add(Offset(pA.x, pB.y))
                            result.add(pB)
                            currentIsHorizontal = true
                        }
                    }
                }
            }
        }

        return simplifyOrthogonalPath(result)
    }

    private fun simplifyOrthogonalPath(points: List<Offset>): List<Offset> {
        if (points.size <= 2) return points
        val simplified = mutableListOf<Offset>()
        simplified.add(points[0])
        for (i in 1 until points.size - 1) {
            val prev = simplified.last()
            val curr = points[i]
            val next = points[i + 1]

            val isCollinearH = abs(prev.y - curr.y) < 0.2f && abs(curr.y - next.y) < 0.2f
            val isCollinearV = abs(prev.x - curr.x) < 0.2f && abs(curr.x - next.x) < 0.2f
            val isDuplicate = (curr - prev).getDistance() < 0.2f

            if (!isCollinearH && !isCollinearV && !isDuplicate) {
                simplified.add(curr)
            }
        }
        simplified.add(points.last())
        return simplified
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
     * Builds a Compose [Path] connecting [points] according to [style], [tension], and [stepMode].
     */
    fun buildConnectionPath(
        points: List<Offset>,
        style: ConnectionCurveStyle = ConnectionCurveStyle.CardinalSpline,
        tension: Float = 0.5f,
        startHorizontal: Boolean = true,
        endHorizontal: Boolean = true,
        scale: Float = 1f,
        stepMode: OrthogonalStepMode = OrthogonalStepMode.Middle
    ): Path {
        val pts = sanitizePoints(points)
        val path = Path()
        if (pts.isEmpty()) return path
        if (pts.size == 1) {
            path.moveTo(pts[0].x, pts[0].y)
            return path
        }

        when (style) {
            ConnectionCurveStyle.Straight -> {
                path.moveTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size) {
                    val p = pts[i]
                    path.lineTo(p.x, p.y)
                }
            }

            ConnectionCurveStyle.Bezier,
            ConnectionCurveStyle.CardinalSpline -> {
                path.moveTo(pts[0].x, pts[0].y)
                val segments = computeHarmonizedSplineSegments(pts, tension, startHorizontal, endHorizontal, scale)
                for (seg in segments) {
                    path.cubicTo(seg.control1.x, seg.control1.y, seg.control2.x, seg.control2.y, seg.end.x, seg.end.y)
                }
            }

            ConnectionCurveStyle.Orthogonal -> {
                val orthoPoints = computeOrthogonalPoints(pts, startHorizontal, endHorizontal, stepMode)
                val r = maxOf(14f * scale, tension * 28f * scale)
                return buildRoundedPolylinePath(orthoPoints, cornerRadius = r)
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
        samplesPerSegment: Int = 20,
        startHorizontal: Boolean = true,
        endHorizontal: Boolean = true,
        scale: Float = 1f,
        stepMode: OrthogonalStepMode = OrthogonalStepMode.Middle
    ): List<Offset> {
        val pts = sanitizePoints(points)
        if (pts.isEmpty()) return emptyList()
        if (pts.size == 1) return pts

        val sampled = mutableListOf<Offset>()

        when (style) {
            ConnectionCurveStyle.Straight -> {
                sampled.addAll(pts)
            }

            ConnectionCurveStyle.Orthogonal -> {
                val orthoPoints = computeOrthogonalPoints(pts, startHorizontal, endHorizontal, stepMode)
                if (orthoPoints.size <= 2) {
                    sampled.addAll(orthoPoints)
                } else {
                    val rBase = maxOf(14f * scale, tension * 28f * scale)
                    sampled.add(orthoPoints[0])
                    for (i in 1 until orthoPoints.size - 1) {
                        val pPrev = orthoPoints[i - 1]
                        val pCurr = orthoPoints[i]
                        val pNext = orthoPoints[i + 1]

                        val vIn = Offset(pPrev.x - pCurr.x, pPrev.y - pCurr.y)
                        val vOut = Offset(pNext.x - pCurr.x, pNext.y - pCurr.y)
                        val lenIn = sqrt(vIn.x * vIn.x + vIn.y * vIn.y)
                        val lenOut = sqrt(vOut.x * vOut.x + vOut.y * vOut.y)

                        val r = minOf(rBase, lenIn * 0.45f, lenOut * 0.45f)
                        if (r < 1f || lenIn == 0f || lenOut == 0f) {
                            sampled.add(pCurr)
                        } else {
                            val startCorner = Offset(pCurr.x + (vIn.x / lenIn) * r, pCurr.y + (vIn.y / lenIn) * r)
                            val endCorner = Offset(pCurr.x + (vOut.x / lenOut) * r, pCurr.y + (vOut.y / lenOut) * r)
                            sampled.add(startCorner)
                            for (s in 1..4) {
                                val t = s / 5f
                                val mt = 1f - t
                                val qx = mt * mt * startCorner.x + 2f * mt * t * pCurr.x + t * t * endCorner.x
                                val qy = mt * mt * startCorner.y + 2f * mt * t * pCurr.y + t * t * endCorner.y
                                sampled.add(Offset(qx, qy))
                            }
                            sampled.add(endCorner)
                        }
                    }
                    sampled.add(orthoPoints.last())
                }
            }

            ConnectionCurveStyle.Bezier,
            ConnectionCurveStyle.CardinalSpline -> {
                val segments = computeHarmonizedSplineSegments(points, tension, startHorizontal, endHorizontal, scale)
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
