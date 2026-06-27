package org.wip.plugintoolkit.features.flows.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
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
import org.wip.plugintoolkit.api.SemanticType
import org.wip.plugintoolkit.api.parseSemanticTypes
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.core.utils.PlatformUtils
import org.wip.plugintoolkit.core.utils.SemanticRegistry
import org.wip.plugintoolkit.features.colorpicker.utils.toHex
import org.wip.plugintoolkit.features.colorpicker.utils.toRGB
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.model.PortConstraints
import org.wip.plugintoolkit.features.flows.viewmodel.ValidationError
import org.wip.plugintoolkit.shared.components.ToolkitChip
import org.wip.plugintoolkit.shared.components.ToolkitChipStyle
import org.wip.plugintoolkit.shared.components.ToolkitTextField
import org.wip.plugintoolkit.shared.components.TooltipArea
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
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun NodeComponent(
    node: Node,
    connectedInputPortIds: Set<String>,
    inferredTypes: Map<Pair<Long, String>, DataType> = emptyMap(),
    inferredSemanticTypes: Map<Pair<Long, String>, List<SemanticType>> = emptyMap(),
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
    onUpdateBoundaryNode: (Long, String, DataType, List<SemanticType>, PortConstraints?, Boolean) -> Unit = { _, _, _, _, _, _ -> },
    onUpdateSystemNodeSettings: (Long, String, List<SemanticType>, String?, List<String>?) -> Unit = { _, _, _, _, _ -> },
    onToggleCollapse: (Long) -> Unit = {},
    onToggleInputsCollapse: (Long) -> Unit = {},
    onToggleOutputsCollapse: (Long) -> Unit = {},
    highlightedPortId: String? = null,
    highlightedPortColor: Color? = null,
    boardLayoutCoordinates: LayoutCoordinates?,
    stateScale: Float,
    stateOffset: Offset,
    selectedNodeIds: Set<Long> = emptySet(),
    isReadOnly: Boolean = false,
    isReady: Boolean = true,
    onFocusLost: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showEditBoundaryDialog by remember { mutableStateOf(false) }
    var showLoadSettingsDialog by remember { mutableStateOf(false) }

    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnEndMove by rememberUpdatedState(onEndMove)
    val currentOnPress by rememberUpdatedState(onPress)

    var showTooltip by remember { mutableStateOf(false) }
    var tooltipJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    var showColorPicker by remember { mutableStateOf(false) }
    var activeColorInputId by remember { mutableStateOf<String?>(null) }



    val (headerColor, onHeaderColor) = when (node) {
        is Node.CapabilityNode -> {
            if (node.isBroken) {
                Pair(MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.onError)
            } else if (!isReady) {
                Pair(MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.onTertiary)
            } else {
                Pair(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
            }
        }

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
                                onClick = { showLoadSettingsDialog = true },
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
                // Body
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(ToolkitTheme.spacing.mediumSmall),
                    verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.mediumSmall)
                ) {
                    // Inputs Section
                    if (node.inputs.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onToggleInputsCollapse(node.id) },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (node.isInputsCollapsed) {
                                    PortCircle(
                                        color = headerColor,
                                        isHighlighted = false,
                                        onDragStart = {}, onDrag = {}, onDragEnd = {},
                                        modifier = Modifier.onGloballyPositioned { coords ->
                                            if (boardLayoutCoordinates != null) {
                                                val center =
                                                    boardLayoutCoordinates.localBoundingBoxOf(coords, false).center
                                                node.inputs.forEach { input ->
                                                    onPortPositioned(node.id, input.id, center)
                                                }
                                            }
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                                }
                                Text(
                                    "Inputs",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Icon(
                                imageVector = if (node.isInputsCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                contentDescription = "Toggle Inputs"
                            )
                        }
                    }
                    if (!node.isInputsCollapsed) {
                        node.inputs.forEach { input ->
                            val currentPortValue = input.value ?: input.defaultValue
                            val portErrors = validationErrors.filter {
                                (it.sourceNodeId == node.id && it.sourcePortId == input.id) ||
                                        (it.targetNodeId == node.id && it.targetPortId == input.id)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    PortCircle(
                                        color = if (highlightedPortId == input.id) (highlightedPortColor
                                            ?: headerColor) else headerColor,
                                        isHighlighted = highlightedPortId == input.id,
                                        onDragStart = { if (!isReadOnly) onStartConnection(node.id, input.id, false) },
                                        onDrag = { if (!isReadOnly) onDragConnection(it) },
                                        onDragEnd = { if (!isReadOnly) onDropConnection(it) },
                                        modifier = Modifier.onGloballyPositioned { coords ->
                                            if (boardLayoutCoordinates != null) {
                                                val center =
                                                    boardLayoutCoordinates.localBoundingBoxOf(coords, false).center
                                                onPortPositioned(node.id, input.id, center)
                                            }
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                                    Column(modifier = Modifier.weight(1f)) {
                                        val isRequired = (node as? Node.CapabilityNode)?.capability?.parameters?.get(input.id)?.required == true
                                        val displayName = if (isRequired) "${input.name} *" else input.name
                                        if (!input.description.isNullOrBlank()) {
                                            TooltipArea(
                                                delayMillis = 3000,
                                                tooltip = {
                                                    Text(
                                                        text = input.description,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            ) {
                                                Text(
                                                    text = displayName,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        } else {
                                            Text(
                                                text = displayName,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        val inferredType = inferredTypes[Pair(node.id, input.id)] ?: input.dataType
                                        val typeLabel =
                                            if (input.dataType is DataType.Primitive && input.dataType.primitiveType == PrimitiveType.ANY &&
                                                !(inferredType is DataType.Primitive && inferredType.primitiveType == PrimitiveType.ANY)
                                            ) {
                                                "${formatDataType(inferredType)} (Implied)"
                                            } else {
                                                formatDataType(input.dataType)
                                            }
                                        Text(
                                            text = typeLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                        val inferredSem =
                                            inferredSemanticTypes[Pair(node.id, input.id)] ?: input.semanticTypes
                                        if (inferredSem.isNotEmpty()) {
                                            val first = inferredSem.first().canonicalId
                                            val semText = if (inferredSem.size > 1) {
                                                "$first (+${inferredSem.size - 1} more)"
                                            } else {
                                                first
                                            }
                                            TooltipArea(
                                                tooltip = {
                                                    Column(modifier = Modifier.padding(4.dp)) {
                                                        inferredSem.forEach { sem ->
                                                            Text(
                                                                text = "• ${sem.canonicalId}",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                }
                                            ) {
                                                Text(
                                                    text = semText,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
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
                                // Default Value Editor
                                val isConnected = connectedInputPortIds.contains(input.id)
                                val valueModifier = if (isConnected) Modifier.alpha(0.5f) else Modifier

                                Box(modifier = Modifier.width(120.dp).then(valueModifier)) {
                                    val inferredSem =
                                        inferredSemanticTypes[Pair(node.id, input.id)] ?: input.semanticTypes
                                    val category = SemanticRegistry.getCategory(inferredSem)?.name?.lowercase()
                                    when (category) {
                                        "color" -> {
                                            val rawValue = getPortValueString(currentPortValue, input.dataType)
                                            val parsedColor = parseColorString(rawValue)
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(
                                                        MaterialTheme.colorScheme.surfaceVariant,
                                                        MaterialTheme.shapes.small
                                                    )
                                                    .then(
                                                        if (portErrors.isNotEmpty() && !isConnected) {
                                                            Modifier.border(
                                                                1.dp,
                                                                MaterialTheme.colorScheme.error,
                                                                MaterialTheme.shapes.small
                                                            )
                                                        } else {
                                                            Modifier
                                                        }
                                                    )
                                                    .padding(
                                                        start = ToolkitTheme.spacing.small,
                                                        end = ToolkitTheme.spacing.extraSmall
                                                    )
                                                    .padding(vertical = ToolkitTheme.spacing.extraSmall)
                                            ) {
                                                BasicTextField(
                                                    value = rawValue,
                                                    onValueChange = { onUpdateValue(node.id, input.id, org.wip.plugintoolkit.features.plugin.utils.SettingsUtils.stringToJson(it, input.dataType)) },
                                                    enabled = !isConnected && !isReadOnly,
                                                    textStyle = MaterialTheme.typography.bodySmall.copy(
                                                        color = if (isConnected) MaterialTheme.colorScheme.onSurface.copy(
                                                            alpha = 0.5f
                                                        )
                                                        else MaterialTheme.colorScheme.onSurface
                                                    ),
                                                    modifier = Modifier.onFocusChanged { if (!it.isFocused) onFocusLost() }.weight(1f),
                                                    singleLine = true
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .size(20.dp)
                                                        .clip(MaterialTheme.shapes.extraSmall)
                                                        .background(parsedColor)
                                                        .border(
                                                            1.dp,
                                                            MaterialTheme.colorScheme.outlineVariant,
                                                            MaterialTheme.shapes.extraSmall
                                                        )
                                                        .pointerInput(isConnected, isReadOnly) {
                                                            if (!isConnected && !isReadOnly) {
                                                                detectTapGestures {
                                                                    activeColorInputId = input.id
                                                                    showColorPicker = true
                                                                }
                                                            }
                                                        }
                                                )
                                            }
                                        }

                                        "file" -> {
                                            val rawValue = getPortValueString(currentPortValue, input.dataType)
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(
                                                        MaterialTheme.colorScheme.surfaceVariant,
                                                        MaterialTheme.shapes.small
                                                    )
                                                    .then(
                                                        if (portErrors.isNotEmpty() && !isConnected) {
                                                            Modifier.border(
                                                                1.dp,
                                                                MaterialTheme.colorScheme.error,
                                                                MaterialTheme.shapes.small
                                                            )
                                                        } else {
                                                            Modifier
                                                        }
                                                    )
                                                    .padding(
                                                        start = ToolkitTheme.spacing.small,
                                                        end = ToolkitTheme.spacing.extraSmall
                                                    )
                                                    .padding(vertical = ToolkitTheme.spacing.extraSmall)
                                            ) {
                                                BasicTextField(
                                                    value = rawValue,
                                                    onValueChange = { onUpdateValue(node.id, input.id, org.wip.plugintoolkit.features.plugin.utils.SettingsUtils.stringToJson(it, input.dataType)) },
                                                    enabled = !isConnected && !isReadOnly,
                                                    textStyle = MaterialTheme.typography.bodySmall.copy(
                                                        color = if (isConnected) MaterialTheme.colorScheme.onSurface.copy(
                                                            alpha = 0.5f
                                                        )
                                                        else MaterialTheme.colorScheme.onSurface
                                                    ),
                                                    modifier = Modifier.onFocusChanged { if (!it.isFocused) onFocusLost() }.weight(1f),
                                                    singleLine = true
                                                )
                                                IconButton(
                                                    onClick = {
                                                        scope.launch {
                                                            val allowedExtensions =
                                                                SemanticRegistry.getAllowedExtensions(inferredSem)
                                                            val pickedPath =
                                                                PlatformUtils.pickFile("Select File", allowedExtensions)
                                                            if (pickedPath != null) {
                                                                val isArray = input.dataType is DataType.Array
                                                                val newValue =
                                                                    appendPickedValue(rawValue, pickedPath, isArray)
                                                                onUpdateValue(node.id, input.id, newValue)
                                                            }
                                                        }
                                                    },
                                                    enabled = !isConnected && !isReadOnly,
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.FolderOpen,
                                                        contentDescription = "Pick File",
                                                        modifier = Modifier.size(16.dp),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }

                                        "path" -> {
                                            val rawValue = getPortValueString(currentPortValue, input.dataType)
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(
                                                        MaterialTheme.colorScheme.surfaceVariant,
                                                        MaterialTheme.shapes.small
                                                    )
                                                    .then(
                                                        if (portErrors.isNotEmpty() && !isConnected) {
                                                            Modifier.border(
                                                                1.dp,
                                                                MaterialTheme.colorScheme.error,
                                                                MaterialTheme.shapes.small
                                                            )
                                                        } else {
                                                            Modifier
                                                        }
                                                    )
                                                    .padding(
                                                        start = ToolkitTheme.spacing.small,
                                                        end = ToolkitTheme.spacing.extraSmall
                                                    )
                                                    .padding(vertical = ToolkitTheme.spacing.extraSmall)
                                            ) {
                                                BasicTextField(
                                                    value = rawValue,
                                                    onValueChange = { onUpdateValue(node.id, input.id, org.wip.plugintoolkit.features.plugin.utils.SettingsUtils.stringToJson(it, input.dataType)) },
                                                    enabled = !isConnected && !isReadOnly,
                                                    textStyle = MaterialTheme.typography.bodySmall.copy(
                                                        color = if (isConnected) MaterialTheme.colorScheme.onSurface.copy(
                                                            alpha = 0.5f
                                                        )
                                                        else MaterialTheme.colorScheme.onSurface
                                                    ),
                                                    modifier = Modifier.onFocusChanged { if (!it.isFocused) onFocusLost() }.weight(1f),
                                                    singleLine = true
                                                )
                                                IconButton(
                                                    onClick = {
                                                        scope.launch {
                                                            val pickedPath = PlatformUtils.pickFolder()
                                                            if (pickedPath != null) {
                                                                val isArray = input.dataType is DataType.Array
                                                                val newValue =
                                                                    appendPickedValue(rawValue, pickedPath, isArray)
                                                                onUpdateValue(node.id, input.id, newValue)
                                                            }
                                                        }
                                                    },
                                                    enabled = !isConnected && !isReadOnly,
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Folder,
                                                        contentDescription = "Pick Folder",
                                                        modifier = Modifier.size(16.dp),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }

                                        "image", "audio", "video" -> {
                                            val rawValue = getPortValueString(currentPortValue, input.dataType)
                                            val isArray = input.dataType is DataType.Array
                                            val fileNames = getFileNames(rawValue, isArray)
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(
                                                        MaterialTheme.colorScheme.surfaceVariant,
                                                        MaterialTheme.shapes.small
                                                    )
                                                    .then(
                                                        if (portErrors.isNotEmpty() && !isConnected) {
                                                            Modifier.border(
                                                                1.dp,
                                                                MaterialTheme.colorScheme.error,
                                                                MaterialTheme.shapes.small
                                                            )
                                                        } else {
                                                            Modifier
                                                        }
                                                    )
                                                    .padding(
                                                        start = ToolkitTheme.spacing.small,
                                                        end = ToolkitTheme.spacing.extraSmall
                                                    )
                                                    .padding(vertical = ToolkitTheme.spacing.extraSmall)
                                            ) {
                                                Box(
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text(
                                                        text = if (fileNames.isNotEmpty()) fileNames else {
                                                            when (category) {
                                                                "image" -> "No image"
                                                                "audio" -> "No audio"
                                                                else -> "No video"
                                                            }
                                                        },
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = if (fileNames.isNotEmpty()) MaterialTheme.colorScheme.onSurface
                                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                if (fileNames.isNotEmpty() && !isConnected && !isReadOnly) {
                                                    IconButton(
                                                        onClick = { onUpdateValue(node.id, input.id, null) },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Close,
                                                            contentDescription = "Clear",
                                                            modifier = Modifier.size(16.dp),
                                                            tint = MaterialTheme.colorScheme.error
                                                        )
                                                    }
                                                }
                                                IconButton(
                                                    onClick = {
                                                        scope.launch {
                                                            val allowedExtensions =
                                                                SemanticRegistry.getAllowedExtensions(inferredSem)
                                                            val pickedPath =
                                                                PlatformUtils.pickFile("Select File", allowedExtensions)
                                                            if (pickedPath != null) {
                                                                val newValue =
                                                                    appendPickedValue(rawValue, pickedPath, isArray)
                                                                onUpdateValue(node.id, input.id, newValue)
                                                            }
                                                        }
                                                    },
                                                    enabled = !isConnected && !isReadOnly,
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.FolderOpen,
                                                        contentDescription = "Pick File",
                                                        modifier = Modifier.size(16.dp),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }

                                        else -> {
                                            when (val type = input.dataType) {
                                                is DataType.Primitive -> {
                                                    when (type.primitiveType) {
                                                        PrimitiveType.BOOLEAN -> {
                                                            val checked = getBooleanValue(currentPortValue)
                                                            Switch(
                                                                checked = checked,
                                                                onCheckedChange = {
                                                                    onUpdateValue(
                                                                        node.id,
                                                                        input.id,
                                                                        it
                                                                    )
                                                                },
                                                                enabled = !isConnected && !isReadOnly,
                                                                modifier = Modifier.scale(0.8f)
                                                            )
                                                        }

                                                        PrimitiveType.INT -> {
                                                            val rawValue = getPortValueString(currentPortValue, input.dataType)
                                                            BasicTextField(
                                                                value = rawValue,
                                                                onValueChange = { newValue ->
                                                                    if (newValue.isEmpty()) {
                                                                        onUpdateValue(node.id, input.id, null)
                                                                    } else {
                                                                        val filtered =
                                                                            newValue.filterIndexed { index, c ->
                                                                                c.isDigit() || (c == '-' && index == 0)
                                                                            }
                                                                        if (filtered == newValue || filtered.isNotEmpty()) {
                                                                            val parsed = filtered.toIntOrNull()
                                                                            onUpdateValue(
                                                                                node.id,
                                                                                input.id,
                                                                                parsed ?: filtered
                                                                            )
                                                                        }
                                                                    }
                                                                },
                                                                enabled = !isConnected && !isReadOnly,
                                                                textStyle = MaterialTheme.typography.bodySmall.copy(
                                                                    color = if (isConnected) MaterialTheme.colorScheme.onSurface.copy(
                                                                        alpha = 0.5f
                                                                    )
                                                                    else MaterialTheme.colorScheme.onSurface
                                                                ),
                                                                modifier = Modifier.onFocusChanged { if (!it.isFocused) onFocusLost() }
                                                                    .background(
                                                                        MaterialTheme.colorScheme.surfaceVariant,
                                                                        MaterialTheme.shapes.small
                                                                    )
                                                                    .then(
                                                                        if (portErrors.isNotEmpty() && !isConnected) {
                                                                            Modifier.border(
                                                                                1.dp,
                                                                                MaterialTheme.colorScheme.error,
                                                                                MaterialTheme.shapes.small
                                                                            )
                                                                        } else {
                                                                            Modifier
                                                                        }
                                                                    )
                                                                    .padding(
                                                                        horizontal = ToolkitTheme.spacing.small,
                                                                        vertical = ToolkitTheme.spacing.extraSmall
                                                                    )
                                                                    .fillMaxWidth(),
                                                                singleLine = true
                                                            )
                                                        }

                                                        PrimitiveType.DOUBLE -> {
                                                            val rawValue = getPortValueString(currentPortValue, input.dataType)
                                                            BasicTextField(
                                                                value = rawValue,
                                                                onValueChange = { newValue ->
                                                                    if (newValue.isEmpty()) {
                                                                        onUpdateValue(node.id, input.id, null)
                                                                    } else {
                                                                        val hasDot = newValue.count { it == '.' } <= 1
                                                                        val validMinus = newValue.lastIndexOf('-') <= 0
                                                                        val validChars =
                                                                            newValue.all { it.isDigit() || it == '.' || it == '-' }
                                                                        if (hasDot && validMinus && validChars) {
                                                                            val parsed = newValue.toDoubleOrNull()
                                                                            onUpdateValue(
                                                                                node.id,
                                                                                input.id,
                                                                                parsed ?: newValue
                                                                            )
                                                                        }
                                                                    }
                                                                },
                                                                enabled = !isConnected && !isReadOnly,
                                                                textStyle = MaterialTheme.typography.bodySmall.copy(
                                                                    color = if (isConnected) MaterialTheme.colorScheme.onSurface.copy(
                                                                        alpha = 0.5f
                                                                    )
                                                                    else MaterialTheme.colorScheme.onSurface
                                                                ),
                                                                modifier = Modifier.onFocusChanged { if (!it.isFocused) onFocusLost() }
                                                                    .background(
                                                                        MaterialTheme.colorScheme.surfaceVariant,
                                                                        MaterialTheme.shapes.small
                                                                    )
                                                                    .then(
                                                                        if (portErrors.isNotEmpty() && !isConnected) {
                                                                            Modifier.border(
                                                                                1.dp,
                                                                                MaterialTheme.colorScheme.error,
                                                                                MaterialTheme.shapes.small
                                                                            )
                                                                        } else {
                                                                            Modifier
                                                                        }
                                                                    )
                                                                    .padding(
                                                                        horizontal = ToolkitTheme.spacing.small,
                                                                        vertical = ToolkitTheme.spacing.extraSmall
                                                                    )
                                                                    .fillMaxWidth(),
                                                                singleLine = true
                                                            )
                                                        }

                                                        else -> {
                                                            val rawValue = getPortValueString(currentPortValue, input.dataType)
                                                            BasicTextField(
                                                                value = rawValue,
                                                                onValueChange = {
                                                                    onUpdateValue(
                                                                        node.id,
                                                                        input.id,
                                                                        it
                                                                    )
                                                                },
                                                                enabled = !isConnected && !isReadOnly,
                                                                textStyle = MaterialTheme.typography.bodySmall.copy(
                                                                    color = if (isConnected) MaterialTheme.colorScheme.onSurface.copy(
                                                                        alpha = 0.5f
                                                                    )
                                                                    else MaterialTheme.colorScheme.onSurface
                                                                ),
                                                                modifier = Modifier.onFocusChanged { if (!it.isFocused) onFocusLost() }
                                                                    .background(
                                                                        MaterialTheme.colorScheme.surfaceVariant,
                                                                        MaterialTheme.shapes.small
                                                                    )
                                                                    .then(
                                                                        if (portErrors.isNotEmpty() && !isConnected) {
                                                                            Modifier.border(
                                                                                1.dp,
                                                                                MaterialTheme.colorScheme.error,
                                                                                MaterialTheme.shapes.small
                                                                            )
                                                                        } else {
                                                                            Modifier
                                                                        }
                                                                    )
                                                                    .padding(
                                                                        horizontal = ToolkitTheme.spacing.small,
                                                                        vertical = ToolkitTheme.spacing.extraSmall
                                                                    )
                                                                    .fillMaxWidth(),
                                                                singleLine = true
                                                            )
                                                        }
                                                    }
                                                }

                                                is DataType.Enum -> {
                                                    val options = type.options
                                                    val rawValue = getPortValueString(currentPortValue, input.dataType)
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
                                                    val rawValue = getPortValueString(currentPortValue, input.dataType)
                                                    BasicTextField(
                                                        value = rawValue,
                                                        onValueChange = { onUpdateValue(node.id, input.id, org.wip.plugintoolkit.features.plugin.utils.SettingsUtils.stringToJson(it, input.dataType)) },
                                                        enabled = !isConnected && !isReadOnly,
                                                        textStyle = MaterialTheme.typography.bodySmall.copy(
                                                            color = if (isConnected) MaterialTheme.colorScheme.onSurface.copy(
                                                                alpha = 0.5f
                                                            )
                                                            else MaterialTheme.colorScheme.onSurface
                                                        ),
                                                        modifier = Modifier.onFocusChanged { if (!it.isFocused) onFocusLost() }
                                                            .background(
                                                                MaterialTheme.colorScheme.surfaceVariant,
                                                                MaterialTheme.shapes.small
                                                            )
                                                            .then(
                                                                if (portErrors.isNotEmpty() && !isConnected) {
                                                                    Modifier.border(
                                                                        1.dp,
                                                                        MaterialTheme.colorScheme.error,
                                                                        MaterialTheme.shapes.small
                                                                    )
                                                                } else {
                                                                    Modifier
                                                                }
                                                            )
                                                            .padding(
                                                                horizontal = ToolkitTheme.spacing.small,
                                                                vertical = ToolkitTheme.spacing.extraSmall
                                                            )
                                                            .fillMaxWidth(),
                                                        singleLine = true
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (node.inputs.isNotEmpty() && node.outputs.isNotEmpty() && !node.isInputsCollapsed && !node.isOutputsCollapsed) {
                        HorizontalDivider(
                            Modifier,
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }

                    // Outputs Section
                    // Outputs Section
                    if (node.outputs.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onToggleOutputsCollapse(node.id) },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Outputs", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (node.isOutputsCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Toggle Outputs"
                                )
                                if (node.isOutputsCollapsed) {
                                    Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                                    PortCircle(
                                        color = headerColor,
                                        isHighlighted = false,
                                        onDragStart = {}, onDrag = {}, onDragEnd = {},
                                        modifier = Modifier.onGloballyPositioned { coords ->
                                            if (boardLayoutCoordinates != null) {
                                                val center =
                                                    boardLayoutCoordinates.localBoundingBoxOf(coords, false).center
                                                node.outputs.forEach { output ->
                                                    onPortPositioned(node.id, output.id, center)
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    if (!node.isOutputsCollapsed) {
                        node.outputs.forEach { output ->
                            Row(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End
                            ) {
                                Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                                    if (!output.description.isNullOrBlank()) {
                                        TooltipArea(
                                            delayMillis = 3000,
                                            tooltip = {
                                                Text(
                                                    text = output.description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        ) {
                                            Text(
                                                output.name,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    } else {
                                        Text(
                                            output.name,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    val inferredType = inferredTypes[Pair(node.id, output.id)] ?: output.dataType
                                    val typeLabel =
                                        if (output.dataType is DataType.Primitive && output.dataType.primitiveType == PrimitiveType.ANY &&
                                            !(inferredType is DataType.Primitive && inferredType.primitiveType == PrimitiveType.ANY)
                                        ) {
                                            "${formatDataType(inferredType)} (Implied)"
                                        } else {
                                            formatDataType(output.dataType)
                                        }
                                    Text(
                                        text = typeLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                    val inferredSem =
                                        inferredSemanticTypes[Pair(node.id, output.id)] ?: output.semanticTypes
                                    if (inferredSem.isNotEmpty()) {
                                        val first = inferredSem.first().canonicalId
                                        val semText = if (inferredSem.size > 1) {
                                            "$first (+${inferredSem.size - 1} more)"
                                        } else {
                                            first
                                        }
                                        TooltipArea(
                                            tooltip = {
                                                Column(modifier = Modifier.padding(4.dp)) {
                                                    inferredSem.forEach { sem ->
                                                        Text(
                                                            text = "• ${sem.canonicalId}",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        ) {
                                            Text(
                                                text = semText,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
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
                                PortCircle(
                                    color = if (highlightedPortId == output.id) (highlightedPortColor
                                        ?: headerColor) else headerColor,
                                    isHighlighted = highlightedPortId == output.id,
                                    onDragStart = { if (!isReadOnly) onStartConnection(node.id, output.id, true) },
                                    onDrag = { if (!isReadOnly) onDragConnection(it) },
                                    onDragEnd = { if (!isReadOnly) onDropConnection(it) },
                                    modifier = Modifier.onGloballyPositioned { coords ->
                                        if (boardLayoutCoordinates != null) {
                                            val center = boardLayoutCoordinates.localBoundingBoxOf(coords, false).center
                                            onPortPositioned(node.id, output.id, center)
                                        }
                                    }
                                )
                            }
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
                    when (val dt =
                        if (port.dataType is DataType.Array) (port.dataType as DataType.Array).items else port.dataType) {
                        is DataType.Primitive -> {
                            if (dt.primitiveType == PrimitiveType.ANY) "Any"
                            else dt.primitiveType.name.lowercase().replaceFirstChar { it.uppercase() }
                        }

                        is DataType.Object -> "Object"
                        is DataType.Array -> "Array"
                        is DataType.Enum -> "Enum"
                        is DataType.MapType -> "Map"
                    }
                )
            }
            var customClassName by remember {
                mutableStateOf(
                    when (val dt =
                        if (port.dataType is DataType.Array) (port.dataType as DataType.Array).items else port.dataType) {
                        is DataType.Object -> dt.className
                        is DataType.Enum -> dt.className
                        else -> ""
                    }
                )
            }
            var semanticType by remember { mutableStateOf(port.semanticTypes.joinToString { it.canonicalId }) }

            var isList by remember {
                mutableStateOf(if (node is Node.FlowInputNode) node.isList else false)
            }
            var minValStr by remember {
                mutableStateOf(if (node is Node.FlowInputNode) node.constraints?.min?.toString() ?: "" else "")
            }
            var maxValStr by remember {
                mutableStateOf(if (node is Node.FlowInputNode) node.constraints?.max?.toString() ?: "" else "")
            }
            var regexStr by remember {
                mutableStateOf(if (node is Node.FlowInputNode) node.constraints?.regex ?: "" else "")
            }

            AlertDialog(
                onDismissRequest = { showEditBoundaryDialog = false },
                title = {
                    Text(
                        text = if (node is Node.FlowInputNode) stringResource(Res.string.node_edit_input_title) else stringResource(
                            Res.string.node_edit_output_title
                        ),
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

                        if (node is Node.FlowInputNode) {
                            HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
                            Text("Constraints & Settings", style = MaterialTheme.typography.titleSmall)

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Accept multiple items (List)")
                                Spacer(modifier = Modifier.weight(1f))
                                Switch(checked = isList, onCheckedChange = { isList = it })
                            }

                            if (selectedTypeOption == "String") {
                                ToolkitTextField(
                                    value = regexStr,
                                    onValueChange = { regexStr = it },
                                    label = { Text("Regex Pattern") },
                                    placeholder = { Text("e.g. ^[a-zA-Z]+$") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            if (selectedTypeOption == "Int" || selectedTypeOption == "Double") {
                                Row(horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)) {
                                    ToolkitTextField(
                                        value = minValStr,
                                        onValueChange = { minValStr = it },
                                        label = { Text("Min Value") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    ToolkitTextField(
                                        value = maxValStr,
                                        onValueChange = { maxValStr = it },
                                        label = { Text("Max Value") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
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

                            val constraints = if (node is Node.FlowInputNode) {
                                org.wip.plugintoolkit.features.flows.model.PortConstraints(
                                    regex = regexStr.takeIf { it.isNotBlank() },
                                    min = minValStr.toDoubleOrNull(),
                                    max = maxValStr.toDoubleOrNull()
                                )
                            } else null

                            onUpdateBoundaryNode(
                                node.id,
                                name.ifBlank { port.name },
                                computedDataType,
                                parseSemanticTypes(semanticType),
                                constraints,
                                isList
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

    if (showColorPicker && activeColorInputId != null) {
        val input = node.inputs.firstOrNull { it.id == activeColorInputId }
        val inferredSem = input?.let { inferredSemanticTypes[Pair(node.id, it.id)] ?: it.semanticTypes } ?: emptyList()
        val hasAlpha = inferredSem.any { it.variant?.contains("rgba", ignoreCase = true) == true }
        org.wip.plugintoolkit.features.colorpicker.ui.ColorPickerDialog(
            show = showColorPicker,
            onDismissRequest = {
                showColorPicker = false
                activeColorInputId = null
            },
            onPickedColor = { color ->
                activeColorInputId?.let { inputId ->
                    val formatted = if (inferredSem.any {
                            it.name.contains("rgb", ignoreCase = true) || it.variant?.contains(
                                "rgb",
                                ignoreCase = true
                            ) == true
                        }) {
                        color.toRGB(rgbPrefix = true, includeAlpha = hasAlpha)
                    } else {
                        color.toHex(hexPrefix = true, includeAlpha = hasAlpha)
                    }
                    val isArray = input?.dataType is DataType.Array
                    val existingValue = input?.let { getPortValueString(it.value ?: it.defaultValue, it.dataType) } ?: ""
                    val newValue = appendPickedValue(existingValue, formatted, isArray)
                    onUpdateValue(node.id, inputId, newValue)
                }
                showColorPicker = false
                activeColorInputId = null
            }
        )
    }

    if (showLoadSettingsDialog && node is Node.SystemNode && node.systemAction.lowercase() == "load") {
        val port = node.outputs.firstOrNull { it.id == "data" }
        val inPort = node.inputs.firstOrNull { it.id == "file_path" }
        if (port != null && inPort != null) {
            var semanticTypesStr by remember { mutableStateOf(port.semanticTypes.joinToString { it.canonicalId }) }
            var extensionsStr by remember { mutableStateOf(inPort.constraints?.extensions?.joinToString(", ") ?: "") }

            AlertDialog(
                onDismissRequest = { showLoadSettingsDialog = false },
                title = {
                    Text(
                        text = "Configure Load Node",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Set the expected semantic types for the loaded file (e.g. image/png, file/txt).",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        ToolkitTextField(
                            value = semanticTypesStr,
                            onValueChange = { semanticTypesStr = it },
                            label = { Text("Supported Semantic Types") },
                            placeholder = { Text("e.g. image/png, file/txt") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Add extensions constraint (!txt to allow semantics but forcefully reject txt)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        ToolkitTextField(
                            value = extensionsStr,
                            onValueChange = { extensionsStr = it },
                            label = { Text("Supported Extensions") },
                            placeholder = { Text("e.g. txt, json, !csv") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val parsed = parseSemanticTypes(semanticTypesStr)
                            val extensionsList = extensionsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            onUpdateSystemNodeSettings(
                                node.id,
                                "data",
                                parsed,
                                "file_path",
                                extensionsList.takeIf { it.isNotEmpty() })
                            showLoadSettingsDialog = false
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLoadSettingsDialog = false }) {
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
        is DataType.MapType -> "Map<String, ${formatDataType(type.valueType)}>"
        is DataType.Enum -> type.className.substringAfterLast('.')
        is DataType.Object -> type.className.substringAfterLast('.')
    }
}

private fun getPortValueString(value: Any?, type: DataType): String {
    if (value == null) return ""
    val jsonElement = org.wip.plugintoolkit.features.flows.model.NodeSerializationUtils.anyToJsonElement(value)
    return org.wip.plugintoolkit.features.plugin.utils.SettingsUtils.jsonToString(jsonElement, type)
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
                "comparator" -> "Compares two values (numeric or string) and outputs minor (<), major (>), equal (==), and not equal (!=) boolean results."
                "for" -> "Runs a subflow iteratively for a range of index values, accumulating data."
                "while" -> "Runs a subflow repeatedly while a boolean condition remains true."
                else -> "System operation: ${node.title}."
            }
        }

        is Node.FlowInputNode -> "Defines an input boundary for this flow."
        is Node.FlowOutputNode -> "Defines an output boundary for this flow."
        is Node.SubFlowNode -> "Executes the sub-flow '${node.flowName}' and maps its inputs/outputs."
    }
}

private fun parseColorString(colorStr: String): Color {
    val lastColor = colorStr.split(",").lastOrNull { it.trim().isNotEmpty() }?.trim() ?: colorStr
    val trimmed = lastColor.trim()
    if (trimmed.isEmpty()) return Color.Transparent
    if (trimmed.startsWith("#")) {
        return try {
            val hex = trimmed.substring(1)
            when (hex.length) {
                3 -> {
                    val r = hex[0].toString().repeat(2).toInt(16) / 255f
                    val g = hex[1].toString().repeat(2).toInt(16) / 255f
                    val b = hex[2].toString().repeat(2).toInt(16) / 255f
                    Color(r, g, b, 1f)
                }

                4 -> {
                    val r = hex[0].toString().repeat(2).toInt(16) / 255f
                    val g = hex[1].toString().repeat(2).toInt(16) / 255f
                    val b = hex[2].toString().repeat(2).toInt(16) / 255f
                    val a = hex[3].toString().repeat(2).toInt(16) / 255f
                    Color(r, g, b, a)
                }

                6 -> {
                    val r = hex.substring(0, 2).toInt(16) / 255f
                    val g = hex.substring(2, 4).toInt(16) / 255f
                    val b = hex.substring(4, 6).toInt(16) / 255f
                    Color(r, g, b, 1f)
                }

                8 -> {
                    val r = hex.substring(0, 2).toInt(16) / 255f
                    val g = hex.substring(2, 4).toInt(16) / 255f
                    val b = hex.substring(4, 6).toInt(16) / 255f
                    val a = hex.substring(6, 8).toInt(16) / 255f
                    Color(r, g, b, a)
                }

                else -> Color.Gray
            }
        } catch (e: Exception) {
            Color.Gray
        }
    }
    if (trimmed.startsWith("rgba", ignoreCase = true)) {
        return try {
            val parts = trimmed.substringAfter("(").substringBefore(")").split(",")
            val r = parts[0].trim().toFloat() / 255f
            val g = parts[1].trim().toFloat() / 255f
            val b = parts[2].trim().toFloat() / 255f
            val a = parts[3].trim().toFloat()
            Color(r, g, b, a)
        } catch (e: Exception) {
            Color.Gray
        }
    }
    if (trimmed.startsWith("rgb", ignoreCase = true)) {
        return try {
            val parts = trimmed.substringAfter("(").substringBefore(")").split(",")
            val r = parts[0].trim().toFloat() / 255f
            val g = parts[1].trim().toFloat() / 255f
            val b = parts[2].trim().toFloat() / 255f
            Color(r, g, b, 1f)
        } catch (e: Exception) {
            Color.Gray
        }
    }
    return when (trimmed.lowercase()) {
        "red" -> Color.Red
        "green" -> Color.Green
        "blue" -> Color.Blue
        "yellow" -> Color.Yellow
        "cyan" -> Color.Cyan
        "magenta" -> Color.Magenta
        "black" -> Color.Black
        "white" -> Color.White
        "gray" -> Color.Gray
        "transparent" -> Color.Transparent
        else -> Color.Gray
    }
}


private fun getFileName(path: String): String {
    if (path.isEmpty()) return ""
    return path.substringAfterLast('/').substringAfterLast('\\')
}

private fun appendPickedValue(existingValue: String, newValue: String, isArray: Boolean): String {
    if (!isArray) return newValue
    if (existingValue.isBlank()) return newValue
    val existingList = existingValue.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    if (newValue in existingList) return existingValue
    return (existingList + newValue).joinToString(", ")
}

private fun getFileNames(path: String, isArray: Boolean): String {
    if (path.isEmpty()) return ""
    if (!isArray) return getFileName(path)
    return path.split(",").map { it.trim() }.filter { it.isNotEmpty() }.map { getFileName(it) }.joinToString(", ")
}

