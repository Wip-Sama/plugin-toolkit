package org.wip.plugintoolkit.core.utils

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Platform-appropriate vertical scrollbar indicator synchronized with [scrollState].
 * On desktop targets, renders an interactive draggable scrollbar track.
 */
@Composable
expect fun PlatformVerticalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier
)

/**
 * Platform-appropriate horizontal scrollbar indicator synchronized with [scrollState].
 * On desktop targets, renders an interactive draggable scrollbar track.
 */
@Composable
expect fun PlatformHorizontalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier
)
