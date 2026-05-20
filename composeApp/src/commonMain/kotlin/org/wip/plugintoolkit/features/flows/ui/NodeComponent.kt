package org.wip.plugintoolkit.features.flows.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import org.wip.plugintoolkit.shared.components.ToolkitTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.booleanOrNull
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.viewmodel.ValidationError
import org.wip.plugintoolkit.shared.components.settings.ExpressiveMenu
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.action_delete
import plugintoolkit.composeapp.generated.resources.action_save
import plugintoolkit.composeapp.generated.resources.dialog_cancel
import plugintoolkit.composeapp.generated.resources.node_class_name_label
import plugintoolkit.composeapp.generated.resources.node_data_type_label
import plugintoolkit.composeapp.generated.resources.node_delete_confirm
import plugintoolkit.composeapp.generated.resources.node_delete_title
import plugintoolkit.composeapp.generated.resources.node_edit_input_title
import plugintoolkit.composeapp.generated.resources.node_edit_output_title
import plugintoolkit.composeapp.generated.resources.node_port_name_label
import plugintoolkit.composeapp.generated.resources.node_semantic_type_label
import plugintoolkit.composeapp.generated.resources.node_semantic_type_placeholder

@Composable
fun NodeComponent(
    node: Node,
    connectedInputPortIds: Set<String>,
    inferredTypes: Map<Pair<Long, String>, DataType> = emptyMap(),
    inferredSemanticTypes: Map<Pair<Long, String>, String?> = emptyMap(),
    validationErrors: List<ValidationError> = emptyList(),
    onMove: (Long, Offset, Boolean, Boolean) -> Unit, // id, delta, snap, showGhost
    onEndMove: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onExpand: (Long) -> Unit,
    onUpdateValue: (Long, String, Any?) -> Unit,
    onStartConnection: (Long, String, Boolean) -> Unit,
    onDragConnection: (Offset) -> Unit = {},
    onDropConnection: (isShiftPressed: Boolean) -> Unit = {},
    onPortPositioned: (Long, String, Offset) -> Unit = { _, _, _ -> },
    onPress: (Long) -> Unit = {},
    onUpdateBoundaryNode: (Long, String, DataType, String?) -> Unit = { _, _, _, _ -> },
    highlightedPortId: String? = null,
    highlightedPortColor: Color? = null,
    boardLayoutCoordinates: LayoutCoordinates?,
    stateScale: Float,
    stateOffset: Offset,
    selectedNodeIds: Set<Long> = emptySet(),
    isReadOnly: Boolean = false,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showEditBoundaryDialog by remember { mutableStateOf(false) }
    
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnEndMove by rememberUpdatedState(onEndMove)
    val currentOnPress by rememberUpdatedState(onPress)

    var showTooltip by remember { mutableStateOf(false) }
    var tooltipJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    val (headerColor, onHeaderColor) = when (node) {
        is Node.CapabilityNode -> Pair(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
        is Node.SystemNode -> {
            if (node.systemAction.lowercase() == "error") {
                Pair(MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.onError)
            } else {
                Pair(ToolkitTheme.colors.success, Color.White)
            }
        }
        is Node.FlowInputNode -> Pair(MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.onTertiary)
        is Node.FlowOutputNode -> Pair(MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.onTertiary)
        is Node.SubFlowNode -> Pair(MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.onSecondary)
    }

    val isSelected = remember(node.id, selectedNodeIds) { selectedNodeIds.contains(node.id) }
    val cardBorder = if (isSelected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        null
    }

    Box(
        modifier = modifier
            .width(300.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = ToolkitTheme.spacing.small),
            shape = MaterialTheme.shapes.medium,
            border = cardBorder,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column {
                // Header
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
                                            delay(2000)
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
                                        currentOnPress(node.id)
                                    }
                                }
                            }
                        }
                        .pointerInput(Unit) {
                            if (!isReadOnly) {
                                detectDragGestures(
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        currentOnMove(node.id, dragAmount, false, true)
                                    },
                                    onDragEnd = { currentOnEndMove(node.id) },
                                    onDragCancel = { currentOnEndMove(node.id) }
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
                            overflow = TextOverflow.Ellipsis
                        )
                        
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
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if ((node is Node.FlowInputNode || node is Node.FlowOutputNode) && !isReadOnly) {
                            IconButton(
                                onClick = { showEditBoundaryDialog = true },
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
                            var isShiftPressed by remember { mutableStateOf(false) }
                            IconButton(
                                onClick = {
                                    if (isShiftPressed) {
                                        onDelete(node.id)
                                    } else {
                                        showDeleteConfirmation = true
                                    }
                                },
                                modifier = Modifier
                                    .size(ToolkitTheme.dimensions.iconMedium)
                                    .pointerInput(node.id) {
                                        awaitPointerEventScope {
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                if (event.type == PointerEventType.Press) {
                                                    isShiftPressed = event.keyboardModifiers.isShiftPressed
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

                // Body
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(ToolkitTheme.spacing.mediumSmall),
                    verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.mediumSmall)
                ) {
                    // Inputs
                    node.inputs.forEach { input ->
                        val currentPortValue = input.value ?: input.defaultValue
                        Row(
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                PortCircle(
                                    color = if (highlightedPortId == input.id) (highlightedPortColor ?: headerColor) else headerColor,
                                    isHighlighted = highlightedPortId == input.id,
                                    onDragStart = { if (!isReadOnly) onStartConnection(node.id, input.id, false) },
                                    onDrag = { if (!isReadOnly) onDragConnection(it) },
                                    onDragEnd = { if (!isReadOnly) onDropConnection(it) }
                                )
                                Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(input.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                    val inferredType = inferredTypes[Pair(node.id, input.id)] ?: input.dataType
                                    val typeLabel = if (input.dataType is DataType.Primitive && input.dataType.primitiveType == PrimitiveType.ANY &&
                                                       !(inferredType is DataType.Primitive && inferredType.primitiveType == PrimitiveType.ANY)) {
                                        "${formatDataType(inferredType)} (Implied)"
                                    } else {
                                        formatDataType(input.dataType)
                                    }
                                    Text(
                                        text = typeLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                    val inferredSem = inferredSemanticTypes[Pair(node.id, input.id)] ?: input.semanticType
                                    if (inferredSem != null) {
                                        Text(inferredSem, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    val portErrors = validationErrors.filter {
                                        (it.sourceNodeId == node.id && it.sourcePortId == input.id) ||
                                        (it.targetNodeId == node.id && it.targetPortId == input.id)
                                    }
                                    if (portErrors.isNotEmpty()) {
                                        Text(
                                            text = portErrors.first().message,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            // Default Value Editor
                            val isConnected = connectedInputPortIds.contains(input.id)
                            val valueModifier = if (isConnected) Modifier.alpha(0.5f) else Modifier
                            
                            Box(modifier = Modifier.width(120.dp).then(valueModifier)) {
                                when (val type = input.dataType) {
                                    is DataType.Primitive -> {
                                        when (type.primitiveType) {
                                            PrimitiveType.BOOLEAN -> {
                                                val checked = getBooleanValue(currentPortValue)
                                                Switch(
                                                    checked = checked,
                                                    onCheckedChange = { onUpdateValue(node.id, input.id, it) },
                                                    enabled = !isConnected && !isReadOnly,
                                                    modifier = Modifier.scale(0.8f)
                                                )
                                            }
                                            PrimitiveType.INT -> {
                                                 val rawValue = getPortValueString(currentPortValue)
                                                 BasicTextField(
                                                     value = rawValue,
                                                     onValueChange = { newValue ->
                                                         if (newValue.isEmpty()) {
                                                             onUpdateValue(node.id, input.id, null)
                                                         } else {
                                                             val filtered = newValue.filterIndexed { index, c ->
                                                                 c.isDigit() || (c == '-' && index == 0)
                                                             }
                                                             if (filtered == newValue || filtered.isNotEmpty()) {
                                                                 val parsed = filtered.toIntOrNull()
                                                                 onUpdateValue(node.id, input.id, parsed ?: filtered)
                                                             }
                                                         }
                                                     },
                                                     enabled = !isConnected && !isReadOnly,
                                                     textStyle = MaterialTheme.typography.bodySmall.copy(
                                                         color = if (isConnected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                                                 else MaterialTheme.colorScheme.onSurface
                                                      ),
                                                     modifier = Modifier
                                                         .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                                                         .padding(horizontal = ToolkitTheme.spacing.small, vertical = ToolkitTheme.spacing.extraSmall)
                                                         .fillMaxWidth(),
                                                     singleLine = true
                                                 )
                                             }
                                             PrimitiveType.DOUBLE -> {
                                                 val rawValue = getPortValueString(currentPortValue)
                                                 BasicTextField(
                                                     value = rawValue,
                                                     onValueChange = { newValue ->
                                                         if (newValue.isEmpty()) {
                                                             onUpdateValue(node.id, input.id, null)
                                                         } else {
                                                             val hasDot = newValue.count { it == '.' } <= 1
                                                             val validMinus = newValue.lastIndexOf('-') <= 0
                                                             val validChars = newValue.all { it.isDigit() || it == '.' || it == '-' }
                                                             if (hasDot && validMinus && validChars) {
                                                                 val parsed = newValue.toDoubleOrNull()
                                                                 onUpdateValue(node.id, input.id, parsed ?: newValue)
                                                             }
                                                         }
                                                     },
                                                     enabled = !isConnected && !isReadOnly,
                                                     textStyle = MaterialTheme.typography.bodySmall.copy(
                                                         color = if (isConnected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                                                 else MaterialTheme.colorScheme.onSurface
                                                      ),
                                                     modifier = Modifier
                                                         .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                                                         .padding(horizontal = ToolkitTheme.spacing.small, vertical = ToolkitTheme.spacing.extraSmall)
                                                         .fillMaxWidth(),
                                                     singleLine = true
                                                 )
                                             }
                                            else -> {
                                                val rawValue = getPortValueString(currentPortValue)
                                                BasicTextField(
                                                    value = rawValue,
                                                    onValueChange = { onUpdateValue(node.id, input.id, it) },
                                                    enabled = !isConnected && !isReadOnly,
                                                    textStyle = MaterialTheme.typography.bodySmall.copy(
                                                        color = if (isConnected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                                                else MaterialTheme.colorScheme.onSurface
                                                     ),
                                                    modifier = Modifier
                                                        .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                                                        .padding(horizontal = ToolkitTheme.spacing.small, vertical = ToolkitTheme.spacing.extraSmall)
                                                        .fillMaxWidth(),
                                                    singleLine = true
                                                )
                                            }
                                        }
                                    }
                                    is DataType.Enum -> {
                                        val options = type.options
                                        val rawValue = getPortValueString(currentPortValue)
                                        val selected = rawValue.ifEmpty { options.firstOrNull() ?: "" }
                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            ExpressiveMenu(
                                                options = options,
                                                selectedOption = selected,
                                                onOptionSelected = { onUpdateValue(node.id, input.id, it) },
                                                labelProvider = { it },
                                                enabled = !isConnected && !isReadOnly
                                            )
                                        }
                                    }
                                    else -> {
                                        val rawValue = getPortValueString(currentPortValue)
                                        BasicTextField(
                                            value = rawValue,
                                            onValueChange = { onUpdateValue(node.id, input.id, it) },
                                            enabled = !isConnected && !isReadOnly,
                                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                                color = if (isConnected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                                        else MaterialTheme.colorScheme.onSurface
                                            ),
                                            modifier = Modifier
                                                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                                                .padding(horizontal = ToolkitTheme.spacing.small, vertical = ToolkitTheme.spacing.extraSmall)
                                                .fillMaxWidth(),
                                            singleLine = true
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (node.inputs.isNotEmpty() && node.outputs.isNotEmpty()) {
                        Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                    }

                    // Outputs
                    node.outputs.forEach { output ->
                        Row(
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
                        ) {
                            Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                                Text(output.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                val inferredType = inferredTypes[Pair(node.id, output.id)] ?: output.dataType
                                val typeLabel = if (output.dataType is DataType.Primitive && output.dataType.primitiveType == PrimitiveType.ANY &&
                                                   !(inferredType is DataType.Primitive && inferredType.primitiveType == PrimitiveType.ANY)) {
                                    "${formatDataType(inferredType)} (Implied)"
                                } else {
                                    formatDataType(output.dataType)
                                }
                                Text(
                                    text = typeLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                                 val inferredSem = inferredSemanticTypes[Pair(node.id, output.id)] ?: output.semanticType
                                 if (inferredSem != null) {
                                       Text(inferredSem, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                 }
                                val portErrors = validationErrors.filter {
                                    (it.sourceNodeId == node.id && it.sourcePortId == output.id) ||
                                    (it.targetNodeId == node.id && it.targetPortId == output.id)
                                }
                                if (portErrors.isNotEmpty()) {
                                    Text(
                                        text = portErrors.first().message,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                            PortCircle(
                                color = if (highlightedPortId == output.id) (highlightedPortColor ?: headerColor) else headerColor,
                                isHighlighted = highlightedPortId == output.id,
                                onDragStart = { if (!isReadOnly) onStartConnection(node.id, output.id, true) },
                                onDrag = { if (!isReadOnly) onDragConnection(it) },
                                onDragEnd = { if (!isReadOnly) onDropConnection(it) }
                            )
                        }
                    }
                }
            }
        }

        if (showTooltip) {
            val description = getNodeDescription(node)
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-65).dp)
                    .width(280.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.inverseSurface),
                shape = MaterialTheme.shapes.small,
                elevation = CardDefaults.cardElevation(defaultElevation = ToolkitTheme.spacing.extraSmall)
            ) {
                Text(
                    text = description,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(ToolkitTheme.spacing.small)
                )
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(stringResource(Res.string.node_delete_title)) },
            text = { Text(stringResource(Res.string.node_delete_confirm, node.title)) },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete(node.id)
                        showDeleteConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(stringResource(Res.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(stringResource(Res.string.dialog_cancel))
                }
            }
        )
    }

    if (showEditBoundaryDialog && (node is Node.FlowInputNode || node is Node.FlowOutputNode)) {
        val port = if (node is Node.FlowInputNode) node.outputs.firstOrNull() else node.inputs.firstOrNull()
        if (port != null) {
            var name by remember { mutableStateOf(port.name) }
            var selectedTypeOption by remember {
                mutableStateOf(
                    when (val dt = port.dataType) {
                        is DataType.Primitive -> {
                            if (dt.primitiveType == PrimitiveType.ANY) "Any"
                            else dt.primitiveType.name.lowercase().replaceFirstChar { it.uppercase() }
                        }
                        is DataType.Object -> "Object"
                        is DataType.Array -> "Array"
                        is DataType.Enum -> "Enum"
                    }
                )
            }
            var customClassName by remember {
                mutableStateOf(
                    when (val dt = port.dataType) {
                        is DataType.Object -> dt.className
                        is DataType.Enum -> dt.className
                        else -> ""
                    }
                )
            }
            var semanticType by remember { mutableStateOf(port.semanticType ?: "") }
            
            AlertDialog(
                onDismissRequest = { showEditBoundaryDialog = false },
                title = { 
                    Text(
                        text = if (node is Node.FlowInputNode) stringResource(Res.string.node_edit_input_title) else stringResource(Res.string.node_edit_output_title),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    ) 
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ToolkitTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(stringResource(Res.string.node_port_name_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        val typeOptions = listOf("Any", "String", "Int", "Double", "Boolean", "Object")
                        var dropdownExpanded by remember { mutableStateOf(false) }
                        
                        Box(modifier = Modifier.fillMaxWidth()) {
                            ToolkitTextField(
                                value = selectedTypeOption,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(Res.string.node_data_type_label)) },
                                trailingIcon = {
                                    IconButton(onClick = { dropdownExpanded = true }) {
                                        Icon(Icons.Default.UnfoldMore, contentDescription = "Select Type")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            DropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                typeOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = {
                                            selectedTypeOption = option
                                            dropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        
                        if (selectedTypeOption == "Object") {
                            ToolkitTextField(
                                value = customClassName,
                                onValueChange = { customClassName = it },
                                label = { Text(stringResource(Res.string.node_class_name_label)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        
                        ToolkitTextField(
                            value = semanticType,
                            onValueChange = { semanticType = it },
                            label = { Text(stringResource(Res.string.node_semantic_type_label)) },
                            placeholder = { Text(stringResource(Res.string.node_semantic_type_placeholder)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val computedDataType = when (selectedTypeOption) {
                                "Any" -> DataType.Primitive(PrimitiveType.ANY)
                                "String" -> DataType.Primitive(PrimitiveType.STRING)
                                "Int" -> DataType.Primitive(PrimitiveType.INT)
                                "Double" -> DataType.Primitive(PrimitiveType.DOUBLE)
                                "Boolean" -> DataType.Primitive(PrimitiveType.BOOLEAN)
                                "Object" -> DataType.Object(customClassName.ifBlank { "java.lang.Object" })
                                else -> DataType.Primitive(PrimitiveType.ANY)
                            }
                            onUpdateBoundaryNode(
                                node.id,
                                name.ifBlank { port.name },
                                computedDataType,
                                semanticType.trim().ifBlank { null }
                            )
                            showEditBoundaryDialog = false
                        }
                    ) {
                        Text(stringResource(Res.string.action_save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEditBoundaryDialog = false }) {
                        Text(stringResource(Res.string.dialog_cancel))
                    }
                }
            )
        }
    }
}

@Composable
fun PortCircle(
    color: Color,
    isHighlighted: Boolean = false,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: (isShiftPressed: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var cumulativeOffset by remember { mutableStateOf(Offset.Zero) }
    var isMouseHovered by remember { mutableStateOf(false) }

    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)

    val displayColor = if (isHighlighted) {
        color
    } else if (isMouseHovered) {
        MaterialTheme.colorScheme.primary
    } else {
        color
    }

    val backgroundDisplayColor = if (isHighlighted) {
        color.copy(alpha = 0.4f)
    } else if (isMouseHovered) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    } else {
        MaterialTheme.colorScheme.background
    }

    Box(
        modifier = modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(backgroundDisplayColor)
            .border(if (isHighlighted || isMouseHovered) 3.dp else 2.dp, displayColor, CircleShape)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Enter) {
                            isMouseHovered = true
                        } else if (event.type == PointerEventType.Exit) {
                            isMouseHovered = false
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown()
                        cumulativeOffset = Offset.Zero
                        currentOnDragStart()
                        
                        var isShift = false
                        
                        drag(down.id) { change ->
                            change.consume()
                            cumulativeOffset += change.position - change.previousPosition
                            currentOnDrag(cumulativeOffset)
                            
                            isShift = currentEvent.keyboardModifiers.isShiftPressed
                        }
                        
                        currentOnDragEnd(isShift)
                    }
                }
            }
    )
}

private fun formatDataType(type: DataType): String {
    return when (type) {
        is DataType.Primitive -> type.primitiveType.name.lowercase().replaceFirstChar { it.uppercase() }
        is DataType.Array -> "List<${formatDataType(type.items)}>"
        is DataType.Enum -> type.className.substringAfterLast('.')
        is DataType.Object -> type.className.substringAfterLast('.')
    }
}

private fun getPortValueString(value: Any?): String {
    if (value == null) return ""
    if (value is kotlinx.serialization.json.JsonPrimitive) {
        return if (value.isString) value.content else value.toString()
    }
    return value.toString()
}

private fun getBooleanValue(value: Any?): Boolean {
    if (value == null) return false
    if (value is Boolean) return value
    if (value is kotlinx.serialization.json.JsonPrimitive) {
        return value.booleanOrNull ?: (value.content.lowercase().toBooleanStrictOrNull() ?: false)
    }
    return value.toString().lowercase().toBooleanStrictOrNull() ?: false
}

private fun getNodeDescription(node: Node): String {
    return when (node) {
        is Node.CapabilityNode -> node.capability.description ?: "Executes the '${node.capability.name}' capability."
        is Node.SystemNode -> {
            when (node.systemAction.lowercase()) {
                "save" -> "Saves data to a file at the specified file path."
                "load" -> "Loads data from a file at the specified file path."
                "log" -> "Logs a message to the console/run logs with the specified severity."
                "delay" -> "Pauses flow execution for a specified duration in milliseconds."
                "convert" -> "Converts inputs safely between primitive types (String, Int, Double, Boolean). Provides a success status."
                "merger" -> "Merges two list arrays into a single combined list."
                "conditional" -> "Routes input data to either the 'If True' or 'If False' output port based on a boolean condition."
                "error" -> "Halts flow execution immediately with a custom error message."
                else -> "System operation: ${node.title}."
            }
        }
        is Node.FlowInputNode -> "Defines an input boundary for this flow."
        is Node.FlowOutputNode -> "Defines an output boundary for this flow."
        is Node.SubFlowNode -> "Executes the sub-flow '${node.flowName}' and maps its inputs/outputs."
    }
}
