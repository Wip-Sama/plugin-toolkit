package org.wip.plugintoolkit.ui.titlebar

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Rectangle

private const val DEFAULT_MIN_WIDTH = 680
private const val DEFAULT_MIN_HEIGHT = 480

/**
 * Overlay placed around the edges and corners of an undecorated window to enable smooth edge/corner resizing.
 */
@Composable
fun BoxScope.WindowResizeOverlay(
    isResizable: Boolean = true
) {
    val window = LocalWindowScope.current?.window ?: return
    val controller = LocalWindowController.current
    // Only enable resizing if the window is floating (not maximized / fullscreen)
    if (!isResizable || (controller != null && controller.isMaximized)) return

    val borderThickness = ToolkitTheme.dimensions.windowResizeBorderThickness
    val cornerSize = ToolkitTheme.dimensions.windowResizeCornerSize

    val minWidth = window.minimumSize?.width ?: DEFAULT_MIN_WIDTH
    val minHeight = window.minimumSize?.height ?: DEFAULT_MIN_HEIGHT

    // ── 4 Edges ────────────────────────────────────────────────────────
    // Top Edge
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(borderThickness)
            .align(Alignment.TopCenter)
            .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR)))
            .pointerInput(Unit) {
                detectDragGestures { _, dragAmount ->
                    val b = window.bounds
                    val newHeight = (b.height - dragAmount.y.toInt()).coerceAtLeast(minHeight)
                    val newY = b.y + (b.height - newHeight)
                    window.bounds = Rectangle(b.x, newY, b.width, newHeight)
                }
            }
    )

    // Bottom Edge
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(borderThickness)
            .align(Alignment.BottomCenter)
            .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR)))
            .pointerInput(Unit) {
                detectDragGestures { _, dragAmount ->
                    val b = window.bounds
                    val newHeight = (b.height + dragAmount.y.toInt()).coerceAtLeast(minHeight)
                    window.bounds = Rectangle(b.x, b.y, b.width, newHeight)
                }
            }
    )

    // Left Edge
    Box(
        modifier = Modifier
            .width(borderThickness)
            .fillMaxHeight()
            .align(Alignment.CenterStart)
            .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR)))
            .pointerInput(Unit) {
                detectDragGestures { _, dragAmount ->
                    val b = window.bounds
                    val newWidth = (b.width - dragAmount.x.toInt()).coerceAtLeast(minWidth)
                    val newX = b.x + (b.width - newWidth)
                    window.bounds = Rectangle(newX, b.y, newWidth, b.height)
                }
            }
    )

    // Right Edge
    Box(
        modifier = Modifier
            .width(borderThickness)
            .fillMaxHeight()
            .align(Alignment.CenterEnd)
            .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)))
            .pointerInput(Unit) {
                detectDragGestures { _, dragAmount ->
                    val b = window.bounds
                    val newWidth = (b.width + dragAmount.x.toInt()).coerceAtLeast(minWidth)
                    window.bounds = Rectangle(b.x, b.y, newWidth, b.height)
                }
            }
    )

    // ── 4 Corners ──────────────────────────────────────────────────────
    // Top-Left Corner
    Box(
        modifier = Modifier
            .size(cornerSize)
            .align(Alignment.TopStart)
            .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR)))
            .pointerInput(Unit) {
                detectDragGestures { _, dragAmount ->
                    val b = window.bounds
                    val newWidth = (b.width - dragAmount.x.toInt()).coerceAtLeast(minWidth)
                    val newHeight = (b.height - dragAmount.y.toInt()).coerceAtLeast(minHeight)
                    val newX = b.x + (b.width - newWidth)
                    val newY = b.y + (b.height - newHeight)
                    window.bounds = Rectangle(newX, newY, newWidth, newHeight)
                }
            }
    )

    // Top-Right Corner
    Box(
        modifier = Modifier
            .size(cornerSize)
            .align(Alignment.TopEnd)
            .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR)))
            .pointerInput(Unit) {
                detectDragGestures { _, dragAmount ->
                    val b = window.bounds
                    val newWidth = (b.width + dragAmount.x.toInt()).coerceAtLeast(minWidth)
                    val newHeight = (b.height - dragAmount.y.toInt()).coerceAtLeast(minHeight)
                    val newY = b.y + (b.height - newHeight)
                    window.bounds = Rectangle(b.x, newY, newWidth, newHeight)
                }
            }
    )

    // Bottom-Left Corner
    Box(
        modifier = Modifier
            .size(cornerSize)
            .align(Alignment.BottomStart)
            .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.SW_RESIZE_CURSOR)))
            .pointerInput(Unit) {
                detectDragGestures { _, dragAmount ->
                    val b = window.bounds
                    val newWidth = (b.width - dragAmount.x.toInt()).coerceAtLeast(minWidth)
                    val newHeight = (b.height + dragAmount.y.toInt()).coerceAtLeast(minHeight)
                    val newX = b.x + (b.width - newWidth)
                    window.bounds = Rectangle(newX, b.y, newWidth, newHeight)
                }
            }
    )

    // Bottom-Right Corner
    Box(
        modifier = Modifier
            .size(cornerSize)
            .align(Alignment.BottomEnd)
            .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR)))
            .pointerInput(Unit) {
                detectDragGestures { _, dragAmount ->
                    val b = window.bounds
                    val newWidth = (b.width + dragAmount.x.toInt()).coerceAtLeast(minWidth)
                    val newHeight = (b.height + dragAmount.y.toInt()).coerceAtLeast(minHeight)
                    window.bounds = Rectangle(b.x, b.y, newWidth, newHeight)
                }
            }
    )
}
