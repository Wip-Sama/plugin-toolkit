package org.wip.plugintoolkit.features.flows.utils

import androidx.compose.ui.geometry.Offset

object BoardMathUtils {

    /**
     * Calculates the minimum distance from a point [p] to a cubic bezier curve defined by [start] and [end]
     * with control points computed using a standard horizontal offset.
     */
    fun getDistanceToBezier(p: Offset, start: Offset, end: Offset): Float {
        val controlPointOffset = kotlin.math.abs(end.x - start.x) / 2f
        val c1x = start.x + controlPointOffset
        val c1y = start.y
        val c2x = end.x - controlPointOffset
        val c2y = end.y

        var minDistance = Float.MAX_VALUE
        val steps = 25

        var prevX = start.x
        var prevY = start.y

        for (i in 1..steps) {
            val t = i.toFloat() / steps
            val mt = 1f - t
            val mt2 = mt * mt
            val mt3 = mt2 * mt
            val t2 = t * t
            val t3 = t2 * t

            val bx = start.x * mt3 + c1x * (3f * mt2 * t) + c2x * (3f * mt * t2) + end.x * t3
            val by = start.y * mt3 + c1y * (3f * mt2 * t) + c2y * (3f * mt * t2) + end.y * t3

            val l2 = (bx - prevX) * (bx - prevX) + (by - prevY) * (by - prevY)
            val dist = if (l2 == 0f) {
                val dx = p.x - prevX
                val dy = p.y - prevY
                kotlin.math.sqrt(dx * dx + dy * dy)
            } else {
                var tProj = ((p.x - prevX) * (bx - prevX) + (p.y - prevY) * (by - prevY)) / l2
                if (tProj < 0f) tProj = 0f else if (tProj > 1f) tProj = 1f
                val projX = prevX + tProj * (bx - prevX)
                val projY = prevY + tProj * (by - prevY)
                val dx = p.x - projX
                val dy = p.y - projY
                kotlin.math.sqrt(dx * dx + dy * dy)
            }

            if (dist < minDistance) {
                minDistance = dist
            }

            prevX = bx
            prevY = by
        }
        return minDistance
    }

    /**
     * Calculates the midpoint (t=0.5) of a cubic bezier curve.
     */
    fun getBezierMidpoint(start: Offset, end: Offset): Offset {
        val controlPointOffset = kotlin.math.abs(end.x - start.x) / 2f
        val c1 = Offset(start.x + controlPointOffset, start.y)
        val c2 = Offset(end.x - controlPointOffset, end.y)
        val t = 0.5f
        val mt = 0.5f
        return start * (mt * mt * mt) +
                c1 * (3f * mt * mt * t) +
                c2 * (3f * mt * t * t) +
                end * (t * t * t)
    }

    /**
     * Data class to hold the 4 points (start, cp1, cp2, end) of a Cubic Bezier curve.
     */
    data class CubicBezierCurve(val p0: Offset, val p1: Offset, val p2: Offset, val p3: Offset)

    /**
     * Splits a cubic bezier curve exactly in half (at t=0.5) using De Casteljau's algorithm.
     * Returns a pair of the left half and right half curves.
     */
    fun splitCubicBezierInHalf(start: Offset, end: Offset): Pair<CubicBezierCurve, CubicBezierCurve> {
        val controlPointOffset = kotlin.math.abs(end.x - start.x) / 2f
        val p0 = start
        val p1 = Offset(start.x + controlPointOffset, start.y)
        val p2 = Offset(end.x - controlPointOffset, end.y)
        val p3 = end

        val p01 = (p0 + p1) / 2f
        val p12 = (p1 + p2) / 2f
        val p23 = (p2 + p3) / 2f
        val p012 = (p01 + p12) / 2f
        val p123 = (p12 + p23) / 2f
        val p0123 = (p012 + p123) / 2f

        return Pair(
            CubicBezierCurve(p0, p01, p012, p0123),
            CubicBezierCurve(p0123, p123, p23, p3)
        )
    }
}
