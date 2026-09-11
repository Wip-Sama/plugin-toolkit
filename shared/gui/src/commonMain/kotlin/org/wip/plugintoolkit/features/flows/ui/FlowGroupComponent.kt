package org.wip.plugintoolkit.features.flows.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.core.utils.PlatformUtils
import org.wip.plugintoolkit.features.colorpicker.ui.ColorPickerDialog
import org.wip.plugintoolkit.features.colorpicker.utils.toHex
import org.wip.plugintoolkit.features.flows.model.FlowGroup
import org.wip.plugintoolkit.shared.components.plugin.inputs.parseColorString
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.flow_group_collapse
import plugintoolkit.composeapp.generated.resources.flow_group_color
import plugintoolkit.composeapp.generated.resources.flow_group_default_title
import plugintoolkit.composeapp.generated.resources.flow_group_delete
import plugintoolkit.composeapp.generated.resources.flow_group_expand
import plugintoolkit.composeapp.generated.resources.flow_group_nodes_count
import kotlin.math.roundToInt

private const val GROUP_CORNER_RADIUS_DP = 16
private const val GROUP_COLLAPSED_HEIGHT_DP = 44
private const val GROUP_MIN_COLLAPSED_WIDTH_DP = 200
private const val GROUP_BACKGROUND_ALPHA = 0.12f
private const val GROUP_BORDER_ALPHA = 0.6f
private const val RESIZE_HANDLE_THICKNESS_DP = 10
private const val RESIZE_CORNER_SIZE_DP = 18

