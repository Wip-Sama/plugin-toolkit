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
     * Computes a harmonized, $C^1$ continuous spline connecting [points] using
     * Catmull-Rom parameterization converted to cubic Bézier control points (PDF 2).
     *
     * Ensures C1 tangent continuity and smooth curvature across all intermediate segments
     * (eliminating fallback to rigid straight lines) while respecting horizontal port boundaries.
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
                val clampedDx = minOf(dx, maxOf(abs(chord.x) * 0.85f, 20f * scale))
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
            val c1 = computeEndpointControl(p0, p1, startHorizontal)
            val c2 = computeEndpointControl(p1, p0, endHorizontal)
            segments.add(CubicSegment(p0, c1, c2, p1))
            return segments
        }

        // Chords and centripetal distances (alpha = 0.5) between consecutive points
        val chords = Array(n - 1) { i -> pts[i + 1] - pts[i] }
        val chordDistances = Array(n - 1) { i -> chords[i].getDistance() }
        val centripetalDistances = Array(n - 1) { i -> sqrt(maxOf(0.001f, chordDistances[i])) }

        // Compute C1 continuous Catmull-Rom tangents at each intermediate point (PDF 2)
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

                // Smooth chord-proportional limit to prevent excessive loops while preserving curvature
                val dPrev = chordDistances[i - 1]
                val dNext = chordDistances[i]
                val maxDist = minOf(dPrev, dNext) * 1.5f
                val tDist = sqrt(tx * tx + ty * ty)
                if (tDist > maxDist && tDist > 0.001f) {
                    val scaleFactor = maxDist / tDist
                    tx *= scaleFactor
                    ty *= scaleFactor
                }

                tangents[i] = Offset(tx, ty)
            }
        }

        for (i in 0 until n - 1) {
            val pCurr = pts[i]
            val pNext = pts[i + 1]

            // Control point 1 (departing pCurr)
            val c1 = if (i == 0) {
                if (startHorizontal) {
                    computeEndpointControl(pCurr, pNext, isHorizontal = true)
                } else {
                    Offset(
                        pCurr.x + (pNext.x - pCurr.x) * 0.333f * clampedTension,
                        pCurr.y + (pNext.y - pCurr.y) * 0.333f * clampedTension
                    )
                }
            } else {
                Offset(
                    pCurr.x + tangents[i].x * 0.333f,
                    pCurr.y + tangents[i].y * 0.333f
                )
            }

            // Control point 2 (approaching pNext)
            val c2 = if (i + 1 == n - 1) {
                if (endHorizontal) {
                    computeEndpointControl(pNext, pCurr, isHorizontal = true)
                } else {
                    Offset(
                        pNext.x - (pNext.x - pCurr.x) * 0.333f * clampedTension,
                        pNext.y - (pNext.y - pCurr.y) * 0.333f * clampedTension
                    )
                }
            } else {
                Offset(
                    pNext.x - tangents[i + 1].x * 0.333f,
                    pNext.y - tangents[i + 1].y * 0.333f
                )
            }

            segments.add(CubicSegment(pCurr, c1, c2, pNext))
        }

        return segments
    }

    /**
     * Builds a polyline path with small rounded corners at bends, ideal for structured / schematic wires.
     * Supports optional [startFilletLeadIn] and [endTrimDistance] for junction connection points.
     */
    fun buildRoundedPolylinePath(
        points: List<Offset>,
        cornerRadius: Float = 14f,
        startFilletLeadIn: Offset? = null,
        endTrimDistance: Float = 0f
    ): Path {
        val path = Path()
        if (points.isEmpty()) return path
        if (points.size == 1) {
            path.moveTo(points[0].x, points[0].y)
            return path
        }

        val effectivePoints = points.toMutableList()
        if (endTrimDistance > 0f && effectivePoints.size >= 2) {
            val pPenultimate = effectivePoints[effectivePoints.size - 2]
            val pLast = effectivePoints.last()
            val vEnd = pLast - pPenultimate
            val lenEnd = vEnd.getDistance()
            if (lenEnd > 0.1f) {
                val trimDist = minOf(endTrimDistance, lenEnd * 0.45f)
                effectivePoints[effectivePoints.size - 1] = Offset(
                    pLast.x - (vEnd.x / lenEnd) * trimDist,
                    pLast.y - (vEnd.y / lenEnd) * trimDist
                )
            }
        }

        // Check start fillet lead-in at connection points
        var hasStartFillet = false
        var startFilletStart = Offset.Zero
        var startFilletEnd = Offset.Zero
        val p0 = effectivePoints[0]
        val p1 = effectivePoints[1]

        if (startFilletLeadIn != null) {
            val vInRaw = p0 - startFilletLeadIn
            val lenIn = vInRaw.getDistance()
            val vOutRaw = p1 - p0
            val lenOut = vOutRaw.getDistance()

            if (lenIn >= 0.1f && lenOut >= 0.1f) {
                val vIn = Offset(vInRaw.x / lenIn, vInRaw.y / lenIn)
                val vOut = Offset(vOutRaw.x / lenOut, vOutRaw.y / lenOut)
                val dot = vIn.x * vOut.x + vIn.y * vOut.y
                if (abs(dot) < 0.1f) {
                    val r = minOf(cornerRadius, lenIn * 0.45f, lenOut * 0.45f)
                    if (r >= 1f) {
                        hasStartFillet = true
                        startFilletStart = Offset(p0.x - vIn.x * r, p0.y - vIn.y * r)
                        startFilletEnd = Offset(p0.x + vOut.x * r, p0.y + vOut.y * r)
                    }
                }
            }
        }

        if (hasStartFillet) {
            path.moveTo(startFilletStart.x, startFilletStart.y)
            path.quadraticBezierTo(p0.x, p0.y, startFilletEnd.x, startFilletEnd.y)

            if (effectivePoints.size == 2) {
                path.lineTo(effectivePoints[1].x, effectivePoints[1].y)
                return path
            }

            for (i in 1 until effectivePoints.size - 1) {
                val pPrev = if (i == 1) startFilletEnd else effectivePoints[i - 1]
                val pCurr = effectivePoints[i]
                val pNext = effectivePoints[i + 1]

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
            path.lineTo(effectivePoints.last().x, effectivePoints.last().y)
            return path
        }

        path.moveTo(effectivePoints[0].x, effectivePoints[0].y)
        if (effectivePoints.size == 2) {
            path.lineTo(effectivePoints[1].x, effectivePoints[1].y)
            return path
        }

        for (i in 1 until effectivePoints.size - 1) {
            val pPrev = effectivePoints[i - 1]
            val pCurr = effectivePoints[i]
            val pNext = effectivePoints[i + 1]

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
        path.lineTo(effectivePoints.last().x, effectivePoints.last().y)
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
        stepMode: OrthogonalStepMode = OrthogonalStepMode.Auto,
        startFilletLeadIn: Offset? = null,
        endTrimDistance: Float = 0f
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
     * configurable [stepMode] (Auto/Min-Break, Middle, Before, After) as specified in PDF 1.
     */
    fun computeOrthogonalPoints(
        points: List<Offset>,
        startHorizontal: Boolean = true,
        endHorizontal: Boolean = true,
        stepMode: OrthogonalStepMode = OrthogonalStepMode.Auto
    ): List<Offset> {
        val pts = sanitizePoints(points)
        if (pts.size < 2) return pts

        if (pts.size == 2) {
            val p0 = pts[0]
            val p1 = pts[1]
            if (abs(p0.x - p1.x) < 0.2f || abs(p0.y - p1.y) < 0.2f) {
                return listOf(p0, p1)
            }

            return when (stepMode) {
                OrthogonalStepMode.Before -> {
                    // D3 / Protovis step-before: Vertical then Horizontal (corner at (x0, y1))
                    listOf(p0, Offset(p0.x, p1.y), p1)
                }
                OrthogonalStepMode.After -> {
                    // D3 / Protovis step-after: Horizontal then Vertical (corner at (x1, y0))
                    listOf(p0, Offset(p1.x, p0.y), p1)
                }
                OrthogonalStepMode.Middle,
                OrthogonalStepMode.Auto -> {
                    if (startHorizontal && endHorizontal) {
                        val midX = (p0.x + p1.x) / 2f
                        listOf(p0, Offset(midX, p0.y), Offset(midX, p1.y), p1)
                    } else if (startHorizontal && !endHorizontal) {
                        listOf(p0, Offset(p1.x, p0.y), p1)
                    } else if (!startHorizontal && endHorizontal) {
                        listOf(p0, Offset(p0.x, p1.y), p1)
                    } else {
                        val midY = (p0.y + p1.y) / 2f
                        listOf(p0, Offset(p0.x, midY), Offset(p1.x, midY), p1)
                    }
                }
            }
        }

        // Multi-point orthogonal routing (n > 2)
        val waypoints = mutableListOf<Offset>()
        waypoints.add(pts[0])

        var currentDir = if (startHorizontal) "H" else "V"
        var currentHeading = if (startHorizontal) {
            if (pts.size > 1 && pts[1].x < pts[0].x) "LEFT" else "RIGHT"
        } else {
            if (pts.size > 1 && pts[1].y < pts[0].y) "UP" else "DOWN"
        }

        for (i in 0 until pts.size - 1) {
            val p0 = pts[i]
            val p1 = pts[i + 1]
            val dx = p1.x - p0.x
            val dy = p1.y - p0.y
            val isLastSegment = (i == pts.size - 2)
            if (stepMode != OrthogonalStepMode.Auto) {
                if (abs(dx) < 0.2f) {
                    currentDir = "V"
                    waypoints.add(p1)
                } else if (abs(dy) < 0.2f) {
                    currentDir = "H"
                    waypoints.add(p1)
                } else {
                    when (stepMode) {
                        OrthogonalStepMode.Before -> {
                            waypoints.add(Offset(p0.x, p1.y))
                            currentDir = "H"
                            waypoints.add(p1)
                        }
                        OrthogonalStepMode.After -> {
                            waypoints.add(Offset(p1.x, p0.y))
                            currentDir = "V"
                            waypoints.add(p1)
                        }
                        OrthogonalStepMode.Middle -> {
                            if (currentDir == "H") {
                                val midX = (p0.x + p1.x) / 2f
                                waypoints.add(Offset(midX, p0.y))
                                waypoints.add(Offset(midX, p1.y))
                                currentDir = "H"
                            } else {
                                val midY = (p0.y + p1.y) / 2f
                                waypoints.add(Offset(p0.x, midY))
                                waypoints.add(Offset(p1.x, midY))
                                currentDir = "V"
                            }
                            waypoints.add(p1)
                        }
                        OrthogonalStepMode.Auto -> {}
                    }
                }
            } else {
                // Direction-aware "Min-Break" Orthogonal Routing (PDF 1)
                if (currentHeading == "RIGHT" || currentHeading == "LEFT") {
                    val isAhead = (currentHeading == "RIGHT" && dx > 0f) || (currentHeading == "LEFT" && dx < 0f)
                    if (abs(dy) < 0.5f && isAhead) {
                        // Aligned continuation horizontally
                        currentHeading = if (dx >= 0f) "RIGHT" else "LEFT"
                    } else if (abs(dx) < 0.5f) {
                        currentHeading = if (dy >= 0f) "DOWN" else "UP"
                    } else if (isLastSegment && endHorizontal && isAhead) {
                        val midX = (p0.x + p1.x) / 2f
                        waypoints.add(Offset(midX, p0.y))
                        waypoints.add(Offset(midX, p1.y))
                    } else if (isAhead) {
                        waypoints.add(Offset(p1.x, p0.y))
                        currentHeading = if (dy >= 0f) "DOWN" else "UP"
                    } else {
                        // U-turn / Backtracking: target opposes heading
                        val stub = if (currentHeading == "RIGHT") 24f else -24f
                        val midY = (p0.y + p1.y) / 2f
                        waypoints.add(Offset(p0.x + stub, p0.y))
                        waypoints.add(Offset(p0.x + stub, midY))
                        waypoints.add(Offset(p1.x, midY))
                        currentHeading = if (dy >= 0f) "DOWN" else "UP"
                    }
                } else {
                    val isAhead = (currentHeading == "DOWN" && dy > 0f) || (currentHeading == "UP" && dy < 0f)
                    if (abs(dx) < 0.5f && isAhead) {
                        // Aligned continuation vertically
                        currentHeading = if (dy >= 0f) "DOWN" else "UP"
                    } else if (abs(dy) < 0.5f) {
                        currentHeading = if (dx >= 0f) "RIGHT" else "LEFT"
                    } else if (isLastSegment && !endHorizontal && isAhead) {
                        val midY = (p0.y + p1.y) / 2f
                        waypoints.add(Offset(p0.x, midY))
                        waypoints.add(Offset(p1.x, midY))
                    } else if (isAhead) {
                        waypoints.add(Offset(p0.x, p1.y))
                        currentHeading = if (dx >= 0f) "RIGHT" else "LEFT"
                    } else {
                        // U-turn / Backtracking: target opposes heading
                        val stub = if (currentHeading == "DOWN") 24f else -24f
                        val midX = (p0.x + p1.x) / 2f
                        waypoints.add(Offset(p0.x, p0.y + stub))
                        waypoints.add(Offset(midX, p0.y + stub))
                        waypoints.add(Offset(midX, p1.y))
                        currentHeading = if (dx >= 0f) "RIGHT" else "LEFT"
                    }
                }
                waypoints.add(p1)
            }
        }

        return simplifyOrthogonalPath(waypoints)
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
     * Supports optional [startFilletLeadIn] and [endTrimDistance] for junction connection points.
     */
    fun buildConnectionPath(
        points: List<Offset>,
        style: ConnectionCurveStyle = ConnectionCurveStyle.CardinalSpline,
        tension: Float = 0.5f,
        startHorizontal: Boolean = true,
        endHorizontal: Boolean = true,
        scale: Float = 1f,
        stepMode: OrthogonalStepMode = OrthogonalStepMode.Middle,
        startFilletLeadIn: Offset? = null,
        endTrimDistance: Float = 0f
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
                return buildRoundedPolylinePath(
                    points = orthoPoints,
                    cornerRadius = r,
                    startFilletLeadIn = startFilletLeadIn,
                    endTrimDistance = endTrimDistance
                )
            }
        }

        return path
    }

    /**
     * Samples points along the connection path for precise distance calculations and hit-testing.
     * Supports optional [startFilletLeadIn] and [endTrimDistance] for junction connection points.
     */
    fun sampleConnectionPoints(
        points: List<Offset>,
        style: ConnectionCurveStyle = ConnectionCurveStyle.CardinalSpline,
        tension: Float = 0.5f,
        samplesPerSegment: Int = 20,
        startHorizontal: Boolean = true,
        endHorizontal: Boolean = true,
        scale: Float = 1f,
        stepMode: OrthogonalStepMode = OrthogonalStepMode.Middle,
        startFilletLeadIn: Offset? = null,
        endTrimDistance: Float = 0f
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
                if (orthoPoints.isEmpty()) return emptyList()
                if (orthoPoints.size == 1) return orthoPoints

                val effectiveOrtho = orthoPoints.toMutableList()
                if (endTrimDistance > 0f && effectiveOrtho.size >= 2) {
                    val pPenultimate = effectiveOrtho[effectiveOrtho.size - 2]
                    val pLast = effectiveOrtho.last()
                    val vEnd = pLast - pPenultimate
                    val lenEnd = vEnd.getDistance()
                    if (lenEnd > 0.1f) {
                        val trimDist = minOf(endTrimDistance, lenEnd * 0.45f)
                        effectiveOrtho[effectiveOrtho.size - 1] = Offset(
                            pLast.x - (vEnd.x / lenEnd) * trimDist,
                            pLast.y - (vEnd.y / lenEnd) * trimDist
                        )
                    }
                }

                val rBase = maxOf(14f * scale, tension * 28f * scale)

                // Check start fillet lead-in
                var hasStartFillet = false
                var startFilletStart = Offset.Zero
                var startFilletEnd = Offset.Zero
                val p0 = effectiveOrtho[0]
                val p1 = effectiveOrtho[1]

                if (startFilletLeadIn != null) {
                    val vInRaw = p0 - startFilletLeadIn
                    val lenIn = vInRaw.getDistance()
                    val vOutRaw = p1 - p0
                    val lenOut = vOutRaw.getDistance()

                    if (lenIn >= 0.1f && lenOut >= 0.1f) {
                        val vIn = Offset(vInRaw.x / lenIn, vInRaw.y / lenIn)
                        val vOut = Offset(vOutRaw.x / lenOut, vOutRaw.y / lenOut)
                        val dot = vIn.x * vOut.x + vIn.y * vOut.y
                        if (abs(dot) < 0.1f) {
                            val r = minOf(rBase, lenIn * 0.45f, lenOut * 0.45f)
                            if (r >= 1f) {
                                hasStartFillet = true
                                startFilletStart = Offset(p0.x - vIn.x * r, p0.y - vIn.y * r)
                                startFilletEnd = Offset(p0.x + vOut.x * r, p0.y + vOut.y * r)
                            }
                        }
                    }
                }

                if (hasStartFillet) {
                    sampled.add(startFilletStart)
                    for (s in 1..4) {
                        val t = s / 5f
                        val mt = 1f - t
                        val qx = mt * mt * startFilletStart.x + 2f * mt * t * p0.x + t * t * startFilletEnd.x
                        val qy = mt * mt * startFilletStart.y + 2f * mt * t * p0.y + t * t * startFilletEnd.y
                        sampled.add(Offset(qx, qy))
                    }
                    sampled.add(startFilletEnd)

                    if (effectiveOrtho.size == 2) {
                        sampled.add(effectiveOrtho[1])
                    } else {
                        for (i in 1 until effectiveOrtho.size - 1) {
                            val pPrev = if (i == 1) startFilletEnd else effectiveOrtho[i - 1]
                            val pCurr = effectiveOrtho[i]
                            val pNext = effectiveOrtho[i + 1]

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
                        sampled.add(effectiveOrtho.last())
                    }
                } else {
                    if (effectiveOrtho.size <= 2) {
                        sampled.addAll(effectiveOrtho)
                    } else {
                        sampled.add(effectiveOrtho[0])
                        for (i in 1 until effectiveOrtho.size - 1) {
                            val pPrev = effectiveOrtho[i - 1]
                            val pCurr = effectiveOrtho[i]
                            val pNext = effectiveOrtho[i + 1]

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
                        sampled.add(effectiveOrtho.last())
                    }
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
