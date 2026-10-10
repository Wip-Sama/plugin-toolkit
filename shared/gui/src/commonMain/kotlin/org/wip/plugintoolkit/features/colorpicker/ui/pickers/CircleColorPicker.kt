package org.wip.plugintoolkit.features.colorpicker.ui.pickers

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.colorpicker.logic.ColorPickerHelper
import org.wip.plugintoolkit.features.colorpicker.model.Colors.gradientColors
import org.wip.plugintoolkit.features.colorpicker.ui.ColorSlideBar
import org.wip.plugintoolkit.features.colorpicker.utils.drawColorSelector

/**
 * Circular HSV wheel color picker.
 * Reactively updates selector location on external color changes and canvas gestures.
 */
@Composable
internal fun CircleColorPicker(
    modifier: Modifier = Modifier,
    selectedColor: Color = Color.White,
    showAlphaBar: Boolean = true,
    showBrightnessBar: Boolean = true,
    lightCenter: Boolean = true,
    onPickedColor: (Color) -> Unit
) {
    var radius by remember { mutableStateOf(0f) }
    var pickerLocation by remember { mutableStateOf(Offset.Zero) }
    var internalColor by remember { mutableStateOf(selectedColor) }
    var brightness by remember { mutableStateOf(0f) }
    var alpha by remember { mutableStateOf(selectedColor.alpha) }

    LaunchedEffect(selectedColor, radius) {
        internalColor = selectedColor
        val hsv = ColorPickerHelper.colorToHsv(selectedColor)
        brightness = if (lightCenter) 1f - hsv.value else hsv.value
        alpha = hsv.alpha
        if (radius > 0f) {
            pickerLocation = ColorPickerHelper.calculateCircleLocation(hsv, radius)
        }
    }

    fun updateFromPosition(pos: Offset) {
        if (radius > 0f) {
            val (newColor, newPos) = ColorPickerHelper.calculateCircleColor(
                x = pos.x,
                y = pos.y,
                radius = radius,
                brightness = brightness,
                alpha = alpha,
                lightCenter = lightCenter
            )
            pickerLocation = newPos
            internalColor = newColor
            onPickedColor(newColor)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = modifier
                .size(ToolkitTheme.dimensions.colorPickerCanvasHeight)
                .onSizeChanged { size ->
                    val r = minOf(size.width, size.height) / 2f
                    radius = r
                    if (r > 0f) {
                        pickerLocation = ColorPickerHelper.calculateCircleLocation(
                            hsv = ColorPickerHelper.colorToHsv(internalColor),
                            radius = r
                        )
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset -> updateFromPosition(offset) }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset -> updateFromPosition(offset) },
                        onDrag = { change, _ ->
                            change.consume()
                            updateFromPosition(change.position)
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val circleCenter = Offset(size.width / 2f, size.height / 2f)
                val currentRadius = size.width / 2f

                drawCircle(
                    brush = Brush.sweepGradient(gradientColors, center = circleCenter),
                    radius = currentRadius,
                    center = circleCenter
                )
                drawCircle(
                    brush = ShaderBrush(
                        RadialGradientShader(
                            circleCenter,
                            colors = listOf(
                                if (lightCenter) Color.White else Color.Black,
                                Color.Transparent
                            ),
                            radius = currentRadius
                        )
                    ),
                    radius = currentRadius,
                    center = circleCenter
                )
                drawColorSelector(internalColor, pickerLocation)
            }
        }

        if (showBrightnessBar) {
            Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))
            ColorSlideBar(
                modifier = Modifier.testTag("circle_brightness_bar"),
                value = brightness,
                onValueChange = { b ->
                    brightness = b
                    val hsv = ColorPickerHelper.colorToHsv(internalColor)
                    val newValue = if (lightCenter) 1f - b else b
                    val newColor = ColorPickerHelper.hsvToColor(hsv.copy(value = newValue, alpha = alpha))
                    internalColor = newColor
                    onPickedColor(newColor)
                },
                colors = listOf(
                    if (lightCenter) Color.Black else Color.White,
                    internalColor.copy(alpha = 1f)
                )
            )
        }

        if (showAlphaBar) {
            Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))
            ColorSlideBar(
                modifier = Modifier.testTag("circle_alpha_bar"),
                value = alpha,
                onValueChange = { a ->
                    alpha = a
                    val newColor = internalColor.copy(alpha = a)
                    internalColor = newColor
                    onPickedColor(newColor)
                },
                colors = listOf(Color.Transparent, internalColor.copy(alpha = 1f)),
                showCheckerboard = true
            )
        }
    }
}
