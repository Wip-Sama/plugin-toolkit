package org.wip.plugintoolkit.features.colorpicker.logic

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import org.wip.plugintoolkit.features.colorpicker.model.ColorRange
import org.wip.plugintoolkit.features.colorpicker.utils.argb
import org.wip.plugintoolkit.features.colorpicker.utils.blue
import org.wip.plugintoolkit.features.colorpicker.utils.darken
import org.wip.plugintoolkit.features.colorpicker.utils.fromHueProgress
import org.wip.plugintoolkit.features.colorpicker.utils.green
import org.wip.plugintoolkit.features.colorpicker.utils.lighten
import org.wip.plugintoolkit.core.theme.ColorEngine
import org.wip.plugintoolkit.features.colorpicker.utils.red
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

internal data class HsvColor(
    val hue: Float,
    val saturation: Float,
    val value: Float,
    val alpha: Float = 1f
) {
    fun toColor(): Color = ColorPickerHelper.hsvToColor(this)
}

internal data class HslColor(
    val hue: Float,
    val saturation: Float,
    val lightness: Float,
    val alpha: Float = 1f
) {
    fun toColor(): Color = ColorPickerHelper.hslToColor(this)
}

internal data class CmykColor(
    val cyan: Float,
    val magenta: Float,
    val yellow: Float,
    val keyBlack: Float,
    val alpha: Float = 1f
) {
    fun toColor(): Color = ColorPickerHelper.cmykToColor(this)
}

internal object ColorPickerHelper {

    fun calculateRangeProgress(progress: Double): Pair<Double, ColorRange> {
        val range: ColorRange
        return progress * 6 - when {
            progress < 1f / 6 -> {
                range = ColorRange.RedToYellow
                0
            }
            progress < 2f / 6 -> {
                range = ColorRange.YellowToGreen
                1
            }
            progress < 3f / 6 -> {
                range = ColorRange.GreenToCyan
                2
            }
            progress < 4f / 6 -> {
                range = ColorRange.CyanToBlue
                3
            }
            progress < 5f / 6 -> {
                range = ColorRange.BlueToPurple
                4
            }
            else -> {
                range = ColorRange.PurpleToRed
                5
            }
        } to range
    }

    fun colorToHsv(color: Color): HsvColor {
        val (a, rInt, gInt, bInt) = color.argb()
        val r = rInt / 255f
        val g = gInt / 255f
        val b = bInt / 255f

        val maxVal = max(r, max(g, b))
        val minVal = min(r, min(g, b))
        val delta = maxVal - minVal

        val hue = if (delta == 0f) {
            0f
        } else {
            val h = when (maxVal) {
                r -> ((g - b) / delta).let { if (g < b) it + 6f else it }
                g -> ((b - r) / delta) + 2f
                else -> ((r - g) / delta) + 4f
            }
            ((h * 60f) % 360f + 360f) % 360f
        }

        val sat = if (maxVal == 0f) 0f else delta / maxVal
        val value = maxVal
        val alpha = a / 255f

        return HsvColor(
            hue = hue.coerceIn(0f, 360f),
            saturation = sat.coerceIn(0f, 1f),
            value = value.coerceIn(0f, 1f),
            alpha = alpha.coerceIn(0f, 1f)
        )
    }

    fun hsvToColor(hsv: HsvColor): Color {
        val h = ((hsv.hue % 360f) + 360f) % 360f
        val s = hsv.saturation.coerceIn(0f, 1f)
        val v = hsv.value.coerceIn(0f, 1f)
        val a = (hsv.alpha.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)

        val c = v * s
        val x = c * (1f - abs((h / 60f) % 2f - 1f))
        val m = v - c

        val (rPrime, gPrime, bPrime) = when ((h / 60f).toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }

        val r = ((rPrime + m) * 255f).roundToInt().coerceIn(0, 255)
        val g = ((gPrime + m) * 255f).roundToInt().coerceIn(0, 255)
        val b = ((bPrime + m) * 255f).roundToInt().coerceIn(0, 255)

        return Color(r, g, b, a)
    }

    fun colorToHsl(color: Color): HslColor {
        val (a, rInt, gInt, bInt) = color.argb()
        val r = rInt / 255f
        val g = gInt / 255f
        val b = bInt / 255f

        val maxVal = max(r, max(g, b))
        val minVal = min(r, min(g, b))
        val delta = maxVal - minVal

        val lightness = (maxVal + minVal) / 2f
        val sat = if (delta == 0f) {
            0f
        } else if (lightness < 0.5f) {
            delta / (maxVal + minVal)
        } else {
            delta / (2f - maxVal - minVal)
        }

        val hue = if (delta == 0f) {
            0f
        } else {
            val h = when (maxVal) {
                r -> ((g - b) / delta).let { if (g < b) it + 6f else it }
                g -> ((b - r) / delta) + 2f
                else -> ((r - g) / delta) + 4f
            }
            ((h * 60f) % 360f + 360f) % 360f
        }

        return HslColor(
            hue = hue.coerceIn(0f, 360f),
            saturation = sat.coerceIn(0f, 1f),
            lightness = lightness.coerceIn(0f, 1f),
            alpha = (a / 255f).coerceIn(0f, 1f)
        )
    }

