package org.wip.plugintoolkit.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect

/**
 * A reusable Composable wrapper that shows a custom tooltip when the content is hovered.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Deprecated(
    message = "Use Modifier.tooltip instead",
    replaceWith = ReplaceWith("Modifier.tooltip(textResource, delay)"),
    level = DeprecationLevel.WARNING
)
@Composable
fun TooltipArea(
    tooltip: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    delayMillis: Int = 0,
    offsetY: Int = 30,
    content: @Composable () -> Unit
) {
    var isHovered by remember { mutableStateOf(false) }
    var isTooltipVisible by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(isHovered) {
        if (isHovered) {
            if (delayMillis > 0) {
                kotlinx.coroutines.delay(delayMillis.toLong().milliseconds)
            }
            isTooltipVisible = true
        } else {
            isTooltipVisible = false
        }
    }

    Box(
        modifier = modifier
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false }
    ) {
        content()

        if (isTooltipVisible) {
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(0, offsetY), // Position relative to the hovered component
                properties = PopupProperties(
                    focusable = false,
                    dismissOnClickOutside = true,
                    dismissOnBackPress = true
                )
            ) {
                Box(
                    modifier = Modifier
                        .shadow(ToolkitTheme.spacing.small, RoundedCornerShape(ToolkitTheme.spacing.extraSmall))
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(ToolkitTheme.spacing.extraSmall)
                        )
                        .border(
                            ToolkitTheme.dimensions.borderUnselected,
                            MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(ToolkitTheme.spacing.extraSmall)
                        )
                        .padding(horizontal = ToolkitTheme.spacing.small, vertical = ToolkitTheme.spacing.extraSmall)
                ) {
                    tooltip()
                }
            }
        }
    }
}

/**
 * A modifier extension that displays a text tooltip when hovering over the component.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.tooltip(textResource: StringResource, delay: Duration = Duration.ZERO): Modifier = composed {
    val text = stringResource(textResource)
    tooltipImpl(text, delay)
}

/**
 * A modifier extension that displays a text tooltip when hovering over the component.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Deprecated(
    message = "Use Modifier.tooltip(StringResource) instead",
    replaceWith = ReplaceWith("Modifier.tooltip(textResource, delay)"),
    level = DeprecationLevel.WARNING
)
fun Modifier.tooltip(text: String, delay: Duration = Duration.ZERO): Modifier = composed {
    tooltipImpl(text, delay)
}

data class TooltipData(
    val text: String,
    val coordinates: LayoutCoordinates
)

class TooltipState {
    var tooltipData by mutableStateOf<TooltipData?>(null)
}

val LocalTooltipState = staticCompositionLocalOf<TooltipState?> { null }

@Composable
fun TooltipProvider(content: @Composable () -> Unit) {
    val tooltipState = remember { TooltipState() }
    
    CompositionLocalProvider(LocalTooltipState provides tooltipState) {
        content()
        
        tooltipState.tooltipData?.let { data ->
            Popup(
                popupPositionProvider = object : PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: IntRect,
                        windowSize: IntSize,
                        layoutDirection: LayoutDirection,
                        popupContentSize: IntSize
                    ): IntOffset {
                        val position = data.coordinates.positionInWindow()
                        val size = data.coordinates.size
                        
                        val x = (position.x + size.width / 2f - popupContentSize.width / 2f).toInt()
                        val y = (position.y - popupContentSize.height - 8).toInt() // 8px offset
                        
                        val adjustedX = x.coerceIn(0, maxOf(0, windowSize.width - popupContentSize.width))
                        val adjustedY = y.coerceIn(0, maxOf(0, windowSize.height - popupContentSize.height))
                        
                        return IntOffset(adjustedX, adjustedY)
                    }
                },
                properties = PopupProperties(
                    focusable = false,
                    dismissOnClickOutside = true,
                    dismissOnBackPress = true
                )
            ) {
                Box(

                    modifier = Modifier
                        .shadow(4.dp, ToolkitTheme.shapes.extraSmall)
                        .background(MaterialTheme.colorScheme.surfaceVariant, ToolkitTheme.shapes.extraSmall)
                        .border(ToolkitTheme.dimensions.borderUnselected, MaterialTheme.colorScheme.outlineVariant, ToolkitTheme.shapes.extraSmall)
                        .padding(horizontal = ToolkitTheme.spacing.small, vertical = ToolkitTheme.spacing.extraSmall)
                ) {
                    Text(
                        text = data.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Modifier.tooltipImpl(text: String, delay: Duration): Modifier {
    val tooltipState = LocalTooltipState.current
    var isHovered by remember { mutableStateOf(false) }
    var layoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    LaunchedEffect(isHovered, tooltipState) {
        if (isHovered) {
            if (delay > Duration.ZERO) {
                delay(delay)
            }
            if (layoutCoordinates != null) {
                tooltipState?.tooltipData = TooltipData(text, layoutCoordinates!!)
            }
        } else {
            if (tooltipState?.tooltipData?.text == text && tooltipState.tooltipData?.coordinates == layoutCoordinates) {
                tooltipState?.tooltipData = null
            }
        }
    }

    DisposableEffect(text, layoutCoordinates, tooltipState) {
        onDispose {
            if (tooltipState?.tooltipData?.text == text && tooltipState.tooltipData?.coordinates == layoutCoordinates) {
                tooltipState?.tooltipData = null
            }
        }
    }

    return this
        .onGloballyPositioned { layoutCoordinates = it }
        .onPointerEvent(PointerEventType.Enter) { isHovered = true }
        .onPointerEvent(PointerEventType.Exit) { isHovered = false }
}

