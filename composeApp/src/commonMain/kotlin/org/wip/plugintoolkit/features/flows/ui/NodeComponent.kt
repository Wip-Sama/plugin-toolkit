package org.wip.plugintoolkit.features.flows.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import org.wip.plugintoolkit.features.flows.viewmodel.ValidationError
import androidx.compose.runtime.*
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.wip.plugintoolkit.features.flows.model.Node
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.geometry.Offset
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.shared.components.settings.ExpressiveMenu
import kotlinx.serialization.json.booleanOrNull
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import androidx.compose.ui.platform.LocalDensity

@Composable
fun NodeComponent(
    node: Node,
    connectedInputPortIds: Set<String>,
    inferredTypes: Map<Pair<Long, String>, DataType> = emptyMap(),
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
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showEditBoundaryDialog by remember { mutableStateOf(false) }
    
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnEndMove by rememberUpdatedState(onEndMove)
    val currentOnPress by rememberUpdatedState(onPress)

    val (headerColor, onHeaderColor) = when (node) {
        is Node.CapabilityNode -> Pair(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
        is Node.SystemNode -> Pair(ToolkitTheme.colors.success, Color.White)
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

    Card(
        modifier = modifier
            .width(300.dp),
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
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                currentOnMove(node.id, dragAmount, false, true)
                            },
                            onDragEnd = { currentOnEndMove(node.id) },
                            onDragCancel = { currentOnEndMove(node.id) }
                        )
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
                    
                    if (node is Node.SubFlowNode) {
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
                    if (node is Node.FlowInputNode || node is Node.FlowOutputNode) {
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
                                onDragStart = { onStartConnection(node.id, input.id, false) },
                                onDrag = onDragConnection,
                                onDragEnd = onDropConnection
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
                                if (input.semanticType != null) {
                                    Text(input.semanticType, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                                enabled = !isConnected,
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
                                                 enabled = !isConnected,
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
                                                 enabled = !isConnected,
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
                                                enabled = !isConnected,
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
                                            enabled = !isConnected
                                        )
                                    }
                                }
                                else -> {
                                    val rawValue = getPortValueString(currentPortValue)
                                    BasicTextField(
                                        value = rawValue,
                                        onValueChange = { onUpdateValue(node.id, input.id, it) },
                                        enabled = !isConnected,
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
                            if (output.semanticType != null) {
                                  Text(output.semanticType, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            onDragStart = { onStartConnection(node.id, output.id, true) },
                            onDrag = onDragConnection,
                            onDragEnd = onDropConnection
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Node") },
            text = { Text("Are you sure you want to delete '${node.title}'? This will also remove all connected lines.") },
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
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
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
                        text = if (node is Node.FlowInputNode) "Edit Flow Input Port" else "Edit Flow Output Port",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    ) 
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Port Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        val typeOptions = listOf("Any", "String", "Int", "Double", "Boolean", "Object")
                        var dropdownExpanded by remember { mutableStateOf(false) }
                        
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = selectedTypeOption,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Data Type") },
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
                            OutlinedTextField(
                                value = customClassName,
                                onValueChange = { customClassName = it },
                                label = { Text("Class Name (e.g. org.wip.MyData)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        
                        OutlinedTextField(
                            value = semanticType,
                            onValueChange = { semanticType = it },
                            label = { Text("Semantic Type / Mimetype (optional)") },
                            placeholder = { Text("e.g. image/png") },
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
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEditBoundaryDialog = false }) {
                        Text("Cancel")
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
