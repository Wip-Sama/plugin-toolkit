package org.wip.plugintoolkit.core.utils

import androidx.compose.runtime.Composable

/**
 * Platform-agnostic descriptor for a native desktop right-click context menu entry.
 */
data class PlatformContextMenuItem(
    val label: String,
    val onClick: () -> Unit
)

/**
 * Wraps [content] with a native desktop context menu on right-click when [items] is non-empty.
 */
@Composable
expect fun PlatformContextMenuArea(
    items: List<PlatformContextMenuItem>,
    enabled: Boolean = true,
    content: @Composable () -> Unit
)
