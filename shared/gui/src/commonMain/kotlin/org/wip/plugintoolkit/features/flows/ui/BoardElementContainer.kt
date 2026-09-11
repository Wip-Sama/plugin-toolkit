package org.wip.plugintoolkit.features.flows.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

/**
 * Universal board element container providing unified positioning and graphicsLayer
 * scaling transform so that all elements (Nodes, Groups, Labels) scale identically with zoom.
 */
@Composable
fun BoardElementContainer(
    position: Offset,
    dragOffset: Offset = Offset.Zero,
    scale: Float,
    boardOffset: Offset,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .offset {
                IntOffset(
                    (((position.x + dragOffset.x) * scale) + boardOffset.x).roundToInt(),
                    (((position.y + dragOffset.y) * scale) + boardOffset.y).roundToInt()
                )
            }
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                transformOrigin = TransformOrigin(0f, 0f)
            )
            .wrapContentSize(align = Alignment.TopStart, unbounded = true)
            .alpha(alpha)
    ) {
        content()
    }
}
