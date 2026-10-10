package org.wip.plugintoolkit.features.colorpicker.utils

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.roundToInt

internal fun DrawScope.drawColorSelector(color: Color, location: Offset, radius: Float = 20f) {
    // Outer dark shadow/border for contrast on light backgrounds
    drawCircle(
        color = Color.Black.copy(alpha = 0.35f),
        radius = radius + 1.5f,
        center = location,
        style = Stroke(width = 3.5f)
    )
    // Primary crisp white ring
    drawCircle(
        color = Color.White,
        radius = radius,
        center = location,
        style = Stroke(width = 3.5f)
    )
    // Inner dark border for contrast on bright backgrounds
    drawCircle(
        color = Color.Black.copy(alpha = 0.2f),
        radius = (radius - 2.5f).coerceAtLeast(1f),
        center = location,
        style = Stroke(width = 1f)
    )
}

internal fun DrawScope.drawTransparentBackground(verticalBoxesSize: Int = 10) {
    val boxSize = size.height / verticalBoxesSize
    repeat((size.width / boxSize).roundToInt() + 1) { x ->
        repeat(verticalBoxesSize) { y ->
            drawRect(
                if ((y + x) % 2 == 0) {
                    Color.LightGray
                } else {
                    Color.White
                }, topLeft = Offset(x * boxSize, y * boxSize), size = Size(boxSize, boxSize)
            )
        }
    }
}

/**
 * Draws a checkered transparent-effect background behind the composable.
 * @param verticalBoxesAmount Amount of the white and gray boxes for a single column.
 */
fun Modifier.transparentBackground(verticalBoxesAmount: Int = 10) = this.drawBehind {
    drawTransparentBackground(verticalBoxesAmount)
}
