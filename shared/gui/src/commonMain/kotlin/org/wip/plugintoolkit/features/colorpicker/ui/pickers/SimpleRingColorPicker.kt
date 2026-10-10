package org.wip.plugintoolkit.features.colorpicker.ui.pickers

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.colorpicker.logic.ColorPickerHelper
import org.wip.plugintoolkit.features.colorpicker.logic.MathHelper
import kotlin.math.atan2

/**
 * Concentric-ring (discrete palette) color picker.
 * Reactively highlights the nearest grid cell when the external color changes.
 */
@Composable
internal fun SimpleRingColorPicker(
    modifier: Modifier = Modifier,
    selectedColor: Color = Color.Red,
    colorWidth: Dp = 20.dp,
    tracksCount: Int = 5,
    sectorsCount: Int = 24,
    onPickedColor: (Color) -> Unit
) {
    val density = LocalDensity.current
    val colorWidthPx = remember(colorWidth, density) { with(density) { colorWidth.toPx() } }
    val widthSmallExtra = ToolkitTheme.dimensions.widthSmallExtra
    val selectColorWidth = remember(colorWidthPx, widthSmallExtra, density) {
        with(density) { colorWidthPx + widthSmallExtra.toPx() }
    }

    var pickerLocation by remember {
        mutableStateOf(ColorPickerHelper.findNearestSimpleRingLocation(selectedColor, tracksCount, sectorsCount))
    }
    var radius by remember { mutableStateOf(0f) }

    LaunchedEffect(selectedColor) {
        pickerLocation = ColorPickerHelper.findNearestSimpleRingLocation(selectedColor, tracksCount, sectorsCount)
    }

    fun updateSelection(pos: Offset) {
        val newLoc = calculateSimpleRingLocation(
            x = pos.x,
            y = pos.y,
            radius = radius,
            colorWidthPx = colorWidthPx,
            selectColorWidth = selectColorWidth,
            tracksCount = tracksCount,
            sectorsCount = sectorsCount
        )
        pickerLocation = newLoc
        val newColor = ColorPickerHelper.getSimpleRingColor(newLoc.x, newLoc.y, tracksCount, sectorsCount)
        onPickedColor(newColor)
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(ToolkitTheme.dimensions.colorPickerCanvasHeight)
                .aspectRatio(1f)
                .onSizeChanged { radius = it.width / 2f }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        updateSelection(down.position)
                        drag(down.id) { change ->
                            updateSelection(change.position)
                            if (change.positionChange() != Offset.Zero) change.consume()
                        }
                    }
                }
        ) {
            repeat(tracksCount) { track ->
                repeat(sectorsCount) { sector ->
                    val degree = 360f / sectorsCount * sector
                    drawArc(
                        ColorPickerHelper.getSimpleRingColor(sector, track, tracksCount, sectorsCount),
                        degree,
                        360f / sectorsCount,
                        false,
                        topLeft = Offset(
                            track * colorWidthPx + colorWidthPx / 2 + selectColorWidth / 2,
                            track * colorWidthPx + colorWidthPx / 2 + selectColorWidth / 2
                        ),
                        size = Size(
                            size.width - (track * colorWidthPx * 2) - colorWidthPx - selectColorWidth,
                            size.height - (track * colorWidthPx * 2) - colorWidthPx - selectColorWidth
                        ),
                        style = Stroke(colorWidthPx)
                    )
                }
            }

            drawIntoCanvas { canvas ->
                val paint = Paint().apply {
                    style = PaintingStyle.Stroke
                    strokeWidth = selectColorWidth
                    color = ColorPickerHelper.getSimpleRingColor(pickerLocation.x, pickerLocation.y, tracksCount, sectorsCount)
                }

                canvas.drawArc(
                    pickerLocation.y * colorWidthPx + colorWidthPx / 2 + selectColorWidth / 2,
                    pickerLocation.y * colorWidthPx + colorWidthPx / 2 + selectColorWidth / 2,
                    (pickerLocation.y * colorWidthPx) + colorWidthPx / 2 + selectColorWidth / 2 +
                            size.width - (pickerLocation.y * colorWidthPx * 2) - colorWidthPx - selectColorWidth,
                    (pickerLocation.y * colorWidthPx) + colorWidthPx / 2 + selectColorWidth / 2 +
                            size.height - (pickerLocation.y * colorWidthPx * 2) - colorWidthPx - selectColorWidth,
                    360 / sectorsCount.toFloat() * pickerLocation.x,
                    360f / sectorsCount,
                    false,
                    paint
                )
            }
        }
    }
}

private fun calculateSimpleRingLocation(
    x: Float,
    y: Float,
    radius: Float,
    colorWidthPx: Float,
    selectColorWidth: Float,
    tracksCount: Int,
    sectorsCount: Int
): IntOffset {
    if (radius <= 0f || colorWidthPx <= 0f) return IntOffset(0, 0)
    val length = MathHelper.getLength(x, y, radius)
    val outerEdge = radius - selectColorWidth / 2
    val trackIndex = ((outerEdge - length) / colorWidthPx).toInt().coerceIn(0, tracksCount - 1)

    val angleRad = atan2(y - radius, x - radius)
    val angleDeg = (angleRad * 180.0 / kotlin.math.PI + 360) % 360
    val angleProgress = angleDeg / 360f

    return IntOffset(
        (sectorsCount * angleProgress).toInt().coerceIn(0, sectorsCount - 1),
        trackIndex
    )
}
