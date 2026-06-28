package org.wip.plugintoolkit.features.flows.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.shared.components.ToolkitChip
import org.wip.plugintoolkit.shared.components.ToolkitChipStyle
import org.wip.plugintoolkit.shared.components.tooltip
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun NodeHeader(
    node: Node,
    headerColor: Color,
    onHeaderColor: Color,
    isReady: Boolean,
    isReadOnly: Boolean,
    onPress: (Long) -> Unit,
    onMove: (Long, Offset, Boolean, Boolean) -> Unit,
    onEndMove: (Long) -> Unit,
    onExpand: (Long) -> Unit,
    onToggleCollapse: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onShowDeleteConfirmation: () -> Unit,
    onShowLoadSettings: () -> Unit,
    onShowEditBoundary: () -> Unit,
) {
    var showTooltip by remember { mutableStateOf(false) }
    var tooltipJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(headerColor)
            .pointerInput(node.id) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Enter) {
                            tooltipJob?.cancel()
                            tooltipJob = scope.launch {
                                delay(2000.milliseconds)
                                showTooltip = true
                            }
                        } else if (event.type == PointerEventType.Exit) {
                            tooltipJob?.cancel()
                            showTooltip = false
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Press) {
                            onPress(node.id)
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                if (!isReadOnly) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onMove(node.id, dragAmount, false, true)
                        },
                        onDragEnd = { onEndMove(node.id) },
                        onDragCancel = { onEndMove(node.id) }
                    )
                }
            }
            .padding(ToolkitTheme.spacing.mediumSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                imageVector = if (node is Node.CapabilityNode) Icons.Default.Cable else Icons.Default.UnfoldMore,
                contentDescription = null,
                tint = onHeaderColor.copy(alpha = 0.8f),
                modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
            )
            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
            Text(
                text = node.title,
                color = onHeaderColor,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .tooltip(node.title)
            )

            if (node is Node.CapabilityNode && node.isBroken) {
                Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                ToolkitChip(
                    text = "BROKEN",
                    containerColor = onHeaderColor,
                    contentColor = headerColor,
                    style = ToolkitChipStyle.Filled,
                    shape = MaterialTheme.shapes.extraSmall
                )
            } else if (!isReady) {
                Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                ToolkitChip(
                    text = "NOT READY",
                    containerColor = onHeaderColor,
                    contentColor = headerColor,
                    style = ToolkitChipStyle.Filled,
                    shape = MaterialTheme.shapes.extraSmall
                )
            }

            if (node is Node.SubFlowNode && !isReadOnly) {
                Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                IconButton(
                    onClick = { onExpand(node.id) },
                    modifier = Modifier.size(ToolkitTheme.dimensions.iconMedium)
                ) {
                    Icon(
                        imageVector = Icons.Default.UnfoldMore,
                        contentDescription = "Expand",
                        tint = onHeaderColor.copy(alpha = 0.8f),
                        modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                    )
                }
            }

            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
            IconButton(
                onClick = { onToggleCollapse(node.id) },
                modifier = Modifier.size(ToolkitTheme.dimensions.iconMedium)
            ) {
                Icon(
                    imageVector = if (node.isCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = "Toggle Collapse",
                    tint = onHeaderColor.copy(alpha = 0.8f),
                    modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (node is Node.SystemNode && node.systemAction.lowercase() == "load" && !isReadOnly) {
                IconButton(
                    onClick = { onShowLoadSettings() },
                    modifier = Modifier.size(ToolkitTheme.dimensions.iconMedium)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Load Node Settings",
                        tint = onHeaderColor.copy(alpha = 0.8f),
                        modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                    )
                }
                Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
            }

            if ((node is Node.FlowInputNode || node is Node.FlowOutputNode) && !isReadOnly) {
                IconButton(
                    onClick = { onShowEditBoundary() },
                    modifier = Modifier.size(ToolkitTheme.dimensions.iconMedium)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Edit Port",
                        tint = onHeaderColor.copy(alpha = 0.8f),
                        modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                    )
                }
                Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
            }

            if (!isReadOnly) {
                var isShiftPressedLocal by remember { mutableStateOf(false) }
                IconButton(
                    onClick = {
                        if (isShiftPressedLocal) {
                            onDelete(node.id)
                        } else {
                            onShowDeleteConfirmation()
                        }
                    },
                    modifier = Modifier
                        .size(ToolkitTheme.dimensions.iconMedium)
                        .pointerInput(node.id) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.type == PointerEventType.Press) {
                                        isShiftPressedLocal = event.keyboardModifiers.isShiftPressed
                                    }
                                }
                            }
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = onHeaderColor.copy(alpha = 0.8f),
                        modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                    )
                }
            }
        }
    }
}
