package org.wip.plugintoolkit.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.composed
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import kotlin.time.Duration.Companion.milliseconds

/**
 * A reusable Composable wrapper that shows a custom tooltip when the content is hovered.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TooltipArea(
    tooltip: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    delayMillis: Int = 0,
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
                offset = IntOffset(0, -60), // Position slightly above the hovered component
                properties = PopupProperties(
                    focusable = false,
                    dismissOnClickOutside = true,
                    dismissOnBackPress = true
                )
            ) {
                Box(
                    modifier = Modifier
                        .shadow(ToolkitTheme.spacing.small, RoundedCornerShape(ToolkitTheme.spacing.extraSmall))
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(ToolkitTheme.spacing.extraSmall))
                        .border(ToolkitTheme.dimensions.borderUnselected, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(ToolkitTheme.spacing.extraSmall))
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
fun Modifier.tooltip(text: String): Modifier = composed {
    var isHovered by remember { mutableStateOf(false) }

    if (isHovered) {
        Popup(
            alignment = Alignment.TopCenter,
            offset = IntOffset(0, -60),
            properties = PopupProperties(
                focusable = false,
                dismissOnClickOutside = true,
                dismissOnBackPress = true
            )
        ) {
            Box(
                modifier = Modifier
                    .shadow(4.dp, RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    this
        .onPointerEvent(PointerEventType.Enter) { isHovered = true }
        .onPointerEvent(PointerEventType.Exit) { isHovered = false }
}

