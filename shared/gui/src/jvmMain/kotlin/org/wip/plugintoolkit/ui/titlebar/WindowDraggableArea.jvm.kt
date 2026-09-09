package org.wip.plugintoolkit.ui.titlebar

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.WindowScope
import org.wip.plugintoolkit.core.utils.PlatformUtils
import java.awt.MouseInfo
import java.awt.Point
import kotlin.math.hypot

/**
 * CompositionLocal providing access to the top-level WindowScope for draggable areas.
 */
val LocalWindowScope = compositionLocalOf<WindowScope?> { null }

private const val DOUBLE_CLICK_TIMEOUT_MS = 350L
private const val DOUBLE_CLICK_MAX_DISTANCE = 10f

@Composable
actual fun WindowDraggableArea(
    modifier: Modifier,
    onDoubleClick: (() -> Unit)?,
    content: @Composable () -> Unit
) {
    val window = LocalWindowScope.current?.window
    val controller = LocalWindowController.current
    val doubleClickHandler = onDoubleClick ?: { controller?.onMaximizeToggle?.invoke() }

    if (window != null) {
        Box(
            modifier = modifier.pointerInput(window, controller, doubleClickHandler) {
                var lastClickTime = 0L
                var lastClickPos = Offset.Zero

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val clickTime = System.currentTimeMillis()
                    val clickPos = down.position

                    val isDoubleClick = (clickTime - lastClickTime < DOUBLE_CLICK_TIMEOUT_MS) &&
                        ((clickPos - lastClickPos).getDistance() < DOUBLE_CLICK_MAX_DISTANCE)

                    if (isDoubleClick) {
                        lastClickTime = 0L
                        doubleClickHandler.invoke()
                        waitForUpOrCancellation()
                        return@awaitEachGesture
                    }

                    lastClickTime = clickTime
                    lastClickPos = clickPos

                    var startScreenMouse: Point? = runCatching { MouseInfo.getPointerInfo()?.location }.getOrNull()
                    var startWindowLoc: Point = window.location
                    val touchSlop = viewConfiguration.touchSlop
                    var isDragging = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break

                        val curScreenMouse = runCatching { MouseInfo.getPointerInfo()?.location }.getOrNull()
                        if (startScreenMouse != null && curScreenMouse != null) {
                            val dx = curScreenMouse.x - startScreenMouse.x
                            val dy = curScreenMouse.y - startScreenMouse.y
                            val dist = hypot(dx.toDouble(), dy.toDouble())

                            if (!isDragging && dist > touchSlop) {
                                isDragging = true

                                // Restore from maximized: unmaximize and position under cursor
                                if (controller?.isMaximized == true) {
                                    controller.onMaximizeToggle()
                                    val curX = curScreenMouse.x
                                    val curY = curScreenMouse.y
                                    val restoredWidth = window.width.takeIf { it > 0 } ?: 1280
                                    val targetX = (curX - restoredWidth / 2).coerceAtLeast(0)
                                    val targetY = (curY - 15).coerceAtLeast(0)
                                    window.setLocation(targetX, targetY)
                                    startWindowLoc = Point(targetX, targetY)
                                    startScreenMouse = Point(curX, curY)
                                }

                                // On Windows: hand off to the OS native move loop via JNI.
                                // PostMessage(WM_NCLBUTTONDOWN, HTCAPTION) enters TrackMoveSize,
                                // giving us full Aero Snap (top-edge max, side-edge half-screen,
                                // drag-away restore). No WS_CAPTION style changes needed.
                                if (PlatformUtils.isWindows) {
                                    val moved = NativeDrag.startWindowMoveFor(window)
                                    if (moved) {
                                        change.consume()
                                        waitForUpOrCancellation()
                                        return@awaitEachGesture
                                    }
                                }
                            }

                            // Fallback: Compose delta dragging (macOS / Linux / Windows if JNI failed)
                            if (isDragging) {
                                change.consume()
                                window.setLocation(startWindowLoc.x + dx, startWindowLoc.y + dy)
                            }
                        } else {
                            // No absolute mouse coords: fall back to relative pointer deltas
                            val delta = change.position - change.previousPosition
                            if (!isDragging && delta.getDistance() > 1f) {
                                isDragging = true
                                if (controller?.isMaximized == true) {
                                    controller.onMaximizeToggle()
                                }
                                if (PlatformUtils.isWindows) {
                                    val moved = NativeDrag.startWindowMoveFor(window)
                                    if (moved) {
                                        change.consume()
                                        waitForUpOrCancellation()
                                        return@awaitEachGesture
                                    }
                                }
                            }
                            if (isDragging) {
                                change.consume()
                                val curLoc = window.location
                                window.setLocation(curLoc.x + delta.x.toInt(), curLoc.y + delta.y.toInt())
                            }
                        }
                    }
                }
            }
        ) {
            content()
        }
    } else {
        Box(modifier = modifier) {
            content()
        }
    }
}
