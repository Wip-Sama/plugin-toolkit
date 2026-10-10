package org.wip.plugintoolkit.features.colorpicker.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.colorpicker.model.Colors
import org.wip.plugintoolkit.features.colorpicker.utils.fromHueProgress

/**
 * A horizontal color slide bar that reports progress [0f..1f] via [onValueChange].
 * Used by all color picker variants for hue, brightness, and alpha bars.
 * Restored to Material 3 Expressive slider specification with vertical pill thumb indicator.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ColorSlideBar(
    value: Float = 1f,
    onValueChange: (Float) -> Unit,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    showCheckerboard: Boolean = false,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isSelected = isPressed || isDragged

    val animatedThumbWidth by animateDpAsState(
        targetValue = if (isSelected) ToolkitTheme.dimensions.strokeWidthThin else ToolkitTheme.dimensions.widthSmallExtra,
        label = "ThumbWidth"
    )

    val activeTrackHeight = ToolkitTheme.dimensions.colorPickerTrackHeight
    val handleLeadingSpace = ToolkitTheme.spacing.badgeHorizontal
    val handleTrailingSpace = ToolkitTheme.spacing.badgeHorizontal
    val handleHeight = ToolkitTheme.dimensions.colorPickerThumbSize + ToolkitTheme.spacing.extraSmall
    val innerCornerRadius = ToolkitTheme.spacing.extraSmall

    val thumbColor = remember(value, colors) {
        if (colors == Colors.gradientColors) {
            Color.fromHueProgress(value)
        } else if (colors.size >= 2) {
            val lerpProgress = value * (colors.size - 1)
            val index = lerpProgress.toInt().coerceIn(0, colors.size - 2)
            val localProgress = lerpProgress - index
            lerpColor(colors[index], colors[index + 1], localProgress)
        } else {
            Color.White
        }
    }

    Slider(
        value = value.coerceIn(0f, 1f),
        onValueChange = onValueChange,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .height(handleHeight),
        thumb = {
            // Material Expressive Vertical Pill/Bar Thumb
            Box(
                modifier = Modifier
                    .size(width = animatedThumbWidth, height = handleHeight)
                    .clip(CircleShape)
                    .background(thumbColor)
            )
        },
        track = {
            val brush = Brush.horizontalGradient(colors)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(handleHeight),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(activeTrackHeight)
                ) {
                    val totalWidth = size.width
                    val thumbPos = totalWidth * value.coerceIn(0f, 1f)
                    val currentThumbWidthPx = animatedThumbWidth.toPx()
                    val gapStart = (thumbPos - (currentThumbWidthPx / 2 + handleLeadingSpace.toPx())).coerceAtLeast(0f)
                    val gapEnd = (thumbPos + (currentThumbWidthPx / 2 + handleTrailingSpace.toPx())).coerceAtMost(totalWidth)
                    val r = activeTrackHeight.toPx() / 2f
                    val ir = innerCornerRadius.toPx()

                    // Active Segment (Left) - Asymmetric Rounding
                    if (gapStart > 0) {
                        val activePath = Path().apply {
                            val rect = Rect(0f, 0f, gapStart, activeTrackHeight.toPx())
                            addRoundRect(
                                RoundRect(
                                    rect = rect,
                                    topLeft = CornerRadius(r, r),
                                    bottomLeft = CornerRadius(r, r),
                                    topRight = CornerRadius(ir, ir),
                                    bottomRight = CornerRadius(ir, ir)
                                )
                            )
                        }
                        drawPath(path = activePath, brush = brush)
                    }

                    // Inactive Segment (Right) - Asymmetric Rounding
                    if (gapEnd < totalWidth) {
                        val inactivePath = Path().apply {
                            val rect = Rect(gapEnd, 0f, totalWidth, activeTrackHeight.toPx())
                            addRoundRect(
                                RoundRect(
                                    rect = rect,
                                    topLeft = CornerRadius(ir, ir),
                                    bottomLeft = CornerRadius(ir, ir),
                                    topRight = CornerRadius(r, r),
                                    bottomRight = CornerRadius(r, r)
                                )
                            )
                        }
                        drawPath(path = inactivePath, brush = brush)
                    }
                }
            }
        }
    )
}

/**
 * Linear interpolation between two colors.
 */
private fun lerpColor(start: Color, end: Color, fraction: Float): Color {
    return Color(
        red = start.red + (end.red - start.red) * fraction,
        green = start.green + (end.green - start.green) * fraction,
        blue = start.blue + (end.blue - start.blue) * fraction,
        alpha = start.alpha + (end.alpha - start.alpha) * fraction
    )
}
