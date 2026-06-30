package org.wip.plugintoolkit.features.flows.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.api.SemanticType
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.model.PortConstraints
import org.wip.plugintoolkit.features.flows.viewmodel.ValidationError
import org.wip.plugintoolkit.shared.components.TooltipArea
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.node_parameters_section
import plugintoolkit.composeapp.generated.resources.node_results_section

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


    val scope = rememberCoroutineScope()
    var showColorPicker by remember { mutableStateOf(false) }
    var activeColorInputId by remember { mutableStateOf<String?>(null) }
    var inputLocationsCollapsed by remember(node.id) { mutableStateOf(node.isCollapsed) }
    var outputLocationsCollapsed by remember(node.id) { mutableStateOf(node.isCollapsed) }

    LaunchedEffect(node.isCollapsed) {
        inputLocationsCollapsed = node.isCollapsed
        outputLocationsCollapsed = node.isCollapsed
    }



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
                NodeHeader(
                    node = node,
                    headerColor = headerColor,
                    onHeaderColor = onHeaderColor,
                    isReady = isReady,
                    isReadOnly = isReadOnly,
                    onPress = currentOnPress,
                    onMove = currentOnMove,
                    onEndMove = currentOnEndMove,
                    onExpand = onExpand,
                    onToggleCollapse = onToggleCollapse,
                    onDelete = onDelete,
                    onShowDeleteConfirmation = { showDeleteConfirmation = true },
                    onShowLoadSettings = { showLoadSettingsDialog = true },
                    onShowEditBoundary = { showEditBoundaryDialog = true }
                )

                // Body
                // Body
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(ToolkitTheme.spacing.mediumSmall),
                    verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.mediumSmall)
                ) {
                    val capNode = node as? Node.CapabilityNode
                    val visibleOutputs = node.outputs.filter { output ->
                        capNode?.capability?.parameters?.get(output.id)?.role != org.wip.plugintoolkit.api.ParameterRole.OUTPUT_LOCATION
                    }
                    val parameters =
                        node.inputs.filter { capNode?.capability?.parameters?.get(it.id)?.role.let { r -> r == null || r == org.wip.plugintoolkit.api.ParameterRole.STANDARD } }
                    val inputLocations =
                        node.inputs.filter { capNode?.capability?.parameters?.get(it.id)?.role == org.wip.plugintoolkit.api.ParameterRole.INPUT_LOCATION }
                    val outputLocations =
                        node.inputs.filter { capNode?.capability?.parameters?.get(it.id)?.role == org.wip.plugintoolkit.api.ParameterRole.OUTPUT_LOCATION }

                    val inputSections = listOf(
                        stringResource(Res.string.node_parameters_section) to parameters,
                        "Input Locations" to inputLocations, //TODO: localize
                        "Output Locations" to outputLocations //TODO: localize
                    ).filter { it.second.isNotEmpty() }

                    inputSections.forEachIndexed { index, (title, sectionInputs) ->
                        val isSectionCollapsed = when (title) {
                            "Input Locations" -> inputLocationsCollapsed
                            "Output Locations" -> outputLocationsCollapsed
                            else -> node.isInputsCollapsed
                        }

                        if (index > 0) {
                            HorizontalDivider(
                                Modifier.padding(vertical = ToolkitTheme.spacing.small),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                when (title) {
                                    "Input Locations" -> inputLocationsCollapsed = !inputLocationsCollapsed
                                    "Output Locations" -> outputLocationsCollapsed = !outputLocationsCollapsed
                                    else -> onToggleInputsCollapse(node.id)
                                }
                            },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isSectionCollapsed) {
                                    PortCircle(
                                        color = headerColor,
                                        isHighlighted = false,
                                        onDragStart = {}, onDrag = {}, onDragEnd = {},
                                        modifier = Modifier.onGloballyPositioned { coords ->
                                            if (boardLayoutCoordinates != null) {
                                                val center =
                                                    boardLayoutCoordinates.localBoundingBoxOf(coords, false).center
                                                sectionInputs.forEach { input ->
                                                    onPortPositioned(node.id, input.id, center)
                                                }
                                            }
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                                }
                                Text(
                                    title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isSectionCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Toggle $title"
                                )
                                if (isSectionCollapsed && title == "Output Locations") {
                                    Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                                    PortCircle(
                                        color = headerColor,
                                        isHighlighted = false,
                                        onDragStart = {}, onDrag = {}, onDragEnd = {},
                                        modifier = Modifier.onGloballyPositioned { coords ->
                                            if (boardLayoutCoordinates != null) {
                                                val center =
                                                    boardLayoutCoordinates.localBoundingBoxOf(coords, false).center
                                                sectionInputs.forEach { input ->
                                                    val correspondingOutput = node.outputs.find { it.id == input.id }
                                                    if (correspondingOutput != null) {
                                                        onPortPositioned(node.id, correspondingOutput.id, center)
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        if (!isSectionCollapsed) {
                            sectionInputs.forEach { input ->

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
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        PortCircle(
                                            color = if (highlightedPortId == input.id) (highlightedPortColor
                                                ?: headerColor) else headerColor,
                                            isHighlighted = highlightedPortId == input.id,
                                            onDragStart = {
                                                if (!isReadOnly) onStartConnection(
                                                    node.id,
                                                    input.id,
                                                    false
                                                )
                                            },
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
                                            val isRequired =
                                                (node as? Node.CapabilityNode)?.capability?.parameters?.get(input.id)?.required == true
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
                                    val isConnected = connectedInputPortIds.contains(input.id)
                                    val valueModifier = if (isConnected) Modifier.alpha(0.5f) else Modifier

                                    NodePropertyEditor(
                                        node = node,
                                        input = input,
                                        currentPortValue = currentPortValue,
                                        isConnected = isConnected,
                                        isReadOnly = isReadOnly,
                                        portErrors = portErrors,
                                        inferredSem = inferredSemanticTypes[Pair(node.id, input.id)]
                                            ?: input.semanticTypes,
                                        onUpdateValue = onUpdateValue,
                                        onFocusLost = onFocusLost,
                                        onShowColorPicker = { inputId ->
                                            activeColorInputId = inputId
                                            showColorPicker = true
                                        }
                                    )

                                    val correspondingOutput = node.outputs.find { it.id == input.id }
                                    val isOutputLocation =
                                        (node as? Node.CapabilityNode)?.capability?.parameters?.get(input.id)?.role == org.wip.plugintoolkit.api.ParameterRole.OUTPUT_LOCATION
                                    if (isOutputLocation && correspondingOutput != null) {
                                        Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                                        PortCircle(
                                            color = if (highlightedPortId == correspondingOutput.id) (highlightedPortColor
                                                ?: headerColor) else headerColor,
                                            isHighlighted = highlightedPortId == correspondingOutput.id,
                                            onDragStart = {
                                                if (!isReadOnly) onStartConnection(
                                                    node.id,
                                                    correspondingOutput.id,
                                                    true
                                                )
                                            },
                                            onDrag = { if (!isReadOnly) onDragConnection(it) },
                                            onDragEnd = { if (!isReadOnly) onDropConnection(it) },
                                            modifier = Modifier.onGloballyPositioned { coords ->
                                                if (boardLayoutCoordinates != null) {
                                                    val center =
                                                        boardLayoutCoordinates.localBoundingBoxOf(coords, false).center
                                                    onPortPositioned(node.id, correspondingOutput.id, center)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (node.inputs.isNotEmpty() && visibleOutputs.isNotEmpty()) {
                        HorizontalDivider(
                            Modifier,
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }

                    // Outputs Section
                    // Outputs Section
                    if (visibleOutputs.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onToggleOutputsCollapse(node.id) },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                stringResource(Res.string.node_results_section),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (node.isOutputsCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Toggle Outputs" //TODO: localize
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
                        visibleOutputs.forEach { output ->

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

    }

    NodeDialogs(
        node = node,
        inferredSemanticTypes = inferredSemanticTypes,
        showDeleteConfirmation = showDeleteConfirmation,
        onDismissDelete = { showDeleteConfirmation = false },
        onConfirmDelete = { onDelete(node.id); showDeleteConfirmation = false },
        showEditBoundaryDialog = showEditBoundaryDialog,
        onDismissEditBoundary = { showEditBoundaryDialog = false },
        onUpdateBoundaryNode = onUpdateBoundaryNode,
        showColorPicker = showColorPicker,
        activeColorInputId = activeColorInputId,
        onDismissColorPicker = { showColorPicker = false; activeColorInputId = null },
        onUpdateValue = onUpdateValue,
        showLoadSettingsDialog = showLoadSettingsDialog,
        onDismissLoadSettings = { showLoadSettingsDialog = false },
        onUpdateSystemNodeSettings = onUpdateSystemNodeSettings
    )
}


