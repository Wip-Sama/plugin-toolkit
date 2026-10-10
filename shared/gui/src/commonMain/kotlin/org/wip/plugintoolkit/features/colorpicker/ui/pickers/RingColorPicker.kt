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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.colorpicker.logic.ColorPickerHelper
import org.wip.plugintoolkit.features.colorpicker.model.Colors.gradientColors
import org.wip.plugintoolkit.features.colorpicker.ui.ColorSlideBar
import org.wip.plugintoolkit.features.colorpicker.utils.drawColorSelector

/**
 * Ring / wheel color picker with optional lightness, darkness and alpha bars.
 * Reactively updates selector location on external color changes and canvas gestures.
 */
@Composable
internal fun RingColorPicker(
    modifier: Modifier = Modifier,
    selectedColor: Color = Color.Red,
    ringWidth: Dp = 10.dp,
    previewRadius: Dp = 80.dp,
    showLightColorBar: Boolean = true,
    showDarkColorBar: Boolean = true,
    showAlphaBar: Boolean = true,
    showColorPreview: Boolean = true,
    onPickedColor: (Color) -> Unit,
) {
    val density = LocalDensity.current
    val ringWidthPx = remember(ringWidth, density) { with(density) { ringWidth.toPx() } }
    val previewRadiusPx = remember(previewRadius, density) { with(density) { previewRadius.toPx() } }

    var radius by remember { mutableStateOf(0f) }
    var pickerLocation by remember { mutableStateOf(Offset.Zero) }
    var internalColor by remember { mutableStateOf(selectedColor) }
    var lightness by remember { mutableStateOf(0f) }
    var darkness by remember { mutableStateOf(0f) }
    var alpha by remember { mutableStateOf(selectedColor.alpha) }

    LaunchedEffect(selectedColor, radius) {
        internalColor = selectedColor
        val hsv = ColorPickerHelper.colorToHsv(selectedColor)
        lightness = 1f - hsv.saturation
        darkness = 1f - hsv.value
        alpha = hsv.alpha
        if (radius > 0f) {
            pickerLocation = ColorPickerHelper.calculateRingLocation(hsv.hue, radius, ringWidthPx)
        }
    }

    fun updateFromPosition(pos: Offset) {
        if (radius > 0f) {
            val (newColor, newPos) = ColorPickerHelper.calculateRingColor(
                x = pos.x,
                y = pos.y,
                radius = radius,
                ringWidthPx = ringWidthPx,
                lightness = lightness,
                darkness = darkness,
                alpha = alpha
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
                        pickerLocation = ColorPickerHelper.calculateRingLocation(
                            hue = ColorPickerHelper.colorToHsv(internalColor).hue,
                            radius = r,
                            ringWidthPx = ringWidthPx
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
                    radius = (currentRadius - ringWidthPx / 2f).coerceAtLeast(0f),
                    center = circleCenter,
                    style = Stroke(ringWidthPx)
                )
                if (showColorPreview) {
                    drawCircle(internalColor, radius = previewRadiusPx.coerceAtMost(currentRadius / 2f), center = circleCenter)
                }
                drawColorSelector(internalColor, pickerLocation)
            }
        }

        if (showLightColorBar) {
            Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))
            ColorSlideBar(
                modifier = Modifier.testTag("ring_lightness_bar"),
                value = lightness,
                onValueChange = { l ->
                    lightness = l
                    val (newColor, _) = ColorPickerHelper.calculateRingColor(
                        x = pickerLocation.x,
                        y = pickerLocation.y,
                        radius = radius,
                        ringWidthPx = ringWidthPx,
                        lightness = l,
                        darkness = darkness,
                        alpha = alpha
                    )
                    internalColor = newColor
                    onPickedColor(newColor)
                },
                colors = listOf(Color.Transparent, internalColor.copy(alpha = 1f))
            )
        }

        if (showDarkColorBar) {
            Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))
            ColorSlideBar(
                modifier = Modifier.testTag("ring_darkness_bar"),
                value = darkness,
                onValueChange = { d ->
                    darkness = d
                    val (newColor, _) = ColorPickerHelper.calculateRingColor(
                        x = pickerLocation.x,
                        y = pickerLocation.y,
                        radius = radius,
                        ringWidthPx = ringWidthPx,
                        lightness = lightness,
                        darkness = d,
                        alpha = alpha
                    )
                    internalColor = newColor
                    onPickedColor(newColor)
                },
                colors = listOf(Color.Transparent, Color.Black)
            )
        }

        if (showAlphaBar) {
            Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))
            ColorSlideBar(
                modifier = Modifier.testTag("ring_alpha_bar"),
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
