package org.wip.plugintoolkit.ui.titlebar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.WindowScope

/**
 * CompositionLocal providing access to the top-level WindowScope for draggable areas.
 */
val LocalWindowScope = compositionLocalOf<WindowScope?> { null }

@Composable
actual fun WindowDraggableArea(
    modifier: Modifier,
    content: @Composable () -> Unit
) {
    val windowScope = LocalWindowScope.current
    if (windowScope != null) {
        windowScope.WindowDraggableArea(modifier = modifier) {
            content()
        }
    } else {
        Box(modifier = modifier) {
            content()
        }
    }
}
