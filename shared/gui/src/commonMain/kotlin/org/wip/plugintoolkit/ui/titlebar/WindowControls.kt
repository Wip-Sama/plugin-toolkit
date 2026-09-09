package org.wip.plugintoolkit.ui.titlebar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.core.utils.PlatformUtils
import org.wip.plugintoolkit.shared.components.tooltip
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.action_close
import plugintoolkit.composeapp.generated.resources.action_maximize
import plugintoolkit.composeapp.generated.resources.action_minimize
import plugintoolkit.composeapp.generated.resources.action_restore
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val MAC_CLOSE_COLOR = Color(0xFFFF5F56)
private val MAC_MINIMIZE_COLOR = Color(0xFFFFBD2E)
private val MAC_MAXIMIZE_COLOR = Color(0xFF27C93F)
private val MAC_BORDER_COLOR = Color(0x33000000)
private val WINDOWS_CLOSE_HOVER = Color(0xFFE81123)
private val WINDOWS_CLOSE_PRESSED = Color(0xFFC4101F)

/**
 * Window control buttons (Minimize, Maximize/Restore, Close).
 * Adapts to host operating system style (macOS traffic lights vs Windows/Linux caption buttons).
 */
@Composable
fun WindowControls(
    controller: WindowController,
    modifier: Modifier = Modifier,
    isMac: Boolean = PlatformUtils.isMac
) {
    if (isMac) {
        MacWindowControls(controller = controller, modifier = modifier)
    } else {
        WindowsWindowControls(controller = controller, modifier = modifier)
    }
}

/**
 * macOS style traffic light window controls.
 */
