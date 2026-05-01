package com.wip.kpm_cpm_wotoolkit.features.colorpicker.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wip.kpm_cpm_wotoolkit.features.colorpicker.model.Colors
import com.wip.kpm_cpm_wotoolkit.features.colorpicker.utils.fromHueProgress

/**
 * A horizontal color slide bar that reports progress [0f..1f] via [onValueChange].
 * Used internally by all color picker variants for hue, brightness, and alpha bars.
 * Refactored to use Material 3 Slider with adaptive/expressive design.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ColorSlideBar(
    value: Float = 1f,
    onValueChange: (Float) -> Unit,
    colors: List<Color>,
) {

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isSelected = isPressed || isDragged

    val animatedThumbWidth by animateDpAsState(
        targetValue = if (isSelected) 2.dp else 4.dp,
        label = "ThumbWidth"
    )

    val activeTrackHeight = 16.dp
    val inactiveTrackHeight = 16.dp
    val handleLeadingSpace = 6.dp
    val handleTrailingSpace = 6.dp
    val handleHeight = 44.dp
    val innerCornerRadius = 4.dp

    LaunchedEffect(value) {
        onValueChange(value)
    }

    Slider(
        value = value,
        onValueChange = onValueChange,
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .height(handleHeight),
        thumb = {
            val thumbColor = if (colors == Colors.gradientColors) {
                Color.fromHueProgress(value)
            } else {
                if (colors.size >= 2) {
                    val lerpProgress = value * (colors.size - 1)
                    val index = lerpProgress.toInt().coerceIn(0, colors.size - 2)
                    val localProgress = lerpProgress - index
                    lerpColor(colors[index], colors[index + 1], localProgress)
                } else {
                    Color.White
                }
            }

            // Material Expressive Vertical Bar Thumb (Animated Width)
            Box(
                modifier = Modifier
                    .size(width = animatedThumbWidth, height = handleHeight)
                    .clip(CircleShape)
                    .background(thumbColor)
            )
        },
        track = { sliderState ->
            val brush = Brush.horizontalGradient(colors)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(handleHeight),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                androidx.compose.foundation.Canvas(
                    modifier = Modifier.fillMaxWidth().height(activeTrackHeight)
                ) {
                    val totalWidth = size.width
                    val thumbPos = totalWidth * value

                    // Use animated width for gap calculation
                    val currentThumbWidthPx = animatedThumbWidth.toPx()
                    val gapStart = (thumbPos - (currentThumbWidthPx / 2 + handleLeadingSpace.toPx())).coerceAtLeast(0f)
                    val gapEnd =
                        (thumbPos + (currentThumbWidthPx / 2 + handleTrailingSpace.toPx())).coerceAtMost(totalWidth)

                    // 1. Draw Active Segment (Left) - Asymmetric Rounding
                    if (gapStart > 0) {
                        val activePath = Path().apply {
                            val rect = Rect(0f, 0f, gapStart, activeTrackHeight.toPx())
                            val r = activeTrackHeight.toPx() / 2
                            val ir = innerCornerRadius.toPx()
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

                    // 2. Draw Inactive Segment (Right) - Differentiated (Smaller) and Asymmetric
                    if (gapEnd < totalWidth) {
                        val inactiveHeightPx = inactiveTrackHeight.toPx()
                        val verticalOffset = (activeTrackHeight.toPx() - inactiveHeightPx) / 2
                        val inactivePath = Path().apply {
                            val rect = Rect(gapEnd, verticalOffset, totalWidth, verticalOffset + inactiveHeightPx)
                            val r = inactiveHeightPx / 2
                            val ir = innerCornerRadius.toPx()
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
                        drawPath(path = inactivePath, brush = brush, alpha = 1f)
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

@Preview
@Composable
private fun ColorSlideBarPreview() {
    var progress by remember { mutableStateOf(.7f) }

    MaterialTheme {
        Column(
            modifier = Modifier.padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text("Material 3 Expressive Color Slider", style = MaterialTheme.typography.titleMedium)

            ColorSlideBar(
                value = progress,
                onValueChange = { progress = it },
                colors = Colors.gradientColors
            )

            Text("Alpha / Opacity Bar", style = MaterialTheme.typography.titleMedium)

            ColorSlideBar(
                value = progress,
                onValueChange = { progress = it },
                colors = listOf(Color.Transparent, Color.Blue)
            )
        }
    }
}

