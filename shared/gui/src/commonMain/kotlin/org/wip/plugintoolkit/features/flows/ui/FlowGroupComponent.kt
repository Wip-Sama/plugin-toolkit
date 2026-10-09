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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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
import org.wip.plugintoolkit.features.flows.ui.snapToGrid
import org.wip.plugintoolkit.features.flows.ui.toComposeOffset
import org.wip.plugintoolkit.shared.components.plugin.inputs.parseColorString
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.flow_group_collapse
import plugintoolkit.composeapp.generated.resources.flow_group_color
import plugintoolkit.composeapp.generated.resources.flow_group_confirm
import plugintoolkit.composeapp.generated.resources.flow_group_default_title
import plugintoolkit.composeapp.generated.resources.flow_group_delete
import plugintoolkit.composeapp.generated.resources.flow_group_expand
import plugintoolkit.composeapp.generated.resources.flow_group_nodes_count
import kotlin.math.roundToInt

private const val GROUP_CORNER_RADIUS_DP = 16
private const val GROUP_COLLAPSED_HEIGHT_DP = 50
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
    onDragDelta: (Offset) -> Unit = {},
    onMove: ((Long, Offset) -> Unit)? = null,
    onEndMove: ((Long) -> Unit)? = null,
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
    onResizeGroup: ((Long, Offset, Offset, Boolean) -> Unit)? = null,
    onSelectGroup: ((Long, Boolean) -> Unit)? = null,
    onPress: ((Long, Boolean) -> Unit)? = null,
    isInteractionBlocked: Boolean = false,
    modifier: Modifier = Modifier
) {
    val dimensions = ToolkitTheme.dimensions
    val spacing = ToolkitTheme.spacing
    val density = LocalDensity.current.density
    var showColorPicker by remember { mutableStateOf(false) }
    var isEditingTitle by remember { mutableStateOf(false) }
    var titleText by remember(group.title, isEditingTitle) { mutableStateOf(group.title) }
    var isCtrlPressedOnGroup by remember { mutableStateOf(false) }
    var isShiftPressedOnGroup by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val currentOnPress by rememberUpdatedState(onPress ?: onSelectGroup)
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnEndMove by rememberUpdatedState(onEndMove)
    val currentOnDragDelta by rememberUpdatedState(onDragDelta)
    val currentIsSelected by rememberUpdatedState(isSelected)
    val currentOnSampleColor by rememberUpdatedState(onSampleColor)
    val currentOnPaintGroup by rememberUpdatedState(onPaintGroup)
    val currentOnWashGroup by rememberUpdatedState(onWashGroup)

    LaunchedEffect(isEditingTitle) {
        if (isEditingTitle) {
            focusRequester.requestFocus()
        }
    }

    val confirmGroupTitle = {
        if (titleText != group.title) {
            onUpdateGroup(group.copy(title = titleText))
        }
        isEditingTitle = false
    }

    val grpColor = group.color
    val baseColor = if (!grpColor.isNullOrBlank()) {
        parseColorString(grpColor)
    } else {
        MaterialTheme.colorScheme.primary
    }

    val groupWidthDp = if (group.isCollapsed) {
        maxOf(group.size.x, GROUP_MIN_COLLAPSED_WIDTH_DP.toFloat()).dp
    } else {
        group.size.x.dp
    }
    val groupHeightDp = if (group.isCollapsed) {
        dimensions.groupCollapsedHeight
    } else {
        group.size.y.dp
    }
    val cornerShape = RoundedCornerShape(GROUP_CORNER_RADIUS_DP.dp)

    val borderColor = when {
        isDropTarget -> MaterialTheme.colorScheme.primary
        isSelected -> MaterialTheme.colorScheme.primary
        else -> baseColor.copy(alpha = GROUP_BORDER_ALPHA)
    }
    val borderWidth = when {
        isDropTarget -> dimensions.progressIndicatorStroke * 1.5f
        isSelected -> dimensions.progressIndicatorStroke
        else -> dimensions.borderUnselected
    }

    Box(
        modifier = modifier
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
            .pointerInput(group.id) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press) {
                            isCtrlPressedOnGroup = event.keyboardModifiers.isCtrlPressed
                            isShiftPressedOnGroup = event.keyboardModifiers.isShiftPressed
                        }
                    }
                }
            }
            .pointerInput(group.id, isReadOnly, isPaintToolActive, isWashToolActive, isEyedropperActive) {
                val sampleColor = currentOnSampleColor
                val paintGroup = currentOnPaintGroup
                val washGroup = currentOnWashGroup
                val endMove = currentOnEndMove
                val move = currentOnMove
                val dragDelta = currentOnDragDelta
                if (isEyedropperActive && sampleColor != null) {
                    detectTapGestures(onTap = { sampleColor(group.color ?: "#4CAF50") })
                } else if (isPaintToolActive && paintGroup != null) {
                    detectTapGestures(onTap = { paintGroup(group.id) })
                } else if (isWashToolActive && washGroup != null) {
                    detectTapGestures(onTap = { washGroup(group.id) })
                } else if (!isReadOnly) {
                    var isDraggingGroup = false
                    coroutineScope {
                        launch {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val isNearRight = currentIsSelected && offset.x >= (group.size.x - RESIZE_HANDLE_THICKNESS_DP * 1.5f)
                                    val isNearBottom = currentIsSelected && !group.isCollapsed && offset.y >= (group.size.y - RESIZE_HANDLE_THICKNESS_DP * 1.5f)
                                    isDraggingGroup = !isNearRight && !isNearBottom
                                    if (isDraggingGroup) {
                                        currentOnPress?.invoke(group.id, isCtrlPressedOnGroup)
                                    }
                                },
                                onDragEnd = {
                                    if (isDraggingGroup) {
                                        isDraggingGroup = false
                                        if (endMove != null) {
                                            endMove(group.id)
                                        } else {
                                            val snapDelta = group.position.snapToGrid() - group.position
                                            if (snapDelta != org.wip.plugintoolkit.features.flows.model.Offset.Zero) {
                                                dragDelta(snapDelta.toComposeOffset())
                                            }
                                        }
                                    }
                                },
                                onDragCancel = {
                                    if (isDraggingGroup) {
                                        isDraggingGroup = false
                                        if (endMove != null) {
                                            endMove(group.id)
                                        } else {
                                            val snapDelta = group.position.snapToGrid() - group.position
                                            if (snapDelta != org.wip.plugintoolkit.features.flows.model.Offset.Zero) {
                                                dragDelta(snapDelta.toComposeOffset())
                                            }
                                        }
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    if (isDraggingGroup) {
                                        change.consume()
                                        if (dragAmount != Offset.Zero) {
                                            if (move != null) {
                                                move(group.id, dragAmount)
                                            } else {
                                                dragDelta(dragAmount)
                                            }
                                        }
                                    }
                                }
                            )
                        }
                        launch {
                            detectTapGestures(
                                onTap = {
                                    if (isShiftPressedOnGroup) {
                                        isEditingTitle = true
                                    } else {
                                        currentOnPress?.invoke(group.id, isCtrlPressedOnGroup)
                                    }
                                }
                            )
                        }
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
                            onValueChange = { titleText = it },
                            textStyle = MaterialTheme.typography.titleMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp
                            ),
                            cursorBrush = SolidColor(baseColor),
                            singleLine = false,
                            modifier = Modifier
                                .testTag("flow_group_title_field_${group.id}")
                                .focusRequester(focusRequester)
                                .onKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyDown) {
                                        when (keyEvent.key) {
                                            Key.Enter -> {
                                                if (keyEvent.isShiftPressed) {
                                                    false
                                                } else {
                                                    confirmGroupTitle()
                                                    true
                                                }
                                            }
                                            Key.Escape -> {
                                                titleText = group.title
                                                isEditingTitle = false
                                                true
                                            }
                                            else -> false
                                        }
                                    } else {
                                        false
                                    }
                                }
                        )
                        IconButton(
                            onClick = { confirmGroupTitle() },
                            modifier = Modifier.size(dimensions.iconSmall)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = stringResource(Res.string.flow_group_confirm),
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
                            overflow = TextOverflow.Ellipsis,
                            modifier = if (!isReadOnly) {
                                Modifier.pointerInput(Unit) {
                                    detectTapGestures(onDoubleTap = { isEditingTitle = true })
                                }
                            } else Modifier
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
        if (isSelected && !isReadOnly && !isInteractionBlocked && onResizeGroup != null) {
            // Right edge handle (resizes width)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(RESIZE_HANDLE_THICKNESS_DP.dp)
                    .fillMaxHeight()
                    .pointerHoverIcon(PlatformUtils.horizontalResizePointerIcon())
                    .pointerInput(group.id) {
                        detectDragGestures(
                            onDragEnd = { onResizeGroup(group.id, Offset.Zero, Offset.Zero, true) },
                            onDragCancel = { onResizeGroup(group.id, Offset.Zero, Offset.Zero, true) },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onResizeGroup(group.id, Offset.Zero, Offset(dragAmount.x, 0f), false)
                            }
                        )
                    }
            )
            
            // Left edge handle
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(RESIZE_HANDLE_THICKNESS_DP.dp)
                    .fillMaxHeight()
                    .pointerHoverIcon(PlatformUtils.horizontalResizePointerIcon())
                    .pointerInput(group.id) {
                        detectDragGestures(
                            onDragEnd = { onResizeGroup(group.id, Offset.Zero, Offset.Zero, true) },
                            onDragCancel = { onResizeGroup(group.id, Offset.Zero, Offset.Zero, true) },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onResizeGroup(group.id, Offset(dragAmount.x, 0f), Offset(-dragAmount.x, 0f), false)
                            }
                        )
                    }
            )
            
            // Top edge handle
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(RESIZE_HANDLE_THICKNESS_DP.dp)
                    .pointerHoverIcon(PlatformUtils.verticalResizePointerIcon())
                    .pointerInput(group.id) {
                        detectDragGestures(
                            onDragEnd = { onResizeGroup(group.id, Offset.Zero, Offset.Zero, true) },
                            onDragCancel = { onResizeGroup(group.id, Offset.Zero, Offset.Zero, true) },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onResizeGroup(group.id, Offset(0f, dragAmount.y), Offset(0f, -dragAmount.y), false)
                            }
                        )
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
                        .pointerInput(group.id) {
                            detectDragGestures(
                                onDragEnd = { onResizeGroup(group.id, Offset.Zero, Offset.Zero, true) },
                                onDragCancel = { onResizeGroup(group.id, Offset.Zero, Offset.Zero, true) },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    onResizeGroup(group.id, Offset.Zero, Offset(0f, dragAmount.y), false)
                                }
                            )
                        }
                )

                // Bottom-right corner handle (resizes both width and height)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(RESIZE_CORNER_SIZE_DP.dp)
                        .pointerHoverIcon(PlatformUtils.diagonalResizePointerIcon())
                        .pointerInput(group.id) {
                            detectDragGestures(
                                onDragEnd = { onResizeGroup(group.id, Offset.Zero, Offset.Zero, true) },
                                onDragCancel = { onResizeGroup(group.id, Offset.Zero, Offset.Zero, true) },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    onResizeGroup(group.id, Offset.Zero, dragAmount, false)
                                }
                            )
                        }
                )
                
                // Bottom-left corner handle
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .size(RESIZE_CORNER_SIZE_DP.dp)
                        .pointerHoverIcon(PlatformUtils.diagonalResizePointerIcon()) // We'd ideally want the other diagonal, but we use what we have or generic
                        .pointerInput(group.id) {
                            detectDragGestures(
                                onDragEnd = { onResizeGroup(group.id, Offset.Zero, Offset.Zero, true) },
                                onDragCancel = { onResizeGroup(group.id, Offset.Zero, Offset.Zero, true) },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    onResizeGroup(group.id, Offset(dragAmount.x, 0f), Offset(-dragAmount.x, dragAmount.y), false)
                                }
                            )
                        }
                )
                
                // Top-left corner handle
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .size(RESIZE_CORNER_SIZE_DP.dp)
                        .pointerHoverIcon(PlatformUtils.diagonalResizePointerIcon())
                        .pointerInput(group.id) {
                            detectDragGestures(
                                onDragEnd = { onResizeGroup(group.id, Offset.Zero, Offset.Zero, true) },
                                onDragCancel = { onResizeGroup(group.id, Offset.Zero, Offset.Zero, true) },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    onResizeGroup(group.id, Offset(dragAmount.x, dragAmount.y), Offset(-dragAmount.x, -dragAmount.y), false)
                                }
                            )
                        }
                )
                
                // Top-right corner handle
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(RESIZE_CORNER_SIZE_DP.dp)
                        .pointerHoverIcon(PlatformUtils.diagonalResizePointerIcon())
                        .pointerInput(group.id) {
                            detectDragGestures(
                                onDragEnd = { onResizeGroup(group.id, Offset.Zero, Offset.Zero, true) },
                                onDragCancel = { onResizeGroup(group.id, Offset.Zero, Offset.Zero, true) },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    onResizeGroup(group.id, Offset(0f, dragAmount.y), Offset(dragAmount.x, -dragAmount.y), false)
                                }
                            )
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
