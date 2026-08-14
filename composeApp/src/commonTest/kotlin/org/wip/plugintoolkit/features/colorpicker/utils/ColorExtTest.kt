package org.wip.plugintoolkit.features.colorpicker.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ColorExtTest {
    @Test
    fun `hex parser accepts rgb and argb values`() {
        assertEquals(Color(0xFF336699.toInt()), parseHexColor("#336699"))
        assertEquals(Color(0xFF336699.toInt()), parseHexColor("336699"))
        assertEquals(Color(0x80336699.toInt()), parseHexColor("80336699"))
        assertEquals(true, colorStringHasAlpha("#80336699"))
        assertEquals(true, colorStringHasAlpha("80336699"))
    }

    @Test
    fun `ARGB values round trip without losing alpha`() {
        val original = "80336699"
        val parsed = parseHexColor(original)!!

        assertEquals(original.lowercase(), parsed.toHex(includeAlpha = colorStringHasAlpha(original)))
    }

    @Test
    fun `hex parser rejects malformed values`() {
        assertNull(parseHexColor("#12345"))
        assertNull(parseHexColor("#GG3366"))
    }

    @Test
    fun `hue progress is normalized`() {
        assertEquals(0f, Color.Red.toHueProgress(), absoluteTolerance = 0.0001f)
        assertEquals(1f / 3f, Color.Green.toHueProgress(), absoluteTolerance = 0.0001f)
        assertEquals(2f / 3f, Color.Blue.toHueProgress(), absoluteTolerance = 0.0001f)
    }

    @Test
    fun `picker coordinates reconstruct the initial color including alpha`() {
        listOf(
            Color(0xFFFF0000.toInt()),
            Color(0xFF336699.toInt()),
            Color(0xFF00FF00.toInt()),
            Color(0xFFFFFFFF.toInt()),
            Color(0x80336699.toInt())
        ).forEach { expected ->
            assertEquals(expected.toArgb(), reconstructPickerColor(expected).toArgb(), "Failed for ${expected.toHex(true)}")
        }
    }

    private fun reconstructPickerColor(color: Color): Color {
        val (saturation, value) = color.saturationAndValue()
        val hueColor = Color.fromHueProgress(color.toHueProgress())
        return Color(
            hueColor.red().lighten(1f - saturation).darken(1f - value),
            hueColor.green().lighten(1f - saturation).darken(1f - value),
            hueColor.blue().lighten(1f - saturation).darken(1f - value),
            color.alpha()
        )
    }
}
