package com.wip.kpm_cpm_wotoolkit.ui.components.colorpicker.pickers

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
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
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.wip.kpm_cpm_wotoolkit.extensions.colorpicker.blue
import com.wip.kpm_cpm_wotoolkit.extensions.colorpicker.darken
import com.wip.kpm_cpm_wotoolkit.extensions.colorpicker.fromHueProgress
import com.wip.kpm_cpm_wotoolkit.extensions.colorpicker.green
import com.wip.kpm_cpm_wotoolkit.extensions.colorpicker.red
import com.wip.kpm_cpm_wotoolkit.helper.colorpicker.MathHelper
import kotlin.math.atan2

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
            awaitEachGesture {
                val down = awaitFirstDown()
                pickerLocation = calculateSimpleRingLocation(
                    x = down.position.x, y = down.position.y,
                    radius = radius,
                    colorWidthPx = colorWidthPx,
                    selectColorWidth = selectColorWidth,
                    tracksCount = tracksCount,
                    sectorsCount = sectorsCount
                )
                drag(down.id) { change ->
                    pickerLocation = calculateSimpleRingLocation(
                        x = change.position.x, y = change.position.y,
                        radius = radius,
                        colorWidthPx = colorWidthPx,
                        selectColorWidth = selectColorWidth,
                        tracksCount = tracksCount,
                        sectorsCount = sectorsCount
                    )
                    if (change.positionChange() != Offset.Zero) change.consume()
                }
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
    selectColorWidth: Float,
    tracksCount: Int,
    sectorsCount: Int
): IntOffset {
    val length = MathHelper.getLength(x, y, radius)
    val outerEdge = radius - selectColorWidth / 2
    val trackIndex = ((outerEdge - length) / colorWidthPx).toInt().coerceIn(0, tracksCount - 1)
    
    val angleProgress = ((Math.toDegrees(
        atan2(y - radius, x - radius).toDouble()
    ) + 360) % 360) / 360f
    
    return IntOffset(
        (sectorsCount * angleProgress).toInt().coerceIn(0, sectorsCount - 1),
        trackIndex
    )
}

private fun getColorAt(progress: Float, deepProgress: Float): Color {
    val pureColor = Color.fromHueProgress(progress)
    val dark: Float = 0.5f * deepProgress
    return Color(
        pureColor.red().darken(dark),
        pureColor.green().darken(dark),
        pureColor.blue().darken(dark)
    )
}
