package org.wip.plugintoolkit.ui.titlebar

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Multiplatform wrapper for window dragging. On desktop JVM, delegates to WindowScope.WindowDraggableArea.
 */
@Composable
expect fun WindowDraggableArea(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
)
