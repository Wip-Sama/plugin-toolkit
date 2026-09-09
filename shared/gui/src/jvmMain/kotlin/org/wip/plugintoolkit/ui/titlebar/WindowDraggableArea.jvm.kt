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
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Toolkit
import kotlin.math.hypot

/**
 * CompositionLocal providing access to the top-level WindowScope for draggable areas.
 */
val LocalWindowScope = compositionLocalOf<WindowScope?> { null }

private const val DOUBLE_CLICK_TIMEOUT_MS = 350L
private const val DOUBLE_CLICK_MAX_DISTANCE = 10f
private const val SNAP_EDGE_THRESHOLD_PX = 8

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
                var preSnapSize: Dimension? = null

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
                        if (!change.pressed) {
                            break
                        }

                        val curScreenMouse = runCatching { MouseInfo.getPointerInfo()?.location }.getOrNull()
                        if (startScreenMouse != null && curScreenMouse != null) {
                            val dx = curScreenMouse.x - startScreenMouse.x
                            val dy = curScreenMouse.y - startScreenMouse.y
                            val dist = hypot(dx.toDouble(), dy.toDouble())

                            if (!isDragging && dist > touchSlop) {
                                isDragging = true
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
                                } else if (preSnapSize != null) {
                                    val restoreW = preSnapSize!!.width
                                    val restoreH = preSnapSize!!.height
                                    val curX = curScreenMouse.x
                                    val curY = curScreenMouse.y
                                    val targetX = (curX - restoreW / 2).coerceAtLeast(0)
                                    val targetY = (curY - 15).coerceAtLeast(0)
                                    window.setBounds(targetX, targetY, restoreW, restoreH)
                                    startWindowLoc = Point(targetX, targetY)
                                    startScreenMouse = Point(curX, curY)
                                    preSnapSize = null
                                }
                            }

                            if (isDragging) {
                                change.consume()
                                window.setLocation(startWindowLoc.x + dx, startWindowLoc.y + dy)
                            }
                        } else {
                            val delta = change.position - change.previousPosition
                            if (!isDragging && delta.getDistance() > 1f) {
                                isDragging = true
                                if (controller?.isMaximized == true) {
                                    controller.onMaximizeToggle()
                                }
                            }
                            if (isDragging) {
                                change.consume()
                                val curLoc = window.location
                                window.setLocation(curLoc.x + delta.x.toInt(), curLoc.y + delta.y.toInt())
                            }
                        }
                    }

                    // On mouse release, apply Aero Snapping if dropped at a screen edge
                    if (isDragging) {
                        val releaseMouse = runCatching { MouseInfo.getPointerInfo()?.location }.getOrNull()
                        if (releaseMouse != null) {
                            val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
                            val screens = ge.screenDevices
                            val targetScreen = screens.firstOrNull { device ->
                                device.defaultConfiguration.bounds.contains(releaseMouse)
                            } ?: ge.defaultScreenDevice

                            val gc = targetScreen.defaultConfiguration
                            val screenBounds = gc.bounds
                            val insets = Toolkit.getDefaultToolkit().getScreenInsets(gc)

                            val usableTop = screenBounds.y + insets.top
                            val usableLeft = screenBounds.x + insets.left
                            val usableRight = screenBounds.x + screenBounds.width - insets.right
                            val usableBottom = screenBounds.y + screenBounds.height - insets.bottom
                            val usableWidth = usableRight - usableLeft
                            val usableHeight = usableBottom - usableTop

                            if (releaseMouse.y <= usableTop + SNAP_EDGE_THRESHOLD_PX) {
                                // Top edge -> Maximize
                                if (controller?.isMaximized == false) {
                                    controller.onMaximizeToggle()
                                }
                            } else if (releaseMouse.x <= usableLeft + SNAP_EDGE_THRESHOLD_PX) {
                                // Left edge -> Snap left half
                                if (controller?.isMaximized == true) {
                                    controller.onMaximizeToggle()
                                }
                                preSnapSize = Dimension(window.width, window.height)
                                window.setBounds(usableLeft, usableTop, usableWidth / 2, usableHeight)
                            } else if (releaseMouse.x >= usableRight - SNAP_EDGE_THRESHOLD_PX) {
                                // Right edge -> Snap right half
                                if (controller?.isMaximized == true) {
                                    controller.onMaximizeToggle()
                                }
                                preSnapSize = Dimension(window.width, window.height)
                                window.setBounds(usableLeft + usableWidth / 2, usableTop, usableWidth / 2, usableHeight)
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