@Composable
fun MacWindowControls(
    controller: WindowController,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isRowHovered by interactionSource.collectIsHoveredAsState()
    val controlSize = ToolkitTheme.dimensions.windowControlMacSize
    val gap = ToolkitTheme.dimensions.windowControlMacGap

    Row(
        modifier = modifier.hoverable(interactionSource),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Close (Red)
        Box(
            modifier = Modifier
                .size(controlSize)
                .clip(CircleShape)
                .background(MAC_CLOSE_COLOR)
                .border(ToolkitTheme.dimensions.borderThin, MAC_BORDER_COLOR, CircleShape)
                .tooltip(
                    Res.string.action_close,
                    delay = 2.seconds
                )
                .clickable(onClick = controller.onClose),
            contentAlignment = Alignment.Center
        ) {
            if (isRowHovered) {
                Canvas(modifier = Modifier.size(6.dp)) {
                    drawLine(
                        color = Color(0xFF4C0000),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.2f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color(0xFF4C0000),
                        start = Offset(size.width, 0f),
                        end = Offset(0f, size.height),
                        strokeWidth = 1.2f,
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        // Minimize (Yellow)
        Box(
            modifier = Modifier
                .size(controlSize)
                .clip(CircleShape)
                .background(MAC_MINIMIZE_COLOR)
                .border(ToolkitTheme.dimensions.borderThin, MAC_BORDER_COLOR, CircleShape)
                .tooltip(
                    Res.string.action_minimize,
                    delay = 2.seconds
                )
                .clickable(onClick = controller.onMinimize),
            contentAlignment = Alignment.Center
        ) {
            if (isRowHovered) {
                Canvas(modifier = Modifier.size(6.dp)) {
                    drawLine(
                        color = Color(0xFF5A3E00),
                        start = Offset(0f, size.height / 2),
                        end = Offset(size.width, size.height / 2),
                        strokeWidth = 1.2f,
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        // Maximize (Green)
        val maxTooltip = if (controller.isMaximized) {
            Res.string.action_restore
        } else {
            Res.string.action_maximize
        }
        Box(
            modifier = Modifier
                .size(controlSize)
                .clip(CircleShape)
                .background(MAC_MAXIMIZE_COLOR)
                .border(ToolkitTheme.dimensions.borderThin, MAC_BORDER_COLOR, CircleShape)
                .tooltip(
                    maxTooltip,
                    delay = 2.seconds
                )
                .clickable(onClick = controller.onMaximizeToggle),
            contentAlignment = Alignment.Center
        ) {
            if (isRowHovered) {
                Canvas(modifier = Modifier.size(6.dp)) {
                    drawLine(
                        color = Color(0xFF0D5316),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.2f,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

/**
 * Windows / Linux style caption buttons.
 */
@Composable
fun WindowsWindowControls(
    controller: WindowController,
    modifier: Modifier = Modifier
) {
    val buttonWidth = ToolkitTheme.dimensions.windowControlWidth
    val buttonHeight = ToolkitTheme.dimensions.windowControlHeight

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Minimize Button
        StandardWindowControlButton(
            tooltip = Res.string.action_minimize,
            width = buttonWidth,
            height = buttonHeight,
            onClick = controller.onMinimize
        ) { iconColor ->
            Canvas(modifier = Modifier.size(10.dp)) {
                drawLine(
                    color = iconColor,
                    start = Offset(0f, size.height / 2),
                    end = Offset(size.width, size.height / 2),
                    strokeWidth = 1.5f,
                    cap = StrokeCap.Square
                )
            }
        }

        // Maximize / Restore Button
        val maxTooltip = if (controller.isMaximized) {
            Res.string.action_restore
        } else {
            Res.string.action_maximize
        }
        StandardWindowControlButton(
            tooltip = maxTooltip,
            width = buttonWidth,
            height = buttonHeight,
            onClick = controller.onMaximizeToggle
        ) { iconColor ->
            Canvas(modifier = Modifier.size(10.dp)) {
                if (controller.isMaximized) {
                    // Overlapping squares for Restore
                    val offset = 2.dp.toPx()
                    val squareSize = size.width - offset
                    // Back square
                    drawRect(
                        color = iconColor,
                        topLeft = Offset(offset, 0f),
                        size = Size(squareSize, squareSize),
                        style = Stroke(width = 1.2f)
                    )
                    // Front square
                    drawRect(
                        color = iconColor,
                        topLeft = Offset(0f, offset),
                        size = Size(squareSize, squareSize),
                        style = Stroke(width = 1.2f)
                    )
                } else {
                    // Single square for Maximize
                    drawRect(
                        color = iconColor,
                        topLeft = Offset.Zero,
                        size = size,
                        style = Stroke(width = 1.2f)
                    )
                }
            }
        }

        // Close Button (Special Windows Red hover)
        CloseWindowControlButton(
            tooltip = Res.string.action_close,
            width = buttonWidth,
            height = buttonHeight,
            onClick = controller.onClose
        )
    }
}

@Composable
private fun StandardWindowControlButton(
    tooltip: StringResource,
    width: Dp,
    height: Dp,
    onClick: () -> Unit,
    content: @Composable (iconColor: Color) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val defaultBg = Color.Transparent
    val hoverBg = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val pressedBg = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)

    val bgColor by animateColorAsState(
        targetValue = when {
            isPressed -> pressedBg
            isHovered -> hoverBg
            else -> defaultBg
        },
        animationSpec = tween(durationMillis = 100)
    )

    val iconColor = MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .background(bgColor)
            .tooltip(
                tooltip,
                delay = 2.seconds
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content(iconColor)
    }
}

@Composable
private fun CloseWindowControlButton(
    tooltip: StringResource,
    width: Dp,
    height: Dp,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val bgColor by animateColorAsState(
        targetValue = when {
            isPressed -> WINDOWS_CLOSE_PRESSED
            isHovered -> WINDOWS_CLOSE_HOVER
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 100)
    )

    val iconColor = if (isHovered || isPressed) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .background(bgColor)
            .tooltip(
                tooltip,
                delay = 2.seconds
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(10.dp)) {
            drawLine(
                color = iconColor,
                start = Offset.Zero,
                end = Offset(size.width, size.height),
                strokeWidth = 1.3f,
                cap = StrokeCap.Square
            )
            drawLine(
                color = iconColor,
                start = Offset(size.width, 0f),
                end = Offset(0f, size.height),
                strokeWidth = 1.3f,
                cap = StrokeCap.Square
            )
        }
    }
}