    fun hslToColor(hsl: HslColor): Color {
        val h = ((hsl.hue % 360f) + 360f) % 360f
        val s = hsl.saturation.coerceIn(0f, 1f)
        val l = hsl.lightness.coerceIn(0f, 1f)
        val a = (hsl.alpha.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)

        val c = (1f - abs(2f * l - 1f)) * s
        val x = c * (1f - abs((h / 60f) % 2f - 1f))
        val m = l - c / 2f

        val (rPrime, gPrime, bPrime) = when ((h / 60f).toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }

        val r = ((rPrime + m) * 255f).roundToInt().coerceIn(0, 255)
        val g = ((gPrime + m) * 255f).roundToInt().coerceIn(0, 255)
        val b = ((bPrime + m) * 255f).roundToInt().coerceIn(0, 255)

        return Color(r, g, b, a)
    }

    fun colorToCmyk(color: Color): CmykColor {
        val (a, rInt, gInt, bInt) = color.argb()
        val r = rInt / 255f
        val g = gInt / 255f
        val b = bInt / 255f

        val k = 1f - max(r, max(g, b))
        val c = if (k >= 1f) 0f else (1f - r - k) / (1f - k)
        val m = if (k >= 1f) 0f else (1f - g - k) / (1f - k)
        val y = if (k >= 1f) 0f else (1f - b - k) / (1f - k)

        return CmykColor(
            cyan = c.coerceIn(0f, 1f),
            magenta = m.coerceIn(0f, 1f),
            yellow = y.coerceIn(0f, 1f),
            keyBlack = k.coerceIn(0f, 1f),
            alpha = (a / 255f).coerceIn(0f, 1f)
        )
    }

    fun cmykToColor(cmyk: CmykColor): Color {
        val c = cmyk.cyan.coerceIn(0f, 1f)
        val m = cmyk.magenta.coerceIn(0f, 1f)
        val y = cmyk.yellow.coerceIn(0f, 1f)
        val k = cmyk.keyBlack.coerceIn(0f, 1f)
        val a = (cmyk.alpha.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)

        val r = ((1f - c) * (1f - k) * 255f).roundToInt().coerceIn(0, 255)
        val g = ((1f - m) * (1f - k) * 255f).roundToInt().coerceIn(0, 255)
        val b = ((1f - y) * (1f - k) * 255f).roundToInt().coerceIn(0, 255)

        return Color(r, g, b, a)
    }

    fun calculateClassicLocation(hsv: HsvColor, width: Float, height: Float): Offset {
        if (width <= 0f || height <= 0f) return Offset.Zero
        val x = (hsv.saturation * width).coerceIn(0f, width)
        val y = ((1f - hsv.value) * height).coerceIn(0f, height)
        return Offset(x, y)
    }

    fun calculateClassicColor(x: Float, y: Float, width: Float, height: Float, hue: Float, alpha: Float): Color {
        if (width <= 0f || height <= 0f) return Color.White
        val sat = (x / width).coerceIn(0f, 1f)
        val value = (1f - (y / height)).coerceIn(0f, 1f)
        return hsvToColor(HsvColor(hue = hue, saturation = sat, value = value, alpha = alpha))
    }

    fun calculateCircleLocation(hsv: HsvColor, radius: Float): Offset {
        if (radius <= 0f) return Offset.Zero
        val angleRad = (hsv.hue * PI / 180.0).toFloat()
        val dist = (hsv.saturation * radius).coerceIn(0f, radius)
        return Offset(
            x = radius + dist * cos(angleRad),
            y = radius + dist * sin(angleRad)
        )
    }

    fun calculateCircleColor(
        x: Float,
        y: Float,
        radius: Float,
        brightness: Float,
        alpha: Float,
        lightCenter: Boolean
    ): Pair<Color, Offset> {
        if (radius <= 0f) return Pair(Color.White, Offset.Zero)
        val angleRad = atan2(y - radius, x - radius)
        val angleDeg = ((angleRad * 180.0 / PI + 360.0) % 360.0).toFloat()
        val length = MathHelper.getLength(x, y, radius)
        val boundedLocation = MathHelper.getBoundedPointWithInRadius(
            x = x,
            y = y,
            length = length,
            radius = radius,
            strategy = BoundedPointStrategy.Inside
        )
        val sat = (length / radius).coerceIn(0f, 1f)
        val value = if (lightCenter) (1f - brightness).coerceIn(0f, 1f) else brightness.coerceIn(0f, 1f)
        val color = hsvToColor(HsvColor(hue = angleDeg, saturation = sat, value = value, alpha = alpha))
        return Pair(color, boundedLocation)
    }

