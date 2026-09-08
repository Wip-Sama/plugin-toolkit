package org.wip.plugintoolkit.features.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.round
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.shared.components.ToolkitTextField
import org.wip.plugintoolkit.shared.components.tooltip
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.scaling_decrease
import plugintoolkit.composeapp.generated.resources.scaling_decrease_double
import plugintoolkit.composeapp.generated.resources.scaling_increase
import plugintoolkit.composeapp.generated.resources.scaling_increase_double

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ScalingControl(
    scaling: Float,
    onScalingChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var isShiftPressed by remember { mutableStateOf(false) }

    val step = if (isShiftPressed) 0.10f else 0.05f

    val canDecrease = enabled && scaling > 0.501f
    val canIncrease = enabled && scaling < 1.999f

    val decreaseTooltip = if (isShiftPressed) {
        stringResource(Res.string.scaling_decrease_double)
    } else {
        stringResource(Res.string.scaling_decrease)
    }

    val increaseTooltip = if (isShiftPressed) {
        stringResource(Res.string.scaling_increase_double)
    } else {
        stringResource(Res.string.scaling_increase)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small),
        modifier = modifier
    ) {
        FilledTonalIconButton(
            onClick = {
                val next = (round((scaling - step) * 20f) / 20f).coerceIn(0.5f, 2.0f)
                onScalingChange(next)
            },
            enabled = canDecrease,
            modifier = Modifier
                .size(ToolkitTheme.dimensions.pluginIcon)
                .onPointerEvent(PointerEventType.Press) {
                    isShiftPressed = it.keyboardModifiers.isShiftPressed
                }
                .onPointerEvent(PointerEventType.Move) {
                    isShiftPressed = it.keyboardModifiers.isShiftPressed
                }
                .tooltip(decreaseTooltip)
        ) {
            Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = decreaseTooltip,
                modifier = Modifier.size(ToolkitTheme.dimensions.iconMedium)
            )
        }

        ToolkitTextField(
            value = "${(scaling * 100).roundToInt()}%",
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center),
            modifier = Modifier
                .width(ToolkitTheme.dimensions.genericInputWidth)
                .height(ToolkitTheme.dimensions.pluginIcon)
        )

        FilledTonalIconButton(
            onClick = {
                val next = (round((scaling + step) * 20f) / 20f).coerceIn(0.5f, 2.0f)
                onScalingChange(next)
            },
            enabled = canIncrease,
            modifier = Modifier
                .size(ToolkitTheme.dimensions.pluginIcon)
                .onPointerEvent(PointerEventType.Press) {
                    isShiftPressed = it.keyboardModifiers.isShiftPressed
                }
                .onPointerEvent(PointerEventType.Move) {
                    isShiftPressed = it.keyboardModifiers.isShiftPressed
                }
                .tooltip(increaseTooltip)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = increaseTooltip,
                modifier = Modifier.size(ToolkitTheme.dimensions.iconMedium)
            )
        }
    }
}
