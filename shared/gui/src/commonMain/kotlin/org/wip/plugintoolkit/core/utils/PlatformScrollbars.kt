package org.wip.plugintoolkit.core.utils

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
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
 * Platform-appropriate vertical scrollbar indicator synchronized with a [LazyListState].
 */
@Composable
expect fun PlatformVerticalScrollbar(
    lazyListState: LazyListState,
    modifier: Modifier = Modifier
)

/**
 * Platform-appropriate vertical scrollbar indicator synchronized with a [LazyGridState].
 */
@Composable
expect fun PlatformVerticalScrollbar(
    lazyGridState: LazyGridState,
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