    fun calculateRingLocation(hue: Float, radius: Float, ringWidthPx: Float): Offset {
        if (radius <= 0f) return Offset.Zero
        val rCenter = (radius - ringWidthPx / 2f).coerceAtLeast(0f)
        val angleRad = (hue * PI / 180.0).toFloat()
        return Offset(
            x = radius + rCenter * cos(angleRad),
            y = radius + rCenter * sin(angleRad)
        )
    }

    fun calculateRingColor(
        x: Float,
        y: Float,
        radius: Float,
        ringWidthPx: Float,
        lightness: Float,
        darkness: Float,
        alpha: Float
    ): Pair<Color, Offset> {
        if (radius <= 0f) return Pair(Color.Red, Offset.Zero)
        val angleRad = atan2(y - radius, x - radius)
        val angleDeg = ((angleRad * 180.0 / PI + 360.0) % 360.0).toFloat()
        val rCenter = (radius - ringWidthPx / 2f).coerceAtLeast(0f)
        val boundedLocation = MathHelper.getBoundedPointWithInRadius(
            x = x,
            y = y,
            length = MathHelper.getLength(x, y, radius),
            radius = rCenter,
            strategy = BoundedPointStrategy.Edge
        )
        val pure = Color.fromHueProgress(angleDeg / 360f)
        val r = pure.red().lighten(lightness).darken(darkness)
        val g = pure.green().lighten(lightness).darken(darkness)
        val b = pure.blue().lighten(lightness).darken(darkness)
        val color = Color(r, g, b, (255f * alpha).roundToInt())
        return Pair(color, boundedLocation)
    }

    fun getSimpleRingColor(sector: Int, track: Int, tracksCount: Int, sectorsCount: Int): Color {
        if (sectorsCount <= 0 || tracksCount <= 0) return Color.Red
        val progress = (sector.toFloat() / sectorsCount).coerceIn(0f, 1f)
        val deepProgress = (track.toFloat() / tracksCount).coerceIn(0f, 1f)
        val pureColor = Color.fromHueProgress(progress)
        val dark = 0.5f * deepProgress
        return Color(
            pureColor.red().darken(dark),
            pureColor.green().darken(dark),
            pureColor.blue().darken(dark)
        )
    }

    fun findNearestSimpleRingLocation(targetColor: Color, tracksCount: Int, sectorsCount: Int): IntOffset {
        if (sectorsCount <= 0 || tracksCount <= 0) return IntOffset(0, 0)
        var bestSector = 0
        var bestTrack = 0
        var minDiff = Float.MAX_VALUE

        val (_, tR, tG, tB) = targetColor.argb()
        for (track in 0 until tracksCount) {
            for (sector in 0 until sectorsCount) {
                val cellColor = getSimpleRingColor(sector, track, tracksCount, sectorsCount)
                val (_, cR, cG, cB) = cellColor.argb()
                val diff = ((tR - cR) * (tR - cR) + (tG - cG) * (tG - cG) + (tB - cB) * (tB - cB)).toFloat()
                if (diff < minDiff) {
                    minDiff = diff
                    bestSector = sector
                    bestTrack = track
                }
            }
        }
        return IntOffset(bestSector, bestTrack)
    }

