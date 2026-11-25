package org.wip.plugintoolkit.core.utils

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import org.wip.plugintoolkit.core.theme.ToolkitTheme

private const val SCROLLBAR_UNHOVER_ALPHA = 0.28f
private const val SCROLLBAR_HOVER_ALPHA = 0.56f

/**
 * Guards Skiko's `VerticalScrollbar` against unbounded height constraints (`Constraints.Infinity`),
 * which otherwise cause `IllegalStateException: Size(w x 2147483647) is out of range` during
 * lookahead, animated pane transitions, window fullscreen switches, or nested scroll passes.
 */
private fun Modifier.guardBoundedVerticalScrollbar(): Modifier = this.layout { measurable, constraints ->
    if (!constraints.hasBoundedHeight || constraints.maxHeight <= 0) {
        layout(0, 0) {}
    } else {
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            placeable.placeRelative(0, 0)
        }
    }
}

/**
 * Guards Skiko's `HorizontalScrollbar` against unbounded width constraints (`Constraints.Infinity`).
 */
private fun Modifier.guardBoundedHorizontalScrollbar(): Modifier = this.layout { measurable, constraints ->
    if (!constraints.hasBoundedWidth || constraints.maxWidth <= 0) {
        layout(0, 0) {}
    } else {
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            placeable.placeRelative(0, 0)
        }
    }
}

@Composable
private fun rememberToolkitScrollbarStyle(): ScrollbarStyle {
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    return ScrollbarStyle(
        minimalHeight = ToolkitTheme.spacing.lg,
        thickness = ToolkitTheme.dimensions.sduiScrollbarThickness,
        shape = ToolkitTheme.shapes.pill,
        hoverDurationMillis = 250,
        unhoverColor = onSurfaceVariant.copy(alpha = SCROLLBAR_UNHOVER_ALPHA),
        hoverColor = onSurfaceVariant.copy(alpha = SCROLLBAR_HOVER_ALPHA)
    )
}

@Composable
actual fun PlatformVerticalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier
) {
    if (scrollState.maxValue <= 0) return
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(scrollState),
        modifier = modifier.guardBoundedVerticalScrollbar(),
        style = rememberToolkitScrollbarStyle()
    )
}

@Composable
actual fun PlatformVerticalScrollbar(
    lazyListState: LazyListState,
    modifier: Modifier
) {
    if (!lazyListState.canScrollForward && !lazyListState.canScrollBackward) return
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(lazyListState),
        modifier = modifier.guardBoundedVerticalScrollbar(),
        style = rememberToolkitScrollbarStyle()
    )
}

@Composable
actual fun PlatformVerticalScrollbar(
    lazyGridState: LazyGridState,
    modifier: Modifier
) {
    if (!lazyGridState.canScrollForward && !lazyGridState.canScrollBackward) return
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(lazyGridState),
        modifier = modifier.guardBoundedVerticalScrollbar(),
        style = rememberToolkitScrollbarStyle()
    )
}

@Composable
actual fun PlatformHorizontalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier
) {
    if (scrollState.maxValue <= 0) return
    HorizontalScrollbar(
        adapter = rememberScrollbarAdapter(scrollState),
        modifier = modifier.guardBoundedHorizontalScrollbar(),
        style = rememberToolkitScrollbarStyle()
    )
}
