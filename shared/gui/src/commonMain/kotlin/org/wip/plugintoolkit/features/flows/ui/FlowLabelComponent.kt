package org.wip.plugintoolkit.features.flows.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.colorpicker.ui.ColorPickerDialog
import org.wip.plugintoolkit.features.colorpicker.utils.toHex
import org.wip.plugintoolkit.features.flows.model.FlowLabel
import org.wip.plugintoolkit.shared.components.plugin.inputs.parseColorString
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.flow_label_color
import plugintoolkit.composeapp.generated.resources.flow_label_default_text
import plugintoolkit.composeapp.generated.resources.flow_label_delete
import kotlin.math.roundToInt

private const val LABEL_CORNER_RADIUS_DP = 12
private const val LABEL_BACKGROUND_ALPHA = 0.12f
private const val LABEL_BORDER_ALPHA = 0.6f

@Composable
fun FlowLabelComponent(
    label: FlowLabel,
    stateScale: Float,
    stateOffset: Offset,
    isReadOnly: Boolean,
    onUpdateLabel: (FlowLabel) -> Unit,
    onDeleteLabel: (FlowLabel) -> Unit,
    onDragDelta: (Offset) -> Unit,
    isSelected: Boolean = false,
    isPaintToolActive: Boolean = false,
    isWashToolActive: Boolean = false,
    isEyedropperActive: Boolean = false,
    onPaintLabel: ((Long) -> Unit)? = null,
    onWashLabel: ((Long) -> Unit)? = null,
    onSampleColor: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val dimensions = ToolkitTheme.dimensions
    val spacing = ToolkitTheme.spacing

    var showColorPicker by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    var textValue by remember(label.text) { mutableStateOf(label.text) }

    val lblColor = label.color
    val baseColor = if (!lblColor.isNullOrBlank()) {
        parseColorString(lblColor)
    } else {
        MaterialTheme.colorScheme.primary
    }

    val cornerShape = RoundedCornerShape((LABEL_CORNER_RADIUS_DP * stateScale).coerceAtLeast(6f).dp)
    val textColor = MaterialTheme.colorScheme.onSurface

    val screenPos = (label.position.toComposeOffset() * stateScale) + stateOffset

    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        baseColor.copy(alpha = LABEL_BORDER_ALPHA)
    }
    val borderWidth = if (isSelected) {
        dimensions.progressIndicatorStroke
    } else {
        (dimensions.borderUnselected * stateScale).coerceAtLeast(1.dp)
    }

    Box(
        modifier = modifier
            .offset { IntOffset(screenPos.x.roundToInt(), screenPos.y.roundToInt()) }
            .background(
                color = baseColor.copy(alpha = LABEL_BACKGROUND_ALPHA),
                shape = cornerShape
            )
            .border(
                BorderStroke(
                    width = borderWidth,
                    color = borderColor
                ),
                shape = cornerShape
            )
            .padding(horizontal = spacing.small, vertical = spacing.extraSmall)
            .pointerInput(label.id, isReadOnly, isPaintToolActive, isWashToolActive, isEyedropperActive) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Press && event.buttons.isPrimaryPressed) {
                            val isShift = event.keyboardModifiers.isShiftPressed
                            if (isEyedropperActive && onSampleColor != null) {
                                onSampleColor(label.color ?: "#FFFFFF")
                                event.changes.forEach { it.consume() }
                            } else if (isPaintToolActive && onPaintLabel != null) {
                                onPaintLabel(label.id)
                                event.changes.forEach { it.consume() }
                            } else if (isWashToolActive && onWashLabel != null) {
                                onWashLabel(label.id)
                                event.changes.forEach { it.consume() }
                            } else if (isShift && !isReadOnly) {
                                isEditing = true
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
            }
            .pointerInput(label.id, isReadOnly, isPaintToolActive, isWashToolActive, isEyedropperActive, stateScale) {
                if (!isReadOnly && !isPaintToolActive && !isWashToolActive && !isEyedropperActive) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDragDelta(dragAmount / stateScale)
                    }
                }
            }
            .testTag("flow_label_${label.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)
        ) {
            if (isEditing && !isReadOnly) {
                BasicTextField(
                    value = textValue,
                    onValueChange = {
                        textValue = it
                        onUpdateLabel(label.copy(text = it))
                    },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = textColor,
                        fontSize = label.fontSize.sp
                    ),
                    cursorBrush = SolidColor(textColor),
                    singleLine = false,
                    modifier = Modifier
                        .testTag("flow_label_field_${label.id}")
                        .onFocusChanged { if (!it.isFocused) isEditing = false }
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Enter) {
                                if (keyEvent.isShiftPressed) {
                                    false
                                } else {
                                    isEditing = false
                                    true
                                }
                            } else {
                                false
                            }
                        }
                )

                IconButton(
                    onClick = { isEditing = false },
                    modifier = Modifier.size(dimensions.iconSmall)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = baseColor,
                        modifier = Modifier.size(dimensions.iconSmall)
                    )
                }
            } else {
                Text(
                    text = label.text.ifBlank { stringResource(Res.string.flow_label_default_text) },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = textColor,
                        fontSize = label.fontSize.sp
                    )
                )
            }

            if (!isReadOnly && !isEditing) {
                IconButton(
                    onClick = { showColorPicker = true },
                    modifier = Modifier.size(dimensions.iconSmall)
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = stringResource(Res.string.flow_label_color),
                        tint = baseColor,
                        modifier = Modifier.size(dimensions.iconExtraSmall)
                    )
                }

                IconButton(
                    onClick = { onDeleteLabel(label) },
                    modifier = Modifier.size(dimensions.iconSmall)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(Res.string.flow_label_delete),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(dimensions.iconExtraSmall)
                    )
                }
            }
        }
    }

    if (showColorPicker) {
        ColorPickerDialog(
            show = showColorPicker,
            onDismissRequest = { showColorPicker = false },
            onPickedColor = { pickedColor ->
                val hex = pickedColor.toHex(hexPrefix = true, includeAlpha = false)
                onUpdateLabel(label.copy(color = hex))
                showColorPicker = false
            }
        )
    }
}
