package com.wip.cmp_desktop_test.extensions.colorpicker

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.wip.cmp_desktop_test.data.colorpicker.ColorRange
import com.wip.cmp_desktop_test.helper.colorpicker.ColorPickerHelper
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Returns an integer array for all color channels value.
 */
fun Color.argb(): Array<Int> {
    val argb = toArgb()
    val alpha = argb shr 24 and 0xff
    val red = argb shr 16 and 0xff
    val green = argb shr 8 and 0xff
    val blue = argb and 0xff
    return arrayOf(alpha, red, green, blue)
}

/**
 * Returns the red value as an integer.
 */
fun Color.red(): Int {
    return toArgb() shr 16 and 0xff
}

/**
 * Returns the green value as an integer.
 */
fun Color.green(): Int {
    return toArgb() shr 8 and 0xff
}

/**
 * Returns the blue value as an integer.
 */
fun Color.blue(): Int {
    return toArgb() and 0xff
}

/**
 * Returns the alpha value as an integer.
 */
fun Color.alpha(): Int {
    return toArgb() shr 24 and 0xff
}

/**
 * Returns ARGB color as a hex string.
 * @param hexPrefix Add # char before the hex number.
 * @param includeAlpha Include the alpha value within the hex string.
 */
fun Color.toHex(hexPrefix: Boolean = false, includeAlpha: Boolean = true): String {
    val (alpha, red, green, blue) = argb()
    return buildString {
        if (hexPrefix) {
            append("#")
        }
        if (includeAlpha) {
            append(alpha.toHex())
        }
        append(red.toHex())
        append(green.toHex())
        append(blue.toHex())
    }
}

private fun Int.toHex(): String {
    return Integer.toHexString(this).let {
        if (it.length == 1) {
            "0$it"
        } else {
            it
        }
    }
}

internal fun Double.lighten(lightness: Float): Double {
    if (this.isNaN() || lightness.isNaN()) return this
    return this + (255 - this) * lightness
}

internal fun Float.lighten(lightness: Float): Float {
    if (this.isNaN() || lightness.isNaN()) return this
    return this + (255 - this) * lightness
}

internal fun Int.lighten(lightness: Float): Int {
    if (lightness.isNaN()) return this
    val result = this + (255 - this) * lightness
    return if (result.isNaN()) this else result.roundToInt()
}

internal fun Double.darken(darkness: Float): Double {
    if (this.isNaN() || darkness.isNaN()) return this
    return this - this * darkness
}

internal fun Float.darken(darkness: Float): Float {
    if (this.isNaN() || darkness.isNaN()) return this
    return this - this * darkness
}

internal fun Int.darken(darkness: Float): Int {
    if (darkness.isNaN()) return this
    val result = this - this * darkness
    return if (result.isNaN()) this else result.roundToInt()
}
internal fun Color.Companion.fromHueProgress(progress: Float): Color {
    val (rangeProgress, range) = ColorPickerHelper.calculateRangeProgress(progress.toDouble())
    val red: Int
    val green: Int
    val blue: Int
    when (range) {
        ColorRange.RedToYellow -> {
            red = 255; green = (255f * rangeProgress).roundToInt(); blue = 0
        }
        ColorRange.YellowToGreen -> {
            red = (255 * (1 - rangeProgress)).roundToInt(); green = 255; blue = 0
        }
        ColorRange.GreenToCyan -> {
            red = 0; green = 255; blue = (255 * rangeProgress).roundToInt()
        }
        ColorRange.CyanToBlue -> {
            red = 0; green = (255 * (1 - rangeProgress)).roundToInt(); blue = 255
        }
        ColorRange.BlueToPurple -> {
            red = (255 * rangeProgress).roundToInt(); green = 0; blue = 255
        }
        ColorRange.PurpleToRed -> {
            red = 255; green = 0; blue = (255 * (1 - rangeProgress)).roundToInt()
        }
    }
    return Color(red, green, blue)
}

internal fun Color.toHueProgress(): Float {
    val red = this.red() / 255f
    val green = this.green() / 255f
    val blue = this.blue() / 255f

    val min = min(min(red, green), blue)
    val max = max(max(red, green), blue)

    if (min == max) {
        return 0f
    }

    var hue = 0f
    if (max == red) {
        hue = (green - blue) / (max - min)
    } else if (max == green) {
        hue = 2f + (blue - red) / (max - min)
    } else {
        hue = 4f + (red - green) / (max - min)
    }

    hue *= 60
    if (hue < 0) hue += 360

    return hue
}
