package org.wip.plugintoolkit.features.board.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.wip.plugintoolkit.features.board.model.Node

@Composable
fun NodeComponent(
    node: Node,
    connectedInputPortIds: Set<String>,
    onMove: (Long, androidx.compose.ui.geometry.Offset, Boolean, Boolean) -> Unit, // id, delta, snap, showGhost
    onEndMove: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onExpand: (Long) -> Unit,
    onUpdateValue: (Long, String, Any?) -> Unit,
    onStartConnection: (Long, String, Boolean) -> Unit,
    onDropConnection: (Long, String) -> Unit,
    onPortPositioned: (Long, String, androidx.compose.ui.layout.LayoutCoordinates) -> Unit = { _, _, _ -> },
    onPress: (Long) -> Unit = {},
    highlightedPortId: String? = null,
    modifier: Modifier = Modifier
) {
    val headerColor = when (node) {
        is Node.CapabilityNode -> Color(0xFF1E88E5)
        is Node.SystemNode -> Color(0xFF43A047)
        is Node.FlowInputNode -> Color(0xFFE53935)
        is Node.FlowOutputNode -> Color(0xFFE53935)
        is Node.SubFlowNode -> Color(0xFF8E24AA)
    }

    Card(
        modifier = modifier.width(300.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerColor)
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
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onMove(node.id, dragAmount, false, true)
                            },
                            onDragEnd = { onEndMove(node.id) },
                            onDragCancel = { onEndMove(node.id) }
                        )
                    }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = if (node is Node.CapabilityNode) Icons.Default.Cable else Icons.Default.UnfoldMore,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = node.title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    if (node is Node.SubFlowNode) {
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = { onExpand(node.id) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.UnfoldMore,
                                contentDescription = "Expand",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = { onDelete(node.id) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Inputs
                node.inputs.forEach { input ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            PortCircle(
                                color = headerColor,
                                isHighlighted = highlightedPortId == input.id,
                                onDragStart = { onStartConnection(node.id, input.id, false) },
                                onDrop = { onDropConnection(node.id, input.id) },
                                modifier = Modifier.onGloballyPositioned { 
                                       onPortPositioned(node.id, input.id, it)
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(input.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                if (input.semanticType != null) {
                                    Text(input.semanticType, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        // Default Value Editor
                        if (!connectedInputPortIds.contains(input.id)) {
                            Box(modifier = Modifier.width(120.dp)) {
                                BasicTextField(
                                    value = (input.value ?: input.defaultValue ?: "").toString(),
                                    onValueChange = { onUpdateValue(node.id, input.id, it) },
                                    textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                        .fillMaxWidth(),
                                    singleLine = true
                                )
                            }
                        }
                    }
                }

                // Divider if there are both inputs and outputs
                if (node.inputs.isNotEmpty() && node.outputs.isNotEmpty()) {
                    Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                }

                // Outputs
                node.outputs.forEach { output ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                            Text(output.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            if (output.semanticType != null) {
                                Text(output.semanticType, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        PortCircle(
                            color = headerColor,
                            isHighlighted = highlightedPortId == output.id,
                            onDragStart = { onStartConnection(node.id, output.id, true) },
                            onDrop = {}, // Outputs don't receive connections
                            modifier = Modifier.onGloballyPositioned { 
                                onPortPositioned(node.id, output.id, it)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PortCircle(
    color: Color,
    isHighlighted: Boolean = false,
    onDragStart: () -> Unit,
    onDrop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(if (isHighlighted) color.copy(alpha = 0.4f) else Color.White)
            .border(if (isHighlighted) 3.dp else 2.dp, color, CircleShape)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Press) {
                            onDragStart()
                        } else if (event.type == PointerEventType.Release) {
                            onDrop()
                        }
                    }
                }
            }
    )
}
