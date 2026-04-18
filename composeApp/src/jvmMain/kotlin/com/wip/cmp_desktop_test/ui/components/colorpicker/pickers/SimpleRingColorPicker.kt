package com.wip.cmp_desktop_test.ui.components.colorpicker.pickers

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.wip.cmp_desktop_test.data.colorpicker.ColorRange
import com.wip.cmp_desktop_test.extensions.colorpicker.darken
import com.wip.cmp_desktop_test.helper.colorpicker.ColorPickerHelper
import com.wip.cmp_desktop_test.helper.colorpicker.MathHelper
import kotlin.math.atan2
import kotlin.math.roundToInt

/**
 * Concentric-ring (discrete) color picker.
 *
 * @param colorWidth   Arc width for each color ring segment.
 * @param tracksCount  Number of concentric tracks.
 * @param sectorsCount Number of color sectors per track.
 * @param onPickedColor Callback invoked whenever the selected color changes.
 */
@Composable
internal fun SimpleRingColorPicker(
    modifier: Modifier = Modifier,
    colorWidth: Dp,
    tracksCount: Int,
    sectorsCount: Int,
    onPickedColor: (Color) -> Unit
) {
    val density = LocalDensity.current
    val colorWidthPx = remember { with(density) { colorWidth.toPx() } }
    val selectColorWidth = remember { with(density) { colorWidthPx + 5.dp.toPx() } }

    var pickerLocation by remember { mutableStateOf(IntOffset(0, 0)) }
    var radius by remember { mutableStateOf(0f) }

    LaunchedEffect(pickerLocation) {
        onPickedColor(
            getColorAt(
                pickerLocation.x / sectorsCount.toFloat(),
                (pickerLocation.y / tracksCount.toFloat()).coerceIn(0f, 1f)
            )
        )
    }

    Canvas(modifier = modifier
        .size(280.dp)
        .aspectRatio(1f)
        .onSizeChanged { radius = it.width / 2f }
        .pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { offset ->
                    pickerLocation = calculateSimpleRingLocation(
                        x = offset.x, y = offset.y,
                        radius = radius,
                        colorWidthPx = colorWidthPx,
                        tracksCount = tracksCount,
                        sectorsCount = sectorsCount
                    )
                }
            ) { change, _ ->
                change.consume()
                pickerLocation = calculateSimpleRingLocation(
                    x = change.position.x, y = change.position.y,
                    radius = radius,
                    colorWidthPx = colorWidthPx,
                    tracksCount = tracksCount,
                    sectorsCount = sectorsCount
                )
            }
        }
    ) {
        repeat(tracksCount) { track ->
            repeat(sectorsCount) { sector ->
                val degree = 360f / sectorsCount * sector
                drawArc(
                    getColorAt(
                        sector / sectorsCount.toFloat(),
                        (track / tracksCount.toFloat()).coerceIn(0f, 1f)
                    ),
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
            }
            val frameworkPaint = paint.asFrameworkPaint()
            frameworkPaint.color = getColorAt(
                pickerLocation.x / sectorsCount.toFloat(),
                (pickerLocation.y / tracksCount.toFloat()).coerceIn(0f, 1f)
            ).toArgb()
//            frameworkPaint.setShadowLayer(50f, 0f, 0f, Color.Black.copy(alpha = 0.4f).toArgb())
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

private fun calculateSimpleRingLocation(
    x: Float, y: Float,
    radius: Float,
    colorWidthPx: Float,
    tracksCount: Int,
    sectorsCount: Int
): IntOffset {
    val length = MathHelper.getLength(x, y, radius)
    val offset = radius - colorWidthPx * tracksCount
    val trackProgress = ((length - offset) / (radius - offset)).coerceIn(0f, 1f)
    val angleProgress = ((Math.toDegrees(
        atan2(y - radius, x - radius).toDouble()
    ) + 360) % 360) / 360f
    return IntOffset(
        (sectorsCount * angleProgress).roundToInt().coerceIn(0, sectorsCount),
        ((tracksCount.toFloat()) * (1 - trackProgress)).roundToInt().coerceIn(0, tracksCount - 1)
    )
}

private fun getColorAt(progress: Float, deepProgress: Float): Color {
    val (rangeProgress, range) = ColorPickerHelper.calculateRangeProgress(progress.toDouble())
    val dark: Float = 0.5f * deepProgress
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
    return Color(red.darken(dark), green.darken(dark), blue.darken(dark))
}
