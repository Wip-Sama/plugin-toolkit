package org.wip.plugintoolkit.features.colorpicker

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import org.wip.plugintoolkit.features.colorpicker.logic.ColorPickerHelper
import org.wip.plugintoolkit.features.colorpicker.logic.CmykColor
import org.wip.plugintoolkit.features.colorpicker.logic.HslColor
import org.wip.plugintoolkit.features.colorpicker.logic.HsvColor
import org.wip.plugintoolkit.features.colorpicker.model.ColorRange
import org.wip.plugintoolkit.features.colorpicker.utils.argb
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ColorPickerHelperTest {

    @Test
    fun testCalculateRangeProgress() {
        val (prog0, range0) = ColorPickerHelper.calculateRangeProgress(0.05)
        assertEquals(ColorRange.RedToYellow, range0)
        assertTrue(prog0 in 0.0..1.0)

        val (prog1, range1) = ColorPickerHelper.calculateRangeProgress(0.2)
        assertEquals(ColorRange.YellowToGreen, range1)
        assertTrue(prog1 in 0.0..1.0)

        val (prog2, range2) = ColorPickerHelper.calculateRangeProgress(0.4)
        assertEquals(ColorRange.GreenToCyan, range2)
        assertTrue(prog2 in 0.0..1.0)

        val (prog3, range3) = ColorPickerHelper.calculateRangeProgress(0.55)
        assertEquals(ColorRange.CyanToBlue, range3)
        assertTrue(prog3 in 0.0..1.0)

        val (prog4, range4) = ColorPickerHelper.calculateRangeProgress(0.7)
        assertEquals(ColorRange.BlueToPurple, range4)
        assertTrue(prog4 in 0.0..1.0)

        val (prog5, range5) = ColorPickerHelper.calculateRangeProgress(0.9)
        assertEquals(ColorRange.PurpleToRed, range5)
        assertTrue(prog5 in 0.0..1.0)
    }

    @Test
    fun testColorToHsvPrimaryColors() {
        val redHsv = ColorPickerHelper.colorToHsv(Color.Red)
        assertEquals(0f, redHsv.hue, 1f)
        assertEquals(1f, redHsv.saturation, 0.01f)
        assertEquals(1f, redHsv.value, 0.01f)
        assertEquals(1f, redHsv.alpha, 0.01f)

        val greenHsv = ColorPickerHelper.colorToHsv(Color.Green)
        assertEquals(120f, greenHsv.hue, 1f)
        assertEquals(1f, greenHsv.saturation, 0.01f)
        assertEquals(1f, greenHsv.value, 0.01f)

        val blueHsv = ColorPickerHelper.colorToHsv(Color.Blue)
        assertEquals(240f, blueHsv.hue, 1f)
        assertEquals(1f, blueHsv.saturation, 0.01f)
        assertEquals(1f, blueHsv.value, 0.01f)

        val yellowHsv = ColorPickerHelper.colorToHsv(Color.Yellow)
        assertEquals(60f, yellowHsv.hue, 1f)
        assertEquals(1f, yellowHsv.saturation, 0.01f)
        assertEquals(1f, yellowHsv.value, 0.01f)

        val cyanHsv = ColorPickerHelper.colorToHsv(Color.Cyan)
        assertEquals(180f, cyanHsv.hue, 1f)
        assertEquals(1f, cyanHsv.saturation, 0.01f)

        val magentaHsv = ColorPickerHelper.colorToHsv(Color.Magenta)
        assertEquals(300f, magentaHsv.hue, 1f)
        assertEquals(1f, magentaHsv.saturation, 0.01f)

        val whiteHsv = ColorPickerHelper.colorToHsv(Color.White)
        assertEquals(0f, whiteHsv.saturation, 0.01f)
        assertEquals(1f, whiteHsv.value, 0.01f)

        val blackHsv = ColorPickerHelper.colorToHsv(Color.Black)
        assertEquals(0f, blackHsv.value, 0.01f)
    }

    @Test
    fun testHsvToColorAndRoundTrip() {
        val colors = listOf(
            Color.Red, Color.Green, Color.Blue,
            Color.Yellow, Color.Cyan, Color.Magenta,
            Color.White, Color.Black, Color(0xFF6750A4),
            Color(0x80FF8800)
        )

        for (c in colors) {
            val hsv = ColorPickerHelper.colorToHsv(c)
            val back = ColorPickerHelper.hsvToColor(hsv)
            val (a1, r1, g1, b1) = c.argb()
            val (a2, r2, g2, b2) = back.argb()
            assertTrue(abs(a1 - a2) <= 2, "Alpha mismatch for $c: $a1 vs $a2")
            assertTrue(abs(r1 - r2) <= 2, "Red mismatch for $c: $r1 vs $r2")
            assertTrue(abs(g1 - g2) <= 2, "Green mismatch for $c: $g1 vs $g2")
            assertTrue(abs(b1 - b2) <= 2, "Blue mismatch for $c: $b1 vs $b2")
        }
    }

    @Test
    fun testHsvColorToColorExtension() {
        val hsv = HsvColor(hue = 0f, saturation = 1f, value = 1f, alpha = 1f)
        val color = hsv.toColor()
        assertEquals(Color.Red.toArgb(), color.toArgb())
    }

    @Test
    fun testColorToHslAndRoundTrip() {
        val redHsl = ColorPickerHelper.colorToHsl(Color.Red)
        assertEquals(0f, redHsl.hue, 1f)
        assertEquals(1f, redHsl.saturation, 0.01f)
        assertEquals(0.5f, redHsl.lightness, 0.01f)

        val colors = listOf(Color.Red, Color.Green, Color.Blue, Color.White, Color.Black, Color(0xFF123456))
        for (c in colors) {
            val hsl = ColorPickerHelper.colorToHsl(c)
            val back = ColorPickerHelper.hslToColor(hsl)
            val (a1, r1, g1, b1) = c.argb()
            val (a2, r2, g2, b2) = back.argb()
            assertTrue(abs(a1 - a2) <= 2)
            assertTrue(abs(r1 - r2) <= 3)
            assertTrue(abs(g1 - g2) <= 3)
            assertTrue(abs(b1 - b2) <= 3)
        }
    }

    @Test
    fun testHslColorToColorExtension() {
        val hsl = HslColor(hue = 120f, saturation = 1f, lightness = 0.5f, alpha = 1f)
        val color = hsl.toColor()
        assertEquals(Color.Green.toArgb(), color.toArgb())
    }

    @Test
    fun testColorToCmykAndRoundTrip() {
        val redCmyk = ColorPickerHelper.colorToCmyk(Color.Red)
        assertEquals(0f, redCmyk.cyan, 0.01f)
        assertEquals(1f, redCmyk.magenta, 0.01f)
        assertEquals(1f, redCmyk.yellow, 0.01f)
        assertEquals(0f, redCmyk.keyBlack, 0.01f)

        val blackCmyk = ColorPickerHelper.colorToCmyk(Color.Black)
        assertEquals(1f, blackCmyk.keyBlack, 0.01f)

        val whiteCmyk = ColorPickerHelper.colorToCmyk(Color.White)
        assertEquals(0f, whiteCmyk.keyBlack, 0.01f)
        assertEquals(0f, whiteCmyk.cyan, 0.01f)

        val cmyk = CmykColor(cyan = 0f, magenta = 1f, yellow = 1f, keyBlack = 0f, alpha = 1f)
        assertEquals(Color.Red.toArgb(), cmyk.toColor().toArgb())
    }

    @Test
    fun testCalculateClassicLocationAndColor() {
        val width = 200f
        val height = 150f

        val hsv = HsvColor(hue = 0f, saturation = 0.5f, value = 0.8f, alpha = 1f)
        val loc = ColorPickerHelper.calculateClassicLocation(hsv, width, height)
        assertEquals(100f, loc.x, 0.1f)
        assertEquals(30f, loc.y, 0.1f) // (1 - 0.8) * 150 = 30

        val reconstructed = ColorPickerHelper.calculateClassicColor(
            x = loc.x,
            y = loc.y,
            width = width,
            height = height,
            hue = hsv.hue,
            alpha = hsv.alpha
        )
        val recHsv = ColorPickerHelper.colorToHsv(reconstructed)
        assertEquals(hsv.hue, recHsv.hue, 1f)
        assertEquals(hsv.saturation, recHsv.saturation, 0.02f)
        assertEquals(hsv.value, recHsv.value, 0.02f)

        // Edge case: zero width/height
        assertEquals(Offset.Zero, ColorPickerHelper.calculateClassicLocation(hsv, 0f, 100f))
        assertEquals(Color.White, ColorPickerHelper.calculateClassicColor(10f, 10f, 0f, 0f, 0f, 1f))
    }

    @Test
    fun testCalculateCircleLocationAndColor() {
        val radius = 100f

        // Center location (sat = 0)
        val centerHsv = HsvColor(hue = 0f, saturation = 0f, value = 1f, alpha = 1f)
        val centerLoc = ColorPickerHelper.calculateCircleLocation(centerHsv, radius)
        assertEquals(radius, centerLoc.x, 0.1f)
        assertEquals(radius, centerLoc.y, 0.1f)

        // Edge location along 0 rad (right edge, hue = 0, sat = 1)
        val edgeHsv = HsvColor(hue = 0f, saturation = 1f, value = 1f, alpha = 1f)
        val edgeLoc = ColorPickerHelper.calculateCircleLocation(edgeHsv, radius)
        assertEquals(200f, edgeLoc.x, 0.1f)
        assertEquals(100f, edgeLoc.y, 0.1f)

        // Calculate color at edge
        val (calcColor, boundedLoc) = ColorPickerHelper.calculateCircleColor(
            x = 200f,
            y = 100f,
            radius = radius,
            brightness = 0f,
            alpha = 1f,
            lightCenter = true
        )
        val calcHsv = ColorPickerHelper.colorToHsv(calcColor)
        assertEquals(0f, calcHsv.hue, 2f)
        assertEquals(1f, calcHsv.saturation, 0.05f)
        assertEquals(200f, boundedLoc.x, 0.1f)

        // Zero radius
        assertEquals(Offset.Zero, ColorPickerHelper.calculateCircleLocation(edgeHsv, 0f))
        val (zeroColor, zeroLoc) = ColorPickerHelper.calculateCircleColor(10f, 10f, 0f, 0f, 1f, true)
        assertEquals(Color.White, zeroColor)
        assertEquals(Offset.Zero, zeroLoc)
    }

    @Test
    fun testCalculateRingLocationAndColor() {
        val radius = 100f
        val ringWidth = 20f

        val loc = ColorPickerHelper.calculateRingLocation(hue = 0f, radius = radius, ringWidthPx = ringWidth)
        assertEquals(190f, loc.x, 0.1f) // 100 + (100 - 10) * 1 = 190
        assertEquals(100f, loc.y, 0.1f)

        val (ringColor, boundedLoc) = ColorPickerHelper.calculateRingColor(
            x = 190f,
            y = 100f,
            radius = radius,
            ringWidthPx = ringWidth,
            lightness = 0f,
            darkness = 0f,
            alpha = 1f
        )
        assertEquals(Color.Red.toArgb(), ringColor.toArgb())
        assertEquals(190f, boundedLoc.x, 0.1f)

        // Zero radius
        assertEquals(Offset.Zero, ColorPickerHelper.calculateRingLocation(0f, 0f, ringWidth))
        val (zeroColor, zeroLoc) = ColorPickerHelper.calculateRingColor(10f, 10f, 0f, 10f, 0f, 0f, 1f)
        assertEquals(Color.Red, zeroColor)
        assertEquals(Offset.Zero, zeroLoc)
    }

    @Test
    fun testSimpleRingColorAndNearestLocation() {
        val tracks = 5
        val sectors = 24

        // Sector 0 track 0 should be pure Red (hue = 0)
        val color00 = ColorPickerHelper.getSimpleRingColor(0, 0, tracks, sectors)
        assertEquals(255, color00.argb()[1])

        // Finding nearest cell for pure Red should return sector 0
        val nearestRed = ColorPickerHelper.findNearestSimpleRingLocation(Color.Red, tracks, sectors)
        assertEquals(0, nearestRed.x)
        assertEquals(0, nearestRed.y)

        // Finding nearest cell for Green (hue = 120) -> sector = 24 * (120/360) = 8
        val nearestGreen = ColorPickerHelper.findNearestSimpleRingLocation(Color.Green, tracks, sectors)
        assertEquals(8, nearestGreen.x)

        // Finding nearest cell for Blue (hue = 240) -> sector = 24 * (240/360) = 16
        val nearestBlue = ColorPickerHelper.findNearestSimpleRingLocation(Color.Blue, tracks, sectors)
        assertEquals(16, nearestBlue.x)

        // Boundary cases: invalid counts
        assertEquals(Color.Red, ColorPickerHelper.getSimpleRingColor(0, 0, 0, 0))
        assertEquals(IntOffset(0, 0), ColorPickerHelper.findNearestSimpleRingLocation(Color.Red, 0, 0))
    }

    @Test
    fun testParseHexFormats() {
        assertEquals(Color(255, 0, 0, 255), ColorPickerHelper.parseHex("F00"))
        assertEquals(Color(255, 0, 0, 255), ColorPickerHelper.parseHex("#F00"))
        assertEquals(Color(255, 128, 0, 255), ColorPickerHelper.parseHex("FF8000"))
        assertEquals(Color(255, 128, 0, 255), ColorPickerHelper.parseHex("#FF8000"))
        assertEquals(Color(255, 0, 0, 128), ColorPickerHelper.parseHex("80FF0000"))
        assertEquals(Color(255, 0, 0, 128), ColorPickerHelper.parseHex("#80FF0000"))

        assertNull(ColorPickerHelper.parseHex("invalid"))
        assertNull(ColorPickerHelper.parseHex("12"))
        assertNull(ColorPickerHelper.parseHex("12345"))
    }

    @Test
    fun testParseRgbHslCmyk() {
        val rgb = ColorPickerHelper.parseRgb(255, 128, 64, 100)
        assertEquals(255, rgb.argb()[1])
        assertEquals(128, rgb.argb()[2])
        assertEquals(64, rgb.argb()[3])
        assertEquals(255, rgb.argb()[0])

        // Clamping RGB
        val clampedRgb = ColorPickerHelper.parseRgb(300, -5, 100, 150)
        assertEquals(255, clampedRgb.argb()[1])
        assertEquals(0, clampedRgb.argb()[2])
        assertEquals(255, clampedRgb.argb()[0])

        val hsl = ColorPickerHelper.parseHsl(0f, 100f, 50f, 100f)
        assertEquals(Color.Red.toArgb(), hsl.toArgb())

        val cmyk = ColorPickerHelper.parseCmyk(0f, 100f, 100f, 0f, 100f)
        assertEquals(Color.Red.toArgb(), cmyk.toArgb())
    }

    @Test
    fun testParseHct() {
        val hctRed = ColorPickerHelper.parseHct(27f, 100f, 50f, 100f)
        assertTrue(hctRed.red > 0.5f)

        val hctAlpha = ColorPickerHelper.parseHct(120f, 50f, 70f, 50f)
        assertEquals(0.5f, hctAlpha.alpha, 0.05f)
    }

    @Test
    fun testParseAnyColor() {
        // Hex
        val hex1 = ColorPickerHelper.parseAnyColor("#FF0000")
        assertNotNull(hex1)
        assertEquals(255, hex1.argb()[1])

        val hex2 = ColorPickerHelper.parseAnyColor("00FF00")
        assertNotNull(hex2)
        assertEquals(255, hex2.argb()[2])

        // CSS rgb
        val rgb = ColorPickerHelper.parseAnyColor("rgb(255, 128, 0)")
        assertNotNull(rgb)
        assertEquals(255, rgb.argb()[1])
        assertEquals(128, rgb.argb()[2])

        // CSS rgba
        val rgba = ColorPickerHelper.parseAnyColor("rgba(0, 0, 255, 0.5)")
        assertNotNull(rgba)
        assertEquals(255, rgba.argb()[3])
        assertEquals(128.0, rgba.argb()[0].toDouble(), 5.0)

        // CSS hsl
        val hsl = ColorPickerHelper.parseAnyColor("hsl(0, 100%, 50%)")
        assertNotNull(hsl)
        assertEquals(Color.Red.toArgb(), hsl.toArgb())

        // CSS hct
        val hct = ColorPickerHelper.parseAnyColor("hct(27, 80, 50)")
        assertNotNull(hct)

        // CSS cmyk
        val cmyk = ColorPickerHelper.parseAnyColor("cmyk(0%, 100%, 100%, 0%)")
        assertNotNull(cmyk)

        // Raw numbers
        val raw = ColorPickerHelper.parseAnyColor("100 150 200")
        assertNotNull(raw)
        assertEquals(100, raw.argb()[1])
        assertEquals(150, raw.argb()[2])
        assertEquals(200, raw.argb()[3])

        // Invalid
        assertNull(ColorPickerHelper.parseAnyColor(""))
        assertNull(ColorPickerHelper.parseAnyColor("invalid-color-string"))
    }

    @Test
    fun testClassicColorBoundaries() {
        val width = 200f
        val height = 200f

        // Top-left boundary: saturation = 0, value = 1 -> pure White
        val white = ColorPickerHelper.calculateClassicColor(0f, 0f, width, height, hue = 180f, alpha = 1f)
        assertEquals(255, white.argb()[1])
        assertEquals(255, white.argb()[2])
        assertEquals(255, white.argb()[3])

        // Middle-left boundary: saturation = 0, value = 0.5 -> pure Gray
        val gray = ColorPickerHelper.calculateClassicColor(0f, 100f, width, height, hue = 180f, alpha = 1f)
        assertEquals(gray.argb()[1], gray.argb()[2])
        assertEquals(gray.argb()[2], gray.argb()[3])
        assertTrue(gray.argb()[1] in 120..135)

        // Bottom boundary: value = 0 -> pure Black across all x positions
        for (x in listOf(0f, 50f, 100f, 150f, 200f)) {
            val black = ColorPickerHelper.calculateClassicColor(x, height, width, height, hue = 270f, alpha = 1f)
            assertEquals(0, black.argb()[1])
            assertEquals(0, black.argb()[2])
            assertEquals(0, black.argb()[3])
        }

        // Top-right boundary: saturation = 1, value = 1 -> pure Hue color
        val pureRed = ColorPickerHelper.calculateClassicColor(width, 0f, width, height, hue = 0f, alpha = 1f)
        assertEquals(255, pureRed.argb()[1])
        assertEquals(0, pureRed.argb()[2])
        assertEquals(0, pureRed.argb()[3])
    }

    @Test
    fun testClassicLocationBoundaries() {
        val width = 200f
        val height = 200f

        val locWhite = ColorPickerHelper.calculateClassicLocation(HsvColor(0f, 0f, 1f), width, height)
        assertEquals(0f, locWhite.x, 0.01f)
        assertEquals(0f, locWhite.y, 0.01f)

        val locBlack = ColorPickerHelper.calculateClassicLocation(HsvColor(0f, 0f, 0f), width, height)
        assertEquals(0f, locBlack.x, 0.01f)
        assertEquals(200f, locBlack.y, 0.01f)

        val locPureColor = ColorPickerHelper.calculateClassicLocation(HsvColor(120f, 1f, 1f), width, height)
        assertEquals(200f, locPureColor.x, 0.01f)
        assertEquals(0f, locPureColor.y, 0.01f)
    }

    @Test
    fun testHslAndHctChannelIndependence() {
        // HSL channel parsing with varied saturation does not corrupt hue
        for (sat in listOf(0f, 25f, 50f, 75f, 100f)) {
            val color = ColorPickerHelper.parseHsl(180f, sat, 50f, 100f)
            assertTrue(color.alpha > 0.99f)
            // Cyan hue should have green == blue and red lower than green/blue
            assertTrue(color.green >= color.red)
            assertTrue(color.blue >= color.red)
        }

        // HCT parse produces consistent colors
        for (chroma in listOf(0f, 30f, 60f, 90f, 120f)) {
            val hctColor = ColorPickerHelper.parseHct(240f, chroma, 50f, 100f)
            assertTrue(hctColor.blue >= hctColor.red)
        }
    }
}
