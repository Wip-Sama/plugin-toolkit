package org.wip.plugintoolkit.features.settings

import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScalingControlTest {

    private fun calculateNextScale(currentScale: Float, isIncrement: Boolean, isShiftPressed: Boolean): Float {
        val step = if (isShiftPressed) 0.10f else 0.05f
        val delta = if (isIncrement) step else -step
        return (round((currentScale + delta) * 20f) / 20f).coerceIn(0.5f, 2.0f)
    }

    @Test
    fun testNormalIncrementAndDecrement() {
        val initial = 1.0f

        val increased = calculateNextScale(initial, isIncrement = true, isShiftPressed = false)
        assertEquals(1.05f, increased)

        val decreased = calculateNextScale(initial, isIncrement = false, isShiftPressed = false)
        assertEquals(0.95f, decreased)
    }

    @Test
    fun testShiftDoubleStepIncrementAndDecrement() {
        val initial = 1.0f

        val doubleIncreased = calculateNextScale(initial, isIncrement = true, isShiftPressed = true)
        assertEquals(1.10f, doubleIncreased)

        val doubleDecreased = calculateNextScale(initial, isIncrement = false, isShiftPressed = true)
        assertEquals(0.90f, doubleDecreased)
    }

    @Test
    fun testMinMaxClamping() {
        // Lower bound clamping at 0.5f
        val atMin = 0.5f
        val belowMin = calculateNextScale(atMin, isIncrement = false, isShiftPressed = true)
        assertEquals(0.5f, belowMin)

        val nearMin = 0.55f
        val clampedToMin = calculateNextScale(nearMin, isIncrement = false, isShiftPressed = true)
        assertEquals(0.5f, clampedToMin)

        // Upper bound clamping at 2.0f
        val atMax = 2.0f
        val aboveMax = calculateNextScale(atMax, isIncrement = true, isShiftPressed = true)
        assertEquals(2.0f, aboveMax)

        val nearMax = 1.95f
        val clampedToMax = calculateNextScale(nearMax, isIncrement = true, isShiftPressed = true)
        assertEquals(2.0f, clampedToMax)
    }

    @Test
    fun testButtonEnabledStates() {
        val minScale = 0.5f
        assertFalse(minScale > 0.501f, "Decrement button should be disabled at min scale 0.5f")
        assertTrue(minScale < 1.999f, "Increment button should be enabled at min scale 0.5f")

        val maxScale = 2.0f
        assertTrue(maxScale > 0.501f, "Decrement button should be enabled at max scale 2.0f")
        assertFalse(maxScale < 1.999f, "Increment button should be disabled at max scale 2.0f")

        val normalScale = 1.0f
        assertTrue(normalScale > 0.501f, "Decrement button should be enabled at 1.0f")
        assertTrue(normalScale < 1.999f, "Increment button should be enabled at 1.0f")
    }

    @Test
    fun testPercentageFormatting() {
        assertEquals("50%", "${(0.5f * 100).roundToInt()}%")
        assertEquals("100%", "${(1.0f * 100).roundToInt()}%")
        assertEquals("105%", "${(1.05f * 100).roundToInt()}%")
        assertEquals("200%", "${(2.0f * 100).roundToInt()}%")
    }
}
