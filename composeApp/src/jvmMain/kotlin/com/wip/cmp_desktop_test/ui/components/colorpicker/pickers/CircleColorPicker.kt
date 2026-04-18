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
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
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
import com.wip.cmp_desktop_test.helper.colorpicker.MathHelper.getBoundedPointWithInRadius
import com.wip.cmp_desktop_test.helper.colorpicker.MathHelper.getLength
import kotlin.math.atan2
import kotlin.math.roundToInt

/**
 * Circular color picker with optional brightness and alpha bars.
 *
 * @param showAlphaBar      Whether to show the alpha/transparency bar.
 * @param showBrightnessBar Whether to show the brightness bar.
 * @param lightCenter       When true, the center fades to white; when false it fades to black.
 * @param onPickedColor     Callback invoked whenever the selected color changes.
 */
@Composable
internal fun CircleColorPicker(
    modifier: Modifier = Modifier,
    showAlphaBar: Boolean,
    showBrightnessBar: Boolean,
    lightCenter: Boolean,
    onPickedColor: (Color) -> Unit
) {
    var radius by remember { mutableStateOf(0f) }
    var pickerLocation by remember(radius) { mutableStateOf(Offset(radius, radius)) }
    var pickerColor by remember {
        mutableStateOf(if (lightCenter) Color.White else Color.Black)
    }
    var brightness by remember { mutableStateOf(0f) }
    var alpha by remember { mutableStateOf(1f) }

    LaunchedEffect(brightness, pickerColor, alpha) {
        onPickedColor(
            Color(
                pickerColor.red().moveColorTo(!lightCenter, brightness),
                pickerColor.green().moveColorTo(!lightCenter, brightness),
                pickerColor.blue().moveColorTo(!lightCenter, brightness),
                (255 * alpha).roundToInt()
            )
        )
    }

    Column(modifier = Modifier.width(IntrinsicSize.Max)) {
        Canvas(modifier = modifier
            .size(200.dp)
            .onSizeChanged { radius = it.width / 2f }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        if (radius > 0) {
                            handleCirclePickerInput(
                                x = offset.x, y = offset.y, radius = radius,
                                lightCenter = lightCenter,
                                onColorChange = { pickerColor = it },
                                onLocationChange = { pickerLocation = it }
                            )
                        }
                    }
                ) { change, _ ->
                    change.consume()
                    if (radius > 0) {
                        handleCirclePickerInput(
                            x = change.position.x, y = change.position.y, radius = radius,
                            lightCenter = lightCenter,
                            onColorChange = { pickerColor = it },
                            onLocationChange = { pickerLocation = it }
                        )
                    }
                }
            }
        ) {
            drawCircle(Brush.sweepGradient(gradientColors))
            drawCircle(
                ShaderBrush(
                    RadialGradientShader(
                        Offset(size.width / 2f, size.height / 2f),
                        colors = listOf(
                            if (lightCenter) Color.White else Color.Black,
                            Color.Transparent
                        ),
                        radius = size.width / 2f
                    )
                )
            )
            drawColorSelector(pickerColor, pickerLocation)
        }

        if (showBrightnessBar) {
            Spacer(modifier = Modifier.height(16.dp))
            ColorSlideBar(
                colors = listOf(
                    if (lightCenter) Color.Black else Color.White,
                    pickerColor
                )
            ) { brightness = 1 - it }
        }

        if (showAlphaBar) {
            Spacer(modifier = Modifier.height(16.dp))
            ColorSlideBar(colors = listOf(Color.Transparent, pickerColor)) { alpha = it }
        }
    }
}

private fun handleCirclePickerInput(
    x: Float,
    y: Float,
    radius: Float,
    lightCenter: Boolean,
    onColorChange: (Color) -> Unit,
    onLocationChange: (Offset) -> Unit
) {
    val angle = (Math.toDegrees(atan2(y - radius, x - radius).toDouble()) + 360) % 360
    val length = getLength(x, y, radius)
    val radiusProgress = (1 - (length / radius)).coerceIn(0f, 1f)
    val angleProgress = angle / 360f
    val (rangeProgress, range) = ColorPickerHelper.calculateRangeProgress(angleProgress.toDouble())

    val newColor = when (range) {
        ColorRange.RedToYellow -> Color(
            red = 255.moveColorTo(lightCenter, radiusProgress.toFloat()),
            green = (255f * rangeProgress).toFloat().moveColorTo(lightCenter, radiusProgress.toFloat()).roundToInt(),
            blue = 0.moveColorTo(lightCenter, radiusProgress.toFloat()),
        )
        ColorRange.YellowToGreen -> Color(
            red = (255 * (1 - rangeProgress)).toFloat().moveColorTo(lightCenter, radiusProgress.toFloat()).roundToInt(),
            green = 255.moveColorTo(lightCenter, radiusProgress.toFloat()),
            blue = 0.moveColorTo(lightCenter, radiusProgress.toFloat()),
        )
        ColorRange.GreenToCyan -> Color(
            red = 0.moveColorTo(lightCenter, radiusProgress.toFloat()),
            green = 255.moveColorTo(lightCenter, radiusProgress.toFloat()),
            blue = (255 * rangeProgress).toFloat().moveColorTo(lightCenter, radiusProgress.toFloat()).roundToInt(),
        )
        ColorRange.CyanToBlue -> Color(
            red = 0.moveColorTo(lightCenter, radiusProgress.toFloat()),
            green = (255 * (1 - rangeProgress)).toFloat().moveColorTo(lightCenter, radiusProgress.toFloat()).roundToInt(),
            blue = 255.moveColorTo(lightCenter, radiusProgress.toFloat()),
        )
        ColorRange.BlueToPurple -> Color(
            red = (255 * rangeProgress).toFloat().moveColorTo(lightCenter, radiusProgress.toFloat()).roundToInt(),
            green = 0.moveColorTo(lightCenter, radiusProgress.toFloat()),
            blue = 255.moveColorTo(lightCenter, radiusProgress.toFloat()),
        )
        ColorRange.PurpleToRed -> Color(
            red = 255.moveColorTo(lightCenter, radiusProgress.toFloat()),
            green = 0.moveColorTo(lightCenter, radiusProgress.toFloat()),
            blue = (255 * (1 - rangeProgress)).toFloat().moveColorTo(lightCenter, radiusProgress.toFloat()).roundToInt(),
        )
    }
    onColorChange(newColor)
    onLocationChange(
        getBoundedPointWithInRadius(x, y, length, radius, BoundedPointStrategy.Inside)
    )
}

private fun Int.moveColorTo(toWhite: Boolean, progress: Float): Int =
    if (toWhite) lighten(progress) else darken(progress)

private fun Float.moveColorTo(toWhite: Boolean, progress: Float): Float =
    if (toWhite) lighten(progress) else darken(progress)

private fun Double.moveColorTo(toWhite: Boolean, progress: Float): Double =
    if (toWhite) lighten(progress) else darken(progress)
