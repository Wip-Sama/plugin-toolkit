package com.wip.kpm_cpm_wotoolkit.features.colorpicker.ui.pickers

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
import com.wip.kpm_cpm_wotoolkit.features.colorpicker.logic.BoundedPointStrategy
import com.wip.kpm_cpm_wotoolkit.features.colorpicker.logic.MathHelper
import com.wip.kpm_cpm_wotoolkit.features.colorpicker.model.Colors.gradientColors
import com.wip.kpm_cpm_wotoolkit.features.colorpicker.ui.ColorSlideBar
import com.wip.kpm_cpm_wotoolkit.features.colorpicker.utils.blue
import com.wip.kpm_cpm_wotoolkit.features.colorpicker.utils.darken
import com.wip.kpm_cpm_wotoolkit.features.colorpicker.utils.drawColorSelector
import com.wip.kpm_cpm_wotoolkit.features.colorpicker.utils.fromHueProgress
import com.wip.kpm_cpm_wotoolkit.features.colorpicker.utils.green
import com.wip.kpm_cpm_wotoolkit.features.colorpicker.utils.lighten
import com.wip.kpm_cpm_wotoolkit.features.colorpicker.utils.red
import kotlin.math.atan2
import kotlin.math.roundToInt

/**
 * Ring / wheel color picker with optional lightness, darkness and alpha bars.
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
                MathHelper.getBoundedPointWithInRadius(
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
        Canvas(
            modifier = modifier
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
            ColorSlideBar(
                value = lightness,
                onValueChange = { lightness = it },
                colors = listOf(Color.Transparent, selectedColor)
            )
        }
        if (showDarkColorBar) {
            Spacer(modifier = Modifier.height(16.dp))
            ColorSlideBar(
                value = darkness,
                onValueChange = { darkness = it },
                colors = listOf(Color.Transparent, lightColor)
            )
        }
        if (showAlphaBar) {
            Spacer(modifier = Modifier.height(16.dp))
            ColorSlideBar(
                value = alpha,
                onValueChange = { alpha = it },
                colors = listOf(Color.Transparent, darkColor)
            )
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
    val angleRad = atan2(y - radius, x - radius)
    var angleDeg = (angleRad * 180.0 / kotlin.math.PI + 360) % 360

    val length = MathHelper.getLength(x, y, radius)
    val progress = angleDeg / 360f
    onLocationChange(
        MathHelper.getBoundedPointWithInRadius(x, y, length, radius - ringWidthPx / 2, BoundedPointStrategy.Edge)
    )
    onColorChange(Color.fromHueProgress(progress.toFloat()))
}
