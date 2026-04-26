package com.wip.kpm_cpm_wotoolkit.features.colorpicker.ui.pickers

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.wip.kpm_cpm_wotoolkit.features.colorpicker.model.Colors.gradientColors
import com.wip.kpm_cpm_wotoolkit.features.colorpicker.utils.*
import com.wip.kpm_cpm_wotoolkit.features.colorpicker.logic.*
import com.wip.kpm_cpm_wotoolkit.features.colorpicker.ui.ColorSlideBar
import kotlin.math.atan2
import kotlin.math.roundToInt

/**
 * Circular color picker with optional brightness and alpha bars.
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
                value = brightness,
                onValueChange = { brightness = it },
                colors = listOf(
                    if (lightCenter) Color.Black else Color.White,
                    pickerColor
                ),
            )
        }

        if (showAlphaBar) {
            Spacer(modifier = Modifier.height(16.dp))
            ColorSlideBar(
                value = alpha,
                onValueChange = { alpha = it },
                colors = listOf(Color.Transparent, pickerColor)
            )
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
    val angleRad = atan2(y - radius, x - radius)
    var angleDeg = (angleRad * 180.0 / kotlin.math.PI + 360) % 360
    
    val length = MathHelper.getLength(x, y, radius)
    val radiusProgress = (1 - (length / radius)).coerceIn(0f, 1f)
    val angleProgress = angleDeg / 360f
    val pureColor = Color.fromHueProgress(angleProgress.toFloat())
    val newColor = Color(
        red = pureColor.red().moveColorTo(lightCenter, radiusProgress.toFloat()),
        green = pureColor.green().moveColorTo(lightCenter, radiusProgress.toFloat()),
        blue = pureColor.blue().moveColorTo(lightCenter, radiusProgress.toFloat()),
    )
    onColorChange(newColor)
    onLocationChange(
        MathHelper.getBoundedPointWithInRadius(x, y, length, radius, BoundedPointStrategy.Inside)
    )
}

private fun Int.moveColorTo(toWhite: Boolean, progress: Float): Int =
    if (toWhite) lighten(progress) else darken(progress)

private fun Float.moveColorTo(toWhite: Boolean, progress: Float): Float =
    if (toWhite) lighten(progress) else darken(progress)

private fun Double.moveColorTo(toWhite: Boolean, progress: Float): Double =
    if (toWhite) lighten(progress) else darken(progress)
