package org.wip.plugintoolkit.features.flows.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

@Composable
fun NodeCardContainer(
    nodePosition: Offset,
    dragOffset: Offset,
    scale: Float,
    boardOffset: Offset,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .offset {
                IntOffset(
                    (((nodePosition.x + dragOffset.x) * scale) + boardOffset.x).roundToInt(),
                    (((nodePosition.y + dragOffset.y) * scale) + boardOffset.y).roundToInt()
                )
            }
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                transformOrigin = TransformOrigin(0f, 0f)
            )
    ) {
        content()
    }
}
