package com.wip.cmp_desktop_test.ui.components.colorpicker.pickers

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wip.cmp_desktop_test.data.colorpicker.ColorRange
import com.wip.cmp_desktop_test.data.colorpicker.Colors.gradientColors
import com.wip.cmp_desktop_test.extensions.colorpicker.blue
import com.wip.cmp_desktop_test.extensions.colorpicker.darken
import com.wip.cmp_desktop_test.extensions.colorpicker.drawColorSelector
import com.wip.cmp_desktop_test.extensions.colorpicker.green
import com.wip.cmp_desktop_test.extensions.colorpicker.lighten
import com.wip.cmp_desktop_test.extensions.colorpicker.red
import com.wip.cmp_desktop_test.helper.colorpicker.BoundedPointStrategy
import com.wip.cmp_desktop_test.helper.colorpicker.ColorPickerHelper
import com.wip.cmp_desktop_test.helper.colorpicker.MathHelper
import com.wip.cmp_desktop_test.helper.colorpicker.MathHelper.getBoundedPointWithInRadius
import kotlin.math.atan2
import kotlin.math.roundToInt

/**
 * Ring / wheel color picker with optional lightness, darkness and alpha bars.
 *
 * @param ringWidth        Width of the color ring arc.
 * @param previewRadius    Radius of the center color-preview circle.
 * @param showLightColorBar Whether to show the lightness bar.
 * @param showDarkColorBar  Whether to show the darkness bar.
 * @param showAlphaBar      Whether to show the alpha/transparency bar.
 * @param showColorPreview  Whether to show the selected color preview in the center.
 * @param onPickedColor     Callback invoked whenever the selected color changes.
 */
@Composable
internal fun RingColorPicker(
    modifier: Modifier = Modifier,
    ringWidth: Dp,
    previewRadius: Dp,
    showLightColorBar: Boolean,
    showDarkColorBar: Boolean,
    showAlphaBar: Boolean,
    showColorPreview: Boolean,
    onPickedColor: (Color) -> Unit,
) {
    val density = LocalDensity.current
    val ringWidthPx = remember { with(density) { ringWidth.toPx() } }
    val previewRadiusPx = remember { with(density) { previewRadius.toPx() } }

    var radius by remember { mutableStateOf(0f) }
    var pickerLocation by remember(radius) {
        mutableStateOf(
            if (radius > 0) {
                getBoundedPointWithInRadius(
                    radius * 2, radius,
                    MathHelper.getLength(radius * 2, radius, radius),
                    radius - ringWidthPx / 2,
                    BoundedPointStrategy.Edge
                )
            } else Offset.Zero
        )
    }
    var selectedColor by remember { mutableStateOf(Color.Red) }
    var color by remember { mutableStateOf(Color.Red) }
    var lightColor by remember { mutableStateOf(Color.Red) }
    var darkColor by remember { mutableStateOf(Color.Red) }
    var lightness by remember { mutableStateOf(0f) }
    var darkness by remember { mutableStateOf(0f) }
    var alpha by remember { mutableStateOf(1f) }

    LaunchedEffect(selectedColor, lightness, darkness, alpha) {
        var red = selectedColor.red().lighten(lightness)
        var green = selectedColor.green().lighten(lightness)
        var blue = selectedColor.blue().lighten(lightness)
        lightColor = Color(red, green, blue, 255)
        red = red.darken(darkness)
        green = green.darken(darkness)
        blue = blue.darken(darkness)
        darkColor = Color(red, green, blue, 255)
        color = Color(red, green, blue, (255 * alpha).roundToInt())
        onPickedColor(color)
    }

    Column(modifier = Modifier.width(IntrinsicSize.Max)) {
        Canvas(modifier = modifier
            .size(200.dp)
            .onSizeChanged { radius = it.width.toFloat() / 2 }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        if (radius > 0) {
                            handleRingPickerInput(
                                x = offset.x, y = offset.y,
                                radius = radius, ringWidthPx = ringWidthPx,
                                onColorChange = { selectedColor = it },
                                onLocationChange = { pickerLocation = it }
                            )
                        }
                    }
                ) { change, _ ->
                    change.consume()
                    if (radius > 0) {
                        handleRingPickerInput(
                            x = change.position.x, y = change.position.y,
                            radius = radius, ringWidthPx = ringWidthPx,
                            onColorChange = { selectedColor = it },
                            onLocationChange = { pickerLocation = it }
                        )
                    }
                }
            }
        ) {
            drawCircle(
                Brush.sweepGradient(gradientColors),
                radius = (radius - ringWidthPx / 2f).coerceAtLeast(0f),
                style = Stroke(ringWidthPx)
            )
            if (showColorPreview) {
                drawCircle(color, radius = previewRadiusPx)
            }
            drawColorSelector(selectedColor, pickerLocation)
        }

        if (showLightColorBar) {
            Spacer(modifier = Modifier.height(16.dp))
            ColorSlideBar(colors = listOf(Color.White, selectedColor)) { lightness = 1 - it }
        }
        if (showDarkColorBar) {
            Spacer(modifier = Modifier.height(16.dp))
            ColorSlideBar(colors = listOf(Color.Black, lightColor)) { darkness = 1 - it }
        }
        if (showAlphaBar) {
            Spacer(modifier = Modifier.height(16.dp))
            ColorSlideBar(colors = listOf(Color.Transparent, darkColor)) { alpha = it }
        }
    }
}

private fun handleRingPickerInput(
    x: Float,
    y: Float,
    radius: Float,
    ringWidthPx: Float,
    onColorChange: (Color) -> Unit,
    onLocationChange: (Offset) -> Unit
) {
    val angle = (Math.toDegrees(atan2(y - radius, x - radius).toDouble()) + 360) % 360
    val length = MathHelper.getLength(x, y, radius)
    val progress = angle / 360f
    val (rangeProgress, range) = ColorPickerHelper.calculateRangeProgress(progress)

    val red: Int
    val green: Int
    val blue: Int
    when (range) {
        ColorRange.RedToYellow -> {
            red = 255; green = (255f * rangeProgress).toFloat().roundToInt(); blue = 0
        }
        ColorRange.YellowToGreen -> {
            red = (255 * (1 - rangeProgress)).toFloat().roundToInt(); green = 255; blue = 0
        }
        ColorRange.GreenToCyan -> {
            red = 0; green = 255; blue = (255 * rangeProgress).toFloat().roundToInt()
        }
        ColorRange.CyanToBlue -> {
            red = 0; green = (255 * (1 - rangeProgress)).toFloat().roundToInt(); blue = 255
        }
        ColorRange.BlueToPurple -> {
            red = (255 * rangeProgress).toFloat().roundToInt(); green = 0; blue = 255
        }
        ColorRange.PurpleToRed -> {
            red = 255; green = 0; blue = (255 * (1 - rangeProgress)).toFloat().roundToInt()
        }
    }
    onLocationChange(
        getBoundedPointWithInRadius(x, y, length, radius - ringWidthPx / 2, BoundedPointStrategy.Edge)
    )
    onColorChange(Color(red, green, blue))
}
