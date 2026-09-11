package org.wip.plugintoolkit.features.flows.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset

/**
 * Node container delegating to the unified [BoardElementContainer].
 */
@Composable
fun NodeCardContainer(
    nodePosition: Offset,
    dragOffset: Offset,
    scale: Float,
    boardOffset: Offset,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
    content: @Composable () -> Unit
) = BoardElementContainer(
    position = nodePosition,
    dragOffset = dragOffset,
    scale = scale,
    boardOffset = boardOffset,
    modifier = modifier,
    alpha = alpha,
    content = content
)
