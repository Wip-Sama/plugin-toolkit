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
    enum class Direction { Up, Down, Left, Right }
    enum class Orientation { Horizontal, Vertical }

    // --- Tuning Constants ---
    const val DEFAULT_GRID_SIZE = 50f  
    const val NODE_PORT_INSET = 19f
    private const val DEFAULT_TENSION = 0.5f
    
    // Tolerance for considering points identical or co-located
    private const val POINT_DUPLICATE_TOLERANCE = 0.1f
    
    // Thresholds for orthogonal snapping
    const val ORTHOGONAL_ALIGNMENT_TOLERANCE = 4f
    const val COLLINEAR_TOLERANCE = 1.0f
    
    // Spline / Bezier tension calculation constants
    private const val CONTROL_POINT_MIN_DX = 30f
    private const val CONTROL_POINT_BASE_FACTOR = 0.5f
    private const val CONTROL_POINT_SOFTEN_OFFSET = 40f
    private const val CONTROL_POINT_CLAMP_FACTOR = 0.85f
    private const val CONTROL_POINT_MIN_CLAMP_DX = 20f
    private const val CONTROL_POINT_VERTICAL_LENGTH_FACTOR = 0.38f
    private const val CONTROL_POINT_VERTICAL_MAX_LENGTH = 150f
    private const val TANGENT_MIN_DISTANCE = 0.001f
    private const val TANGENT_FACTOR = 0.333f
    private const val MAX_TANGENT_DISTANCE_FACTOR = 1.5f
    
    // Path rounding and filleting constants
    const val DEFAULT_CORNER_RADIUS = 14f
    const val TENSION_RADIUS_FACTOR = 28f
    const val MIN_RENDER_CORNER_RADIUS = 4f
    const val TRIM_FACTOR = 0.5f
    const val MIN_CORNER_RADIUS = 0.01f
    const val FILLET_DOT_TOLERANCE_STRAIGHT = 0.1f
    const val FILLET_DOT_TOLERANCE_BEND = -0.75f
    const val MIN_SEGMENT_LENGTH = 0.1f
    
    // Midpoint and interpolation constants
    private const val MIDPOINT_DISTANCE_TOLERANCE = 0.5f


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
            if ((curr - prev).getDistance() >= POINT_DUPLICATE_TOLERANCE) {
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
        tension: Float = DEFAULT_TENSION
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
        tension: Float = DEFAULT_TENSION,
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
            if (dist < TANGENT_MIN_DISTANCE) return pFrom

            return if (isHorizontal) {
                val baseDx = abs(chord.x) * CONTROL_POINT_BASE_FACTOR
                val softenedDx = if (abs(chord.y) > abs(chord.x)) {
                    minOf(baseDx, abs(chord.y) * CONTROL_POINT_BASE_FACTOR + CONTROL_POINT_SOFTEN_OFFSET * scale)
                } else {
                    baseDx
                }
                val dx = maxOf(softenedDx, CONTROL_POINT_MIN_DX * scale) * clampedTension
                val signX = if (chord.x >= 0f) 1f else -1f
                val clampedDx = minOf(dx, maxOf(abs(chord.x) * CONTROL_POINT_CLAMP_FACTOR, CONTROL_POINT_MIN_CLAMP_DX * scale))
                Offset(pFrom.x + signX * clampedDx, pFrom.y)
            } else {
                val len = minOf(dist * CONTROL_POINT_VERTICAL_LENGTH_FACTOR, CONTROL_POINT_VERTICAL_MAX_LENGTH * scale) * clampedTension
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
        val centripetalDistances = Array(n - 1) { i -> sqrt(maxOf(TANGENT_MIN_DISTANCE, chordDistances[i])) }

        // Compute C1 continuous Catmull-Rom tangents at each intermediate point (PDF 2)
        val tangents = Array(n) { Offset.Zero }
        for (i in 1 until n - 1) {
            val sPrev = chords[i - 1]
            val sNext = chords[i]
            val dtPrev = centripetalDistances[i - 1]
            val dtNext = centripetalDistances[i]
            val dtSum = dtPrev + dtNext

            if (dtSum > TANGENT_MIN_DISTANCE) {
                // Centripetal Catmull-Rom weighting: dtNext weights sPrev, dtPrev weights sNext
                val wPrev = dtNext / dtSum
                val wNext = dtPrev / dtSum
                var tx = (sPrev.x * wPrev + sNext.x * wNext) * clampedTension
                var ty = (sPrev.y * wPrev + sNext.y * wNext) * clampedTension

                // Smooth chord-proportional limit to prevent excessive loops while preserving curvature
                val dPrev = chordDistances[i - 1]
                val dNext = chordDistances[i]
                val maxDist = minOf(dPrev, dNext) * MAX_TANGENT_DISTANCE_FACTOR
                val tDist = sqrt(tx * tx + ty * ty)
                if (tDist > maxDist && tDist > TANGENT_MIN_DISTANCE) {
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
                        pCurr.x + (pNext.x - pCurr.x) * TANGENT_FACTOR * clampedTension,
                        pCurr.y + (pNext.y - pCurr.y) * TANGENT_FACTOR * clampedTension
                    )
                }
            } else {
                Offset(
                    pCurr.x + tangents[i].x * TANGENT_FACTOR,
                    pCurr.y + tangents[i].y * TANGENT_FACTOR
                )
            }

            // Control point 2 (approaching pNext)
            val c2 = if (i + 1 == n - 1) {
                if (endHorizontal) {
                    computeEndpointControl(pNext, pCurr, isHorizontal = true)
                } else {
                    Offset(
                        pNext.x - (pNext.x - pCurr.x) * TANGENT_FACTOR * clampedTension,
                        pNext.y - (pNext.y - pCurr.y) * TANGENT_FACTOR * clampedTension
                    )
                }
            } else {
                Offset(
                    pNext.x - tangents[i + 1].x * TANGENT_FACTOR,
                    pNext.y - tangents[i + 1].y * TANGENT_FACTOR
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
        cornerRadius: Float = DEFAULT_CORNER_RADIUS,
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
        val origP1 = points[1]
        var trimDist = 0f
        if (endTrimDistance > 0f && effectivePoints.size >= 2) {
            val pPenultimate = effectivePoints[effectivePoints.size - 2]
            val pLast = effectivePoints.last()
            val vEnd = pLast - pPenultimate
            val lenEnd = vEnd.getDistance()
            if (lenEnd > MIN_SEGMENT_LENGTH) {
                trimDist = minOf(endTrimDistance, lenEnd * TRIM_FACTOR)
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

        if (startFilletLeadIn != null) {
            val vInRaw = p0 - startFilletLeadIn
            val lenIn = vInRaw.getDistance()
            val vOutRaw = origP1 - p0
            val lenOut = vOutRaw.getDistance()

            if (lenIn >= MIN_SEGMENT_LENGTH && lenOut >= MIN_SEGMENT_LENGTH) {
                val vIn = Offset(vInRaw.x / lenIn, vInRaw.y / lenIn)
                val vOut = Offset(vOutRaw.x / lenOut, vOutRaw.y / lenOut)
                val dot = vIn.x * vOut.x + vIn.y * vOut.y
                if (abs(dot) < MIN_SEGMENT_LENGTH || dot < FILLET_DOT_TOLERANCE_BEND) {
                    var r = minOf(cornerRadius, lenIn * TRIM_FACTOR, lenOut * TRIM_FACTOR)
                    // If a 2-point segment has both a start fillet and an end trim, ensure the fillet and trim
                    // don't leave a gap or cross over when the segment is short:
                    if (effectivePoints.size == 2 && trimDist > 0f) {
                        val totalDist = lenOut
                        if (r + trimDist > totalDist && totalDist > 0f) {
                            val ratio = totalDist / (r + trimDist)
                            r *= ratio
                            val scaledTrim = trimDist * ratio
                            val pLast = points.last()
                            val vEnd = pLast - p0
                            effectivePoints[1] = Offset(
                                pLast.x - (vEnd.x / totalDist) * scaledTrim,
                                pLast.y - (vEnd.y / totalDist) * scaledTrim
                            )
                        }
                    }
                    val minR = MIN_CORNER_RADIUS
                    if (r >= minR) {
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

                val r = minOf(cornerRadius, lenIn * TRIM_FACTOR, lenOut * TRIM_FACTOR)
                val minR = MIN_CORNER_RADIUS
                if (lenIn == 0f || lenOut == 0f || r < minR || r <= 0f) {
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

            val r = minOf(cornerRadius, lenIn * TRIM_FACTOR, lenOut * TRIM_FACTOR)
            val minR = MIN_CORNER_RADIUS
            if (lenIn == 0f || lenOut == 0f || r < minR) {
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


    /**
     * Evaluates parametric midpoints (t = 0.5) for each segment of the connection path.
     * Pass [canvasOffset] (state.offset) when [points] are in screen space for zoom-stable routing.
     */
    fun computeSegmentMidpoints(
        points: List<Offset>,
        style: ConnectionCurveStyle = ConnectionCurveStyle.CardinalSpline,
        tension: Float = DEFAULT_TENSION,
        startHorizontal: Boolean = true,
        endHorizontal: Boolean = true,
        scale: Float = 1f,
        canvasOffset: Offset = Offset.Zero,
        stepMode: OrthogonalStepMode = OrthogonalStepMode.Auto,
        startFilletLeadIn: Offset? = null,
        endTrimDistance: Float = 0f,
        useMiddleRouteForDirectConnection: Boolean = true,
        startPortLead: Boolean = false,
        endPortLead: Boolean = false,
        gridSize: Float = DEFAULT_GRID_SIZE,
        startBorderX: Float? = null,
        endBorderX: Float? = null
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
                // Route in board space for zoom-stable thresholds.
                val rawBoardPts = if (scale != 1f || canvasOffset != Offset.Zero) {
                    points.filter { it.x.isFinite() && it.y.isFinite() }.map { (it - canvasOffset) * (1f / scale) }
                } else {
                    points.filter { it.x.isFinite() && it.y.isFinite() }
                }
                val boardPts = sanitizePoints(rawBoardPts)
                val orthoBoard = computeOrthogonalPoints(
                    boardPts,
                    startHorizontal,
                    endHorizontal,
                    stepMode,
                    useMiddleRouteForDirectConnection,
                    startPortLead = startPortLead,
                    endPortLead = endPortLead,
                    gridSize = gridSize,
                    startBorderX = startBorderX,
                    endBorderX = endBorderX
                )
                // Evaluate midpoints purely in board coordinates, eliminating any zoom dependency.
                (0 until boardPts.size - 1).map { i ->
                    val bp0 = boardPts[i]
                    val bp1 = boardPts[i + 1]
                    val idx0 = orthoBoard.indexOfFirst { (it - bp0).getDistance() < COLLINEAR_TOLERANCE }
                    val idx1 = orthoBoard.indexOfLast { (it - bp1).getDistance() < COLLINEAR_TOLERANCE }
                    val boardMid = if (idx0 != -1 && idx1 != -1 && idx1 > idx0) {
                        val subPoints = orthoBoard.subList(idx0, idx1 + 1)
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
                        foundMid ?: Offset((bp0.x + bp1.x) * 0.5f, (bp0.y + bp1.y) * 0.5f)
                    } else {
                        Offset((bp0.x + bp1.x) * 0.5f, (bp0.y + bp1.y) * 0.5f)
                    }
                    if (scale != 1f || canvasOffset != Offset.Zero) {
                        (boardMid * scale) + canvasOffset
                    } else {
                        boardMid
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
        stepMode: OrthogonalStepMode = OrthogonalStepMode.Auto,
        useMiddleRouteForDirectConnection: Boolean = true,
        startPortLead: Boolean = false,
        endPortLead: Boolean = false,
        gridSize: Float = DEFAULT_GRID_SIZE,
        startBorderX: Float? = null,
        endBorderX: Float? = null
    ): List<Offset> {
        val pts = sanitizePoints(points)
        if (pts.size < 2) return pts

        if (stepMode == OrthogonalStepMode.Auto && (startPortLead || endPortLead)) {
            return routeOrthogonalAutoWithPortLeads(
                pts = pts,
                startHorizontal = startHorizontal,
                endHorizontal = endHorizontal,
                startPortLead = startPortLead,
                endPortLead = endPortLead,
                gridSize = gridSize,
                startBorderX = startBorderX,
                endBorderX = endBorderX
            )
        }
        
        val waypoints = mutableListOf<Offset>()
        waypoints.add(pts[0])

        var currentDir = if (startHorizontal) Orientation.Horizontal else Orientation.Vertical
        val isDirectConnection = useMiddleRouteForDirectConnection && pts.size == 2

        for (i in 0 until pts.size - 1) {
            val p0 = pts[i]
            val p1 = pts[i + 1]
            val dx = p1.x - p0.x
            val dy = p1.y - p0.y
            
            if (abs(dx) < ORTHOGONAL_ALIGNMENT_TOLERANCE) {
                currentDir = Orientation.Vertical
                waypoints.add(p1)
            } else if (abs(dy) < ORTHOGONAL_ALIGNMENT_TOLERANCE) {
                currentDir = Orientation.Horizontal
                waypoints.add(p1)
            } else {
                when (stepMode) {
                    OrthogonalStepMode.Before -> {
                        waypoints.add(Offset(p0.x, p1.y))
                        currentDir = Orientation.Horizontal
                        waypoints.add(p1)
                    }
                    OrthogonalStepMode.After -> {
                        waypoints.add(Offset(p1.x, p0.y))
                        currentDir = Orientation.Vertical
                        waypoints.add(p1)
                    }
                    OrthogonalStepMode.Middle -> {
                        if (currentDir == Orientation.Horizontal) {
                            val midX = (p0.x + p1.x) / 2f
                            waypoints.add(Offset(midX, p0.y))
                            waypoints.add(Offset(midX, p1.y))
                            currentDir = Orientation.Horizontal
                        } else {
                            val midY = (p0.y + p1.y) / 2f
                            waypoints.add(Offset(p0.x, midY))
                            waypoints.add(Offset(p1.x, midY))
                            currentDir = Orientation.Vertical
                        }
                        waypoints.add(p1)
                    }
                    OrthogonalStepMode.Auto -> {
                        if (currentDir == Orientation.Horizontal) {
                            if (isDirectConnection && endHorizontal) {
                                val midX = (p0.x + p1.x) / 2f
                                waypoints.add(Offset(midX, p0.y))
                                waypoints.add(Offset(midX, p1.y))
                            } else {
                                waypoints.add(Offset(p1.x, p0.y))
                                currentDir = Orientation.Vertical
                            }
                        } else {
                            if (isDirectConnection && !endHorizontal) {
                                val midY = (p0.y + p1.y) / 2f
                                waypoints.add(Offset(p0.x, midY))
                                waypoints.add(Offset(p1.x, midY))
                            } else {
                                waypoints.add(Offset(p0.x, p1.y))
                                currentDir = Orientation.Horizontal
                            }
                        }
                        waypoints.add(p1)
                    }
                }
            }
        }
        
        return simplifyOrthogonalPath(waypoints)
    }

    fun computeStartPortLead(
        p0: Offset,
        startBorderX: Float? = null,
        gridSize: Float = DEFAULT_GRID_SIZE
    ): Offset {
        val borderX = startBorderX ?: (p0.x + NODE_PORT_INSET)
        return Offset(borderX + gridSize, p0.y)
    }

    fun computeEndPortLead(
        p1: Offset,
        endBorderX: Float? = null,
        gridSize: Float = DEFAULT_GRID_SIZE
    ): Offset {
        val borderX = endBorderX ?: (p1.x - NODE_PORT_INSET)
        return Offset(borderX - gridSize, p1.y)
    }

    private fun routeOrthogonalAutoWithPortLeads(
        pts: List<Offset>,
        startHorizontal: Boolean,
        endHorizontal: Boolean,
        startPortLead: Boolean,
        endPortLead: Boolean,
        gridSize: Float,
        startBorderX: Float? = null,
        endBorderX: Float? = null
    ): List<Offset> {
        if (pts.size == 2) {
            val waypoints = mutableListOf<Offset>()
            routeDirectConnectionWithPortLeads(
                p0 = pts[0],
                p1 = pts[1],
                startHorizontal = startHorizontal,
                endHorizontal = endHorizontal,
                startPortLead = startPortLead,
                endPortLead = endPortLead,
                gridSize = gridSize,
                startBorderX = startBorderX,
                endBorderX = endBorderX,
                outWaypoints = waypoints
            )
            return simplifyOrthogonalPath(waypoints)
        }

        // Multi-point connection with intermediate waypoints:
        // Place start lead waypoint at wStart and end lead waypoint at wEnd,
        // then route through all intermediate waypoints using standard Auto (no middle route).
        val innerPts = mutableListOf<Offset>()
        if (startPortLead) {
            val pLead0 = computeStartPortLead(pts.first(), startBorderX, gridSize)
            val pNext = pts[1]
            // If the next waypoint is ahead but within lead range, avoid adding overshooting lead waypoint
            if (pNext.x > pts.first().x && pNext.x <= pLead0.x) {
                innerPts.add(pts.first())
            } else {
                innerPts.add(pLead0)
            }
        } else {
            innerPts.add(pts.first())
        }
        for (i in 1 until pts.size - 1) {
            innerPts.add(pts[i])
        }
        if (endPortLead) {
            val pLead1 = computeEndPortLead(pts.last(), endBorderX, gridSize)
            val pPrev = pts[pts.size - 2]
            // If previous waypoint is behind target but within lead range, avoid adding overshooting lead waypoint
            if (pPrev.x < pts.last().x && pPrev.x >= pLead1.x) {
                innerPts.add(pts.last())
            } else {
                innerPts.add(pLead1)
            }
        } else {
            innerPts.add(pts.last())
        }

        val routedInner = computeOrthogonalPoints(
            points = innerPts,
            startHorizontal = if (startPortLead) true else startHorizontal,
            endHorizontal = if (endPortLead) true else endHorizontal,
            stepMode = OrthogonalStepMode.Auto,
            useMiddleRouteForDirectConnection = false,
            startPortLead = false,
            endPortLead = false,
            gridSize = gridSize
        )

        val finalWaypoints = mutableListOf<Offset>()
        finalWaypoints.add(pts.first())
        finalWaypoints.addAll(routedInner)
        finalWaypoints.add(pts.last())
        return simplifyOrthogonalPath(finalWaypoints)
    }

    private fun routeDirectConnectionWithPortLeads(
        p0: Offset,
        p1: Offset,
        startHorizontal: Boolean,
        endHorizontal: Boolean,
        startPortLead: Boolean,
        endPortLead: Boolean,
        gridSize: Float,
        startBorderX: Float? = null,
        endBorderX: Float? = null,
        outWaypoints: MutableList<Offset>
    ) {
        outWaypoints.add(p0)

        // 1. Both start and end have port leads (Node output -> Node input)
        if (startPortLead && endPortLead) {
            val pLead0 = computeStartPortLead(p0, startBorderX, gridSize)
            val pLead1 = computeEndPortLead(p1, endBorderX, gridSize)
            val b0 = startBorderX ?: (p0.x + NODE_PORT_INSET)
            val b1 = endBorderX ?: (p1.x - NODE_PORT_INSET)
            val isForward = if (startBorderX != null && endBorderX != null) b1 > b0 else p1.x > p0.x

            if (isForward) {
                if (abs(p0.y - p1.y) < ORTHOGONAL_ALIGNMENT_TOLERANCE) {
                    outWaypoints.add(p1)
                } else if (pLead1.x >= pLead0.x) {
                    outWaypoints.add(Offset(pLead1.x, p0.y))
                    outWaypoints.add(pLead1)
                    outWaypoints.add(p1)
                } else {
                    // Nodes or ports are too close together for full leads.
                    // If ports are in near range (< 30f), avoid adding the additional middle waypoints
                    // that cause loops or improper vertical middle fallbacks.
                    val corridorWidth = if (b1 > b0) b1 - b0 else (p1.x - p0.x)
                    if (corridorWidth < 30f) {
                        outWaypoints.add(Offset(p0.x, p1.y))
                        outWaypoints.add(p1)
                    } else {
                        val stepX = if (b1 > b0) (b0 + b1) / 2f else (p0.x + p1.x) / 2f
                        outWaypoints.add(Offset(stepX, p0.y))
                        outWaypoints.add(Offset(stepX, p1.y))
                        outWaypoints.add(p1)
                    }
                }
            } else {
                // Backward connection (target node is behind source node) -> loop around
                val turnY = if (abs(p0.y - p1.y) < ORTHOGONAL_ALIGNMENT_TOLERANCE) {
                    p0.y + gridSize
                } else {
                    (p0.y + p1.y) / 2f
                }
                outWaypoints.add(pLead0)
                outWaypoints.add(Offset(pLead0.x, turnY))
                outWaypoints.add(Offset(pLead1.x, turnY))
                outWaypoints.add(pLead1)
                outWaypoints.add(p1)
            }
            return
        }

        // 2. Only startPortLead (Node output -> Junction or floating target)
        if (startPortLead) {
            val pLead0 = computeStartPortLead(p0, startBorderX, gridSize)
            val b0 = startBorderX ?: (p0.x + NODE_PORT_INSET)
            val isForward = if (startBorderX != null) p1.x > b0 else p1.x > p0.x

            if (isForward) {
                if (abs(p0.y - p1.y) < ORTHOGONAL_ALIGNMENT_TOLERANCE) {
                    outWaypoints.add(p1)
                } else if (p1.x >= pLead0.x) {
                    if (endHorizontal) {
                        outWaypoints.add(pLead0)
                        outWaypoints.add(Offset(pLead0.x, p1.y))
                    } else {
                        outWaypoints.add(Offset(p1.x, p0.y))
                    }
                    outWaypoints.add(p1)
                } else {
                    // Target is ahead but within lead range; if in near range, avoid additional waypoints
                    val corridorWidth = p1.x - p0.x
                    if (corridorWidth < 30f) {
                        if (endHorizontal) {
                            outWaypoints.add(Offset(p0.x, p1.y))
                        } else {
                            outWaypoints.add(Offset(p1.x, p0.y))
                        }
                    } else {
                        if (endHorizontal) {
                            val stepX = (p0.x + p1.x) / 2f
                            outWaypoints.add(Offset(stepX, p0.y))
                            outWaypoints.add(Offset(stepX, p1.y))
                        } else {
                            outWaypoints.add(Offset(p1.x, p0.y))
                        }
                    }
                    outWaypoints.add(p1)
                }
            } else {
                val turnY = if (abs(p0.y - p1.y) < ORTHOGONAL_ALIGNMENT_TOLERANCE) p0.y + gridSize else (p0.y + p1.y) / 2f
                outWaypoints.add(pLead0)
                outWaypoints.add(Offset(pLead0.x, turnY))
                outWaypoints.add(Offset(p1.x, turnY))
                outWaypoints.add(p1)
            }
            return
        }

        // 3. Only endPortLead (Junction -> Node input)
        if (endPortLead) {
            val pLead1 = computeEndPortLead(p1, endBorderX, gridSize)
            val b1 = endBorderX ?: (p1.x - NODE_PORT_INSET)
            val isForward = if (endBorderX != null) b1 > p0.x else p1.x > p0.x

            if (isForward) {
                if (abs(p0.y - p1.y) < ORTHOGONAL_ALIGNMENT_TOLERANCE) {
                    outWaypoints.add(p1)
                } else if (pLead1.x >= p0.x) {
                    if (startHorizontal) {
                        outWaypoints.add(Offset(pLead1.x, p0.y))
                        outWaypoints.add(pLead1)
                    } else {
                        outWaypoints.add(Offset(p0.x, p1.y))
                        outWaypoints.add(pLead1)
                    }
                    outWaypoints.add(p1)
                } else {
                    // Source is ahead of pLead1.x (too close to target); if in near range, avoid additional waypoints
                    val corridorWidth = p1.x - p0.x
                    if (corridorWidth < 30f) {
                        outWaypoints.add(Offset(p0.x, p1.y))
                    } else {
                        if (startHorizontal) {
                            val stepX = (p0.x + p1.x) / 2f
                            outWaypoints.add(Offset(stepX, p0.y))
                            outWaypoints.add(Offset(stepX, p1.y))
                        } else {
                            outWaypoints.add(Offset(p0.x, p1.y))
                        }
                    }
                    outWaypoints.add(p1)
                }
            } else {
                val turnY = if (abs(p0.y - p1.y) < ORTHOGONAL_ALIGNMENT_TOLERANCE) p0.y + gridSize else (p0.y + p1.y) / 2f
                if (startHorizontal) {
                    outWaypoints.add(Offset(p0.x + gridSize, p0.y))
                    outWaypoints.add(Offset(p0.x + gridSize, turnY))
                    outWaypoints.add(Offset(pLead1.x, turnY))
                    outWaypoints.add(pLead1)
                } else {
                    outWaypoints.add(Offset(p0.x, turnY))
                    outWaypoints.add(Offset(pLead1.x, turnY))
                    outWaypoints.add(pLead1)
                }
                outWaypoints.add(p1)
            }
            return
        }

        outWaypoints.add(p1)
    }

    private fun simplifyOrthogonalPath(points: List<Offset>): List<Offset> {
        if (points.size <= 2) return points
        val simplified = mutableListOf<Offset>()
        simplified.add(points[0])
        for (i in 1 until points.size - 1) {
            val prev = simplified.last()
            val curr = points[i]
            val next = points[i + 1]

            val isCollinearH = abs(prev.y - curr.y) < COLLINEAR_TOLERANCE && abs(curr.y - next.y) < COLLINEAR_TOLERANCE
            val isCollinearV = abs(prev.x - curr.x) < COLLINEAR_TOLERANCE && abs(curr.x - next.x) < COLLINEAR_TOLERANCE
            val isDuplicate = (curr - prev).getDistance() < COLLINEAR_TOLERANCE

            if (!isCollinearH && !isCollinearV && !isDuplicate) {
                simplified.add(curr)
            }
        }
        val last = points.last()
        if ((last - simplified.last()).getDistance() >= COLLINEAR_TOLERANCE) {
            simplified.add(last)
        } else if (simplified.size == 1) {
            simplified.add(last)
        } else {
            simplified[simplified.size - 1] = last
        }
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
     *
     * @param canvasOffset The canvas pan offset (state.offset). When provided together with [scale],
     *   the function treats [points] as screen-space coordinates (board * scale + offset) and
     *   de-transforms to board space before computing orthogonal routing. This ensures the routing
     *   thresholds are always evaluated in logical units, making rendering fully zoom-stable.
     */
    fun buildConnectionPath(
        points: List<Offset>,
        style: ConnectionCurveStyle = ConnectionCurveStyle.CardinalSpline,
        tension: Float = DEFAULT_TENSION,
        startHorizontal: Boolean = true,
        endHorizontal: Boolean = true,
        scale: Float = 1f,
        canvasOffset: Offset = Offset.Zero,
        stepMode: OrthogonalStepMode = OrthogonalStepMode.Auto,
        startFilletLeadIn: Offset? = null,
        endTrimDistance: Float = 0f,
        useMiddleRouteForDirectConnection: Boolean = true,
        startPortLead: Boolean = false,
        endPortLead: Boolean = false,
        gridSize: Float = DEFAULT_GRID_SIZE,
        startBorderX: Float? = null,
        endBorderX: Float? = null
    ): Path {
        val validPoints = points.filter { it.x.isFinite() && it.y.isFinite() }
        val path = Path()
        if (validPoints.isEmpty()) return path
        if (validPoints.size == 1) {
            path.moveTo(validPoints[0].x, validPoints[0].y)
            return path
        }

        when (style) {
            ConnectionCurveStyle.Straight -> {
                val pts = sanitizePoints(validPoints)
                path.moveTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size) {
                    val p = pts[i]
                    path.lineTo(p.x, p.y)
                }
            }

            ConnectionCurveStyle.Bezier,
            ConnectionCurveStyle.CardinalSpline -> {
                val pts = sanitizePoints(validPoints)
                path.moveTo(pts[0].x, pts[0].y)
                val segments = computeHarmonizedSplineSegments(pts, tension, startHorizontal, endHorizontal, scale)
                for (seg in segments) {
                    path.cubicTo(seg.control1.x, seg.control1.y, seg.control2.x, seg.control2.y, seg.end.x, seg.end.y)
                }
            }

            ConnectionCurveStyle.Orthogonal -> {
                // Always route in board space so the routing thresholds (0.2f, 0.5f, etc.) are
                // evaluated against logical units — producing the same path topology at every zoom.
                val rawBoardPts = if (scale != 1f || canvasOffset != Offset.Zero) {
                    validPoints.map { (it - canvasOffset) * (1f / scale) }
                } else {
                    validPoints
                }
                val boardPts = sanitizePoints(rawBoardPts)
                val orthoBoard = computeOrthogonalPoints(
                    points = boardPts,
                    startHorizontal = startHorizontal,
                    endHorizontal = endHorizontal,
                    stepMode = stepMode,
                    useMiddleRouteForDirectConnection = useMiddleRouteForDirectConnection,
                    startPortLead = startPortLead,
                    endPortLead = endPortLead,
                    gridSize = gridSize,
                    startBorderX = startBorderX,
                    endBorderX = endBorderX
                )
                // Re-transform orthogonal waypoints back to screen space.
                val orthoScreen = if (scale != 1f || canvasOffset != Offset.Zero) {
                    orthoBoard.map { it * scale + canvasOffset }
                } else {
                    orthoBoard
                }
                val r = maxOf(
                    DEFAULT_CORNER_RADIUS * scale,
                    tension * TENSION_RADIUS_FACTOR * scale,
                    MIN_RENDER_CORNER_RADIUS
                )
                return buildRoundedPolylinePath(
                    points = orthoScreen,
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
     * Pass [canvasOffset] (state.offset) when [points] are in screen space to ensure orthogonal
     * routing thresholds operate in board space and remain zoom-stable.
     */
    fun sampleConnectionPoints(
        points: List<Offset>,
        style: ConnectionCurveStyle = ConnectionCurveStyle.CardinalSpline,
        tension: Float = DEFAULT_TENSION,
        samplesPerSegment: Int = 20,
        startHorizontal: Boolean = true,
        endHorizontal: Boolean = true,
        scale: Float = 1f,
        canvasOffset: Offset = Offset.Zero,
        stepMode: OrthogonalStepMode = OrthogonalStepMode.Auto,
        startFilletLeadIn: Offset? = null,
        endTrimDistance: Float = 0f,
        useMiddleRouteForDirectConnection: Boolean = true,
        startPortLead: Boolean = false,
        endPortLead: Boolean = false,
        gridSize: Float = DEFAULT_GRID_SIZE,
        startBorderX: Float? = null,
        endBorderX: Float? = null
    ): List<Offset> {
        val validPoints = points.filter { it.x.isFinite() && it.y.isFinite() }
        if (validPoints.isEmpty()) return emptyList()
        if (validPoints.size == 1) return validPoints

        val sampled = mutableListOf<Offset>()

        when (style) {
            ConnectionCurveStyle.Straight -> {
                sampled.addAll(sanitizePoints(validPoints))
            }

            ConnectionCurveStyle.Orthogonal -> {
                // Route in board space for zoom-stable thresholds, then re-transform to screen.
                val rawBoardPts = if (scale != 1f || canvasOffset != Offset.Zero) {
                    validPoints.map { (it - canvasOffset) * (1f / scale) }
                } else { validPoints }
                val boardPts = sanitizePoints(rawBoardPts)
                val orthoBoard = computeOrthogonalPoints(
                    boardPts,
                    startHorizontal,
                    endHorizontal,
                    stepMode,
                    useMiddleRouteForDirectConnection,
                    startPortLead = startPortLead,
                    endPortLead = endPortLead,
                    gridSize = gridSize,
                    startBorderX = startBorderX,
                    endBorderX = endBorderX
                )
                val orthoPoints = if (scale != 1f || canvasOffset != Offset.Zero) {
                    orthoBoard.map { it * scale + canvasOffset }
                } else { orthoBoard }
                if (orthoPoints.isEmpty()) return emptyList()
                if (orthoPoints.size == 1) return orthoPoints

                val effectiveOrtho = orthoPoints.toMutableList()
                if (endTrimDistance > 0f && effectiveOrtho.size >= 2) {
                    val pPenultimate = effectiveOrtho[effectiveOrtho.size - 2]
                    val pLast = effectiveOrtho.last()
                    val vEnd = pLast - pPenultimate
                    val lenEnd = vEnd.getDistance()
                    if (lenEnd > MIN_SEGMENT_LENGTH) {
                        val trimDist = minOf(endTrimDistance, lenEnd * TRIM_FACTOR)
                        effectiveOrtho[effectiveOrtho.size - 1] = Offset(
                            pLast.x - (vEnd.x / lenEnd) * trimDist,
                            pLast.y - (vEnd.y / lenEnd) * trimDist
                        )
                    }
                }

                val rBase = maxOf(DEFAULT_CORNER_RADIUS * scale, tension * TENSION_RADIUS_FACTOR * scale)

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

                    if (lenIn >= MIN_SEGMENT_LENGTH && lenOut >= MIN_SEGMENT_LENGTH) {
                        val vIn = Offset(vInRaw.x / lenIn, vInRaw.y / lenIn)
                        val vOut = Offset(vOutRaw.x / lenOut, vOutRaw.y / lenOut)
                        val dot = vIn.x * vOut.x + vIn.y * vOut.y
                        if (abs(dot) < MIN_SEGMENT_LENGTH || dot < FILLET_DOT_TOLERANCE_BEND) {
                            val r = minOf(rBase, lenIn * TRIM_FACTOR, lenOut * TRIM_FACTOR)
                            val minR = MIN_CORNER_RADIUS
                            if (r >= minR && r > 0f) {
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

                            val r = minOf(rBase, lenIn * TRIM_FACTOR, lenOut * TRIM_FACTOR)
                            val minR = MIN_CORNER_RADIUS
                            if (lenIn == 0f || lenOut == 0f || r < minR || r <= 0f) {
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

                            val r = minOf(rBase, lenIn * TRIM_FACTOR, lenOut * TRIM_FACTOR)
                            val minR = MIN_CORNER_RADIUS
                            if (lenIn == 0f || lenOut == 0f || r < minR || r <= 0f) {
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
