package org.wip.plugintoolkit.shared.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

/**
 * Applies vertical fading edges at the top and bottom of a scrollable container.
 * Only shows the top fade if scrolled down, and bottom fade if more content can be scrolled into view.
 */
fun Modifier.verticalFadingEdges(
    scrollState: ScrollState,
    topFadeLength: Dp,
    bottomFadeLength: Dp,
    almostOpaque: Float = 0.99f
): Modifier = this
    .graphicsLayer { alpha = almostOpaque }
    .drawWithContent {
        drawContent()
        val showTop = scrollState.canScrollBackward
        val showBottom = scrollState.canScrollForward
        val topFadePx = if (showTop) topFadeLength.toPx() else 0f
        val bottomFadePx = if (showBottom) bottomFadeLength.toPx() else 0f

        if (topFadePx > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black),
                    startY = 0f,
                    endY = topFadePx
                ),
                blendMode = BlendMode.DstIn
            )
        }
        if (bottomFadePx > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Black, Color.Transparent),
                    startY = size.height - bottomFadePx,
                    endY = size.height
                ),
                blendMode = BlendMode.DstIn
            )
        }
    }

/**
 * Applies vertical fading edges at the top and bottom of a LazyList.
 * Only shows the top fade if scrolled down, and bottom fade if more items can be scrolled into view.
 */
fun Modifier.verticalFadingEdges(
    lazyListState: LazyListState,
    topFadeLength: Dp,
    bottomFadeLength: Dp,
    almostOpaque: Float = 0.99f
): Modifier = this
    .graphicsLayer { alpha = almostOpaque }
    .drawWithContent {
        drawContent()
        val showTop = lazyListState.canScrollBackward
        val showBottom = lazyListState.canScrollForward
        val topFadePx = if (showTop) topFadeLength.toPx() else 0f
        val bottomFadePx = if (showBottom) bottomFadeLength.toPx() else 0f

        if (topFadePx > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black),
                    startY = 0f,
                    endY = topFadePx
                ),
                blendMode = BlendMode.DstIn
            )
        }
        if (bottomFadePx > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Black, Color.Transparent),
                    startY = size.height - bottomFadePx,
                    endY = size.height
                ),
                blendMode = BlendMode.DstIn
            )
        }
    }

/**
 * Fallback overload for vertical fading edges with custom conditional lambdas.
 */
fun Modifier.verticalFadingEdges(
    topFadeLength: Dp,
    bottomFadeLength: Dp,
    showTop: () -> Boolean = { true },
    showBottom: () -> Boolean = { true },
    almostOpaque: Float = 0.99f
): Modifier = this
    .graphicsLayer { alpha = almostOpaque }
    .drawWithContent {
        drawContent()
        val topFadePx = if (showTop()) topFadeLength.toPx() else 0f
        val bottomFadePx = if (showBottom()) bottomFadeLength.toPx() else 0f

        if (topFadePx > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black),
                    startY = 0f,
                    endY = topFadePx
                ),
                blendMode = BlendMode.DstIn
            )
        }
        if (bottomFadePx > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Black, Color.Transparent),
                    startY = size.height - bottomFadePx,
                    endY = size.height
                ),
                blendMode = BlendMode.DstIn
            )
        }
    }

/**
 * Applies horizontal fading edges at the start and end of a scrollable container.
 * Only shows the left fade if scrolled right, and right fade if more content can be scrolled into view.
 */
fun Modifier.horizontalFadingEdges(
    scrollState: ScrollState,
    leftFadeLength: Dp,
    rightFadeLength: Dp,
    almostOpaque: Float = 0.99f
): Modifier = this
    .graphicsLayer { alpha = almostOpaque }
    .drawWithContent {
        drawContent()
        val showLeft = scrollState.canScrollBackward
        val showRight = scrollState.canScrollForward
        val leftFadePx = if (showLeft) leftFadeLength.toPx() else 0f
        val rightFadePx = if (showRight) rightFadeLength.toPx() else 0f

        if (leftFadePx > 0f) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, Color.Black),
                    startX = 0f,
                    endX = leftFadePx
                ),
                blendMode = BlendMode.DstIn
            )
        }
        if (rightFadePx > 0f) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Black, Color.Transparent),
                    startX = size.width - rightFadePx,
                    endX = size.width
                ),
                blendMode = BlendMode.DstIn
            )
        }
    }

/**
 * Applies horizontal fading edges at the start and end of a LazyList.
 * Only shows the left fade if scrolled right, and right fade if more items can be scrolled into view.
 */
fun Modifier.horizontalFadingEdges(
    lazyListState: LazyListState,
    leftFadeLength: Dp,
    rightFadeLength: Dp,
    almostOpaque: Float = 0.99f
): Modifier = this
    .graphicsLayer { alpha = almostOpaque }
    .drawWithContent {
        drawContent()
        val showLeft = lazyListState.canScrollBackward
        val showRight = lazyListState.canScrollForward
        val leftFadePx = if (showLeft) leftFadeLength.toPx() else 0f
        val rightFadePx = if (showRight) rightFadeLength.toPx() else 0f

        if (leftFadePx > 0f) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, Color.Black),
                    startX = 0f,
                    endX = leftFadePx
                ),
                blendMode = BlendMode.DstIn
            )
        }
        if (rightFadePx > 0f) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Black, Color.Transparent),
                    startX = size.width - rightFadePx,
                    endX = size.width
                ),
                blendMode = BlendMode.DstIn
            )
        }
    }

/**
 * Fallback overload for horizontal fading edges with custom conditional lambdas.
 */
fun Modifier.horizontalFadingEdges(
    leftFadeLength: Dp,
    rightFadeLength: Dp,
    showLeft: () -> Boolean = { true },
    showRight: () -> Boolean = { true },
    almostOpaque: Float = 0.99f
): Modifier = this
    .graphicsLayer { alpha = almostOpaque }
    .drawWithContent {
        drawContent()
        val leftFadePx = if (showLeft()) leftFadeLength.toPx() else 0f
        val rightFadePx = if (showRight()) rightFadeLength.toPx() else 0f

        if (leftFadePx > 0f) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, Color.Black),
                    startX = 0f,
                    endX = leftFadePx
                ),
                blendMode = BlendMode.DstIn
            )
        }
        if (rightFadePx > 0f) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Black, Color.Transparent),
                    startX = size.width - rightFadePx,
                    endX = size.width
                ),
                blendMode = BlendMode.DstIn
            )
        }
    }