    fun parseHex(hexStr: String): Color? {
        val trimmed = hexStr.trim().removePrefix("#")
        return try {
            when (trimmed.length) {
                3 -> {
                    val r = trimmed[0].toString().repeat(2).toInt(16)
                    val g = trimmed[1].toString().repeat(2).toInt(16)
                    val b = trimmed[2].toString().repeat(2).toInt(16)
                    Color(r, g, b, 255)
                }
                6 -> {
                    val r = trimmed.substring(0, 2).toInt(16)
                    val g = trimmed.substring(2, 4).toInt(16)
                    val b = trimmed.substring(4, 6).toInt(16)
                    Color(r, g, b, 255)
                }
                8 -> {
                    val a = trimmed.substring(0, 2).toInt(16)
                    val r = trimmed.substring(2, 4).toInt(16)
                    val g = trimmed.substring(4, 6).toInt(16)
                    val b = trimmed.substring(6, 8).toInt(16)
                    Color(r, g, b, a)
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun parseRgb(r: Int, g: Int, b: Int, aPercent: Int): Color {
        return Color(
            red = r.coerceIn(0, 255),
            green = g.coerceIn(0, 255),
            blue = b.coerceIn(0, 255),
            alpha = ((aPercent.coerceIn(0, 100) / 100f) * 255f).roundToInt()
        )
    }

    fun parseHsl(h: Float, sPercent: Float, lPercent: Float, aPercent: Float): Color {
        return hslToColor(
            HslColor(
                hue = h.coerceIn(0f, 360f),
                saturation = (sPercent / 100f).coerceIn(0f, 1f),
                lightness = (lPercent / 100f).coerceIn(0f, 1f),
                alpha = (aPercent / 100f).coerceIn(0f, 1f)
            )
        )
    }

    fun parseCmyk(cPercent: Float, mPercent: Float, yPercent: Float, kPercent: Float, aPercent: Float): Color {
        return cmykToColor(
            CmykColor(
                cyan = (cPercent / 100f).coerceIn(0f, 1f),
                magenta = (mPercent / 100f).coerceIn(0f, 1f),
                yellow = (yPercent / 100f).coerceIn(0f, 1f),
                keyBlack = (kPercent / 100f).coerceIn(0f, 1f),
                alpha = (aPercent / 100f).coerceIn(0f, 1f)
            )
        )
    }

    fun parseHct(h: Float, c: Float, t: Float, aPercent: Float = 100f): Color {
        val base = ColorEngine.hctToColor(h.coerceIn(0f, 360f), c.coerceAtLeast(0f), t.coerceIn(0f, 100f))
        return base.copy(alpha = (aPercent / 100f).coerceIn(0f, 1f))
    }

    fun parseAnyColor(input: String): Color? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        val hexCandidate = trimmed.removePrefix("#").trim()
        if ((hexCandidate.length == 3 || hexCandidate.length == 6 || hexCandidate.length == 8) &&
            hexCandidate.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
        ) {
            parseHex(hexCandidate)?.let { return it }
        }

        val lower = trimmed.lowercase()

        if (lower.startsWith("rgb")) {
            val nums = extractNumbers(lower)
            if (nums.size >= 3) {
                val r = nums[0].toInt().coerceIn(0, 255)
                val g = nums[1].toInt().coerceIn(0, 255)
                val b = nums[2].toInt().coerceIn(0, 255)
                val a = if (nums.size >= 4) {
                    val rawA = nums[3]
                    if (rawA <= 1f) (rawA * 100f).roundToInt() else rawA.roundToInt()
                } else 100
                return parseRgb(r, g, b, a)
            }
        }

        if (lower.startsWith("hsl")) {
            val nums = extractNumbers(lower)
            if (nums.size >= 3) {
                val h = nums[0].coerceIn(0f, 360f)
                val s = nums[1].coerceIn(0f, 100f)
                val l = nums[2].coerceIn(0f, 100f)
                val a = if (nums.size >= 4) {
                    val rawA = nums[3]
                    if (rawA <= 1f) rawA * 100f else rawA
                } else 100f
                return parseHsl(h, s, l, a)
            }
        }

        if (lower.startsWith("hct")) {
            val nums = extractNumbers(lower)
            if (nums.size >= 3) {
                val h = nums[0].coerceIn(0f, 360f)
                val c = nums[1].coerceAtLeast(0f)
                val t = nums[2].coerceIn(0f, 100f)
                val a = if (nums.size >= 4) {
                    val rawA = nums[3]
                    if (rawA <= 1f) rawA * 100f else rawA
                } else 100f
                return parseHct(h, c, t, a)
            }
        }

        if (lower.startsWith("cmyk")) {
            val nums = extractNumbers(lower)
            if (nums.size >= 4) {
                val c = nums[0].coerceIn(0f, 100f)
                val m = nums[1].coerceIn(0f, 100f)
                val y = nums[2].coerceIn(0f, 100f)
                val k = nums[3].coerceIn(0f, 100f)
                val a = if (nums.size >= 5) {
                    val rawA = nums[4]
                    if (rawA <= 1f) rawA * 100f else rawA
                } else 100f
                return parseCmyk(c, m, y, k, a)
            }
        }

        val genericNums = extractNumbers(trimmed)
        if (genericNums.size in 3..4) {
            val r = genericNums[0].toInt().coerceIn(0, 255)
            val g = genericNums[1].toInt().coerceIn(0, 255)
            val b = genericNums[2].toInt().coerceIn(0, 255)
            val a = if (genericNums.size == 4) {
                val rawA = genericNums[3]
                if (rawA <= 1f) (rawA * 100f).roundToInt() else rawA.roundToInt()
            } else 100
            return parseRgb(r, g, b, a)
        }

        return null
    }

    private fun extractNumbers(s: String): List<Float> {
        val regex = Regex("""[+-]?\d+(?:\.\d+)?""")
        return regex.findAll(s).mapNotNull { it.value.toFloatOrNull() }.toList()
    }
}
