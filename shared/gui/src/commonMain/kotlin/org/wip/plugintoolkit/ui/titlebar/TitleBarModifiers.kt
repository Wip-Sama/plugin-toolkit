package org.wip.plugintoolkit.ui.titlebar

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Marks a composable inside the custom title bar or window topbar as non-draggable (interactive),
 * ensuring mouse events (clicks, hovers, drags) are dispatched to Compose rather than
 * triggering native OS window dragging.
 *
 * Use this on any buttons, search bars, tabs, or input controls placed in the title bar area.
 */
@Composable
expect fun Modifier.nonDraggableTitleBar(key: Any? = null): Modifier

/**
 * Synchronizes the left offset of the draggable title bar (in DP).
 * Keeps the title bar drag handle aligned with an animated or fixed sidebar.
 */
@Composable
expect fun TitleBarLeftOffsetSync(leftOffsetDp: Int)
