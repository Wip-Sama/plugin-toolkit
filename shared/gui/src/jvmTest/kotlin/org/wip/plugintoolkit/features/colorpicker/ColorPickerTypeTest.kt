package org.wip.plugintoolkit.features.colorpicker

import androidx.compose.ui.unit.dp
import org.wip.plugintoolkit.features.colorpicker.model.ColorPickerType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ColorPickerTypeTest {

    @Test
    fun testClassicTypeDefaults() {
        val classic = ColorPickerType.Classic()
        assertTrue(classic.showAlphaBar)

        val classicNoAlpha = ColorPickerType.Classic(showAlphaBar = false)
        assertTrue(!classicNoAlpha.showAlphaBar)
    }

    @Test
    fun testCircleTypeDefaults() {
        val circle = ColorPickerType.Circle()
        assertTrue(circle.showBrightnessBar)
        assertTrue(circle.showAlphaBar)
        assertTrue(circle.lightCenter)
    }

    @Test
    fun testRingTypeDefaults() {
        val ring = ColorPickerType.Ring()
        assertEquals(10.dp, ring.ringWidth)
        assertEquals(80.dp, ring.previewRadius)
        assertTrue(ring.showLightnessBar)
        assertTrue(ring.showDarknessBar)
        assertTrue(ring.showAlphaBar)
        assertTrue(ring.showColorPreview)
    }

    @Test
    fun testSimpleRingTypeDefaults() {
        val simple = ColorPickerType.SimpleRing()
        assertEquals(20.dp, simple.colorWidth)
        assertEquals(5, simple.tracksCount)
        assertEquals(24, simple.sectorsCount)
    }

    @Test
    fun testColorsGradient() {
        val colors = org.wip.plugintoolkit.features.colorpicker.model.Colors.gradientColors
        assertEquals(7, colors.size)
    }
}
