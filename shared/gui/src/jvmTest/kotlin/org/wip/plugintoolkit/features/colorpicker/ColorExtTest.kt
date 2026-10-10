package org.wip.plugintoolkit.features.colorpicker.utils

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ColorExtTest {

    @Test
    fun testChannels() {
        val color = Color(0x80112233)
        assertEquals(0x80, color.alpha())
        assertEquals(0x11, color.red())
        assertEquals(0x22, color.green())
        assertEquals(0x33, color.blue())

        val (a, r, g, b) = color.argb()
        assertEquals(0x80, a)
        assertEquals(0x11, r)
        assertEquals(0x22, g)
        assertEquals(0x33, b)
    }

    @Test
    fun testToHex() {
        val color = Color(0xFF112233)
        assertEquals("ff112233", color.toHex(hexPrefix = false, includeAlpha = true))
        assertEquals("#ff112233", color.toHex(hexPrefix = true, includeAlpha = true))
        assertEquals("112233", color.toHex(hexPrefix = false, includeAlpha = false))
        assertEquals("#112233", color.toHex(hexPrefix = true, includeAlpha = false))

        // Single digit channel test
        val lowChannelColor = Color(0x05010203)
        assertEquals("#05010203", lowChannelColor.toHex(hexPrefix = true, includeAlpha = true))
    }

    @Test
    fun testToRGB() {
        val color = Color(255, 128, 64, 255)
        assertEquals("rgb(255, 128, 64)", color.toRGB(rgbPrefix = true, includeAlpha = false))
        assertEquals("(255, 128, 64)", color.toRGB(rgbPrefix = false, includeAlpha = false))
        assertEquals("rgba(255, 128, 64, 1.0)", color.toRGB(rgbPrefix = true, includeAlpha = true))
        assertEquals("(255, 128, 64, 1.0)", color.toRGB(rgbPrefix = false, includeAlpha = true))
    }

    @Test
    fun testToHSL() {
        val red = Color.Red
        assertEquals("hsl(0, 100%, 50%)", red.toHSL(hslPrefix = true, includeAlpha = false))
        assertEquals("(0, 100%, 50%)", red.toHSL(hslPrefix = false, includeAlpha = false))
        assertEquals("hsla(0, 100%, 50%, 1.0)", red.toHSL(hslPrefix = true, includeAlpha = true))

        val white = Color.White
        assertEquals("hsl(0, 0%, 100%)", white.toHSL(hslPrefix = true, includeAlpha = false))

        val green = Color.Green
        assertEquals("hsl(120, 100%, 50%)", green.toHSL(hslPrefix = true, includeAlpha = false))

        val blue = Color.Blue
        assertEquals("hsl(240, 100%, 50%)", blue.toHSL(hslPrefix = true, includeAlpha = false))
    }

    @Test
    fun testToCMYK() {
        val red = Color.Red
        assertEquals("cmyk(0%, 100%, 100%, 0%)", red.toCMYK(cmykPrefix = true, includeAlpha = false))
        assertEquals("(0%, 100%, 100%, 0%)", red.toCMYK(cmykPrefix = false, includeAlpha = false))
        assertEquals("cmyk(0%, 100%, 100%, 0%, 1.0)", red.toCMYK(cmykPrefix = true, includeAlpha = true))

        val black = Color.Black
        assertEquals("cmyk(0%, 0%, 0%, 100%)", black.toCMYK(cmykPrefix = true, includeAlpha = false))
    }

    @Test
    fun testLightenAndDarken() {
        // Int
        assertEquals(255, 0.lighten(1f))
        assertEquals(100, 100.lighten(0f))
        assertEquals(0, 100.darken(1f))
        assertEquals(100, 100.darken(0f))
        assertEquals(100, 100.lighten(Float.NaN))
        assertEquals(100, 100.darken(Float.NaN))

        // Float
        assertEquals(255f, 0f.lighten(1f), 0.01f)
        assertEquals(100f, 100f.lighten(0f), 0.01f)
        assertEquals(0f, 100f.darken(1f), 0.01f)
        assertEquals(100f, 100f.darken(0f), 0.01f)
        assertTrue(Float.NaN.lighten(0.5f).isNaN())
        assertTrue(Float.NaN.darken(0.5f).isNaN())

        // Double
        assertEquals(255.0, 0.0.lighten(1f), 0.01)
        assertEquals(100.0, 100.0.lighten(0f), 0.01)
        assertEquals(0.0, 100.0.darken(1f), 0.01)
        assertEquals(100.0, 100.0.darken(0f), 0.01)
        assertTrue(Double.NaN.lighten(0.5f).isNaN())
        assertTrue(Double.NaN.darken(0.5f).isNaN())
    }

    @Test
    fun testFromHueProgressAcrossSpectrum() {
        val red = Color.fromHueProgress(0f)
        assertEquals(255, red.red())
        assertEquals(0, red.green())
        assertEquals(0, red.blue())

        val yellow = Color.fromHueProgress(1f / 6f)
        assertEquals(255, yellow.red())
        assertEquals(255, yellow.green())
        assertEquals(0, yellow.blue())

        val green = Color.fromHueProgress(2f / 6f)
        assertEquals(0, green.red())
        assertEquals(255, green.green())
        assertEquals(0, green.blue())

        val cyan = Color.fromHueProgress(3f / 6f)
        assertEquals(0, cyan.red())
        assertEquals(255, cyan.green())
        assertEquals(255, cyan.blue())

        val blue = Color.fromHueProgress(4f / 6f)
        assertEquals(0, blue.red())
        assertEquals(0, blue.green())
        assertEquals(255, blue.blue())

        val magenta = Color.fromHueProgress(5f / 6f)
        assertEquals(255, magenta.red())
        assertEquals(0, magenta.green())
        assertEquals(255, magenta.blue())
    }

    @Test
    fun testToHueDegreesAndProgress() {
        assertEquals(0f, Color.Red.toHueDegrees(), 0.1f)
        assertEquals(0f, Color.Red.toHueProgress(), 0.01f)

        assertEquals(120f, Color.Green.toHueDegrees(), 0.1f)
        assertEquals(120f / 360f, Color.Green.toHueProgress(), 0.01f)

        assertEquals(240f, Color.Blue.toHueDegrees(), 0.1f)
        assertEquals(240f / 360f, Color.Blue.toHueProgress(), 0.01f)

        assertEquals(0f, Color.White.toHueDegrees(), 0.1f)
    }

    @Test
    fun testDrawExt() {
        val bitmap = ImageBitmap(100, 100)
        val canvas = Canvas(bitmap)
        val drawScope = CanvasDrawScope()
        drawScope.draw(
            density = Density(1f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = canvas,
            size = Size(100f, 100f)
        ) {
            drawColorSelector(Color.Red, Offset(50f, 50f))
            drawTransparentBackground(verticalBoxesSize = 5)
        }
        val mod = Modifier.transparentBackground()
        assertTrue(mod != null)
    }
}
