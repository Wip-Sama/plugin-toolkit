package org.wip.plugintoolkit.shared.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

/**
 * Applies vertical fading edges at the top and bottom of a scrollable container.
 */
fun Modifier.verticalFadingEdges(
    topFadeLength: Dp,
    bottomFadeLength: Dp,
    almostOpaque: Float = 0.99f
): Modifier = this
    .graphicsLayer { alpha = almostOpaque }
    .drawWithContent {
        drawContent()
        val topFadePx = topFadeLength.toPx()
        val bottomFadePx = bottomFadeLength.toPx()

        if (topFadePx > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black),
                    startY = 0f,
                    endY = topFadePx
                ),
                blendMode = BlendMode.DstIn
            )
        }
        if (bottomFadePx > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Black, Color.Transparent),
                    startY = size.height - bottomFadePx,
                    endY = size.height
                ),
                blendMode = BlendMode.DstIn
            )
        }
    }
