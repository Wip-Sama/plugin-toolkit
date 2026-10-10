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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.colorpicker.logic.ColorPickerHelper
import org.wip.plugintoolkit.features.colorpicker.model.Colors.gradientColors
import org.wip.plugintoolkit.features.colorpicker.ui.ColorSlideBar
import org.wip.plugintoolkit.features.colorpicker.utils.drawColorSelector
import org.wip.plugintoolkit.features.colorpicker.utils.fromHueProgress

/**
 * Classic rectangular HSV color picker.
 * Reactively responds to both canvas gestures and external color modifications.
 */
@Composable
internal fun ClassicColorPicker(
    modifier: Modifier = Modifier,
    selectedColor: Color = Color.White,
    hue: Float? = null,
    showAlphaBar: Boolean = true,
    showHueBar: Boolean = true,
    onPickedColor: (Color) -> Unit
) {
    var pickerLocation by remember { mutableStateOf(Offset.Zero) }
    var colorPickerSize by remember { mutableStateOf(IntSize.Zero) }
    var internalColor by remember { mutableStateOf(selectedColor) }

    var currentHue by remember {
        val hsv = ColorPickerHelper.colorToHsv(selectedColor)
        mutableStateOf(hue ?: if (hsv.saturation > 0.01f && hsv.value > 0.01f) hsv.hue else 0f)
    }

    LaunchedEffect(hue) {
        if (hue != null) {
            currentHue = hue
        }
    }

    LaunchedEffect(selectedColor, colorPickerSize) {
        if (selectedColor != internalColor) {
            internalColor = selectedColor
            val hsv = ColorPickerHelper.colorToHsv(selectedColor)
            if (hue == null && hsv.saturation > 0.01f && hsv.value > 0.01f) {
                currentHue = hsv.hue
            }
            if (colorPickerSize.width > 0 && colorPickerSize.height > 0) {
                val newY = ((1f - hsv.value) * colorPickerSize.height.toFloat()).coerceIn(0f, colorPickerSize.height.toFloat())
                // If the color is pure black (value == 0), keep existing x location if user had placed it at the bottom
                val newX = if (hsv.value == 0f && pickerLocation.y >= colorPickerSize.height.toFloat() - 2f) {
                    pickerLocation.x
                } else {
                    (hsv.saturation * colorPickerSize.width.toFloat()).coerceIn(0f, colorPickerSize.width.toFloat())
                }
                pickerLocation = Offset(newX, newY)
            }
        }
    }

    val rangeColor = remember(currentHue) { Color.fromHueProgress(currentHue / 360f) }

    fun updateFromPosition(pos: Offset) {
        if (colorPickerSize.width > 0 && colorPickerSize.height > 0) {
            val clampedX = pos.x.coerceIn(0f, colorPickerSize.width.toFloat())
            val clampedY = pos.y.coerceIn(0f, colorPickerSize.height.toFloat())
            pickerLocation = Offset(clampedX, clampedY)
            val sat = (clampedX / colorPickerSize.width.toFloat()).coerceIn(0f, 1f)
            val value = (1f - (clampedY / colorPickerSize.height.toFloat())).coerceIn(0f, 1f)
            val newColor = ColorPickerHelper.calculateClassicColor(
                x = clampedX,
                y = clampedY,
                width = colorPickerSize.width.toFloat(),
                height = colorPickerSize.height.toFloat(),
                hue = currentHue,
                alpha = internalColor.alpha
            )
            internalColor = newColor
            onPickedColor(newColor)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(ToolkitTheme.dimensions.colorPickerCanvasHeight)
                .onSizeChanged { size ->
                    colorPickerSize = size
                    if (size.width > 0 && size.height > 0) {
                        val hsv = ColorPickerHelper.colorToHsv(internalColor)
                        val newY = ((1f - hsv.value) * size.height.toFloat()).coerceIn(0f, size.height.toFloat())
                        val newX = (hsv.saturation * size.width.toFloat()).coerceIn(0f, size.width.toFloat())
                        pickerLocation = Offset(newX, newY)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        updateFromPosition(offset)
                    }
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
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .clip(MaterialTheme.shapes.large)
            ) {
                drawRect(Brush.horizontalGradient(listOf(Color.White, rangeColor)))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            }
            Canvas(modifier = Modifier.matchParentSize()) {
                drawColorSelector(internalColor, pickerLocation)
            }
        }

        if (showHueBar) {
            Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))
            ColorSlideBar(
                value = currentHue / 360f,
                onValueChange = { progress ->
                    val newHue = progress * 360f
                    currentHue = newHue
                    val sat = if (colorPickerSize.width > 0) (pickerLocation.x / colorPickerSize.width.toFloat()).coerceIn(0f, 1f) else 0f
                    val value = if (colorPickerSize.height > 0) (1f - (pickerLocation.y / colorPickerSize.height.toFloat())).coerceIn(0f, 1f) else 1f
                    val newColor = ColorPickerHelper.calculateClassicColor(
                        x = pickerLocation.x,
                        y = pickerLocation.y,
                        width = colorPickerSize.width.toFloat(),
                        height = colorPickerSize.height.toFloat(),
                        hue = newHue,
                        alpha = internalColor.alpha
                    )
                    internalColor = newColor
                    onPickedColor(newColor)
                },
                colors = gradientColors
            )
        }

        if (showAlphaBar) {
            Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))
            ColorSlideBar(
                value = internalColor.alpha,
                onValueChange = { alphaVal ->
                    val newColor = internalColor.copy(alpha = alphaVal)
                    internalColor = newColor
                    onPickedColor(newColor)
                },
                colors = listOf(Color.Transparent, internalColor.copy(alpha = 1f)),
                showCheckerboard = true
            )
        }
    }
}