@Composable
fun FlowGroupComponent(
    group: FlowGroup,
    stateScale: Float,
    stateOffset: Offset,
    isReadOnly: Boolean,
    onUpdateGroup: (FlowGroup) -> Unit,
    onDeleteGroup: (FlowGroup) -> Unit,
    onDragDelta: (Offset) -> Unit,
    isSelected: Boolean = false,
    isDropTarget: Boolean = false,
    hasIncomingConnections: Boolean = false,
    hasOutgoingConnections: Boolean = false,
    isPaintToolActive: Boolean = false,
    isWashToolActive: Boolean = false,
    isEyedropperActive: Boolean = false,
    onPaintGroup: ((Long) -> Unit)? = null,
    onWashGroup: ((Long) -> Unit)? = null,
    onSampleColor: ((String) -> Unit)? = null,
    onResizeGroup: ((Long, Offset) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val dimensions = ToolkitTheme.dimensions
    val spacing = ToolkitTheme.spacing
    val density = LocalDensity.current.density
    var showColorPicker by remember { mutableStateOf(false) }
    var isEditingTitle by remember { mutableStateOf(false) }
    var titleText by remember(group.title) { mutableStateOf(group.title) }

    val grpColor = group.color
    val baseColor = if (!grpColor.isNullOrBlank()) {
        parseColorString(grpColor)
    } else {
        MaterialTheme.colorScheme.primary
    }

    val screenPos = (group.position.toComposeOffset() * stateScale) + stateOffset
    val groupWidthPx = if (group.isCollapsed) {
        maxOf(group.size.x * stateScale, GROUP_MIN_COLLAPSED_WIDTH_DP * density)
    } else {
        group.size.x * stateScale
    }
    val groupHeightPx = if (group.isCollapsed) {
        GROUP_COLLAPSED_HEIGHT_DP * density
    } else {
        group.size.y * stateScale
    }
    val groupWidthDp = with(LocalDensity.current) { groupWidthPx.toDp() }
    val groupHeightDp = with(LocalDensity.current) { groupHeightPx.toDp() }
    val cornerShape = RoundedCornerShape((GROUP_CORNER_RADIUS_DP * stateScale).coerceAtLeast(8f).dp)

    val borderColor = when {
        isDropTarget -> MaterialTheme.colorScheme.primary
        isSelected -> MaterialTheme.colorScheme.primary
        else -> baseColor.copy(alpha = GROUP_BORDER_ALPHA)
    }
    val borderWidth = when {
        isDropTarget -> dimensions.progressIndicatorStroke * 1.5f
        isSelected -> dimensions.progressIndicatorStroke
        else -> (dimensions.borderUnselected * stateScale).coerceAtLeast(1.dp)
    }

    Box(
        modifier = modifier
            .offset { IntOffset(screenPos.x.roundToInt(), screenPos.y.roundToInt()) }
            .width(groupWidthDp)
            .height(groupHeightDp)
            .background(baseColor.copy(alpha = GROUP_BACKGROUND_ALPHA), cornerShape)
            .border(
                BorderStroke(
                    width = borderWidth,
                    color = borderColor
                ),
                shape = cornerShape
            )
            .pointerInput(group.id, isReadOnly, isPaintToolActive, isWashToolActive, isEyedropperActive) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Press && event.buttons.isPrimaryPressed) {
                            val isShift = event.keyboardModifiers.isShiftPressed
                            if (isEyedropperActive && onSampleColor != null) {
                                onSampleColor(group.color ?: "#4CAF50")
                                event.changes.forEach { it.consume() }
                            } else if (isPaintToolActive && onPaintGroup != null) {
                                onPaintGroup(group.id)
                                event.changes.forEach { it.consume() }
                            } else if (isWashToolActive && onWashGroup != null) {
                                onWashGroup(group.id)
                                event.changes.forEach { it.consume() }
                            } else if (isShift && !isReadOnly) {
                                isEditingTitle = true
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
            }
            .pointerInput(group.id, isReadOnly, isPaintToolActive, isWashToolActive, isEyedropperActive, stateScale) {
                if (!isReadOnly && !isPaintToolActive && !isWashToolActive && !isEyedropperActive) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDragDelta(dragAmount / stateScale)
                    }
                }
            }
            .testTag("flow_group_${group.id}")
    ) {
        // Group Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.small, vertical = spacing.extraSmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                if (!isReadOnly) {
                    IconButton(
                        onClick = {
                            onUpdateGroup(group.copy(isCollapsed = !group.isCollapsed))
                        },
                        modifier = Modifier.size(dimensions.iconMedium)
                    ) {
                        Icon(
                            imageVector = if (group.isCollapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                            contentDescription = if (group.isCollapsed) {
                                stringResource(Res.string.flow_group_expand)
                            } else {
                                stringResource(Res.string.flow_group_collapse)
                            },
                            tint = baseColor,
                            modifier = Modifier.size(dimensions.iconSmall)
                        )
                    }
                }

                if (isEditingTitle && !isReadOnly) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)
                    ) {
                        BasicTextField(
                            value = titleText,
                            onValueChange = {
                                titleText = it
                                onUpdateGroup(group.copy(title = it))
                            },
                            textStyle = MaterialTheme.typography.titleMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp
                            ),
                            cursorBrush = SolidColor(baseColor),
                            singleLine = false,
                            modifier = Modifier
                                .testTag("flow_group_title_field_${group.id}")
                                .onFocusChanged { if (!it.isFocused) isEditingTitle = false }
                                .onKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Enter) {
                                        if (keyEvent.isShiftPressed) {
                                            false
                                        } else {
                                            isEditingTitle = false
                                            true
                                        }
                                    } else {
                                        false
                                    }
                                }
                        )
                        IconButton(
                            onClick = { isEditingTitle = false },
                            modifier = Modifier.size(dimensions.iconSmall)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = baseColor,
                                modifier = Modifier.size(dimensions.iconSmall)
                            )
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)
                    ) {
                        Text(
                            text = group.title.ifBlank { stringResource(Res.string.flow_group_default_title) },
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (group.isCollapsed) {
                            Text(
                                text = "(${stringResource(Res.string.flow_group_nodes_count, group.nodeIds.size)})",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (!isReadOnly) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)
                ) {
                    IconButton(
                        onClick = { showColorPicker = true },
                        modifier = Modifier.size(dimensions.iconMedium)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = stringResource(Res.string.flow_group_color),
                            tint = baseColor,
                            modifier = Modifier.size(dimensions.iconSmall)
                        )
                    }

                    IconButton(
                        onClick = { onDeleteGroup(group) },
                        modifier = Modifier.size(dimensions.iconMedium)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(Res.string.flow_group_delete),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(dimensions.iconSmall)
                        )
                    }
                }
            }
        }

        // Generic I/O Port Badges when Collapsed
        if (group.isCollapsed) {
            if (hasIncomingConnections) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = (-5).dp)
                        .size(10.dp)
                        .background(baseColor, CircleShape)
                        .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
                )
            }
            if (hasOutgoingConnections) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset(x = 5.dp)
                        .size(10.dp)
                        .background(baseColor, CircleShape)
                        .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
                )
            }
        }

        // Resize Handles (Desktop Window style border resizing with cursor feedback)
        if (!isReadOnly && onResizeGroup != null) {
            // Right edge handle (resizes width)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(RESIZE_HANDLE_THICKNESS_DP.dp)
                    .fillMaxHeight()
                    .pointerHoverIcon(PlatformUtils.horizontalResizePointerIcon())
                    .pointerInput(group.id, stateScale) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onResizeGroup(group.id, Offset(dragAmount.x / stateScale, 0f))
                        }
                    }
            )

            if (!group.isCollapsed) {
                // Bottom edge handle (resizes height)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(RESIZE_HANDLE_THICKNESS_DP.dp)
                        .pointerHoverIcon(PlatformUtils.verticalResizePointerIcon())
                        .pointerInput(group.id, stateScale) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                onResizeGroup(group.id, Offset(0f, dragAmount.y / stateScale))
                            }
                        }
                )

                // Bottom-right corner handle (resizes both width and height)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(RESIZE_CORNER_SIZE_DP.dp)
                        .pointerHoverIcon(PlatformUtils.diagonalResizePointerIcon())
                        .pointerInput(group.id, stateScale) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                onResizeGroup(group.id, Offset(dragAmount.x / stateScale, dragAmount.y / stateScale))
                            }
                        }
                )
            }
        }
    }

    if (showColorPicker) {
        ColorPickerDialog(
            show = showColorPicker,
            onDismissRequest = { showColorPicker = false },
            onPickedColor = { pickedColor ->
                val hex = pickedColor.toHex(hexPrefix = true, includeAlpha = false)
                onUpdateGroup(group.copy(color = hex))
                showColorPicker = false
            }
        )
    }
}
