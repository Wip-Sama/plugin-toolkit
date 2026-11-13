package org.wip.plugintoolkit.core.utils

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.wip.plugintoolkit.core.theme.ToolkitTheme

private const val SCROLLBAR_UNHOVER_ALPHA = 0.28f
private const val SCROLLBAR_HOVER_ALPHA = 0.56f
private const val SCROLLBAR_HIDE_DELAY_MILLIS = 1500

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
        modifier = modifier,
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
        modifier = modifier,
        style = rememberToolkitScrollbarStyle()
    )
}
